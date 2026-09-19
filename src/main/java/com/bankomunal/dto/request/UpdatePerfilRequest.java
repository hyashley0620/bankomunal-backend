package com.bankomunal.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UpdatePerfilRequest {
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    private String nombre;
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres.")
    private String apellido;
    @Email(message = "Ingresa un correo electrónico válido.")
    @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
    private String email;
    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres.")
    private String telefono;
    @Size(max = 255, message = "La dirección no puede superar los 255 caracteres.")
    private String direccion;
    @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres.")
    private String ciudad;
    @Size(max = 100, message = "El departamento no puede superar los 100 caracteres.")
    private String departamento;
    @Size(max = 100, message = "La ocupación no puede superar los 100 caracteres.")
    private String ocupacion;
    @Size(max = 50, message = "La cédula no puede superar los 50 caracteres.")
    private String cedula;
}
