package com.bankomunal.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * soporte.js llama GET /api/config/soporte para obtener el número de WhatsApp.
 * Este endpoint es público (sin autenticación).
 */
@RestController
@RequestMapping("/api/config")
public class PublicConfigController {

    @GetMapping("/soporte")
    public ResponseEntity<Map<String, Object>> configSoporte() {
        return ResponseEntity.ok(Map.of(
                "whatsappUrl", "https://wa.me/573001234567",
                "telefono", "+57 300 123 4567",
                "email", "soporte@bankomunal.com",
                "horarioAtencion", "Lunes a Viernes 8am - 6pm",
                // chat-soporte.html lee estos dos campos para poblar
                // #agentNameDisplay y #agentStatusText.
                "nombreAgente", "Asistente Bankomunal",
                "disponible", true));
    }
}
