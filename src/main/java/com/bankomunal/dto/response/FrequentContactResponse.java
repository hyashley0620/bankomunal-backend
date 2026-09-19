package com.bankomunal.dto.response;

import lombok.*;
import java.time.LocalDateTime;

@Data
@Builder
public class FrequentContactResponse {
    private Long id;
    private String nombre;
    private String email;
    private String cuentaNumero;
    /** true si el email corresponde a un socio real de Bankomunal (y por
     *  tanto cuentaNumero se pudo resolver de forma confiable). Si es
     *  false, cuentaNumero siempre es null — el contacto se guardó, pero
     *  aún no se puede usar para transferir hasta que esa persona se
     *  registre en la plataforma. */
    private boolean usuarioRegistrado;
    private LocalDateTime createdAt;
}
