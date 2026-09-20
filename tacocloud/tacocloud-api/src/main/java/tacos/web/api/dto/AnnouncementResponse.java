package tacos.web.api.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.announcements.AnnouncementLevel;
import tacos.announcements.AnnouncementScope;
import tacos.announcements.OperationalAnnouncement;

// Ejercicio 33: Reemplazar Notes por anuncios operativos seguros
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnouncementResponse {

  private String id;
  private String title;
  private String message;
  private AnnouncementLevel level;
  private AnnouncementScope scope;
  private boolean active;
  private Date createdAt;
  private Date expiresAt;
  private String createdBy;
  private String deactivatedBy;
  private Date deactivatedAt;

  public static AnnouncementResponse fromEntity(OperationalAnnouncement entity) {
    if (entity == null) {
      return null;
    }
    return AnnouncementResponse.builder()
        .id(entity.getId())
        .title(entity.getTitle())
        .message(entity.getMessage())
        .level(entity.getLevel())
        .scope(entity.getScope())
        .active(entity.isCurrentlyActive())
        .createdAt(entity.getCreatedAt())
        .expiresAt(entity.getExpiresAt())
        .createdBy(entity.getCreatedBy())
        .deactivatedBy(entity.getDeactivatedBy())
        .deactivatedAt(entity.getDeactivatedAt())
        .build();
  }
}
