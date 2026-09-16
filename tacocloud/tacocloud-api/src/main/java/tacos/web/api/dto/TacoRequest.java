package tacos.web.api.dto;

import java.util.List;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.Ingredient;
import tacos.Taco;

/**
 * DTO de Entrada (Request):
 * Define estrictamente los campos que el cliente puede enviar al diseñar/crear un Taco.
 * Protege contra vulnerabilidades de Mass Assignment (Over-posting) al no exponer
 * campos internos del sistema como 'id' o 'createdAt'.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TacoRequest {

  @NotNull
  @Size(min = 5, message = "Name must be at least 5 characters long")
  private String name;

  @NotNull
  @Size(min = 1, message = "You must choose at least 1 ingredient")
  private List<Ingredient> ingredients;

  /**
   * Mapeo del DTO de entrada a la entidad de persistencia del dominio.
   */
  public Taco toEntity() {
    Taco taco = new Taco();
    taco.setName(this.name);
    taco.setIngredients(this.ingredients);
    return taco;
  }
}
