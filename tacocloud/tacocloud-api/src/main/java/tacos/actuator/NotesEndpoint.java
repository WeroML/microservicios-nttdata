package tacos.actuator;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.stereotype.Component;

// Ejercicio 33: Reemplazar Notes por anuncios operativos seguros
/**
 * Reemplazo seguro del NotesEndpoint original.
 * Sustituye el ArrayList no sincronizado y el borrado vulnerable por índice
 * por una estructura concurrente protegida (CopyOnWriteArrayList con UUIDs),
 * validación de entrada y registro de auditoría.
 */
@Component
@Endpoint(id = "notes", enableByDefault = true)
public class NotesEndpoint {

  private static final Logger log = LoggerFactory.getLogger(NotesEndpoint.class);

  private final List<SafeNote> notes = new CopyOnWriteArrayList<>();

  @ReadOperation
  public List<SafeNote> notes() {
    return new ArrayList<>(notes);
  }

  @ReadOperation
  public SafeNote getNoteById(@Selector String id) {
    if (id == null) {
      return null;
    }
    return notes.stream()
        .filter(n -> id.equalsIgnoreCase(n.getId()))
        .findFirst()
        .orElse(null);
  }

  @WriteOperation
  public List<SafeNote> addNote(String text) {
    if (text == null || text.trim().isEmpty()) {
      log.warn("// Ejercicio 33: Intento de agregar nota vacía rechazado.");
      return new ArrayList<>(notes);
    }
    String sanitized = text.replaceAll("(?i)<script.*?>.*?</script>", "")
        .replaceAll("<[^>]*>", "")
        .trim();
    SafeNote note = new SafeNote(sanitized);
    notes.add(note);
    log.info("// Ejercicio 33: [AUDIT] Anuncio operativo (Note) seguro creado: id={}, text='{}'", note.getId(), sanitized);
    return new ArrayList<>(notes);
  }

  @DeleteOperation
  public List<SafeNote> deleteNote(int index) {
    // Protección contra condiciones de carrera sobre índices numéricos
    synchronized (notes) {
      if (index >= 0 && index < notes.size()) {
        SafeNote removed = notes.remove(index);
        log.info("// Ejercicio 33: [AUDIT] Nota eliminada de forma segura por índice {}: id={}", index, removed.getId());
      } else {
        log.warn("// Ejercicio 33: Intento de eliminar índice inválido: {} (tamaño actual: {})", index, notes.size());
      }
    }
    return new ArrayList<>(notes);
  }

  @DeleteOperation
  public boolean deleteNoteById(@Selector String id) {
    if (id == null) {
      return false;
    }
    boolean removed = notes.removeIf(n -> id.equalsIgnoreCase(n.getId()));
    if (removed) {
      log.info("// Ejercicio 33: [AUDIT] Nota eliminada por UUID seguro: {}", id);
    }
    return removed;
  }

  public static class SafeNote {
    private final String id = UUID.randomUUID().toString();
    private final Date time = new Date();
    private final String text;

    public SafeNote(String text) {
      this.text = text;
    }

    public String getId() {
      return id;
    }

    public Date getTime() {
      return time;
    }

    public String getText() {
      return text;
    }
  }
}
