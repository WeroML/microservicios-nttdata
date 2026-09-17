package tacos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import tacos.Ingredient.Type;
import tacos.TacoOrder.OrderStatus;

// Ejercicio 16: Reservar y liberar inventario sin vender aire
public class InventoryDomainTest {

  @Test
  public void ingredient_hasSufficientStock_whenStockIsEnough_returnsTrue() {
    Ingredient ingredient = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("0.75"), true, 10);

    assertThat(ingredient.hasSufficientStock(5)).isTrue();
    assertThat(ingredient.hasSufficientStock(10)).isTrue();
    assertThat(ingredient.hasSufficientStock(11)).isFalse();
  }

  @Test
  public void ingredient_hasSufficientStock_whenUnavailable_returnsFalse() {
    Ingredient ingredient = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("0.75"), false, 10);

    assertThat(ingredient.hasSufficientStock(5)).isFalse();
  }

  @Test
  public void ingredient_decrementStock_decrementsAndMarksUnavailableAtZero() {
    Ingredient ingredient = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("2.50"), true, 5);

    ingredient.decrementStock(3);
    assertThat(ingredient.getStock()).isEqualTo(2);
    assertThat(ingredient.isAvailable()).isTrue();

    ingredient.decrementStock(2);
    assertThat(ingredient.getStock()).isEqualTo(0);
    assertThat(ingredient.isAvailable()).isFalse();
    assertThat(ingredient.isInStock()).isFalse();
  }

  @Test
  public void ingredient_incrementStock_incrementsAndRestoresAvailability() {
    Ingredient ingredient = new Ingredient("CARN", "Carnitas", Type.PROTEIN, new BigDecimal("2.80"), false, 0);

    ingredient.incrementStock(15);
    assertThat(ingredient.getStock()).isEqualTo(15);
    assertThat(ingredient.isAvailable()).isTrue();
    assertThat(ingredient.isInStock()).isTrue();
  }

  @Test
  public void taco_stockManagement_decrementsAndRestoresAvailability() {
    Taco taco = new Taco();
    taco.setName("Carnivore");
    taco.setStock(2);
    taco.setAvailable(true);

    assertThat(taco.hasSufficientStock(2)).isTrue();
    assertThat(taco.hasSufficientStock(3)).isFalse();

    taco.decrementStock(2);
    assertThat(taco.getStock()).isEqualTo(0);
    assertThat(taco.isAvailable()).isFalse();

    taco.incrementStock(5);
    assertThat(taco.getStock()).isEqualTo(5);
    assertThat(taco.isAvailable()).isTrue();
  }

  @Test
  public void tacoOrder_status_defaultsToConfirmedAndCanChange() {
    TacoOrder order = new TacoOrder();
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);

    order.setStatus(OrderStatus.CANCELLED);
    assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
  }
}
