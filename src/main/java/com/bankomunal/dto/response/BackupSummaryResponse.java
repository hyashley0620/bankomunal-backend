package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
public class BackupSummaryResponse {
    private long totalBackups;
    private String almacenamientoUsadoLegible;
    private double porcentajeUsado;
    private String capacidadDetalle;
    private String ultimoBackupLegible;
    private LocalDateTime proximoBackupAutomatico;
    private boolean baseDatosActiva;
    private boolean cifradoActivo;
    private String integridadUltimoRespaldo;
    private String herramientasDisponibles;
}
