package tacos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import tacos.Ingredient.Type;

// Ejercicio 14: Calcular precios y cantidades del lado servidor
public class OrderPricingDomainTest {

  @Test
  public void taco_shouldDefaultQuantityToOneWhenMissingOrZeroOrNegative() {
    Taco taco = new Taco();
    assertThat(taco.getQuantity()).isEqualTo(1);

    taco.setQuantity(null);
    assertThat(taco.getQuantity()).isEqualTo(1);

    taco.setQuantity(0);
    assertThat(taco.getQuantity()).isEqualTo(1);

    taco.setQuantity(-5);
    assertThat(taco.getQuantity()).isEqualTo(1);

    taco.setQuantity(3);
    assertThat(taco.getQuantity()).isEqualTo(3);
  }

  @Test
  public void taco_calculatePriceFromIngredients_shouldSumIngredientPrices() {
    Ingredient tortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("0.75"), true, 10);
    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("2.50"), true, 10);
    Ingredient cheese = new Ingredient("CHED", "Cheddar", Type.CHEESE, new BigDecimal("0.90"), true, 10);

    Taco taco = new Taco();
    taco.setName("Cheesy Beef Taco");
    taco.setIngredients(Arrays.asList(tortilla, beef, cheese));

    BigDecimal price = taco.calculatePriceFromIngredients();

    assertThat(price).isEqualByComparingTo(new BigDecimal("4.15"));
    assertThat(taco.getPrice()).isEqualByComparingTo(new BigDecimal("4.15"));
  }

  @Test
  public void order_calculateTotal_shouldSumTacoPricesMultipliedByQuantities() {
    Taco taco1 = new Taco();
    taco1.setName("Taco 1");
    taco1.setPrice(new BigDecimal("4.15"));
    taco1.setQuantity(2); // 4.15 * 2 = 8.30

    Taco taco2 = new Taco();
    taco2.setName("Taco 2");
    taco2.setPrice(new BigDecimal("5.00"));
    taco2.setQuantity(1); // 5.00 * 1 = 5.00

    TacoOrder order = new TacoOrder();
    order.addTaco(taco1);
    order.addTaco(taco2);

    BigDecimal total = order.calculateTotal();

    assertThat(total).isEqualByComparingTo(new BigDecimal("13.30"));
    assertThat(order.getTotal()).isEqualByComparingTo(new BigDecimal("13.30"));
  }
}
