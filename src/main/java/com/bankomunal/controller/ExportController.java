package com.bankomunal.controller;

import com.bankomunal.dto.response.ReportTemplateResponse;
import com.bankomunal.dto.response.TransactionResponse;
import com.bankomunal.entity.User;
import com.bankomunal.service.ReportTemplateService;
import com.bankomunal.service.TransactionService;
import com.bankomunal.util.EtiquetasES;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/exportar")
@RequiredArgsConstructor
public class ExportController {

    private final TransactionService transactionService;
    private final ReportTemplateService reportTemplateService;

    private static final DateTimeFormatter FMT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_FILE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static String traducirTipo(String tipo) {
        return EtiquetasES.tipoMovimiento(tipo);
    }

    private static String traducirEstado(String estado) {
        return EtiquetasES.estadoMovimiento(estado);
    }

    /*
     * ─────────────────────────────────────────────────────────────────────────
     * GET /api/exportar/excel — Genera un .xlsx
     * ─────────────────────────────────────────────────────────────────────────
     */
    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) Long plantillaId,
            @AuthenticationPrincipal User user) throws IOException {

        ReportTemplateService.FiltrosExportacion filtros = plantillaId != null
                ? reportTemplateService.resolverFiltros(plantillaId)
                : new ReportTemplateService.FiltrosExportacion();

        List<TransactionResponse> movimientos = transactionService.getMovimientos(
                user.getId(), filtros.desde, filtros.hasta, filtros.tipoMovimiento);

        byte[] bytes = generarExcel(user, movimientos);
        String filename = "bankomunal-movimientos-" + LocalDateTime.now().format(FMT_FILE) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    /*
     * ─────────────────────────────────────────────────────────────────────────
     * GET /api/exportar/pdf — HTML imprimible (Ctrl+P → Guardar como PDF)
     * ─────────────────────────────────────────────────────────────────────────
     */
    @GetMapping("/pdf")
    public ResponseEntity<byte[]> exportarPdf(
            @RequestParam(required = false) Long plantillaId,
            @AuthenticationPrincipal User user) throws IOException {

        ReportTemplateService.FiltrosExportacion filtros = plantillaId != null
                ? reportTemplateService.resolverFiltros(plantillaId)
                : new ReportTemplateService.FiltrosExportacion();

        List<TransactionResponse> movimientos = transactionService.getMovimientos(
                user.getId(), filtros.desde, filtros.hasta, filtros.tipoMovimiento);

        String html = generarHtmlImprimible(user, movimientos);
        byte[] bytes = html.getBytes("UTF-8");
        String filename = "bankomunal-historial-" + LocalDateTime.now().format(FMT_FILE) + ".html";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/html; charset=UTF-8"))
                .body(bytes);
    }

    /*
     * ─────────────────────────────────────────────────────────────────────────
     * Plantillas de reporte — guardar/reutilizar combinaciones de filtros
     * ─────────────────────────────────────────────────────────────────────────
     */
    public static class PlantillaRequest {
        public String nombre;
        public String tipo;
        public LocalDateTime desde;
        public LocalDateTime hasta;
        public String tipoMovimiento;
        public boolean esPublico;
    }

    @GetMapping("/plantillas")
    public ResponseEntity<List<ReportTemplateResponse>> listarPlantillas(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(reportTemplateService.listar(user.getId()).stream()
                .map(ReportTemplateResponse::from)
                .toList());
    }

    @PostMapping("/plantillas")
    public ResponseEntity<ReportTemplateResponse> crearPlantilla(
            @RequestBody PlantillaRequest req, @AuthenticationPrincipal User user) {
        return ResponseEntity.ok(ReportTemplateResponse.from(reportTemplateService.crear(
                user, req.nombre, req.tipo, req.desde, req.hasta, req.tipoMovimiento, req.esPublico)));
    }

    @DeleteMapping("/plantillas/{id}")
    public ResponseEntity<Void> eliminarPlantilla(@PathVariable Long id, @AuthenticationPrincipal User user) {
        reportTemplateService.eliminar(id, user);
        return ResponseEntity.noContent().build();
    }

    /*
     * ─────────────────────────────────────────────────────────────────────────
     * Generación del archivo Excel con Apache POI
     * ─────────────────────────────────────────────────────────────────────────
     */
    private byte[] generarExcel(User user, List<TransactionResponse> movimientos) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Movimientos");
            sheet.setDefaultColumnWidth(18);

            /* ── Estilos ─────────────────────────────────────────────── */
            CellStyle headerStyle = wb.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            Font hFont = wb.createFont();
            hFont.setColor(IndexedColors.WHITE.getIndex());
            hFont.setBold(true);
            hFont.setFontHeightInPoints((short) 11);
            headerStyle.setFont(hFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);

            CellStyle titleStyle = wb.createCellStyle();
            Font tFont = wb.createFont();
            tFont.setBold(true);
            tFont.setFontHeightInPoints((short) 14);
            tFont.setColor(IndexedColors.DARK_TEAL.getIndex());
            titleStyle.setFont(tFont);

            CellStyle moneyStyle = wb.createCellStyle();
            DataFormat df = wb.createDataFormat();
            moneyStyle.setDataFormat(df.getFormat("#,##0.00"));
            moneyStyle.setAlignment(HorizontalAlignment.RIGHT);

            CellStyle altStyle = wb.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setAlignment(HorizontalAlignment.CENTER);

            /* ── Fila 0: Título ──────────────────────────────────────── */
            Row title = sheet.createRow(0);
            title.setHeightInPoints(28);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("BANKOMUNAL — Historial de Movimientos");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            /* ── Fila 1: Meta-info ───────────────────────────────────── */
            Row meta = sheet.createRow(1);
            meta.createCell(0).setCellValue("Usuario: " + user.getFirstName() + " " +
                    (user.getLastName() != null ? user.getLastName() : ""));
            meta.createCell(3).setCellValue("Generado: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

            /* ── Fila 2: vacía ───────────────────────────────────────── */
            sheet.createRow(2);

            /* ── Fila 3: Cabecera ────────────────────────────────────── */
            Row header = sheet.createRow(3);
            header.setHeightInPoints(20);
            String[] cols = { "Tipo", "Fecha", "Monto (COP)", "Descripción", "Referencia", "Estado" };
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            /* ── Filas de datos ──────────────────────────────────────── */
            int rowNum = 4;
            for (TransactionResponse m : movimientos) {
                Row row = sheet.createRow(rowNum);
                if (rowNum % 2 == 0) {
                    for (int i = 0; i < 6; i++)
                        row.createCell(i).setCellStyle(altStyle);
                }

                row.createCell(0).setCellValue(traducirTipo(m.getTipo()));
                Cell fechaCell = row.createCell(1);
                fechaCell.setCellValue(m.getFecha() != null ? m.getFecha().format(FMT_DATE) : "");
                fechaCell.setCellStyle(dateStyle);

                Cell montoCell = row.createCell(2);
                if (m.getMonto() != null) {
                    montoCell.setCellValue(m.getMonto().doubleValue());
                    montoCell.setCellStyle(moneyStyle);
                } else {
                    montoCell.setCellValue(0);
                }

                row.createCell(3).setCellValue(m.getDescripcion() != null ? m.getDescripcion() : "");
                row.createCell(4).setCellValue(m.getReferencia() != null ? m.getReferencia() : "");
                row.createCell(5).setCellValue(traducirEstado(m.getEstado()));
                rowNum++;
            }

            /* ── Fila final: Total ───────────────────────────────────── */
            if (rowNum > 4) {
                sheet.createRow(rowNum); // separador
                Row totalRow = sheet.createRow(rowNum + 1);
                CellStyle totalStyle = wb.createCellStyle();
                Font totalFont = wb.createFont();
                totalFont.setBold(true);
                totalStyle.setFont(totalFont);
                totalStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
                totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                Cell totalLabel = totalRow.createCell(1);
                totalLabel.setCellValue("TOTAL REGISTROS:");
                totalLabel.setCellStyle(totalStyle);
                Cell totalCount = totalRow.createCell(2);
                totalCount.setCellValue(movimientos.size());
                totalCount.setCellStyle(totalStyle);
            }

            /* ── Auto-size columnas ──────────────────────────────────── */
            for (int i = 0; i < 6; i++)
                sheet.autoSizeColumn(i);
            sheet.setColumnWidth(3, 8000); // Descripción más ancha
            sheet.setColumnWidth(4, 5000); // Referencia

            /* ── Inmovilizar primera fila de cabecera ────────────────── */
            sheet.createFreezePane(0, 4);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /*
     * ─────────────────────────────────────────────────────────────────────────
     * Generación del HTML imprimible
     * ─────────────────────────────────────────────────────────────────────────
     */
    private String generarHtmlImprimible(User user, List<TransactionResponse> movimientos) {
        DateTimeFormatter fmtDisplay = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        StringBuilder sb = new StringBuilder();

        sb.append("<!DOCTYPE html><html lang='es'><head><meta charset='UTF-8'>")
                .append("<title>Historial Bankomunal</title><style>")
                .append("body{font-family:Arial,sans-serif;margin:30px;color:#1a202c}")
                .append("h1{color:#005F73;border-bottom:2px solid #005F73;padding-bottom:8px}")
                .append("table{width:100%;border-collapse:collapse;margin-top:20px;font-size:13px}")
                .append("th{background:#005F73;color:#fff;padding:10px 8px;text-align:left}")
                .append("td{padding:8px;border-bottom:1px solid #e2e8f0}")
                .append("tr:nth-child(even){background:#f8fafc}")
                .append(".footer{margin-top:30px;font-size:11px;color:#94a3b8;text-align:center}")
                .append("@media print{body{margin:15px}}")
                .append("</style></head><body>")
                .append("<h1>Historial de Movimientos — Bankomunal</h1>")
                .append("<p>Usuario: <strong>")
                .append(esc(user.getFirstName() + " " + (user.getLastName() != null ? user.getLastName() : "")))
                .append("</strong> &nbsp;|&nbsp; Generado: <strong>")
                .append(LocalDateTime.now().format(fmtDisplay))
                .append("</strong> &nbsp;|&nbsp; Total: <strong>")
                .append(movimientos.size()).append(" registros</strong></p>")
                .append("<table><thead><tr>")
                .append("<th>Tipo</th><th>Fecha</th><th>Monto (COP)</th>")
                .append("<th>Descripción</th><th>Referencia</th><th>Estado</th>")
                .append("</tr></thead><tbody>");

        for (TransactionResponse m : movimientos) {
            sb.append("<tr>")
                    .append("<td>").append(esc(traducirTipo(m.getTipo()))).append("</td>")
                    .append("<td>").append(m.getFecha() != null ? m.getFecha().format(fmtDisplay) : "").append("</td>")
                    .append("<td style='text-align:right'>")
                    .append(m.getMonto() != null ? String.format("%,.0f", m.getMonto()) : "0")
                    .append("</td>")
                    .append("<td>").append(esc(m.getDescripcion())).append("</td>")
                    .append("<td>").append(esc(m.getReferencia())).append("</td>")
                    .append("<td>").append(esc(traducirEstado(m.getEstado()))).append("</td>")
                    .append("</tr>");
        }

        sb.append("</tbody></table>")
                .append("<div class='footer'>Bankomunal — Microfinanzas Comunitarias · ayuda@bankomunal.org</div>")
                .append("<script>window.onload=function(){window.print();}</script>")
                .append("</body></html>");

        return sb.toString();
    }

    private String esc(String s) {
        if (s == null)
            return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}