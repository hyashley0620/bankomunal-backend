package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
public class AccountResponse {
    private Long id;
    private String numero;
    private String tipo;
    private BigDecimal saldo;
    private String currency;
    private String status;
}
