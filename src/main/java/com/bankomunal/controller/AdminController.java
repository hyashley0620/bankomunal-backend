package com.bankomunal.controller;

import com.bankomunal.dto.request.*;
import com.bankomunal.dto.response.*;
import com.bankomunal.entity.User;
import com.bankomunal.service.*;
import com.bankomunal.util.EtiquetasES;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class AdminController {

    private final AdminService adminService;
    private final AuditService auditService;
    private final SupportService supportService;
    private final LoanService loanService;
    private final CourseService cursoService;
    private final CreditRiskService creditRiskService;

    // ─── Usuarios (admin + secretario, sujeto a permiso fino del módulo) ────

    @PreAuthorize("hasAnyRole('admin','secretario') and @permisos.tiene('usuarios','leer')")
    @GetMapping("/usuarios")
    public ResponseEntity<List<AdminUserResponse>> usuarios() {
        return ResponseEntity.ok(adminService.getUsuarios());
    }

    /** GET /api/admin/usuarios/{id} — detalle de un usuario */
    @PreAuthorize("hasAnyRole('admin','secretario') and @permisos.tiene('usuarios','leer')")
    @GetMapping("/usuarios/{id}")
    public ResponseEntity<AdminUserResponse> getUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getUsuarioById(id));
    }

    /** POST /api/admin/usuarios — crear usuario desde panel admin (secretario puede registrar socios) */
    @PreAuthorize("hasAnyRole('admin','secretario') and @permisos.tiene('usuarios','crear')")
    @PostMapping("/usuarios")
    public ResponseEntity<AdminUserResponse> crearUsuario(
            @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.crearUsuario(body));
    }

    /**
     * PUT /api/admin/usuarios/{id}/bloquear
     */
    @PutMapping("/usuarios/{id}/bloquear")
    public ResponseEntity<AdminUserResponse> bloquearUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.updateEstado(id, "blocked"));
    }

    /** PUT /api/admin/usuarios/{id}/activar */
    @PutMapping("/usuarios/{id}/activar")
    public ResponseEntity<AdminUserResponse> activarUsuario(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.updateEstado(id, "active"));
    }

    /**
     * PATCH /api/admin/usuarios/{id}/rol
     */
    @PatchMapping("/usuarios/{id}/rol")
    public ResponseEntity<AdminUserResponse> cambiarRol(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(adminService.cambiarRol(id, body.get("rol")));
    }

    // ─── Préstamos ────────────────────────────────────────────────────────────

    // ─── Préstamos (admin + tesorero, sujeto a permiso fino del módulo) ────

    @PreAuthorize("hasAnyRole('admin','tesorero') and @permisos.tiene('gestion-prestamos','leer')")
    @GetMapping("/prestamos")
    public ResponseEntity<List<AdminLoanResponse>> prestamos() {
        return ResponseEntity.ok(adminService.getPrestamos());
    }

    /**
     * GET /api/admin/prestamos/socio/{userId}/riesgo — score de riesgo
     * crediticio del socio (calculado con su historial real de pagos, ver
     * CreditRiskService) + la traza completa de cómo fue cambiando. Pensado
     * para consultarse justo antes de aprobar/rechazar una solicitud.
     */
    @PreAuthorize("hasAnyRole('admin','tesorero') and @permisos.tiene('gestion-prestamos','leer')")
    @GetMapping("/prestamos/socio/{userId}/riesgo")
    public ResponseEntity<Map<String, Object>> riesgoSocio(@PathVariable Long userId) {
        var actual = creditRiskService.getScoreActual(userId);
        var historial = creditRiskService.getHistorial(userId);
        return ResponseEntity.ok(Map.of(
                "score", actual.getScore(),
                "nivel", actual.getRiskLevel().name(),
                "historial", historial.stream().map(h -> Map.of(
                        "score", h.getScore(),
                        "nivel", h.getRiskLevel().name(),
                        "motivo", h.getMotivo() != null ? h.getMotivo() : "",
                        "fecha", h.getCreatedAt() != null ? h.getCreatedAt().toString() : ""
                )).toList()
        ));
    }

    @PreAuthorize("hasAnyRole('admin','tesorero') and @permisos.tiene('gestion-prestamos','editar')")
    @PatchMapping("/prestamos/{id}/aprobar")
    public ResponseEntity<LoanResponse> aprobarPrestamo(@PathVariable Long id) {
        return ResponseEntity.ok(loanService.aprobar(id));
    }

    @PreAuthorize("hasAnyRole('admin','tesorero') and @permisos.tiene('gestion-prestamos','editar')")
    @PatchMapping("/prestamos/{id}/rechazar")
    public ResponseEntity<LoanResponse> rechazarPrestamo(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body) {
        return ResponseEntity.ok(loanService.rechazar(id,
                body != null ? body.get("motivo") : null));
    }

    /**
     * POST /api/admin/prestamos/socio/{socioId}
     * Un admin origina un préstamo a nombre de un socio (ej. alguien que
     * solicitó presencialmente en oficina). Queda igual en estado "pending",
     * pasa por la aprobación normal — esto solo evita que el socio tenga
     * que llenar el formulario desde la app.
     */
    @PreAuthorize("hasAnyRole('admin','tesorero') and @permisos.tiene('gestion-prestamos','crear')")
    @PostMapping("/prestamos/socio/{socioId}")
    public ResponseEntity<LoanResponse> solicitarParaSocio(
            @PathVariable Long socioId,
            @Valid @RequestBody LoanRequest req,
            @AuthenticationPrincipal User admin) {
        return ResponseEntity.ok(loanService.solicitarParaSocio(req, socioId, admin));
    }

    /**
     * POST /api/admin/prestamos/{id}/registrar-pago
     * Para abonos que el socio hace presencialmente (efectivo, transferencia
     * externa, etc.) y que un admin deja constancia en el sistema.
     * body: { "metodo": "efectivo", "observacion": "...", "tipoPago": "completo|abono",
     *         "montoAbono": 50000 } — tipoPago y montoAbono opcionales
     *         (tipoPago por defecto "completo"; montoAbono solo aplica si tipoPago="abono").
     * Devuelve los datos aplicados (monto, saldo antes/después, referencia, etc.)
     * para que el frontend genere un comprobante, igual que en los demás pagos.
     */
    @PreAuthorize("hasAnyRole('admin','tesorero') and @permisos.tiene('gestion-prestamos','editar')")
    @PostMapping("/prestamos/{id}/registrar-pago")
    public ResponseEntity<Map<String, Object>> registrarPagoManual(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal User admin) {
        String metodo = body != null && body.get("metodo") != null ? body.get("metodo").toString() : null;
        String observacion = body != null && body.get("observacion") != null ? body.get("observacion").toString() : null;
        String tipoPago = body != null && body.get("tipoPago") != null ? body.get("tipoPago").toString() : "completo";
        java.math.BigDecimal montoAbono = (body != null && body.get("montoAbono") != null
                && !body.get("montoAbono").toString().isBlank())
                        ? new java.math.BigDecimal(body.get("montoAbono").toString())
                        : null;
        return ResponseEntity.ok(loanService.registrarPagoManual(id, admin, metodo, observacion, tipoPago, montoAbono));
    }

    // ─── Reportes (admin + tesorero + secretario + auditor) y auditoría (admin + auditor) ──
    // Sujeto a permiso fino del módulo.

    @PreAuthorize("hasAnyRole('admin','tesorero','secretario','auditor') and @permisos.tiene('reportes-financieros','leer')")
    @GetMapping("/reportes")
    public ResponseEntity<AdminReporteResponse> reportes(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fin) {
        LocalDateTime ini = inicio != null ? LocalDateTime.parse(inicio) : null;
        LocalDateTime fnl = fin != null ? LocalDateTime.parse(fin) : null;
        return ResponseEntity.ok(adminService.getReporte(ini, fnl));
    }

    /**
     * GET /api/admin/auditoria
     * GET /api/admin/auditoria?inicio=&fin=
     * GET /api/admin/auditoria?tipo=LOGIN_SUCCESS ← filtro por tipo de evento
     */
    @PreAuthorize("hasAnyRole('admin','auditor') and @permisos.tiene('auditoria','leer')")
    @GetMapping("/auditoria")
    public ResponseEntity<List<AuditLogResponse>> auditoria(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fin,
            @RequestParam(required = false) String tipo) {
        LocalDateTime ini = inicio != null ? LocalDateTime.parse(inicio) : null;
        LocalDateTime fnl = fin != null ? LocalDateTime.parse(fin) : null;
        return ResponseEntity.ok(auditService.getLogs(ini, fnl, tipo));
    }

    /**
     * GET /api/admin/auditoria/exportar — Excel real del registro de auditoría.
     */
    @PreAuthorize("hasAnyRole('admin','auditor') and @permisos.tiene('auditoria','leer')")
    @GetMapping("/auditoria/exportar")
    public ResponseEntity<byte[]> exportarAuditoria(
            @RequestParam(required = false) String inicio,
            @RequestParam(required = false) String fin,
            @RequestParam(required = false) String tipo,
            @AuthenticationPrincipal User user) throws IOException {

        LocalDateTime ini = inicio != null ? LocalDateTime.parse(inicio) : null;
        LocalDateTime fnl = fin != null ? LocalDateTime.parse(fin) : null;
        List<AuditLogResponse> logs = auditService.getLogs(ini, fnl, tipo);

        byte[] bytes = generarExcelAuditoria(user, logs);
        String filename = "bankomunal-auditoria-" +
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + ".xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    // ─── Soporte ──────────────────────────────────────────────────────────────

    @GetMapping("/soporte/tickets")
    public ResponseEntity<List<SupportTicketResponse>> tickets() {
        return ResponseEntity.ok(supportService.getTodos());
    }

    /** GET /api/admin/soporte/tickets/{id} — detalle completo de un ticket */
    @GetMapping("/soporte/tickets/{id}")
    public ResponseEntity<SupportTicketResponse> ticketDetalle(@PathVariable Long id) {
        return ResponseEntity.ok(supportService.getDetalle(id));
    }

    /**
     * POST /api/admin/soporte/tickets/{id}/responder
     * body: { "respuesta": "...", "estado": "resolved" }  (estado es opcional)
     */
    @PostMapping("/soporte/tickets/{id}/responder")
    public ResponseEntity<SupportTicketResponse> responderTicket(
            @PathVariable Long id, @RequestBody Map<String, String> body) {
        return ResponseEntity.ok(supportService.responder(id, body.get("respuesta"), body.get("estado")));
    }

    // ─── Roles ────────────────────────────────────────────────────────────────

    @GetMapping("/roles")
    public ResponseEntity<List<Map<String, Object>>> listarRoles() {
        return ResponseEntity.ok(adminService.getRoles());
    }

    @PostMapping("/roles")
    public ResponseEntity<Map<String, Object>> crearRol(
            @RequestBody Map<String, String> body) {
        String nombre = body.getOrDefault("nombre", "").trim();
        String desc = body.getOrDefault("descripcion", nombre);
        if (nombre.isEmpty())
            return ResponseEntity.badRequest()
                    .body(Map.of("mensaje", "El nombre del rol es requerido."));
        return ResponseEntity.ok(adminService.crearRol(nombre, desc));
    }

    /**
     * GET /api/admin/roles/{id}/permisos
     */
    @GetMapping("/roles/{id}/permisos")
    public ResponseEntity<List<Map<String, Object>>> getPermisosPorRol(
            @PathVariable Long id) {
        return ResponseEntity.ok(adminService.getPermisosPorRol(id));
    }

    /**
     * PUT /api/admin/roles/{id}/permisos
     * body: { "rolId": ..., "permisos": [{modulo, leer, crear, editar, borrar}, ...] }
     */
    @SuppressWarnings("unchecked")
    @PutMapping("/roles/{id}/permisos")
    public ResponseEntity<MessageResponse> updatePermisosPorRol(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        List<Map<String, Object>> permisos = (List<Map<String, Object>>) body.getOrDefault("permisos", List.of());
        return ResponseEntity.ok(adminService.guardarPermisosPorRol(id, permisos));
    }

    // ─── Generación del Excel de auditoría ─────────────────────────────────────

    private byte[] generarExcelAuditoria(User user, List<AuditLogResponse> logs) throws IOException {
        DateTimeFormatter fmtDate = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        try (XSSFWorkbook wb = new XSSFWorkbook();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = wb.createSheet("Auditoria");
            sheet.setDefaultColumnWidth(20);

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

            CellStyle altStyle = wb.createCellStyle();
            altStyle.setFillForegroundColor(IndexedColors.LIGHT_TURQUOISE.getIndex());
            altStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setAlignment(HorizontalAlignment.CENTER);

            Row title = sheet.createRow(0);
            title.setHeightInPoints(28);
            Cell titleCell = title.createCell(0);
            titleCell.setCellValue("BANKOMUNAL — Registro de Auditoría");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

            Row meta = sheet.createRow(1);
            meta.createCell(0).setCellValue("Exportado por: " + user.getFirstName() + " " +
                    (user.getLastName() != null ? user.getLastName() : ""));
            meta.createCell(3).setCellValue("Generado: " +
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));

            sheet.createRow(2);

            Row header = sheet.createRow(3);
            header.setHeightInPoints(20);
            String[] cols = { "Fecha", "Usuario", "Evento", "Objeto", "IP", "Detalle" };
            for (int i = 0; i < cols.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(headerStyle);
            }

            int rowNum = 4;
            for (AuditLogResponse log : logs) {
                Row row = sheet.createRow(rowNum);
                if (rowNum % 2 == 0) {
                    for (int i = 0; i < 6; i++)
                        row.createCell(i).setCellStyle(altStyle);
                }

                Cell fechaCell = row.createCell(0);
                fechaCell.setCellValue(log.getCreatedAt() != null ? log.getCreatedAt().format(fmtDate) : "");
                fechaCell.setCellStyle(dateStyle);

                String correo = log.getUserEmail() != null ? log.getUserEmail()
                        : (log.getUsuario() != null ? log.getUsuario().getEmail() : "");
                row.createCell(1).setCellValue(correo != null ? correo : "");
                row.createCell(2).setCellValue(EtiquetasES.eventoAuditoria(log.getEventType()));
                row.createCell(3).setCellValue(EtiquetasES.objetoAuditoria(log.getObjectType()));
                row.createCell(4).setCellValue(log.getIpAddress() != null ? log.getIpAddress() : "");
                row.createCell(5).setCellValue(log.getDetails() != null ? log.getDetails() : "");
                rowNum++;
            }

            if (rowNum > 4) {
                sheet.createRow(rowNum);
                Row totalRow = sheet.createRow(rowNum + 1);
                CellStyle totalStyle = wb.createCellStyle();
                Font totalFont = wb.createFont();
                totalFont.setBold(true);
                totalStyle.setFont(totalFont);
                totalStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
                totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

                Cell totalLabel = totalRow.createCell(1);
                totalLabel.setCellValue("TOTAL EVENTOS:");
                totalLabel.setCellStyle(totalStyle);
                Cell totalCount = totalRow.createCell(2);
                totalCount.setCellValue(logs.size());
                totalCount.setCellStyle(totalStyle);
            }

            for (int i = 0; i < 6; i++)
                sheet.autoSizeColumn(i);
            sheet.setColumnWidth(5, 10000);
            sheet.createFreezePane(0, 4);

            wb.write(out);
            return out.toByteArray();
        }
    }

    /* ══════════════════════════════════════════════════════════════════
       EDUCACIÓN — CRUD de cursos
       ══════════════════════════════════════════════════════════════════ */

    @GetMapping("/educacion/cursos")
    public ResponseEntity<List<com.bankomunal.dto.response.CourseResponse>> listarCursosAdmin() {
        return ResponseEntity.ok(cursoService.getTodosAdmin());
    }

    @GetMapping("/educacion/cursos/{id}")
    public ResponseEntity<com.bankomunal.dto.response.CourseResponse> verCursoAdmin(@PathVariable Long id) {
        return ResponseEntity.ok(cursoService.getPorId(id));
    }

    @PostMapping("/educacion/cursos")
    public ResponseEntity<com.bankomunal.dto.response.CourseResponse> crearCurso(
            @jakarta.validation.Valid @RequestBody com.bankomunal.dto.request.CourseRequest req) {
        return ResponseEntity.ok(cursoService.crear(req));
    }

    @PutMapping("/educacion/cursos/{id}")
    public ResponseEntity<com.bankomunal.dto.response.CourseResponse> actualizarCurso(
            @PathVariable Long id,
            @jakarta.validation.Valid @RequestBody com.bankomunal.dto.request.CourseRequest req) {
        return ResponseEntity.ok(cursoService.actualizar(id, req));
    }

    /** "Eliminar" en realidad desactiva — no se borra para no invalidar certificados ya emitidos. */
    @DeleteMapping("/educacion/cursos/{id}")
    public ResponseEntity<MessageResponse> desactivarCurso(@PathVariable Long id) {
        cursoService.desactivar(id);
        return ResponseEntity.ok(new MessageResponse("Curso desactivado — ya no aparece en el catálogo de socios."));
    }

    @PatchMapping("/educacion/cursos/{id}/reactivar")
    public ResponseEntity<MessageResponse> reactivarCurso(@PathVariable Long id) {
        cursoService.reactivar(id);
        return ResponseEntity.ok(new MessageResponse("Curso reactivado."));
    }

}

// NOTA: la llave de clase anterior en la línea 207 cierra AdminController.
// Agregamos /admin/sistema como un controlador separado para evitar modificar
// la clase sellada.