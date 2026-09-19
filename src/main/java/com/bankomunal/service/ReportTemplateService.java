package com.bankomunal.service;

import com.bankomunal.entity.ReportTemplate;
import com.bankomunal.entity.User;
import com.bankomunal.repository.ReportTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Plantillas de reporte reutilizables. Ahora deja guardar
 * una combinación de filtros (fecha desde/hasta, tipo de movimiento) con un
 * nombre, para no tener que volver a escribirlos cada vez que se exporta —
 * y ExportController#exportarExcel/exportarPdf ya la usa de verdad si se
 * les pasa `plantillaId` (ver ExportController).
 */
@Service
@RequiredArgsConstructor
public class ReportTemplateService {

    private final ReportTemplateRepository templateRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /** Plantillas propias del usuario + las que otros marcaron como públicas. */
    @Transactional(readOnly = true)
    public List<ReportTemplate> listar(Long userId) {
        return templateRepository.findByCreadoPorIdOrEsPublicoTrue(userId);
    }

    @Transactional
    public ReportTemplate crear(User user, String nombre, String tipo,
            LocalDateTime desde, LocalDateTime hasta, String tipoMovimiento, boolean esPublico) {
        if (nombre == null || nombre.isBlank())
            throw new IllegalArgumentException("La plantilla necesita un nombre.");

        Map<String, Object> params = new HashMap<>();
        if (desde != null)
            params.put("desde", desde.format(ISO));
        if (hasta != null)
            params.put("hasta", hasta.format(ISO));
        if (tipoMovimiento != null && !tipoMovimiento.isBlank())
            params.put("tipoMovimiento", tipoMovimiento);

        String json;
        try {
            json = objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            json = "{}";
        }

        return templateRepository.save(ReportTemplate.builder()
                .nombre(nombre)
                .tipo(tipo != null && !tipo.isBlank() ? tipo : "movimientos")
                .parametros(json)
                .creadoPor(user)
                .esPublico(esPublico)
                .build());
    }

    @Transactional
    public void eliminar(Long id, User user) {
        ReportTemplate plantilla = templateRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada."));
        boolean esDueño = plantilla.getCreadoPor() != null
                && plantilla.getCreadoPor().getId().equals(user.getId());
        if (!esDueño)
            throw new SecurityException("No puedes eliminar una plantilla que no creaste.");
        templateRepository.delete(plantilla);
    }

    /** Parámetros ya parseados, listos para usar como filtro de exportación. */
    @Transactional(readOnly = true)
    public FiltrosExportacion resolverFiltros(Long plantillaId) {
        ReportTemplate plantilla = templateRepository.findById(plantillaId)
                .orElseThrow(() -> new IllegalArgumentException("Plantilla no encontrada."));

        FiltrosExportacion filtros = new FiltrosExportacion();
        try {
            Map<?, ?> params = objectMapper.readValue(
                    plantilla.getParametros() != null ? plantilla.getParametros() : "{}", Map.class);
            if (params.get("desde") != null)
                filtros.desde = LocalDateTime.parse(String.valueOf(params.get("desde")), ISO);
            if (params.get("hasta") != null)
                filtros.hasta = LocalDateTime.parse(String.valueOf(params.get("hasta")), ISO);
            if (params.get("tipoMovimiento") != null)
                filtros.tipoMovimiento = String.valueOf(params.get("tipoMovimiento"));
        } catch (Exception ignored) {
            // Plantilla con parámetros corruptos o vacíos — se exporta sin filtro,
            // no se rompe la exportación por esto.
        }
        return filtros;
    }

    public static class FiltrosExportacion {
        public LocalDateTime desde;
        public LocalDateTime hasta;
        public String tipoMovimiento;
    }
}