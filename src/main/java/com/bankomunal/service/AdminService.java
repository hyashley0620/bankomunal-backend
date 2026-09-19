package com.bankomunal.service;

import com.bankomunal.dto.response.*;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AdminService {

        private final UserRepository userRepository;
        private final LoanRepository loanRepository;
        private final TransactionRepository transactionRepository;
        private final PasswordEncoder passwordEncoder;
        private final RoleRepository roleRepository;
        private final RolePermissionRepository rolePermissionRepository;

        private static final List<String> MODULOS = List.of(
                        "usuarios", "gestion-prestamos", "reportes-financieros",
                        "auditoria", "roles-permisos", "respaldo-recuperacion");

        public List<AdminUserResponse> getUsuarios() {
                return userRepository.findAll().stream().map(u -> AdminUserResponse.builder()
                                .id(u.getId())
                                .nombre(u.getFirstName())
                                .apellido(u.getLastName() != null ? u.getLastName() : "")
                                .email(u.getEmail())
                                .cedula(u.getIdentificationNumber())
                                .telefono(u.getPhone())
                                .ciudad(u.getCiudad())
                                .estado(u.getStatus().name())
                                .rol(u.getRoles().stream().findFirst().map(Role::getName).orElse("socio"))
                                .fechaRegistro(u.getCreatedAt())
                                .puntos(u.getPuntos())
                                .nivel(u.getNivel())
                                .build()).toList();
        }

        /** Obtener detalle de un usuario por ID — incluye resumen de sus préstamos (ficha de socio) */
        public AdminUserResponse getUsuarioById(Long id) {
                User u = userRepository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                List<Loan> prestamos = loanRepository.findByBorrowerUserId(id);
                long activos = prestamos.stream()
                                .filter(l -> l.getStatus() == Loan.LoanStatus.active).count();
                BigDecimal saldoTotal = prestamos.stream()
                                .filter(l -> l.getStatus() == Loan.LoanStatus.active)
                                .map(l -> l.getSaldoPendiente() != null ? l.getSaldoPendiente() : BigDecimal.ZERO)
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                return AdminUserResponse.builder()
                                .id(u.getId())
                                .nombre(u.getFirstName())
                                .apellido(u.getLastName() != null ? u.getLastName() : "")
                                .email(u.getEmail())
                                .cedula(u.getIdentificationNumber())
                                .telefono(u.getPhone())
                                .ciudad(u.getCiudad())
                                .ocupacion(u.getOcupacion())
                                .estado(u.getStatus().name())
                                .rol(u.getRoles().stream().findFirst().map(Role::getName).orElse("socio"))
                                .fechaRegistro(u.getCreatedAt())
                                .puntos(u.getPuntos())
                                .nivel(u.getNivel())
                                .creditScore(u.getCreditScore())
                                .totalPrestamos(prestamos.size())
                                .prestamosActivos((int) activos)
                                .saldoPendienteTotal(saldoTotal)
                                .build();
        }

        /** Crear usuario desde panel admin */
        @Transactional
        public AdminUserResponse crearUsuario(java.util.Map<String, String> body) {
                String email = body.getOrDefault("email", "").trim();
                if (email.isEmpty())
                        throw new IllegalArgumentException("El email es requerido.");
                if (userRepository.findByEmail(email).isPresent())
                        throw new IllegalArgumentException("Ya existe un usuario con ese email.");

                User u = User.builder()
                                .firstName(body.getOrDefault("nombres", "").trim())
                                .lastName(body.getOrDefault("apellidos", body.getOrDefault("apellido", "")).trim())
                                .email(email)
                                .identificationNumber(body.getOrDefault("documento", body.getOrDefault("cedula", null)))
                                .phone(body.getOrDefault("telefono", null))
                                .passwordHash(passwordEncoder.encode(
                                                body.getOrDefault("password", "Temporal2024!")))
                                .status(User.UserStatus.active)
                                .build();

                // Asignar rol
                String rolNombre = body.getOrDefault("rol", "SOCIO").toUpperCase();
                roleRepository.findByName(rolNombre.toLowerCase()).ifPresent(r -> u.getRoles().add(r));

                userRepository.save(u);

                return AdminUserResponse.builder()
                                .id(u.getId()).nombre(u.getFirstName())
                                .apellido(u.getLastName() != null ? u.getLastName() : "")
                                .email(u.getEmail()).cedula(u.getIdentificationNumber())
                                .estado(u.getStatus().name())
                                .rol(rolNombre)
                                .build();
        }

        @Transactional
        public AdminUserResponse updateEstado(Long userId, String estado) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
                user.setStatus(User.UserStatus.valueOf(estado));
                userRepository.save(user);
                return AdminUserResponse.builder()
                                .id(user.getId()).nombre(user.getFirstName())
                                .email(user.getEmail()).estado(user.getStatus().name()).build();
        }

        /**
         * PATCH /api/admin/usuarios/{id}/rol
         * Cambia el rol de un usuario ya existente. El sistema asigna un único
         * rol principal por usuario (igual que crearUsuario), así que se
         * limpian los roles previos antes de asignar el nuevo.
         */
        @Transactional
        public AdminUserResponse cambiarRol(Long userId, String nuevoRol) {
                if (nuevoRol == null || nuevoRol.isBlank())
                        throw new IllegalArgumentException("El rol es requerido.");

                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));

                Role rol = roleRepository.findByName(nuevoRol.toLowerCase())
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "El rol '" + nuevoRol + "' no existe."));

                user.getRoles().clear();
                user.getRoles().add(rol);
                userRepository.save(user);

                return AdminUserResponse.builder()
                                .id(user.getId())
                                .nombre(user.getFirstName())
                                .apellido(user.getLastName() != null ? user.getLastName() : "")
                                .email(user.getEmail())
                                .cedula(user.getIdentificationNumber())
                                .estado(user.getStatus().name())
                                .rol(rol.getName().toUpperCase())
                                .build();
        }

        /**
         * Préstamos institucionales para la gestión del admin. Se excluyen a
         * propósito los préstamos solidarios (fundingSource=group_fund): esos
         * no los aprueba ni rechaza un admin, los decide la votación de los
         * socios del grupo (ver GroupLoanController/GroupLoanService).
         */
        public List<AdminLoanResponse> getPrestamos() {
                return loanRepository.findAllByOrderByCreatedAtDesc().stream()
                                .filter(l -> l.getFundingSource() != Loan.FundingSource.group_fund)
                                .map(l -> AdminLoanResponse.builder()
                                .id(l.getId()).loanCode(l.getLoanCode())
                                .solicitante(l.getBorrowerUser().getFirstName() + " " +
                                                (l.getBorrowerUser().getLastName() != null
                                                                ? l.getBorrowerUser().getLastName()
                                                                : ""))
                                .emailSolicitante(l.getBorrowerUser().getEmail())
                                .montoSolicitado(l.getMontoSolicitado())
                                .plazoMeses(l.getPlazoMeses())
                                .cuotaMensual(l.getCuotaMensual())
                                .estado(l.getStatus().name())
                                .fechaSolicitud(l.getCreatedAt())
                                .build()).toList();
        }

        public AdminReporteResponse getReporte(LocalDateTime inicio, LocalDateTime fin) {
                if (inicio == null)
                        inicio = LocalDateTime.now().minusMonths(6);
                if (fin == null)
                        fin = LocalDateTime.now();
                List<String> labels = new ArrayList<>();
                List<BigDecimal> ingresos = new ArrayList<>();
                List<BigDecimal> egresos = new ArrayList<>();
                LocalDateTime cursor = inicio.withDayOfMonth(1).toLocalDate().atStartOfDay();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM yyyy");
                while (!cursor.isAfter(fin)) {
                        LocalDateTime finMes = cursor.plusMonths(1);
                        labels.add(cursor.format(fmt));
                        ingresos.add(transactionRepository.sumIngresosByDateRange(cursor, finMes));
                        egresos.add(transactionRepository.sumEgresosByDateRange(cursor, finMes));
                        cursor = finMes;
                }
                // Calcular montoCartera (suma de saldos de préstamos activos)
                java.math.BigDecimal montoCartera = java.math.BigDecimal.ZERO;
                double indiceMora = 0.0;
                try {
                        List<com.bankomunal.entity.Loan> prestamosActivos = loanRepository.findAll().stream()
                                        .filter(l -> l.getStatus() == com.bankomunal.entity.Loan.LoanStatus.active)
                                        .toList();
                        montoCartera = prestamosActivos.stream()
                                        .map(l -> l.getSaldoPendiente() != null ? l.getSaldoPendiente()
                                                        : java.math.BigDecimal.ZERO)
                                        .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
                        long enMora = prestamosActivos.stream()
                                        .filter(l -> l.getStatus() == com.bankomunal.entity.Loan.LoanStatus.defaulted ||
                                        // Calcular vencimiento aproximado: aprobacion + plazo en meses
                                                        (l.getApprovedAt() != null && l.getPlazoMeses() != null &&
                                                                        l.getApprovedAt().plusMonths(l.getPlazoMeses())
                                                                                        .isBefore(LocalDateTime.now())))
                                        .count();
                        indiceMora = prestamosActivos.isEmpty() ? 0 : (enMora * 100.0 / prestamosActivos.size());
                } catch (Exception ignored) {
                }

                return AdminReporteResponse.builder()
                                .labels(labels).ingresos(ingresos).egresos(egresos)
                                .totalMovimientos(transactionRepository.count())
                                .totalUsuarios(userRepository.count())
                                .totalPrestamos(loanRepository.count())
                                .montoCartera(montoCartera)
                                .indiceMora(indiceMora)
                                .carteraTotal(montoCartera)
                                .sociosActivos(userRepository.findAll().stream()
                                                .filter(u -> u.getStatus() == com.bankomunal.entity.User.UserStatus.active)
                                                .count())
                                .prestamosActivos(loanRepository.findAll().stream()
                                                .filter(l -> l.getStatus() == com.bankomunal.entity.Loan.LoanStatus.active)
                                                .count())
                                .tasaPago(100.0 - indiceMora)
                                .tendenciaCartera(String.format("%.1f%%", indiceMora > 5 ? -indiceMora : 2.3))
                                .tendenciaMora(String.format("%.1f%%", indiceMora))
                                .tendenciaSocios("+")
                                .build();
        }

        @Transactional
        public MessageResponse cambiarPassword(Long userId, String passwordNueva) {
                User user = userRepository.findById(userId)
                                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
                user.setPasswordHash(passwordEncoder.encode(passwordNueva));
                userRepository.save(user);
                return new MessageResponse("Contraseña actualizada.");
        }

        /** Crear rol personalizado desde panel de administración */
        @Transactional
        public Map<String, Object> crearRol(String nombre, String descripcion) {
                if (roleRepository.findByName(nombre).isPresent())
                        throw new IllegalArgumentException("Ya existe un rol con el nombre '" + nombre + "'.");
                Role rol = roleRepository.save(Role.builder()
                                .name(nombre.toLowerCase().replaceAll("\\s+", "_"))
                                .description(descripcion)
                                .build());
                return Map.of(
                                "id", rol.getId(),
                                "nombre", rol.getName(),
                                "descripcion", rol.getDescription() != null ? rol.getDescription() : "",
                                "mensaje", "Rol '" + nombre + "' creado exitosamente.");
        }

        /** Listar todos los roles */
        public List<Map<String, Object>> getRoles() {
                return roleRepository.findAll().stream().map(r -> Map.<String, Object>of(
                                "id", r.getId(),
                                "nombre", r.getName(),
                                "descripcion", r.getDescription() != null ? r.getDescription() : "")).toList();
        }

        /**
         * Permisos de un rol, módulo por módulo. Si el rol todavía no tiene
         * permisos guardados en base de datos (primera vez que se abre), se
         * calculan valores por defecto razonables sin persistirlos — recién se
         * guardan cuando el admin presiona "Aplicar cambios".
         */
        public List<Map<String, Object>> getPermisosPorRol(Long roleId) {
                Role rol = roleRepository.findById(roleId)
                                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado."));
                List<RolePermission> guardados = rolePermissionRepository.findByRoleId(roleId);
                Map<String, RolePermission> porModulo = new HashMap<>();
                guardados.forEach(p -> porModulo.put(p.getModulo(), p));

                // Los roles base (admin/tesorero/secretario/auditor) ya vienen con
                // permisos reales sembrados por RolePermissionSeeder. Para un rol
                // nuevo que el admin acabe de crear y todavía no tenga fila guardada,
                // el valor por defecto es "denegado" — más seguro que asumir acceso.
                boolean esAdmin = "admin".equalsIgnoreCase(rol.getName());
                return MODULOS.stream().map(m -> {
                        RolePermission p = porModulo.get(m);
                        Map<String, Object> mapa = new LinkedHashMap<>();
                        mapa.put("modulo", m);
                        mapa.put("leer", p != null ? p.isLeer() : esAdmin);
                        mapa.put("crear", p != null ? p.isCrear() : esAdmin);
                        mapa.put("editar", p != null ? p.isEditar() : esAdmin);
                        mapa.put("borrar", p != null ? p.isBorrar() : esAdmin);
                        return mapa;
                }).toList();
        }

        /** Guarda (crea o actualiza) los permisos de un rol, módulo por módulo. */
        @Transactional
        public MessageResponse guardarPermisosPorRol(Long roleId, List<Map<String, Object>> permisos) {
                Role rol = roleRepository.findById(roleId)
                                .orElseThrow(() -> new IllegalArgumentException("Rol no encontrado."));

                for (Map<String, Object> p : permisos) {
                        String modulo = String.valueOf(p.get("modulo"));
                        RolePermission existente = rolePermissionRepository
                                        .findByRoleIdAndModulo(roleId, modulo)
                                        .orElse(RolePermission.builder().role(rol).modulo(modulo).build());
                        existente.setLeer(Boolean.TRUE.equals(p.get("leer")));
                        existente.setCrear(Boolean.TRUE.equals(p.get("crear")));
                        existente.setEditar(Boolean.TRUE.equals(p.get("editar")));
                        existente.setBorrar(Boolean.TRUE.equals(p.get("borrar")));
                        rolePermissionRepository.save(existente);
                }
                return new MessageResponse("Permisos de '" + rol.getName() + "' actualizados correctamente.");
        }
}