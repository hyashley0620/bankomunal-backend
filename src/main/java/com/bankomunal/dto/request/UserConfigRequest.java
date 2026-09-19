package com.bankomunal.dto.request;

import lombok.Data;

/**
 * PUT /api/usuarios/configuracion.
 * Todos los campos son opcionales — solo se actualizan los que vienen no-nulos.
 */
@Data
public class UserConfigRequest {

    /** Notificaciones por email */
    private Boolean notifEmail;

    /** Notificaciones push */
    private Boolean notifPush;

    /** Notificaciones por SMS */
    private Boolean notifSms;

    /** Alertas de saldo bajo */
    private Boolean alertaSaldo;

    /** Notificaciones por WhatsApp (notifSms en el frontend) */
    private Boolean notifWhatsapp;

    /**
     * Autenticación de dos factores — guardado en campo mfa_code de User como flag
     */
    private Boolean mfaEnabled;

    /** Idioma preferido */
    private String idioma;
}
