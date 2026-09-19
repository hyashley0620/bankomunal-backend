package com.bankomunal.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDate;

@Data
public class RegisterRequest {
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    private String nombre;
    @Size(max = 50, message = "El documento no puede superar los 50 caracteres.")
    private String cedula;
    @Size(max = 100, message = "El nombre no puede superar los 100 caracteres.")
    private String nombres;
    @NotBlank
    @Size(max = 100, message = "El apellido no puede superar los 100 caracteres.")
    private String apellidos;
    private String tipoDoc;
    @Size(max = 50, message = "El documento no puede superar los 50 caracteres.")
    private String documento;
    private LocalDate fechaNacimiento;
    private String genero;
    @Size(max = 30, message = "El teléfono no puede superar los 30 caracteres.")
    private String telefono;
    @NotBlank
    @Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[a-zA-Z]{2,}$", message = "Ingresa un correo electrónico válido (ejemplo@correo.com).")
    @Size(max = 150, message = "El correo no puede superar los 150 caracteres.")
    private String email;
    @NotBlank
    @Size(min = 6)
    private String password;
    @Size(max = 255, message = "La dirección no puede superar los 255 caracteres.")
    private String direccion;
    @Size(max = 100, message = "La ciudad no puede superar los 100 caracteres.")
    private String ciudad;
    @Size(max = 100, message = "El departamento no puede superar los 100 caracteres.")
    private String departamento;
    @Size(max = 100, message = "La ocupación no puede superar los 100 caracteres.")
    private String ocupacion;
    private boolean autorizaDatos;
}
