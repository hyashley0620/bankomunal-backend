package com.bankomunal.service;

import com.bankomunal.dto.response.BackupRecordResponse;
import com.bankomunal.dto.response.BackupSummaryResponse;
import com.bankomunal.entity.BackupRecord;
import com.bankomunal.entity.BackupRecord.Estado;
import com.bankomunal.entity.BackupRecord.Metodo;
import com.bankomunal.entity.User;
import com.bankomunal.repository.BackupRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Genera y restaura respaldos REALES de la base de datos completa (dump vía
 * mysqldump / restauración vía mysql).
 *
 * Aquí el dump queda en disco (cifrado con AES-256-GCM si hay llave
 * configurada), su metadata en la tabla backup_records, y la restauración
 * ejecuta un rollback real de todo el esquema — por eso antes de restaurar
 * se toma automáticamente un respaldo de seguridad del estado actual.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupService {

    private final BackupRecordRepository backupRecordRepository;
    private final AuditService auditService;

    @Value("${spring.datasource.url}")
    private String datasourceUrl;
    @Value("${spring.datasource.username}")
    private String dbUsername;
    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Value("${backup.directorio:./backups}")
    private String directorioBackups;
    @Value("${backup.mysqldump-path:mysqldump}")
    private String mysqldumpPath;
    @Value("${backup.mysql-path:mysql}")
    private String mysqlPath;
    @Value("${backup.storage-quota-mb:5120}")
    private long cuotaMb;
    @Value("${backup.encryption-key:}")
    private String encryptionKey;

    private static final Pattern URL_PATTERN = Pattern
            .compile("jdbc:mysql://([^:/]+):(\\d+)/([^?]+)");
    private static final String CONFIRMACION_ESPERADA = "RESTAURAR";

    // ─── Consultas ──────────────────────────────────────────────────────────

    public List<BackupRecordResponse> listar() {
        return backupRecordRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toResponse)
                .toList();
    }

    public BackupSummaryResponse resumen() {
        long total = backupRecordRepository.count();
        long bytesUsados = backupRecordRepository.findAllByOrderByCreatedAtDesc().stream()
                .filter(b -> b.getEstado() == Estado.COMPLETADO || b.getEstado() == Estado.RESTAURADO)
                .mapToLong(b -> b.getTamanoBytes() != null ? b.getTamanoBytes() : 0L)
                .sum();
        double cuotaBytes = cuotaMb * 1024d * 1024d;
        double pct = cuotaBytes > 0 ? Math.min(100.0, (bytesUsados / cuotaBytes) * 100.0) : 0;

        var ultimo = backupRecordRepository.findFirstByEstadoOrderByCreatedAtDesc(Estado.COMPLETADO)
                .or(() -> backupRecordRepository.findFirstByEstadoOrderByCreatedAtDesc(Estado.RESTAURADO));

        boolean bdActiva;
        try {
            backupRecordRepository.count();
            bdActiva = true;
        } catch (Exception e) {
            bdActiva = false;
        }

        String integridad = ultimo.map(b -> b.getChecksum() != null
                ? "Verificado (SHA-256)"
                : "Sin checksum registrado").orElse("Sin respaldos aún");

        return BackupSummaryResponse.builder()
                .totalBackups(total)
                .almacenamientoUsadoLegible(formatearTamano(bytesUsados))
                .porcentajeUsado(Math.round(pct * 10) / 10.0)
                .capacidadDetalle(formatearTamano(bytesUsados) + " / " + formatearTamano((long) cuotaBytes))
                .ultimoBackupLegible(ultimo.map(b -> formatearFechaRelativa(b.getCreatedAt())).orElse("Nunca"))
                .proximoBackupAutomatico(proximaEjecucionAutomatica())
                .baseDatosActiva(bdActiva)
                .cifradoActivo(encryptionKey != null && !encryptionKey.isBlank())
                .integridadUltimoRespaldo(integridad)
                .herramientasDisponibles(verificarHerramientas())
                .build();
    }

    // ─── Crear respaldo ─────────────────────────────────────────────────────

    public BackupRecordResponse crearBackupManual(User user) {
        BackupRecord registro = ejecutarDump(Metodo.MANUAL, user, "Respaldo manual solicitado desde el panel");
        return toResponse(registro);
    }

    /** Respaldo automático semanal — domingos 02:00 a.m. */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void crearBackupAutomatico() {
        try {
            ejecutarDump(Metodo.AUTOMATICO, null, "Respaldo automático programado (domingo 02:00)");
        } catch (Exception e) {
            log.error("Falló el respaldo automático programado", e);
        }
    }

    private BackupRecord ejecutarDump(Metodo metodo, User user, String detalle) {
        Matcher m = URL_PATTERN.matcher(datasourceUrl);
        if (!m.find()) {
            throw new IllegalStateException(
                    "No se pudo interpretar spring.datasource.url para respaldar la base de datos.");
        }
        String host = m.group(1);
        String port = m.group(2);
        String dbName = m.group(3);

        try {
            Path dir = Paths.get(directorioBackups);
            Files.createDirectories(dir);

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            String nombreBase = "bankomunal-" + timestamp + ".sql";
            boolean cifrar = encryptionKey != null && !encryptionKey.isBlank();
            String nombreArchivo = cifrar ? nombreBase + ".enc" : nombreBase;
            Path destino = dir.resolve(nombreArchivo);
            Path dumpPlano = cifrar ? Files.createTempFile("bkm-dump-", ".sql") : destino;

            ProcessBuilder pb = new ProcessBuilder(
                    resolverRuta(mysqldumpPath, "mysqldump"),
                    "--host=" + host,
                    "--port=" + port,
                    "-u" + dbUsername,
                    "--single-transaction",
                    "--routines",
                    "--triggers",
                    "--databases", dbName);
            pb.environment().put("MYSQL_PWD", dbPassword == null ? "" : dbPassword);
            pb.redirectErrorStream(false);
            pb.redirectOutput(dumpPlano.toFile());

            Process proceso = iniciarProceso(pb, "mysqldump");
            String stderr = leerFlujo(proceso.getErrorStream());
            int codigo = esperarProceso(proceso);

            if (codigo != 0) {
                Files.deleteIfExists(dumpPlano);
                throw new IllegalStateException(
                        "mysqldump terminó con error (código " + codigo + "): " +
                                (stderr.isBlank() ? "sin detalle" : stderr.trim()));
            }

            if (cifrar) {
                cifrarArchivo(dumpPlano, destino);
                Files.deleteIfExists(dumpPlano);
            }

            long tamano = Files.size(destino);
            String checksum = calcularChecksum(destino);

            BackupRecord registro = backupRecordRepository.save(BackupRecord.builder()
                    .nombreArchivo(nombreArchivo)
                    .rutaArchivo(destino.toAbsolutePath().toString())
                    .metodo(metodo)
                    .estado(Estado.COMPLETADO)
                    .tamanoBytes(tamano)
                    .checksum(checksum)
                    .cifrado(cifrar)
                    .responsable(user)
                    .responsableNombre(user != null ? (user.getFirstName() + " " +
                            (user.getLastName() != null ? user.getLastName() : "")).trim() : "Sistema")
                    .detalle(detalle)
                    .build());

            auditService.log(user, "BACKUP_CREADO", "BackupRecord", registro.getId(), null,
                    "Respaldo " + metodo + " generado: " + nombreArchivo + " (" + formatearTamano(tamano) + ")");
            return registro;

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            log.error("Error generando respaldo", e);
            String msg = mensajeHerramientaNoDisponible(e, mysqldumpPath);
            auditService.log(user, "BACKUP_FALLIDO", "BackupRecord", null, null, msg);
            throw new IllegalStateException(msg, e);
        }
    }

    // ─── Descargar ──────────────────────────────────────────────────────────

    public record ArchivoBackup(byte[] contenido, String nombreArchivo) {
    }

    public ArchivoBackup obtenerArchivoParaDescarga(Long id) {
        BackupRecord registro = backupRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Respaldo no encontrado"));
        if (registro.getEstado() != Estado.COMPLETADO && registro.getEstado() != Estado.RESTAURADO) {
            throw new IllegalStateException("Este respaldo no está disponible para descargar");
        }
        try {
            Path ruta = Paths.get(registro.getRutaArchivo());
            if (!Files.exists(ruta)) {
                throw new IllegalStateException("El archivo de este respaldo ya no existe en el servidor");
            }
            byte[] datos;
            if (registro.isCifrado()) {
                Path temp = Files.createTempFile("bkm-descarga-", ".sql");
                descifrarArchivo(ruta, temp);
                datos = Files.readAllBytes(temp);
                Files.deleteIfExists(temp);
            } else {
                datos = Files.readAllBytes(ruta);
            }
            String nombreDescarga = registro.getNombreArchivo().replace(".enc", "");
            return new ArchivoBackup(datos, nombreDescarga);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo del respaldo: " + e.getMessage(), e);
        }
    }

    // ─── Restaurar ──────────────────────────────────────────────────────────

    public void restaurar(Long id, String confirmacion, User user) {
        if (!CONFIRMACION_ESPERADA.equals(confirmacion)) {
            throw new IllegalArgumentException(
                    "Confirmación inválida. Escribe exactamente \"" + CONFIRMACION_ESPERADA + "\" para continuar.");
        }
        BackupRecord registro = backupRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Respaldo no encontrado"));
        if (registro.getEstado() != Estado.COMPLETADO && registro.getEstado() != Estado.RESTAURADO) {
            throw new IllegalStateException("Este respaldo no está en un estado restaurable");
        }

        Path ruta = Paths.get(registro.getRutaArchivo());
        if (!Files.exists(ruta)) {
            throw new IllegalStateException("El archivo de este respaldo ya no existe en el servidor");
        }

        try {
            String checksumActual = calcularChecksum(ruta);
            if (registro.getChecksum() != null && !registro.getChecksum().equals(checksumActual)) {
                throw new IllegalStateException(
                        "El archivo de respaldo no coincide con su checksum original — puede estar dañado o alterado. Restauración cancelada por seguridad.");
            }

            // Respaldo de seguridad del estado actual antes de sobrescribir todo.
            ejecutarDump(Metodo.PRE_RESTAURACION, user,
                    "Respaldo automático de seguridad antes de restaurar el respaldo #" + id);

            Path dumpPlano = registro.isCifrado() ? Files.createTempFile("bkm-restore-", ".sql") : ruta;
            if (registro.isCifrado()) {
                descifrarArchivo(ruta, dumpPlano);
            }

            Matcher m = URL_PATTERN.matcher(datasourceUrl);
            if (!m.find()) {
                throw new IllegalStateException("No se pudo interpretar spring.datasource.url para restaurar.");
            }
            String host = m.group(1);
            String port = m.group(2);

            ProcessBuilder pb = new ProcessBuilder(resolverRuta(mysqlPath, "mysql"), "--host=" + host, "--port=" + port, "-u" + dbUsername);
            pb.environment().put("MYSQL_PWD", dbPassword == null ? "" : dbPassword);
            pb.redirectInput(dumpPlano.toFile());

            Process proceso = iniciarProceso(pb, "mysql");
            String stderr = leerFlujo(proceso.getErrorStream());
            int codigo = esperarProceso(proceso);

            if (registro.isCifrado())
                Files.deleteIfExists(dumpPlano);

            if (codigo != 0) {
                throw new IllegalStateException(
                        "mysql terminó con error al restaurar (código " + codigo + "): " +
                                (stderr.isBlank() ? "sin detalle" : stderr.trim()));
            }

            registro.setEstado(Estado.RESTAURADO);
            registro.setRestoredAt(LocalDateTime.now());
            backupRecordRepository.save(registro);

            auditService.log(user, "RESTAURACION_EJECUTADA", "BackupRecord", id, null,
                    "Sistema restaurado desde el respaldo #" + id + " (" + registro.getNombreArchivo() + ")");

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException)
                Thread.currentThread().interrupt();
            String msg = mensajeHerramientaNoDisponible(e, mysqlPath);
            auditService.log(user, "RESTAURACION_FALLIDA", "BackupRecord", id, null,
                    "Falló restauración del respaldo #" + id + ": " + msg);
            throw new IllegalStateException(msg, e);
        }
    }

    // ─── Eliminar ───────────────────────────────────────────────────────────

    public void eliminar(Long id, User user) {
        BackupRecord registro = backupRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Respaldo no encontrado"));
        try {
            Files.deleteIfExists(Paths.get(registro.getRutaArchivo()));
        } catch (IOException e) {
            log.warn("No se pudo borrar el archivo físico de {}: {}", registro.getRutaArchivo(), e.getMessage());
        }
        backupRecordRepository.delete(registro);
        auditService.log(user, "BACKUP_ELIMINADO", "BackupRecord", id, null,
                "Respaldo #" + id + " (" + registro.getNombreArchivo() + ") eliminado");
    }

    // ─── Utilidades ─────────────────────────────────────────────────────────

    private Process iniciarProceso(ProcessBuilder pb, String herramienta) throws IOException {
        try {
            return pb.start();
        } catch (IOException e) {
            throw new IOException("No se encontró el ejecutable de " + herramienta +
                    ". Configura la ruta completa en application.properties (backup." +
                    herramienta + "-path) — por ejemplo, en XAMPP suele estar en " +
                    "C:/xampp/mysql/bin/" + herramienta + ".exe", e);
        }
    }

    private String mensajeHerramientaNoDisponible(Exception e, String rutaConfigurada) {
        if (e.getMessage() != null && e.getMessage().contains("No se encontró el ejecutable")) {
            return e.getMessage();
        }
        return "Error ejecutando '" + rutaConfigurada + "': " + e.getMessage();
    }

    private String leerFlujo(InputStream in) throws IOException {
        try (ByteArrayOutputStream buf = new ByteArrayOutputStream()) {
            in.transferTo(buf);
            return buf.toString();
        }
    }

    private int esperarProceso(Process p) throws InterruptedException {
        return p.waitFor();
    }

    private String verificarHerramientas() {
        boolean dump = existeEjecutable(resolverRuta(mysqldumpPath, "mysqldump"));
        boolean restore = existeEjecutable(resolverRuta(mysqlPath, "mysql"));
        if (dump && restore)
            return "mysqldump y mysql disponibles";
        if (!dump && !restore)
            return "mysqldump y mysql no encontrados en el PATH — configura backup.mysqldump-path y backup.mysql-path";
        return (!dump ? "mysqldump no encontrado" : "mysql no encontrado") +
                " — revisa la configuración de rutas de respaldo";
    }

    /**
     * Si la ruta configurada (o el nombre por defecto "mysqldump"/"mysql")
     * no resuelve directamente — típico en Windows sin XAMPP/MySQL en el
     * PATH — se prueba en las carpetas donde suelen instalarse estas
     * herramientas, para no obligar a editar application.properties a mano.
     */
    private String resolverRuta(String configurada, String nombreExe) {
        if (existeEjecutable(configurada)) {
            return configurada;
        }
        String exeWin = nombreExe + ".exe";
        for (Path candidato : candidatosWindows(exeWin)) {
            String ruta = candidato.toString();
            if (existeEjecutable(ruta)) {
                return ruta;
            }
        }
        return configurada;
    }

    /** Ubicaciones típicas de mysqldump.exe/mysql.exe en instalaciones Windows. */
    private List<Path> candidatosWindows(String exe) {
        List<Path> candidatos = new java.util.ArrayList<>();
        candidatos.add(Paths.get("C:/xampp/mysql/bin", exe));
        candidatos.add(Paths.get("C:/laragon/bin/mysql/mysql-8.0/bin", exe));
        agregarSubcarpetas(candidatos, "C:/wamp64/bin/mysql", exe);
        agregarSubcarpetas(candidatos, "C:/Program Files/MySQL", exe);
        return candidatos;
    }

    /** Busca "<base>/<cualquier-subcarpeta>/bin/<exe>", ej. MySQL Server 8.0/bin. */
    private void agregarSubcarpetas(List<Path> destino, String base, String exe) {
        Path baseDir = Paths.get(base);
        if (!Files.isDirectory(baseDir))
            return;
        try (DirectoryStream<Path> hijos = Files.newDirectoryStream(baseDir)) {
            for (Path hijo : hijos) {
                if (Files.isDirectory(hijo))
                    destino.add(hijo.resolve("bin").resolve(exe));
            }
        } catch (IOException ignored) {
            // si no se puede listar, simplemente no se agregan candidatos de esa base
        }
    }

    private boolean existeEjecutable(String ruta) {
        try {
            Process p = new ProcessBuilder(ruta, "--version").start();
            p.getOutputStream().close();
            boolean terminó = p.waitFor(3, java.util.concurrent.TimeUnit.SECONDS);
            if (!terminó) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private LocalDateTime proximaEjecucionAutomatica() {
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime proximoDomingo = ahora.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .withHour(2).withMinute(0).withSecond(0).withNano(0);
        if (!proximoDomingo.isAfter(ahora)) {
            proximoDomingo = proximoDomingo.plusWeeks(1);
        }
        return proximoDomingo;
    }

    private String formatearFechaRelativa(LocalDateTime fecha) {
        LocalDateTime ahora = LocalDateTime.now();
        if (fecha.toLocalDate().equals(ahora.toLocalDate())) {
            return "Hoy, " + fecha.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        if (fecha.toLocalDate().equals(ahora.toLocalDate().minusDays(1))) {
            return "Ayer, " + fecha.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String formatearTamano(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024)
            return String.format("%.1f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024)
            return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    private String calcularChecksum(Path archivo) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(archivo)) {
                byte[] buffer = new byte[8192];
                int leidos;
                while ((leidos = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, leidos);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest())
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private SecretKeySpec claveAes() throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] keyBytes = digest.digest(encryptionKey.getBytes("UTF-8"));
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IOException("No se pudo derivar la llave de cifrado", e);
        }
    }

    private void cifrarArchivo(Path origen, Path destino) throws IOException {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, claveAes(), new GCMParameterSpec(128, iv));
            try (OutputStream fos = Files.newOutputStream(destino)) {
                fos.write(iv);
                try (CipherOutputStream cos = new CipherOutputStream(fos, cipher);
                        InputStream fis = Files.newInputStream(origen)) {
                    fis.transferTo(cos);
                }
            }
        } catch (Exception e) {
            throw new IOException("Error cifrando el respaldo: " + e.getMessage(), e);
        }
    }

    private void descifrarArchivo(Path origen, Path destino) throws IOException {
        try (InputStream fis = Files.newInputStream(origen)) {
            byte[] iv = fis.readNBytes(12);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, claveAes(), new GCMParameterSpec(128, iv));
            try (CipherInputStream cis = new CipherInputStream(fis, cipher);
                    OutputStream fos = Files.newOutputStream(destino)) {
                cis.transferTo(fos);
            }
        } catch (Exception e) {
            throw new IOException("Error descifrando el respaldo: " + e.getMessage(), e);
        }
    }

    private BackupRecordResponse toResponse(BackupRecord b) {
        boolean disponible = b.getEstado() == Estado.COMPLETADO || b.getEstado() == Estado.RESTAURADO;
        return BackupRecordResponse.builder()
                .id(b.getId())
                .fecha(b.getCreatedAt())
                .responsable(b.getResponsableNombre() != null ? b.getResponsableNombre() : "Sistema")
                .metodo(b.getMetodo().name())
                .estado(b.getEstado().name())
                .tamanoLegible(b.getTamanoBytes() != null ? formatearTamano(b.getTamanoBytes()) : "--")
                .cifrado(b.isCifrado())
                .descargable(disponible)
                .restaurable(disponible)
                .build();
    }
}
