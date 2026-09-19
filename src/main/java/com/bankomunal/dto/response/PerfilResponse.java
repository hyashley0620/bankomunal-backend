package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder
public class PerfilResponse {
    private Long   id;
    private String nombre;
    private String apellido;
    private String email;
    private String telefono;
    private String direccion;
    private String ciudad;
    private String departamento;
    private String ocupacion;
    private String fotoUrl;
    private String estado;
    private LocalDateTime createdAt;
    private String cedula;
}
