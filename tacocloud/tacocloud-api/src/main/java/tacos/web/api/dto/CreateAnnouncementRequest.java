package tacos.web.api.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Positive;
import javax.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.announcements.AnnouncementLevel;
import tacos.announcements.AnnouncementScope;

// Ejercicio 33: Reemplazar Notes por anuncios operativos seguros
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnnouncementRequest {

  @NotBlank(message = "El título no puede estar vacío")
  @Size(min = 3, max = 100, message = "El título debe tener entre 3 y 100 caracteres")
  private String title;

  @NotBlank(message = "El mensaje no puede estar vacío")
  @Size(min = 3, max = 1000, message = "El mensaje debe tener entre 3 y 1000 caracteres")
  private String message;

  @Builder.Default
  private AnnouncementLevel level = AnnouncementLevel.INFO;

  @Builder.Default
  private AnnouncementScope scope = AnnouncementScope.ALL;

  @Positive(message = "La duración en minutos debe ser positiva")
  private Long durationMinutes;
}
