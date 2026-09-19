package com.bankomunal.controller;

import com.bankomunal.dto.request.RestaurarBackupRequest;
import com.bankomunal.dto.response.BackupRecordResponse;
import com.bankomunal.dto.response.BackupSummaryResponse;
import com.bankomunal.dto.response.MessageResponse;
import com.bankomunal.entity.User;
import com.bankomunal.service.BackupService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Respaldo y recuperación real de la base de datos completa.
 */
@RestController
@RequestMapping("/api/admin/respaldo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('admin')")
public class BackupController {

    private final BackupService backupService;

    @PreAuthorize("hasRole('admin') and @permisos.tiene('respaldo-recuperacion','leer')")
    @GetMapping
    public ResponseEntity<List<BackupRecordResponse>> listar() {
        return ResponseEntity.ok(backupService.listar());
    }

    @PreAuthorize("hasRole('admin') and @permisos.tiene('respaldo-recuperacion','leer')")
    @GetMapping("/resumen")
    public ResponseEntity<BackupSummaryResponse> resumen() {
        return ResponseEntity.ok(backupService.resumen());
    }

    @PreAuthorize("hasRole('admin') and @permisos.tiene('respaldo-recuperacion','crear')")
    @PostMapping
    public ResponseEntity<BackupRecordResponse> crear(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(backupService.crearBackupManual(user));
    }

    @PreAuthorize("hasRole('admin') and @permisos.tiene('respaldo-recuperacion','leer')")
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargar(@PathVariable Long id) {
        BackupService.ArchivoBackup archivo = backupService.obtenerArchivoParaDescarga(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + archivo.nombreArchivo() + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(archivo.contenido());
    }

    /**
     * Restauración real: sobrescribe TODA la base de datos actual con el
     * contenido del respaldo elegido. Exige el permiso más alto ('borrar')
     * y una confirmación explícita en el cuerpo de la petición.
     */
    @PreAuthorize("hasRole('admin') and @permisos.tiene('respaldo-recuperacion','borrar')")
    @PostMapping("/{id}/restaurar")
    public ResponseEntity<MessageResponse> restaurar(
            @PathVariable Long id,
            @RequestBody RestaurarBackupRequest req,
            @AuthenticationPrincipal User user) {
        backupService.restaurar(id, req.getConfirmacion(), user);
        return ResponseEntity.ok(new MessageResponse(
                "Sistema restaurado correctamente desde el respaldo #" + id + "."));
    }

    @PreAuthorize("hasRole('admin') and @permisos.tiene('respaldo-recuperacion','borrar')")
    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> eliminar(@PathVariable Long id, @AuthenticationPrincipal User user) {
        backupService.eliminar(id, user);
        return ResponseEntity.ok(new MessageResponse("Respaldo eliminado."));
    }
}