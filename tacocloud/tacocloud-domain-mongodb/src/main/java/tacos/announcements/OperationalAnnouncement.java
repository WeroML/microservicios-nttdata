package tacos.announcements;

import java.io.Serializable;
import java.util.Date;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 33: Reemplazar Notes por anuncios operativos seguros
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "operational_announcements")
public class OperationalAnnouncement implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  @Builder.Default
  private String id = UUID.randomUUID().toString();

  private String title;
  private String message;

  @Builder.Default
  private AnnouncementLevel level = AnnouncementLevel.INFO;

  @Builder.Default
  private AnnouncementScope scope = AnnouncementScope.ALL;

  @Builder.Default
  private boolean active = true;

  @Builder.Default
  private Date createdAt = new Date();

  private Date expiresAt;

  private String createdBy;
  private String deactivatedBy;
  private Date deactivatedAt;

  /**
   * Verifica si el anuncio ha expirado temporalmente.
   */
  public boolean isExpired() {
    if (expiresAt == null) {
      return false;
    }
    return new Date().after(expiresAt);
  }

  /**
   * Determina si el anuncio está actualmente activo y no expirado.
   */
  public boolean isCurrentlyActive() {
    return active && !isExpired();
  }
}
