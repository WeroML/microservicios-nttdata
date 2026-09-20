package tacos.actuator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.actuate.endpoint.annotation.DeleteOperation;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;
import org.springframework.boot.actuate.endpoint.annotation.WriteOperation;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import tacos.announcements.AnnouncementLevel;
import tacos.announcements.AnnouncementScope;
import tacos.announcements.OperationalAnnouncement;
import tacos.announcements.OperationalAnnouncementsService;
import tacos.web.api.dto.CreateAnnouncementRequest;

// Ejercicio 33: Reemplazar Notes por anuncios operativos seguros
@Component
@Endpoint(id = "announcements", enableByDefault = true)
public class OperationalAnnouncementsEndpoint {

  private final OperationalAnnouncementsService service;

  public OperationalAnnouncementsEndpoint(OperationalAnnouncementsService service) {
    this.service = service;
  }

  @ReadOperation
  public List<OperationalAnnouncement> announcements(@Nullable String scope, @Nullable String level) {
    return service.getActiveAnnouncements(scope, level).collectList().block();
  }

  @ReadOperation
  public OperationalAnnouncement announcement(@Selector String id) {
    return service.getAnnouncementById(id).block();
  }

  @WriteOperation
  public OperationalAnnouncement createAnnouncement(
      String title,
      String message,
      @Nullable String level,
      @Nullable String scope,
      @Nullable Long durationMinutes,
      @Nullable String createdBy) {

    AnnouncementLevel annLevel = AnnouncementLevel.INFO;
    if (level != null && !level.trim().isEmpty()) {
      try {
        annLevel = AnnouncementLevel.valueOf(level.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }

    AnnouncementScope annScope = AnnouncementScope.ALL;
    if (scope != null && !scope.trim().isEmpty()) {
      try {
        annScope = AnnouncementScope.valueOf(scope.trim().toUpperCase());
      } catch (IllegalArgumentException ignored) {
      }
    }

    CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
        .title(title)
        .message(message)
        .level(annLevel)
        .scope(annScope)
        .durationMinutes(durationMinutes)
        .build();

    return service.createAnnouncement(request, createdBy).block();
  }

  @DeleteOperation
  public Map<String, Object> deleteAnnouncement(@Selector String id, @Nullable String deactivatedBy) {
    OperationalAnnouncement deactivated = service.deactivateAnnouncement(id, deactivatedBy).block();
    Map<String, Object> result = new HashMap<>();
    result.put("status", "DEACTIVATED");
    result.put("id", id);
    result.put("deactivatedBy", deactivated != null ? deactivated.getDeactivatedBy() : deactivatedBy);
    return result;
  }
}
