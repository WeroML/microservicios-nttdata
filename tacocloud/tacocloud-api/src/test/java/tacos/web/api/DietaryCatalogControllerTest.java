package tacos.web.api;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import tacos.Allergen;
import tacos.DietaryLabel;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.SpiceLevel;
import tacos.Taco;
import tacos.data.IngredientRepository;
import tacos.data.TacoRepository;

// Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
public class DietaryCatalogControllerTest {

  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private WebTestClient testClient;

  private Ingredient flourTortilla;
  private Ingredient cornTortilla;
  private Ingredient carnitas;
  private Ingredient salsa;
  private Ingredient habanero;
  private Ingredient cheese;

  private Taco veganTaco;
  private Taco meatTaco;
  private Taco spicyTaco;

  @BeforeEach
  public void setUp() {
    ingredientRepo = Mockito.mock(IngredientRepository.class);
    tacoRepo = Mockito.mock(TacoRepository.class);

    // FLTO: Harina con Gluten, Vegana, No picante
    flourTortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP,
        new BigDecimal("0.75"), true, 50,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN)),
        new HashSet<>(Collections.singletonList(Allergen.GLUTEN)),
        SpiceLevel.NONE);

    // COTO: Maíz, Sin gluten, Vegano, No picante
    cornTortilla = new Ingredient("COTO", "Corn Tortilla", Type.WRAP,
        new BigDecimal("0.70"), true, 50,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(),
        SpiceLevel.NONE);

    // CARN: Carnitas, No vegano, Sin gluten, Poco picante
    carnitas = new Ingredient("CARN", "Carnitas", Type.PROTEIN,
        new BigDecimal("2.80"), true, 40,
        new HashSet<>(Arrays.asList(DietaryLabel.KETO, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(),
        SpiceLevel.MILD);

    // SLSA: Salsa, Vegana, Sin gluten, Picor medio
    salsa = new Ingredient("SLSA", "Salsa", Type.SAUCE,
        new BigDecimal("0.60"), true, 60,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(),
        SpiceLevel.MEDIUM);

    // HBNR: Salsa Habanero, Vegana, Sin gluten, Muy picante
    habanero = new Ingredient("HBNR", "Habanero", Type.SAUCE,
        new BigDecimal("0.90"), true, 20,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(),
        SpiceLevel.EXTRA_HOT);

    // CHED: Queso, Vegetariano, Lácteo, No picante
    cheese = new Ingredient("CHED", "Cheddar", Type.CHEESE,
        new BigDecimal("0.80"), true, 30,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGETARIAN, DietaryLabel.KETO)),
        new HashSet<>(Collections.singletonList(Allergen.DAIRY)),
        SpiceLevel.NONE);

    veganTaco = new Taco();
    veganTaco.setId("TACO_VEGAN");
    veganTaco.setName("Vegan Taco");
    veganTaco.setIngredients(Arrays.asList(cornTortilla, salsa));

    meatTaco = new Taco();
    meatTaco.setId("TACO_MEAT");
    meatTaco.setName("Carnitas Wrap");
    meatTaco.setIngredients(Arrays.asList(flourTortilla, carnitas, cheese));

    spicyTaco = new Taco();
    spicyTaco.setId("TACO_SPICY");
    spicyTaco.setName("Fire Taco");
    spicyTaco.setIngredients(Arrays.asList(cornTortilla, habanero));

    when(ingredientRepo.findAll()).thenReturn(Flux.just(flourTortilla, cornTortilla, carnitas, salsa, habanero, cheese));
    when(tacoRepo.findAll()).thenReturn(Flux.just(veganTaco, meatTaco, spicyTaco));

    testClient = WebTestClient.bindToController(
        new CatalogController(ingredientRepo, tacoRepo)).build();
  }

  @Test
  public void getCatalog_filterByDietary_shouldReturnOnlyVeganItems() {
    testClient.get()
        .uri("/api/catalog?dietary=VEGAN")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.ingredients[?(@.id == 'CARN')]").doesNotExist()
          .jsonPath("$.ingredients[?(@.id == 'CHED')]").doesNotExist()
          .jsonPath("$.ingredients[?(@.id == 'COTO')]").exists()
          .jsonPath("$.tacos[?(@.id == 'TACO_MEAT')]").doesNotExist()
          .jsonPath("$.tacos[?(@.id == 'TACO_VEGAN')]").exists();
  }

  @Test
  public void getCatalog_excludeAllergen_shouldExcludeItemsWithGluten() {
    testClient.get()
        .uri("/api/catalog?excludeAllergen=GLUTEN")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.ingredients[?(@.id == 'FLTO')]").doesNotExist()
          .jsonPath("$.ingredients[?(@.id == 'COTO')]").exists()
          .jsonPath("$.tacos[?(@.id == 'TACO_MEAT')]").doesNotExist()
          .jsonPath("$.tacos[?(@.id == 'TACO_VEGAN')]").exists();
  }

  @Test
  public void getCatalog_maxSpice_shouldExcludeHotAndExtraHotItems() {
    testClient.get()
        .uri("/api/catalog?maxSpice=MILD")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.ingredients[?(@.id == 'SLSA')]").doesNotExist()
          .jsonPath("$.ingredients[?(@.id == 'HBNR')]").doesNotExist()
          .jsonPath("$.ingredients[?(@.id == 'CARN')]").exists()
          .jsonPath("$.ingredients[?(@.id == 'COTO')]").exists()
          .jsonPath("$.tacos[?(@.id == 'TACO_SPICY')]").doesNotExist();
  }

  @Test
  public void getIngredientsCatalog_withFilters_shouldReturnMatchingIngredients() {
    testClient.get()
        .uri("/api/catalog/ingredients?dietary=KETO&excludeAllergen=DAIRY")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$[?(@.id == 'CHED')]").doesNotExist() // Tiene alérgeno DAIRY
          .jsonPath("$[?(@.id == 'CARN')]").exists();      // KETO y sin DAIRY
  }

  @Test
  public void getTacosCatalog_withFilters_shouldReturnMatchingTacos() {
    testClient.get()
        .uri("/api/catalog/tacos?excludeAllergen=DAIRY")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$[?(@.id == 'TACO_MEAT')]").doesNotExist() // Tiene CHED (DAIRY)
          .jsonPath("$[?(@.id == 'TACO_VEGAN')]").exists();
  }
}
