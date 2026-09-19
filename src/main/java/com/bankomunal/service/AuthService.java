package com.bankomunal.service;

import com.bankomunal.dto.request.*;
import com.bankomunal.dto.response.*;
import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import com.bankomunal.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Servicio de autenticación: registro, login, recuperación y reset de
 * contraseña.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final AccountRepository accountRepository;
    private final PasswordResetTokenRepository resetTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuditService auditService;
    private final EmailService emailService;
    private final DocumentService documentService;
    private final UserSessionRepository userSessionRepository;

    // ─── Registro con documentos (multipart) ──────────────────────────

    @Transactional
    public MessageResponse register(RegisterRequest req,
            MultipartFile cedulaFrontal,
            MultipartFile cedulaPosterior,
            MultipartFile selfieCedula) {

        String email = req.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email))
            throw new IllegalArgumentException("El correo ya está registrado.");

        // Compatibilidad con ambas versiones del DTO (nombre/nombres, cedula/documento)
        String nombres = req.getNombres() != null ? req.getNombres() : (req.getNombre() != null ? req.getNombre() : "");
        String apellidos = req.getApellidos() != null ? req.getApellidos() : "";
        String documento = req.getDocumento() != null ? req.getDocumento()
                : (req.getCedula() != null ? req.getCedula() : "");
        String tipoDoc = req.getTipoDoc() != null ? req.getTipoDoc() : "CC";

        if (nombres.isBlank())
            throw new IllegalArgumentException("El nombre es obligatorio.");
        if (documento.isBlank())
            throw new IllegalArgumentException("El número de documento es obligatorio.");
        if (userRepository.existsByIdentificationNumber(documento))
            throw new IllegalArgumentException("El número de identificación ya está registrado.");

        Role rolSocio = roleRepository.findByName("socio")
                .orElseGet(() -> roleRepository.save(
                        Role.builder().name("socio").description("Socio").build()));

        User user = User.builder()
                .firstName(nombres).lastName(apellidos)
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .tipoDocumento(tipoDoc)
                .identificationNumber(documento)
                .phone(req.getTelefono()).genero(req.getGenero())
                .fechaNacimiento(req.getFechaNacimiento())
                .direccion(req.getDireccion()).ciudad(req.getCiudad())
                .departamento(req.getDepartamento()).ocupacion(req.getOcupacion())
                .status(User.UserStatus.active)
                .autorizaDatos(req.isAutorizaDatos())
                .roles(new HashSet<>(Set.of(rolSocio)))
                .build();

        userRepository.save(user);

        // Guardar documentos adjuntos si vienen en el request
        guardarDocumento(cedulaFrontal, "frontal", user);
        guardarDocumento(cedulaPosterior, "posterior", user);
        guardarDocumento(selfieCedula, "selfie", user);
        userRepository.save(user);

        auditService.log(user, "USER_REGISTERED", "User", user.getId(), "system", "Registro completado");

        String code = "BKM-"
                + nombres.toUpperCase().replaceAll("\\s+", "")
                        .substring(0, Math.min(6, nombres.length()))
                + "-" + (System.currentTimeMillis() % 10000);

        accountRepository.save(Account.builder()
                .accountCode(code)
                .accountType(Account.AccountType.individual)
                .ownerUser(user)
                .balance(BigDecimal.ZERO)
                .currency("COP")
                .status(Account.AccountStatus.active)
                .build());

        return new MessageResponse("Registro exitoso. Ya puedes iniciar sesión.");
    }

    // ─── Login con bloqueo por intentos ───────────────────────────────

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public LoginResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Credenciales incorrectas."));

        if (user.getStatus() == User.UserStatus.pending)
            throw new IllegalArgumentException("Tu cuenta está pendiente de aprobación. Contacta al administrador.");
        if (user.getStatus() == User.UserStatus.suspended)
            throw new IllegalArgumentException("Tu cuenta ha sido suspendida. Contacta al administrador.");
        if (user.getStatus() == User.UserStatus.blocked)
            throw new IllegalArgumentException("Tu cuenta ha sido bloqueada. Contacta al administrador.");
        if (user.getStatus() == User.UserStatus.deleted)
            throw new IllegalArgumentException("Credenciales incorrectas.");

        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now()))
            throw new IllegalArgumentException("Cuenta bloqueada temporalmente. Intenta más tarde.");

        if (!passwordEncoder.matches(req.getPassword(), user.getPasswordHash())) {
            user.setFailedLoginAttempts(user.getFailedLoginAttempts() + 1);
            user.setLastFailedLogin(LocalDateTime.now());
            if (user.getFailedLoginAttempts() >= 5)
                user.setLockedUntil(LocalDateTime.now().plusMinutes(30));
            userRepository.save(user);
            auditService.log(user, "LOGIN_FAILED", "User", user.getId(), req.getIp(), "Contraseña incorrecta");
            throw new IllegalArgumentException("Credenciales incorrectas.");
        }

        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        userRepository.save(user);

        if (user.isMfaEnabled()) {
            String codigo = String.valueOf(100000 + new java.security.SecureRandom().nextInt(900000));
            user.setMfaCode(codigo);
            user.setMfaExpiresAt(LocalDateTime.now().plusMinutes(10));
            userRepository.save(user);
            emailService.enviarCodigoMfa(user.getEmail(), codigo, user.getFirstName());
            auditService.log(user, "LOGIN_MFA_SENT", "User", user.getId(), req.getIp(),
                    "Código de verificación en dos pasos enviado por correo");

            return LoginResponse.builder()
                    .mfaRequired(true)
                    .mfaEnabled(true)
                    .email(user.getEmail())
                    .build();
        }

        return emitirSesion(user, req.getIp(), req.getUserAgent());
    }

    /** Construye la sesión completa (token + datos de usuario) tras un login exitoso. */
    private LoginResponse emitirSesion(User user, String ip, String userAgent) {
        Role rolEntity = user.getRoles().stream().findFirst().orElse(null);
        String rol = rolEntity != null ? rolEntity.getName() : "socio";
        String token = jwtUtil.generateToken(user.getEmail(), rol);

        user.setActiveToken(token);
        userRepository.save(user);

        // Registro real del inicio de sesión.
        userSessionRepository.save(UserSession.builder()
                .user(user)
                .tokenHash(jwtUtil.hash(token))
                .ipAddress(ip)
                .userAgent(userAgent)
                .expiresAt(LocalDateTime.now().plus(jwtUtil.getExpirationMs(), java.time.temporal.ChronoUnit.MILLIS))
                .build());

        auditService.log(user, "LOGIN_SUCCESS", "User", user.getId(), ip, "Inicio de sesión exitoso");

        BigDecimal saldo = accountRepository
                .findFirstByOwnerUserIdAndAccountTypeAndStatus(
                        user.getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .map(Account::getBalance).orElse(BigDecimal.ZERO);

        String cuenta = accountRepository
                .findFirstByOwnerUserIdAndAccountTypeAndStatus(
                        user.getId(), Account.AccountType.individual, Account.AccountStatus.active)
                .map(Account::getAccountCode).orElse("");

        String iniciales = buildIniciales(user);
        String nombreCorto = buildNombreCorto(user);
        List<Map<String, Object>> permisos = calcularPermisos(rolEntity);

        return LoginResponse.builder()
                .token(token)
                .id(user.getId())
                .nombre(user.getFirstName()
                        + (user.getLastName() != null ? " " + user.getLastName() : ""))
                .nombreCorto(nombreCorto)
                .email(user.getEmail())
                .rol(rol)
                .permisos(permisos)
                .genero(user.getGenero())
                .cuenta(cuenta)
                .iniciales(iniciales)
                .saldoTotal(saldo)
                .mfaRequired(false)
                .fotoUrl(normalizarFotoUrl(user.getSelfiePath()))
                .createdAt(user.getCreatedAt() != null ? user.getCreatedAt().toString() : null)
                .mfaEnabled(user.isMfaEnabled())
                .build();
    }

    /**
     * POST /api/auth/mfa/verify — segundo paso del login cuando el socio tiene
     * activada la verificación en dos pasos. Valida el código OTP enviado por
     * correo y, si es correcto y no ha expirado, emite la sesión completa.
     */
    @Transactional
    public LoginResponse verifyMfa(MfaRequest req, String ip, String userAgent) {
        User user = userRepository.findByEmail(req.getEmail().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Código incorrecto o expirado."));

        boolean valido = user.getMfaCode() != null
                && user.getMfaCode().equals(req.getCodigo() == null ? null : req.getCodigo().trim())
                && user.getMfaExpiresAt() != null
                && user.getMfaExpiresAt().isAfter(LocalDateTime.now());

        if (!valido) {
            auditService.log(user, "LOGIN_MFA_FAILED", "User", user.getId(), ip, "Código de verificación incorrecto o expirado");
            throw new IllegalArgumentException("Código incorrecto o expirado.");
        }

        user.setMfaCode(null);
        user.setMfaExpiresAt(null);
        userRepository.save(user);

        return emitirSesion(user, ip, userAgent);
    }

    /**
     * POST /api/auth/logout — invalida la sesión del lado del servidor:
     * limpia el activeToken (para que JwtAuthFilter rechace ese token en su
     * próxima petición, aunque no haya expirado todavía) y cierra el
     * registro correspondiente en user_sessions.
     */
    @Transactional
    public MessageResponse logout(User user, String token) {
        user.setActiveToken(null);
        userRepository.save(user);

        if (token != null) {
            userSessionRepository.findByTokenHash(jwtUtil.hash(token))
                    .ifPresent(s -> {
                        s.setExpiresAt(LocalDateTime.now());
                        userSessionRepository.save(s);
                    });
        }

        auditService.log(user, "LOGOUT", "User", user.getId(), "system", "Cierre de sesión");
        return new MessageResponse("Sesión cerrada correctamente.");
    }

    // ─── Solicitar recuperación — genera token y envía email ─────────

    @Transactional
    public MessageResponse recoverPassword(RecoverPasswordRequest req) {
        // Siempre devolvemos el mismo mensaje (seguridad: no revelar si el correo
        // existe)
        userRepository.findByEmail(req.getEmail().trim().toLowerCase()).ifPresent(user -> {
            resetTokenRepository.deleteByUserId(user.getId());

            PasswordResetToken prt = PasswordResetToken.builder()
                    .user(user)
                    .token(UUID.randomUUID().toString())
                    .expiresAt(LocalDateTime.now().plusHours(2))
                    .build();
            resetTokenRepository.save(prt);

            // Enviar correo real con el enlace de recuperación
            String nombre = user.getFirstName() != null ? user.getFirstName() : "Usuario";
            emailService.enviarCorreoRecuperacion(user.getEmail(), prt.getToken(), nombre);

            auditService.log(user, "PASSWORD_RECOVERY_REQUESTED", "User", user.getId(),
                    "system", "Solicitud de recuperación enviada");
        });

        return new MessageResponse(
                "Si el correo está registrado, recibirás instrucciones de recuperación en los próximos minutos.");
    }

    // ─── Restablecer contraseña con el token del email ───────────────

    @Transactional
    public MessageResponse resetPassword(ResetPasswordRequest req) {
        PasswordResetToken prt = resetTokenRepository
                .findByTokenAndUsedFalse(req.getToken())
                .orElseThrow(() -> new IllegalArgumentException("Token inválido o ya utilizado."));

        if (prt.getExpiresAt().isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("El enlace ha expirado. Solicita uno nuevo.");

        User user = prt.getUser();
        user.setPasswordHash(passwordEncoder.encode(req.getPassword()));
        user.setActiveToken(null); // invalidar todas las sesiones activas
        userRepository.save(user);
        cerrarSesionesDeUsuario(user);

        prt.setUsed(true);
        resetTokenRepository.save(prt);

        auditService.log(user, "PASSWORD_RESET_SUCCESS", "User", user.getId(),
                "system", "Contraseña restablecida correctamente");

        return new MessageResponse("Contraseña actualizada correctamente. Ya puedes iniciar sesión.");
    }

    // ─── Helpers privados ────────────────────────────────────────────────────

    /**
     * Marca como terminadas (expiresAt = ahora) todas las sesiones aún
     * vigentes del usuario en user_sessions — usado junto con cualquier
     * acción que ya limpia activeToken (reset de contraseña) para que el
     * historial de sesiones no siga mostrando como "activa" una sesión cuyo
     * token ya fue invalidado por otra vía.
     */
    private void cerrarSesionesDeUsuario(User user) {
        LocalDateTime ahora = LocalDateTime.now();
        for (UserSession s : userSessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (s.getExpiresAt() != null && s.getExpiresAt().isAfter(ahora)) {
                s.setExpiresAt(ahora);
                userSessionRepository.save(s);
            }
        }
    }

    /**
     * Guarda el archivo tanto en la ruta legacy (uploads/cedulas + campo del
     * User, usado como fotoUrl de perfil en el caso de la selfie) COMO un
     * registro real en el sistema de Documentos (DocumentService/Document
     * entity).
     */
    private void guardarDocumento(MultipartFile file, String tipo, User user) {
        if (file == null || file.isEmpty())
            return;
        try {
            String ext = getExtension(file.getOriginalFilename());
            String filename = tipo + "_" + user.getId() + "_" + UUID.randomUUID() + "." + ext;
            Path uploads = Paths.get("uploads", "cedulas");
            Files.createDirectories(uploads);
            Files.copy(file.getInputStream(), uploads.resolve(filename),
                    StandardCopyOption.REPLACE_EXISTING);
            String url = "/uploads/cedulas/" + filename;
            switch (tipo) {
                case "frontal" -> user.setCedulaFrontalPath(url);
                case "posterior" -> user.setCedulaPosteriorPath(url);
                case "selfie" -> user.setSelfiePath(url);
            }
        } catch (Exception e) {
            // Log pero no fallar el registro por un documento
        }

        try {
            String nombreAmigable = switch (tipo) {
                case "frontal" -> "Cédula - Foto frontal";
                case "posterior" -> "Cédula - Foto posterior";
                case "selfie" -> "Selfie con cédula";
                default -> "Documento de identidad";
            };
            documentService.subir(file, nombreAmigable, "identidad", user);
        } catch (Exception e) {
            // Log pero no fallar el registro por un documento
        }
    }

    /**
     * Normaliza URLs de foto almacenadas con formatos legacy:
     * "fotos/foto_2_xxx.jpg" → "/uploads/fotos/foto_2_xxx.jpg"
     * "/fotos/foto_2_xxx.jpg" → "/uploads/fotos/foto_2_xxx.jpg"
     * "/uploads/fotos/foto_2_xxx.jpg" → "/uploads/fotos/foto_2_xxx.jpg"
     * null / "" → null
     */
    private String normalizarFotoUrl(String raw) {
        if (raw == null || raw.isBlank())
            return null;
        if (raw.startsWith("/uploads/"))
            return raw;
        if (raw.startsWith("http"))
            return raw;
        if (raw.startsWith("/fotos/"))
            return "/uploads" + raw;
        if (raw.startsWith("fotos/"))
            return "/uploads/" + raw;
        return "/uploads/" + raw;
    }

    /** Módulos administrativos del sidebar — deben coincidir con AdminService.MODULOS. */
    private static final List<String> MODULOS_ADMIN = List.of(
            "usuarios", "gestion-prestamos", "reportes-financieros",
            "auditoria", "roles-permisos", "respaldo-recuperacion");

    /**
     * Permisos del rol del usuario sobre cada módulo administrativo — esto es
     * lo que el sidebar usa para decidir qué páginas mostrarle. Si el rol no
     * tiene ninguna fila guardada en `role_permissions` (rol recién creado sin
     * configurar), el valor por defecto es denegado, salvo para "admin".
     */
    private List<Map<String, Object>> calcularPermisos(Role rol) {
        if (rol == null)
            return List.of();
        boolean esAdmin = "admin".equalsIgnoreCase(rol.getName());
        Map<String, RolePermission> guardados = new HashMap<>();
        rolePermissionRepository.findByRoleId(rol.getId()).forEach(p -> guardados.put(p.getModulo(), p));

        return MODULOS_ADMIN.stream().map(m -> {
            RolePermission p = guardados.get(m);
            Map<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("modulo", m);
            mapa.put("leer", p != null ? p.isLeer() : esAdmin);
            mapa.put("crear", p != null ? p.isCrear() : esAdmin);
            mapa.put("editar", p != null ? p.isEditar() : esAdmin);
            mapa.put("borrar", p != null ? p.isBorrar() : esAdmin);
            return mapa;
        }).toList();
    }

    /** Primera palabra de un texto (ej. "Juan Carlos" → "Juan"). */
    private String primeraPalabra(String texto) {
        if (texto == null || texto.isBlank())
            return "";
        return texto.trim().split("\\s+")[0];
    }

    /**
     * "Primer nombre + primer apellido" — para mostrar en el header al lado
     * del avatar. A diferencia de {@code nombre} (que es el nombre completo,
     * usado en certificados y documentos), este es el que se ve en pantalla
     * en el día a día: "Juan Pérez" en vez de "Juan Carlos Pérez Gómez".
     */
    private String buildNombreCorto(User user) {
        String pNombre = primeraPalabra(user.getFirstName());
        String pApellido = primeraPalabra(user.getLastName());
        String corto = (pNombre + " " + pApellido).trim();
        return corto.isBlank() ? "Usuario" : corto;
    }

    /**
     * Iniciales para el avatar cuando no hay foto: primera letra del primer
     * nombre + primera letra del primer apellido (no de todas las palabras
     * del nombre completo).
     */
    private String buildIniciales(User user) {
        String pNombre = primeraPalabra(user.getFirstName());
        String pApellido = primeraPalabra(user.getLastName());
        String ini = (pNombre.isEmpty() ? "" : pNombre.substring(0, 1))
                + (pApellido.isEmpty() ? "" : pApellido.substring(0, 1));
        return ini.isBlank() ? "U" : ini.toUpperCase();
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains("."))
            return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}