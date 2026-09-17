package tacos.web.api.dto;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 22: Calificaciones y ranking de tacos
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingRequest {

  @Min(value = 1, message = "La calificación mínima permitida es 1 estrella")
  @Max(value = 5, message = "La calificación máxima permitida es 5 estrellas")
  private int rating;

  @Size(max = 500, message = "El comentario no puede exceder 500 caracteres")
  private String comment;
}
