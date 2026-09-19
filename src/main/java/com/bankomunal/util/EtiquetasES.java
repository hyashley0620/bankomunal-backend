package com.bankomunal.util;

import java.util.Map;

/**
 * Traducción de valores de enum (guardados en inglés en BD, ej. Transaction.TxType,
 * Transaction.TxStatus) a las mismas etiquetas en español que ya usa el frontend
 * (ver tipoLabel en paginas.js), para que cualquier archivo descargado (Excel, PDF,
 * CSV) coincida con lo que se ve en pantalla.
 */
public final class EtiquetasES {

    private EtiquetasES() {
    }

    // Debe coincidir exactamente con labelTipoTransaccion() en js/core.js
    // (única fuente de verdad de estas etiquetas en el frontend) para que lo
    // que se descarga diga lo mismo que lo que se ve en pantalla.
    private static final Map<String, String> TIPO_MOVIMIENTO = Map.ofEntries(
            Map.entry("deposit", "Depósito"),
            Map.entry("withdrawal", "Retiro"),
            Map.entry("transfer", "Transferencia"),
            Map.entry("transfer_sent", "Transferencia"),
            Map.entry("transfer_received", "Transferencia recibida"),
            Map.entry("loan_disbursement", "Desembolso préstamo"),
            Map.entry("loan_payment", "Pago préstamo"),
            Map.entry("fee", "Comisión"),
            Map.entry("service_payment", "Pago de servicio"),
            Map.entry("fondo_comun", "Aporte fondo común"),
            Map.entry("adjustment", "Ajuste"));

    private static final Map<String, String> ESTADO_MOVIMIENTO = Map.of(
            "pending", "Pendiente",
            "completed", "Completado",
            "failed", "Fallido",
            "reversed", "Reversado");

    // Debe coincidir exactamente con labelEventoAuditoria() en js/core.js
    private static final Map<String, String> EVENTO_AUDITORIA = Map.ofEntries(
            Map.entry("LOGIN_SUCCESS", "Inicio de sesión exitoso"),
            Map.entry("LOGIN_FAILED", "Inicio de sesión fallido"),
            Map.entry("LOGIN_MFA_SENT", "Código de verificación enviado"),
            Map.entry("LOGIN_MFA_FAILED", "Código de verificación incorrecto"),
            Map.entry("ADMIN_VIEWED_DOCUMENTS", "Administrador consultó documentos"),
            Map.entry("ADMIN_ACCESSED_DOCUMENT", "Administrador accedió a un documento"),
            Map.entry("PASSWORD_RECOVERY_REQUESTED", "Solicitud de recuperación de contraseña"),
            Map.entry("PASSWORD_RESET_SUCCESS", "Contraseña restablecida"),
            Map.entry("USER_REGISTERED", "Usuario registrado"));

    /** Tipo de movimiento/transacción (transfer, service_payment, etc.) → español. */
    public static String tipoMovimiento(String valor) {
        if (valor == null) return "";
        return TIPO_MOVIMIENTO.getOrDefault(valor.toLowerCase(), valor);
    }

    /** Estado de transacción (pending, completed, failed, reversed) → español. */
    public static String estadoMovimiento(String valor) {
        if (valor == null) return "";
        return ESTADO_MOVIMIENTO.getOrDefault(valor.toLowerCase(), valor);
    }

    /** Tipo de evento de auditoría (LOGIN_SUCCESS, etc.) → español. */
    public static String eventoAuditoria(String valor) {
        if (valor == null) return "";
        return EVENTO_AUDITORIA.getOrDefault(valor, valor);
    }

    /** Tipo de objeto auditado (Document, User) → español. */
    public static String objetoAuditoria(String valor) {
        if (valor == null) return "";
        return switch (valor) {
            case "Document" -> "Documento";
            case "User" -> "Usuario";
            default -> valor;
        };
    }
}