package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
public class BackupRecordResponse {
    private Long id;
    private LocalDateTime fecha;
    private String responsable;
    private String metodo;
    private String estado;
    private String tamanoLegible;
    private boolean cifrado;
    private boolean descargable;
    private boolean restaurable;
}
