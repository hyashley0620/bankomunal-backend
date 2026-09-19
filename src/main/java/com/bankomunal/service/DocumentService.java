package com.bankomunal.service;

import com.bankomunal.entity.*;
import com.bankomunal.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentoRepository;
    private final DocumentVersionRepository versionRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    /**
     * Carpeta privada — fuera de /uploads (que se sirve públicamente). El acceso
     * a estos archivos SIEMPRE pasa por DocumentController, que valida dueño.
     */
    private static final Path RAIZ = Paths.get("private-uploads", "documentos");

    public List<Document> misDocumentos(User user) {
        return documentoRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
    }

    /**
     * (ADMIN) Documentos de un socio específico — para verificar identidad o
     * soportes al momento de aprobar un préstamo. Cada consulta queda en el
     * registro de auditoría, para que quede trazable quién miró qué.
     */
    public List<Document> documentosDeUsuario(Long userId, User admin) {
        User socio = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
        auditService.log(admin, "ADMIN_VIEWED_DOCUMENTS", "Document", userId, "system",
                "Consultó los documentos de " + socio.getFirstName() + " " +
                        (socio.getLastName() != null ? socio.getLastName() : "") + " (id " + userId + ")");
        return documentoRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public DocumentVersion versionActual(Document doc) {
        return versionRepository.findTopByDocumentoIdOrderByVersionDesc(doc.getId())
                .orElseThrow(() -> new IllegalStateException("El documento no tiene versiones."));
    }

    public List<DocumentVersion> historialVersiones(Document doc) {
        return versionRepository.findByDocumentoIdOrderByVersionDesc(doc.getId());
    }

    @Transactional
    public Document subir(MultipartFile file, String nombre, String categoria, User user) {
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("El archivo está vacío.");
        if (file.getSize() > 10L * 1024 * 1024)
            throw new IllegalArgumentException("El archivo supera el límite de 10 MB.");

        Document doc = documentoRepository.save(Document.builder()
                .user(user)
                .nombre(nombre != null && !nombre.isBlank() ? nombre : file.getOriginalFilename())
                .categoria(categoria != null && !categoria.isBlank() ? categoria : "otro")
                .versionActual(1)
                .build());

        guardarVersion(doc, file, 1);
        return doc;
    }

    @Transactional
    public Document nuevaVersion(Long documentoId, MultipartFile file, User user) {
        Document doc = obtenerPropio(documentoId, user);
        if (file == null || file.isEmpty())
            throw new IllegalArgumentException("El archivo está vacío.");
        if (file.getSize() > 10L * 1024 * 1024)
            throw new IllegalArgumentException("El archivo supera el límite de 10 MB.");

        int nuevaVersion = doc.getVersionActual() + 1;
        guardarVersion(doc, file, nuevaVersion);
        doc.setVersionActual(nuevaVersion);
        doc.setUpdatedAt(LocalDateTime.now());
        return documentoRepository.save(doc);
    }

    private void guardarVersion(Document doc, MultipartFile file, int version) {
        try {
            Path carpeta = RAIZ.resolve(String.valueOf(doc.getUser().getId()));
            Files.createDirectories(carpeta);
            String ext = getExtension(file.getOriginalFilename());
            String filename = "doc_" + doc.getId() + "_v" + version + "_" + UUID.randomUUID()
                    + (ext.isEmpty() ? "" : "." + ext);
            Path destino = carpeta.resolve(filename);
            Files.copy(file.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);

            versionRepository.save(DocumentVersion.builder()
                    .documento(doc)
                    .version(version)
                    .nombreArchivo(file.getOriginalFilename() != null ? file.getOriginalFilename() : filename)
                    .rutaArchivo(destino.toString())
                    .contentType(file.getContentType())
                    .tamanoBytes(file.getSize())
                    .build());
        } catch (IOException e) {
            throw new RuntimeException("Error al guardar el documento: " + e.getMessage());
        }
    }

    @Transactional
    public void eliminar(Long documentoId, User user) {
        Document doc = obtenerPropio(documentoId, user);
        historialVersiones(doc).forEach(v -> {
            try {
                Files.deleteIfExists(Paths.get(v.getRutaArchivo()));
            } catch (IOException ignored) {
            }
        });
        versionRepository.deleteAll(historialVersiones(doc));
        documentoRepository.delete(doc);
    }

    /**
     * Verifica que el documento exista y pertenezca al usuario autenticado (o sea
     * admin). Usado para operaciones de escritura (nueva versión, eliminar) — el
     * tesorero puede REVISAR documentos de un socio pero no modificarlos/borrarlos.
     *
     * Los documentos de identidad (cédula/selfie subidos en el registro) quedan
     * bloqueados para el propio socio aquí: la entidad necesita conservarlos como
     * quedaron en el registro, así que solo un admin puede reemplazarlos o
     * borrarlos. El socio los sigue viendo y descargando en "Mis Documentos"
     * (eso pasa por {@link #misDocumentos}, no por este método), solo no puede
     * modificarlos ni eliminarlos.
     */
    public Document obtenerPropio(Long documentoId, User user) {
        Document doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado."));
        boolean esAdmin = user.getRoles().stream().anyMatch(r -> "admin".equalsIgnoreCase(r.getName()));
        boolean esDueño = doc.getUser().getId().equals(user.getId());
        if (!esAdmin && !esDueño)
            throw new SecurityException("No tienes permiso para acceder a este documento.");
        if (!esAdmin && "identidad".equals(doc.getCategoria()))
            throw new SecurityException(
                    "Los documentos de identidad no se pueden modificar ni eliminar. Contacta a un administrador si necesitas actualizarlos.");
        if (esAdmin && !esDueño) {
            auditService.log(user, "ADMIN_ACCESSED_DOCUMENT", "Document", doc.getId(), "system",
                    "Accedió al documento '" + doc.getNombre() + "' de " + doc.getUser().getFirstName()
                            + " (id " + doc.getUser().getId() + ")");
        }
        return doc;
    }

    /**
     * Igual que {@link #obtenerPropio}, pero para operaciones de SOLO LECTURA
     * (preview/descarga). Además del dueño y admin, incluye a tesorero, ya que
     * también aprueba préstamos y necesita revisar soportes de identidad.
     */
    public Document obtenerParaVisualizar(Long documentoId, User user) {
        Document doc = documentoRepository.findById(documentoId)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado."));
        boolean esPrivilegiado = user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getName()) || "tesorero".equalsIgnoreCase(r.getName()));
        boolean esDueño = doc.getUser().getId().equals(user.getId());
        if (!esPrivilegiado && !esDueño)
            throw new SecurityException("No tienes permiso para acceder a este documento.");
        if (esPrivilegiado && !esDueño) {
            auditService.log(user, "ADMIN_ACCESSED_DOCUMENT", "Document", doc.getId(), "system",
                    "Accedió al documento '" + doc.getNombre() + "' de " + doc.getUser().getFirstName()
                            + " (id " + doc.getUser().getId() + ")");
        }
        return doc;
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains("."))
            return "";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    public static String formatTamano(Long bytes) {
        if (bytes == null)
            return "-";
        if (bytes < 1024)
            return bytes + " B";
        if (bytes < 1024 * 1024)
            return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }
}
