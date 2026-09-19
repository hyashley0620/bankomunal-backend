package com.bankomunal.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PaymentRequest {
    @NotBlank
    private String servicio;
    @NotBlank(message = "Ingresa el número de referencia de la factura.")
    private String referencia;
    @NotNull
    @Positive
    private BigDecimal monto;
    // TransactionService maneja los valores nulos/vacíos recurriendo a la primera
    // cuenta activa del usuario.
    private String cuenta;
}
