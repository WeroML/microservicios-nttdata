package tacos;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import tacos.Ingredient.Type;

// Ejercicio 13: Catálogo con precio, disponibilidad y stock
public class IngredientCatalogDomainTest {

  @Test
  public void ingredient_shouldStorePriceAvailabilityAndStockCorrectly() {
    Ingredient ingredient = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("1.50"), true, 25);

    assertThat(ingredient.getId()).isEqualTo("FLTO");
    assertThat(ingredient.getName()).isEqualTo("Flour Tortilla");
    assertThat(ingredient.getType()).isEqualTo(Type.WRAP);
    assertThat(ingredient.getPrice()).isEqualByComparingTo(new BigDecimal("1.50"));
    assertThat(ingredient.getAvailable()).isTrue();
    assertThat(ingredient.getStock()).isEqualTo(25);
    assertThat(ingredient.isAvailable()).isTrue();
    assertThat(ingredient.isInStock()).isTrue();
  }

  @Test
  public void ingredient_whenStockIsZero_isAvailableShouldBeFalse() {
    Ingredient outOfStock = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("2.50"), true, 0);

    assertThat(outOfStock.getAvailable()).isTrue();
    assertThat(outOfStock.getStock()).isEqualTo(0);
    assertThat(outOfStock.isInStock()).isFalse();
    // Aunque available sea true, al no tener stock no está disponible para ordenar
    assertThat(outOfStock.isAvailable()).isFalse();
  }

  @Test
  public void ingredient_whenMarkedUnavailable_isAvailableShouldBeFalse() {
    Ingredient disabled = new Ingredient("TMTO", "Tomatoes", Type.VEGGIES, new BigDecimal("0.75"), false, 50);

    assertThat(disabled.getAvailable()).isFalse();
    assertThat(disabled.isAvailable()).isFalse();
  }

  @Test
  public void taco_shouldSupportCatalogFields() {
    Taco taco = new Taco();
    taco.setName("Carnitas Taco");
    taco.setPrice(new BigDecimal("5.99"));
    taco.setAvailable(true);
    taco.setStock(15);

    assertThat(taco.getPrice()).isEqualByComparingTo(new BigDecimal("5.99"));
    assertThat(taco.getAvailable()).isTrue();
    assertThat(taco.getStock()).isEqualTo(15);
    assertThat(taco.isAvailable()).isTrue();
    assertThat(taco.isInStock()).isTrue();
  }
}
