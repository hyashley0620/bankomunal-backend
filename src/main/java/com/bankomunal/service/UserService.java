package com.bankomunal.service;

import com.bankomunal.dto.request.CambiarPasswordRequest;
import com.bankomunal.dto.request.UpdatePerfilRequest;
import com.bankomunal.dto.response.MessageResponse;
import com.bankomunal.dto.response.PerfilResponse;
import com.bankomunal.dto.response.UserSessionResponse;
import com.bankomunal.entity.User;
import com.bankomunal.entity.UserSession;
import com.bankomunal.repository.UserRepository;
import com.bankomunal.repository.UserSessionRepository;
import com.bankomunal.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserSessionRepository userSessionRepository;
    private final JwtUtil jwtUtil;

    public java.util.List<java.util.Map<String, Object>> buscarUsuarios(String q) {
        String query = (q == null ? "" : q.trim().toLowerCase());
        return userRepository.findAll().stream()
                .filter(u -> u.getStatus() == User.UserStatus.active)
                .filter(u -> query.isEmpty()
                        || (u.getFirstName() != null && u.getFirstName().toLowerCase().contains(query))
                        || (u.getLastName() != null && u.getLastName().toLowerCase().contains(query))
                        || (u.getEmail() != null && u.getEmail().toLowerCase().contains(query))
                        || (u.getIdentificationNumber() != null
                                && u.getIdentificationNumber().toLowerCase().contains(query)))
                .limit(20)
                .map(u -> java.util.Map.<String, Object>of(
                        "id", u.getId(),
                        "nombre", u.getFirstName() != null ? u.getFirstName() : "",
                        "apellido", u.getLastName() != null ? u.getLastName() : "",
                        "email", u.getEmail() != null ? u.getEmail() : "",
                        "cedula", u.getIdentificationNumber() != null ? u.getIdentificationNumber() : ""))
                .toList();
    }

    /** Obtener perfil */
    public PerfilResponse getPerfil(User user) {
        return toPerfilResponse(user);
    }

    /** Actualizar perfil */
    @Transactional
    public PerfilResponse updatePerfil(UpdatePerfilRequest req, User user) {
        if (req.getNombre() != null) {
            if (req.getNombre().isBlank())
                throw new IllegalArgumentException("El nombre no puede quedar vacío.");
            user.setFirstName(req.getNombre().trim());
        }

        if (req.getApellido() != null)
            user.setLastName(req.getApellido());
        if (req.getCedula() != null)
            user.setIdentificationNumber(req.getCedula());
        if (req.getEmail() != null && !req.getEmail().isBlank())
            user.setEmail(req.getEmail().trim().toLowerCase());
        if (req.getTelefono() != null)
            user.setPhone(req.getTelefono());
        if (req.getDireccion() != null)
            user.setDireccion(req.getDireccion());
        if (req.getCiudad() != null)
            user.setCiudad(req.getCiudad());
        if (req.getDepartamento() != null)
            user.setDepartamento(req.getDepartamento());
        if (req.getOcupacion() != null)
            user.setOcupacion(req.getOcupacion());
        userRepository.save(user);
        return toPerfilResponse(user);
    }

    /**
     * POST /api/usuarios/foto — guarda la imagen en uploads/fotos/ y
     * devuelve la URL canónica "/uploads/fotos/<filename>".
     */
    @Transactional
    public String uploadFoto(MultipartFile file, User user) {
        if (file.isEmpty())
            throw new IllegalArgumentException("El archivo está vacío.");
        String ext = getExtension(file.getOriginalFilename());
        String filename = "foto_" + user.getId() + "_" + UUID.randomUUID() + "." + ext;
        Path uploads = Paths.get("uploads", "fotos");
        try {
            Files.createDirectories(uploads);
            Files.copy(file.getInputStream(), uploads.resolve(filename),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar la foto: " + e.getMessage());
        }
        // Ruta canónica siempre con /uploads/fotos/
        String url = "/uploads/fotos/" + filename;
        user.setSelfiePath(url);
        userRepository.save(user);
        return url;
    }

    /** Desactivar cuenta */
    @Transactional
    public void desactivarCuenta(Long id, User actor) {
        User target = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        target.setStatus(User.UserStatus.suspended);
        target.setActiveToken(null);
        userRepository.save(target);
        cerrarTodasLasSesiones(target);
    }

    /** Cambiar contraseña */
    @Transactional
    public MessageResponse cambiarPassword(CambiarPasswordRequest req, User user) {
        if (!passwordEncoder.matches(req.getPasswordActual(), user.getPasswordHash()))
            throw new IllegalArgumentException("La contraseña actual es incorrecta.");
        user.setPasswordHash(passwordEncoder.encode(req.getPasswordNueva()));
        user.setActiveToken(null);
        user.setPasswordChangedAt(java.time.LocalDateTime.now());
        userRepository.save(user);
        cerrarTodasLasSesiones(user);
        return new MessageResponse("Contraseña actualizada correctamente.");
    }

    /**
     * Cerrar todas las otras sesiones activas, conservando la sesión actual.
     */
    @Transactional
    public int cerrarOtrasSesiones(User user, String tokenActual) {
        user.setActiveToken(tokenActual);
        userRepository.save(user);

        String hashActual = tokenActual != null ? jwtUtil.hash(tokenActual) : null;
        LocalDateTime ahora = LocalDateTime.now();
        int cerradas = 0;
        for (UserSession s : userSessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            boolean esLaActual = hashActual != null && hashActual.equals(s.getTokenHash());
            boolean estabaActiva = s.getExpiresAt() != null && s.getExpiresAt().isAfter(ahora);
            if (!esLaActual && estabaActiva) {
                s.setExpiresAt(ahora);
                userSessionRepository.save(s);
                cerradas++;
            }
        }
        return cerradas;
    }

    /**
     * Historial de sesiones del usuario (GET /api/usuarios/sesiones).
     */
    @Transactional(readOnly = true)
    public List<UserSessionResponse> listarSesiones(User user, String tokenActual) {
        String hashActual = tokenActual != null ? jwtUtil.hash(tokenActual) : null;
        LocalDateTime ahora = LocalDateTime.now();
        return userSessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId()).stream()
                .map(s -> UserSessionResponse.builder()
                        .id(s.getId())
                        .ipAddress(s.getIpAddress())
                        .userAgent(s.getUserAgent())
                        .createdAt(s.getCreatedAt())
                        .expiresAt(s.getExpiresAt())
                        .activa(s.getExpiresAt() != null && s.getExpiresAt().isAfter(ahora))
                        .actual(hashActual != null && hashActual.equals(s.getTokenHash()))
                        .build())
                .toList();
    }

    /* ── Helpers ─────────────────────────────────────────────────────── */

    /**
     * Marca como terminadas (expiresAt = ahora) todas las sesiones aún
     * vigentes del usuario en user_sessions. Se usa junto con cualquier
     * acción que ya limpia activeToken (desactivar cuenta, cambiar
     * contraseña) para que el historial de sesiones no siga mostrando como
     * "activa" una sesión cuyo token ya fue invalidado por otra vía.
     */
    private void cerrarTodasLasSesiones(User user) {
        LocalDateTime ahora = LocalDateTime.now();
        for (UserSession s : userSessionRepository.findByUserIdOrderByCreatedAtDesc(user.getId())) {
            if (s.getExpiresAt() != null && s.getExpiresAt().isAfter(ahora)) {
                s.setExpiresAt(ahora);
                userSessionRepository.save(s);
            }
        }
    }

    private PerfilResponse toPerfilResponse(User u) {
        return PerfilResponse.builder()
                .id(u.getId())
                .nombre(u.getFirstName())
                .apellido(u.getLastName())
                .email(u.getEmail())
                .telefono(u.getPhone())
                .direccion(u.getDireccion())
                .ciudad(u.getCiudad())
                .departamento(u.getDepartamento())
                .ocupacion(u.getOcupacion())
                .fotoUrl(normalizarFotoUrl(u.getSelfiePath()))
                .estado(u.getStatus() != null ? u.getStatus().name() : "active")
                .createdAt(u.getCreatedAt())
                .cedula(u.getIdentificationNumber())
                .build();
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
        // Ya tiene el prefijo correcto
        if (raw.startsWith("/uploads/"))
            return raw;
        // Empieza con http(s): es una URL externa, no tocar
        if (raw.startsWith("http"))
            return raw;
        // Tiene /fotos/ sin /uploads/
        if (raw.startsWith("/fotos/"))
            return "/uploads" + raw;
        // Sin barra inicial y empieza con fotos/
        if (raw.startsWith("fotos/"))
            return "/uploads/" + raw;
        // Cualquier otro caso relativo: anteponer /uploads/
        return "/uploads/" + raw;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains("."))
            return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }
}