package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@Builder
public class PaymentResponse {
    private String referencia;
    private String empresa;
    private BigDecimal monto;
}
