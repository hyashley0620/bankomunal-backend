package com.bankomunal.controller;

import com.bankomunal.dto.request.CambiarPasswordRequest;
import com.bankomunal.dto.request.UpdatePerfilRequest;
import com.bankomunal.dto.request.UserConfigRequest;
import com.bankomunal.dto.response.MessageResponse;
import com.bankomunal.dto.response.PerfilResponse;
import com.bankomunal.dto.response.UserConfigResponse;
import com.bankomunal.dto.response.UserSessionResponse;
import com.bankomunal.entity.User;
import com.bankomunal.service.UserConfigService;
import com.bankomunal.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserConfigService userConfigService;

    /** GET /api/usuarios/perfil */
    @GetMapping("/perfil")
    public ResponseEntity<PerfilResponse> getPerfil(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.getPerfil(user));
    }

    /** PUT /api/usuarios/perfil */
    @PutMapping("/perfil")
    public ResponseEntity<PerfilResponse> updatePerfil(
            @Valid @RequestBody UpdatePerfilRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.updatePerfil(req, user));
    }

    /** POST /api/usuarios/foto */
    @PostMapping("/foto")
    public ResponseEntity<java.util.Map<String, String>> uploadFoto(
            @RequestParam("foto") MultipartFile file,
            @AuthenticationPrincipal User user) {
        String url = userService.uploadFoto(file, user);
        return ResponseEntity.ok(java.util.Map.of("url", url));
    }

    /** GET /api/usuarios/configuracion — leer preferencias actuales */
    @GetMapping("/configuracion")
    public ResponseEntity<UserConfigResponse> getConfig(
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userConfigService.getConfig(user));
    }

    /** PUT /api/usuarios/configuracion — guardar preferencias en BD */
    @PutMapping("/configuracion")
    public ResponseEntity<UserConfigResponse> updateConfig(
            @RequestBody UserConfigRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userConfigService.updateConfig(req, user));
    }

    /** PATCH /api/usuarios/{id}/desactivar */
    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<MessageResponse> desactivar(
            @PathVariable Long id,
            @AuthenticationPrincipal User user) {
        userService.desactivarCuenta(id, user);
        return ResponseEntity.ok(new MessageResponse("Cuenta desactivada."));
    }

    /**
     * GET /api/usuarios/buscar?q=...
     */
    @GetMapping("/buscar")
    public ResponseEntity<java.util.List<java.util.Map<String, Object>>> buscarUsuarios(
            @RequestParam(value = "q", required = false, defaultValue = "") String q) {
        return ResponseEntity.ok(userService.buscarUsuarios(q));
    }

    /** POST /api/usuarios/cambiar-password */
    @PostMapping("/cambiar-password")
    public ResponseEntity<MessageResponse> cambiarPassword(
            @Valid @RequestBody CambiarPasswordRequest req,
            @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(userService.cambiarPassword(req, user));
    }

    /** POST /api/usuarios/sesiones/cerrar-otras — invalida sesiones anteriores */
    @PostMapping("/sesiones/cerrar-otras")
    public ResponseEntity<MessageResponse> cerrarOtrasSesiones(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        // El token de ESTA sesión (la que hizo el clic) debe quedar como el
        // único válido — pasarlo para que se conserve, en vez de limpiar
        // activeToken a null (ver UserService.cerrarOtrasSesiones).
        String tokenActual = extraerToken(request);
        int cerradas = userService.cerrarOtrasSesiones(user, tokenActual);
        String mensaje = cerradas > 0
                ? "Se cerraron " + cerradas + " sesión(es) en otros dispositivos."
                : "No había otras sesiones activas en otros dispositivos.";
        return ResponseEntity.ok(new MessageResponse(mensaje));
    }

    /**
     * GET /api/usuarios/sesiones — historial de sesiones del usuario (antes
     * "Sesiones activas" en seguridad.html se calculaba contando eventos
     * LOGIN_SUCCESS del log de auditoría en vez de sesiones activas reales).
     */
    @GetMapping("/sesiones")
    public ResponseEntity<List<UserSessionResponse>> getSesiones(
            @AuthenticationPrincipal User user,
            HttpServletRequest request) {
        return ResponseEntity.ok(userService.listarSesiones(user, extraerToken(request)));
    }

    private String extraerToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        return (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.substring(7)
                : null;
    }
}