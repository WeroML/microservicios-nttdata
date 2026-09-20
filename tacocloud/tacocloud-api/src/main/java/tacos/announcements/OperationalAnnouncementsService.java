package tacos.announcements;

import java.util.Comparator;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.web.api.dto.CreateAnnouncementRequest;

// Ejercicio 33: Reemplazar Notes por anuncios operativos seguros
@Service
public class OperationalAnnouncementsService {

  private static final Logger log = LoggerFactory.getLogger(OperationalAnnouncementsService.class);

  private final ConcurrentMap<String, OperationalAnnouncement> store = new ConcurrentHashMap<>();

  /**
   * Obtiene todos los anuncios actualmente activos y vigentes, aplicando filtros opcionales.
   */
  public Flux<OperationalAnnouncement> getActiveAnnouncements(String scopeStr, String levelStr) {
    return Flux.fromIterable(store.values())
        .filter(OperationalAnnouncement::isCurrentlyActive)
        .filter(a -> matchesScope(a, scopeStr))
        .filter(a -> matchesLevel(a, levelStr))
        .sort(Comparator.comparing(OperationalAnnouncement::getCreatedAt).reversed());
  }

  /**
   * Obtiene el histórico completo de anuncios (activos e inactivos) para auditoría operativa.
   */
  public Flux<OperationalAnnouncement> getAllAnnouncements() {
    return Flux.fromIterable(store.values())
        .sort(Comparator.comparing(OperationalAnnouncement::getCreatedAt).reversed());
  }

  /**
   * Busca un anuncio por su identificador único persistente (UUID).
   */
  public Mono<OperationalAnnouncement> getAnnouncementById(String id) {
    if (id == null || id.trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del anuncio no puede ser nulo o vacío"));
    }
    OperationalAnnouncement announcement = store.get(id.trim());
    if (announcement == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Anuncio operativo no encontrado con id: " + id));
    }
    return Mono.just(announcement);
  }

  /**
   * Crea y publica un anuncio operativo de forma segura y atómica.
   */
  public Mono<OperationalAnnouncement> createAnnouncement(CreateAnnouncementRequest request, String createdBy) {
    if (request == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no puede ser nulo"));
    }

    String sanitizedTitle = sanitize(request.getTitle());
    String sanitizedMessage = sanitize(request.getMessage());

    if (sanitizedTitle.isEmpty() || sanitizedTitle.length() < 3) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El título del anuncio no es válido"));
    }
    if (sanitizedMessage.isEmpty() || sanitizedMessage.length() < 3) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El mensaje del anuncio no es válido"));
    }

    Date expiresAt = null;
    if (request.getDurationMinutes() != null && request.getDurationMinutes() > 0) {
      expiresAt = new Date(System.currentTimeMillis() + request.getDurationMinutes() * 60_000L);
    }

    String operator = (createdBy != null && !createdBy.trim().isEmpty()) ? createdBy.trim() : "system";

    OperationalAnnouncement announcement = OperationalAnnouncement.builder()
        .id(UUID.randomUUID().toString())
        .title(sanitizedTitle)
        .message(sanitizedMessage)
        .level(request.getLevel() != null ? request.getLevel() : AnnouncementLevel.INFO)
        .scope(request.getScope() != null ? request.getScope() : AnnouncementScope.ALL)
        .active(true)
        .createdAt(new Date())
        .expiresAt(expiresAt)
        .createdBy(operator)
        .build();

    store.put(announcement.getId(), announcement);

    log.info("// Ejercicio 33: [AUDIT] Anuncio operativo creado exitosamente: id={}, title='{}', level={}, scope={}, by={}",
        announcement.getId(), announcement.getTitle(), announcement.getLevel(), announcement.getScope(), operator);

    return Mono.just(announcement);
  }

  /**
   * Desactiva un anuncio operativo de manera segura (soft delete con auditoría).
   */
  public Mono<OperationalAnnouncement> deactivateAnnouncement(String id, String deactivatedBy) {
    if (id == null || id.trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del anuncio no puede ser nulo o vacío"));
    }

    String operator = (deactivatedBy != null && !deactivatedBy.trim().isEmpty()) ? deactivatedBy.trim() : "operator";

    OperationalAnnouncement updated = store.computeIfPresent(id.trim(), (k, existing) -> {
      existing.setActive(false);
      existing.setDeactivatedBy(operator);
      existing.setDeactivatedAt(new Date());
      return existing;
    });

    if (updated == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Anuncio operativo no encontrado con id: " + id));
    }

    log.info("// Ejercicio 33: [AUDIT] Anuncio operativo desactivado: id={}, by={}", id, operator);
    return Mono.just(updated);
  }

  /**
   * Elimina un anuncio de forma definitiva por ID.
   */
  public Mono<Boolean> deletePermanently(String id, String deletedBy) {
    if (id == null || id.trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del anuncio no puede ser nulo o vacío"));
    }
    OperationalAnnouncement removed = store.remove(id.trim());
    if (removed == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Anuncio operativo no encontrado con id: " + id));
    }
    log.warn("// Ejercicio 33: [AUDIT] Anuncio operativo eliminado permanentemente: id={}, by={}", id, deletedBy);
    return Mono.just(true);
  }

  /**
   * Utilidad para tests: reinicia el almacén.
   */
  public void clear() {
    store.clear();
  }

  private boolean matchesScope(OperationalAnnouncement announcement, String scopeStr) {
    if (scopeStr == null || scopeStr.trim().isEmpty()) {
      return true;
    }
    String s = scopeStr.trim().toUpperCase();
    if (announcement.getScope() == AnnouncementScope.ALL || s.equals("ALL")) {
      return true;
    }
    return announcement.getScope().name().equalsIgnoreCase(s);
  }

  private boolean matchesLevel(OperationalAnnouncement announcement, String levelStr) {
    if (levelStr == null || levelStr.trim().isEmpty()) {
      return true;
    }
    return announcement.getLevel().name().equalsIgnoreCase(levelStr.trim());
  }

  private String sanitize(String input) {
    if (input == null) {
      return "";
    }
    // Sanitización básica: remover etiquetas HTML/script potenciales para evitar inyecciones
    return input.replaceAll("(?i)<script.*?>.*?</script>", "")
        .replaceAll("<[^>]*>", "")
        .trim();
  }
}
