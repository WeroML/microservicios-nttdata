package tacos.web.api.dto;

import java.util.Date;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.Ingredient;
import tacos.Taco;

/**
 * DTO de Respuesta (Response):
 * Define el contrato público devuelto al cliente HTTP.
 * Desacopla la API del esquema y metadatos internos de persistencia MongoDB.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TacoResponse {

  private String id;
  private String name;
  private Date createdAt;
  private List<Ingredient> ingredients;

  /**
   * Mapeo de la entidad de persistencia al DTO de respuesta.
   */
  public static TacoResponse fromEntity(Taco taco) {
    if (taco == null) {
      return null;
    }
    return new TacoResponse(
        taco.getId(),
        taco.getName(),
        taco.getCreatedAt(),
        taco.getIngredients()
    );
  }
}
