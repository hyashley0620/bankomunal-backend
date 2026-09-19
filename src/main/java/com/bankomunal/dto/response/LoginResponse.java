package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {
    private String token;
    private Long id;
    private String nombre;
    /** Primer nombre + primer apellido — para mostrar junto al avatar. */
    private String nombreCorto;
    private String email;
    private String rol;
    /** Permisos del rol sobre los módulos administrativos — para que el
     *  sidebar sepa qué páginas mostrarle según su rol (admin, tesorero,
     *  secretario, auditor, socio). */
    private java.util.List<java.util.Map<String, Object>> permisos;
    private String genero;
    private String cuenta;
    private String iniciales;
    private BigDecimal saldoTotal;
    private boolean mfaRequired;
    private String fotoUrl;
    private String createdAt;
    private boolean mfaEnabled;
}