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

  // Ejercicio 13: Catálogo con precio, disponibilidad y stock
  private java.math.BigDecimal price;
  private Boolean available;
  private Integer stock;

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  private Integer quantity;

  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  private java.util.Set<tacos.DietaryLabel> dietaryLabels;
  private java.util.Set<tacos.Allergen> allergens;
  private tacos.SpiceLevel spiceLevel;

  public TacoResponse(String id, String name, Date createdAt, List<Ingredient> ingredients,
                      java.math.BigDecimal price, Boolean available, Integer stock, Integer quantity) {
    this(id, name, createdAt, ingredients, price, available, stock, quantity, null, null, null);
  }

  /**
   * Mapeo de la entidad de persistencia al DTO de respuesta.
   */
  public static TacoResponse fromEntity(Taco taco) {
    if (taco == null) {
      return null;
    }
    java.util.Set<tacos.DietaryLabel> labels = taco.computeDietaryLabels();
    java.util.Set<tacos.Allergen> allergens = taco.computeAllergens();
    tacos.SpiceLevel spice = taco.computeSpiceLevel();

    return new TacoResponse(
        taco.getId(),
        taco.getName(),
        taco.getCreatedAt(),
        taco.getIngredients(),
        taco.getPrice(),
        taco.getAvailable(),
        taco.getStock(),
        taco.getQuantity(),
        labels,
        allergens,
        spice
    );
  }
}
