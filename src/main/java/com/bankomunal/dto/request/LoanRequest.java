package com.bankomunal.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class LoanRequest {
    @NotNull
    @Positive
    private BigDecimal montoSolicitado;
    @NotNull
    @Min(1)
    @Max(120)
    private int plazoMeses;
    private String motivo;
}
