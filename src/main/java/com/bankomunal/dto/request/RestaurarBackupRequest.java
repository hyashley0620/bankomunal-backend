package com.bankomunal.dto.request;

import lombok.Data;

/**
 * La restauración sobrescribe TODA la base de datos actual, así que exigimos
 * una confirmación explícita escrita por el usuario (no solo un click) —
 * misma idea de "defensa en profundidad" que ya usa PermisoService, aplicada
 * en el frontend con un prompt() y revalidada aquí en el backend.
 */
@Data
public class RestaurarBackupRequest {
    private String confirmacion;
}
