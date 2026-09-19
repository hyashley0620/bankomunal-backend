package com.bankomunal.service;

import com.bankomunal.dto.response.FacturaResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Bankomunal no está conectado a ningún facturador real (energía, agua,
 * teléfono, etc.) — igual que en cualquier entorno de pruebas de un banco,
 * esto se simula: a partir del tipo de servicio + número de referencia se
 * genera una factura *determinística* (siempre la misma empresa/monto para
 * la misma referencia, hasta que cambie el día) y se calcula su fecha
 * límite de pago.  el usuario escribe la referencia y el sistema le dice
 * a qué corresponde y hasta cuándo puede pagarla, en vez de que el usuario
 * tenga que inventarse el monto.
 */
@Service
public class FacturaService {

    private static final Map<String, List<String>> EMPRESAS = Map.of(
            "energia", List.of("Electrifica S.A.", "Luz Comunal E.S.P.", "Energía del Valle"),
            "agua", List.of("Aguas Comunales E.S.P.", "Acueducto Municipal", "Aquavida S.A."),
            "gas", List.of("Gas Natural del Pueblo", "Gasomun E.S.P."),
            "internet", List.of("ConectaNet", "FibraComunal", "WebLink Telecom"),
            "telefonia", List.of("Movilnet", "TeleComunal", "RedFon Móvil"),
            "otro", List.of("Facturador Genérico", "Servicio Varios S.A.S."));

    private static final Map<String, int[]> RANGOS = Map.of(
            // {montoMinimo, montoMaximo} en pesos
            "energia", new int[] { 40000, 180000 },
            "agua", new int[] { 20000, 90000 },
            "gas", new int[] { 15000, 70000 },
            "internet", new int[] { 60000, 150000 },
            "telefonia", new int[] { 20000, 120000 },
            "otro", new int[] { 10000, 200000 });

    /**
     * Resuelve una referencia a su factura. Lanza IllegalArgumentException si
     * la referencia no tiene un formato válido (simula que no se encontró en
     * el facturador).
     */
    public FacturaResponse consultarFactura(String tipoServicioCrudo, String referenciaCrudo) {
        String referencia = referenciaCrudo == null ? "" : referenciaCrudo.trim();
        if (referencia.length() < 6 || referencia.length() > 20 || !referencia.matches("[A-Za-z0-9-]+")) {
            throw new IllegalArgumentException(
                    "No encontramos ninguna factura con esa referencia. Verifica el número e intenta de nuevo.");
        }

        String tipo = (tipoServicioCrudo == null || !EMPRESAS.containsKey(tipoServicioCrudo.toLowerCase()))
                ? "otro"
                : tipoServicioCrudo.toLowerCase();

        // Hash estable (mismo tipo+referencia ⇒ siempre la misma factura).
        // Se usa una máscara de bits en vez de Math.abs() para evitar el caso
        // límite en que hashCode() devuelva Integer.MIN_VALUE.
        int hash = (tipo + "|" + referencia).hashCode() & 0x7fffffff;

        List<String> empresas = EMPRESAS.get(tipo);
        String empresa = empresas.get(hash % empresas.size());

        int[] rango = RANGOS.get(tipo);
        int monto = rango[0] + (hash % (rango[1] - rango[0]));
        // Redondear a miles para que se vea como un monto real de factura
        monto = (monto / 1000) * 1000;

        // Fecha límite: entre 5 días vencida y 20 días por vencer, estable
        // mientras no cambie el día de hoy.
        int offsetDias = (hash % 26) - 5;
        LocalDate fechaLimite = LocalDate.now().plusDays(offsetDias);

        return FacturaResponse.builder()
                .tipoServicio(tipo)
                .referencia(referencia)
                .empresa(empresa)
                .monto(BigDecimal.valueOf(monto))
                .fechaLimite(fechaLimite)
                .vencida(fechaLimite.isBefore(LocalDate.now()))
                .build();
    }
}
