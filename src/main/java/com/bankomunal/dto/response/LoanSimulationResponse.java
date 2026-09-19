package com.bankomunal.dto.response;

import lombok.*;
import java.math.BigDecimal;

@Data
@Builder
public class LoanSimulationResponse {
    private BigDecimal cuotaMensual;
    private BigDecimal totalPagar;
    private BigDecimal totalIntereses;
}
