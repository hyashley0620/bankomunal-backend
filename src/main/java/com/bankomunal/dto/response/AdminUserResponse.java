package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserResponse {
    private Long id;
    private String nombre;
    private String apellido;
    private String email;
    private String cedula;
    private String telefono;
    private String ciudad;
    private String ocupacion;
    private String estado;
    private String rol;
    private LocalDateTime fechaRegistro;
    private Integer puntos;
    private String nivel;
    private Integer creditScore;
    /** Solo poblado en el detalle de un usuario (ficha de socio para admin). */
    private Integer totalPrestamos;
    private Integer prestamosActivos;
    private BigDecimal saldoPendienteTotal;
}
