package tacos.events;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.Ingredient;
import tacos.Taco;

// Ejercicio 27: Contrato único de eventos de orden
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventTaco implements Serializable {

  private static final long serialVersionUID = 1L;

  private String tacoId;
  private String name;
  private int quantity;
  private List<String> ingredients;
  private BigDecimal price;

  public static OrderEventTaco fromTaco(Taco taco) {
    if (taco == null) {
      return null;
    }
    List<String> ingredientNames = new ArrayList<>();
    if (taco.getIngredients() != null) {
      for (Ingredient ing : taco.getIngredients()) {
        if (ing != null) {
          ingredientNames.add(ing.getName() != null ? ing.getName() : ing.getId());
        }
      }
    }

    return OrderEventTaco.builder()
        .tacoId(taco.getId())
        .name(taco.getName())
        .quantity(taco.getQuantity() != null && taco.getQuantity() > 0 ? taco.getQuantity() : 1)
        .ingredients(ingredientNames)
        .price(taco.getPrice())
        .build();
  }
}
