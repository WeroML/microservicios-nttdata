package tacos.web.api;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.data.IngredientRepository;
import tacos.data.TacoRepository;

// Test para Ejercicio 13: Catálogo con precio, disponibilidad y stock
public class CatalogControllerTest {

  @Test
  public void getCatalog_shouldReturnIngredientsAndTacosWithPriceStockAndAvailability() {
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);

    Ingredient ing1 = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("1.25"), true, 50);
    Ingredient ing2 = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("2.75"), true, 30);

    Taco taco1 = new Taco();
    taco1.setId("TACO1");
    taco1.setName("Carnitas Taco");
    taco1.setPrice(new BigDecimal("5.99"));
    taco1.setAvailable(true);
    taco1.setStock(20);
    taco1.setIngredients(Arrays.asList(ing1, ing2));

    when(ingredientRepo.findAll()).thenReturn(Flux.just(ing1, ing2));
    when(tacoRepo.findAll()).thenReturn(Flux.just(taco1));

    WebTestClient testClient = WebTestClient.bindToController(
        new CatalogController(ingredientRepo, tacoRepo)).build();

    testClient.get()
        .uri("/api/catalog")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.ingredients").isArray()
          .jsonPath("$.ingredients[0].id").isEqualTo("FLTO")
          .jsonPath("$.ingredients[0].price").isEqualTo(1.25)
          .jsonPath("$.ingredients[0].available").isEqualTo(true)
          .jsonPath("$.ingredients[0].stock").isEqualTo(50)
          .jsonPath("$.tacos").isArray()
          .jsonPath("$.tacos[0].id").isEqualTo("TACO1")
          .jsonPath("$.tacos[0].name").isEqualTo("Carnitas Taco")
          .jsonPath("$.tacos[0].price").isEqualTo(5.99)
          .jsonPath("$.tacos[0].available").isEqualTo(true)
          .jsonPath("$.tacos[0].stock").isEqualTo(20);

    verify(ingredientRepo).findAll();
    verify(tacoRepo).findAll();
  }

  @Test
  public void getIngredientsCatalog_onlyAvailable_shouldFilterAvailableWithStock() {
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);

    Ingredient availableIng = new Ingredient("CHED", "Cheddar", Type.CHEESE, new BigDecimal("0.80"), true, 15);
    when(ingredientRepo.findByAvailableTrueAndStockGreaterThan(0)).thenReturn(Flux.just(availableIng));

    WebTestClient testClient = WebTestClient.bindToController(
        new CatalogController(ingredientRepo, tacoRepo)).build();

    testClient.get()
        .uri("/api/catalog/ingredients?onlyAvailable=true")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$[0].id").isEqualTo("CHED")
          .jsonPath("$[0].price").isEqualTo(0.80)
          .jsonPath("$[0].available").isEqualTo(true)
          .jsonPath("$[0].stock").isEqualTo(15);

    verify(ingredientRepo).findByAvailableTrueAndStockGreaterThan(0);
  }
}
