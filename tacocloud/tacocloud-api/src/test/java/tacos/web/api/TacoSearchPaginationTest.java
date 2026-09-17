package tacos.web.api;

import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

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
import tacos.data.TacoRepository;

// Ejercicio 19: Buscar, filtrar, ordenar y paginar tacos
public class TacoSearchPaginationTest {

  private TacoRepository tacoRepo;
  private WebTestClient testClient;

  private Ingredient cornTortilla;
  private Ingredient flourTortilla;
  private Ingredient carnitas;
  private Ingredient beef;
  private Ingredient salsa;
  private Ingredient habanero;
  private Ingredient cheddar;

  private Taco tacoAlPastor;
  private Taco tacoCarnitas;
  private Taco tacoVeggie;
  private Taco tacoSpicyBeast;
  private Taco tacoBeefy;

  @BeforeEach
  public void setUp() {
    tacoRepo = Mockito.mock(TacoRepository.class);
    TacoQueryService queryService = new TacoQueryService(tacoRepo);
    testClient = WebTestClient.bindToController(new TacoController(tacoRepo, null, queryService)).build();

    cornTortilla = new Ingredient("COTO", "Corn Tortilla", Type.WRAP,
        new BigDecimal("1.00"), true, 50,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.NONE);

    flourTortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP,
        new BigDecimal("1.20"), true, 50,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN)),
        new HashSet<>(Collections.singletonList(Allergen.GLUTEN)), SpiceLevel.NONE);

    carnitas = new Ingredient("CARN", "Carnitas", Type.PROTEIN,
        new BigDecimal("4.00"), true, 20,
        new HashSet<>(Arrays.asList(DietaryLabel.KETO, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.MILD);

    beef = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN,
        new BigDecimal("3.50"), true, 30,
        new HashSet<>(Arrays.asList(DietaryLabel.KETO, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.NONE);

    salsa = new Ingredient("SLSA", "Salsa Mild", Type.SAUCE,
        new BigDecimal("0.80"), true, 40,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.MILD);

    habanero = new Ingredient("HBNR", "Habanero Sauce", Type.SAUCE,
        new BigDecimal("1.50"), true, 15,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGAN, DietaryLabel.VEGETARIAN, DietaryLabel.GLUTEN_FREE, DietaryLabel.DAIRY_FREE)),
        Collections.emptySet(), SpiceLevel.EXTRA_HOT);

    cheddar = new Ingredient("CHED", "Cheddar Cheese", Type.CHEESE,
        new BigDecimal("1.00"), true, 25,
        new HashSet<>(Arrays.asList(DietaryLabel.VEGETARIAN, DietaryLabel.KETO)),
        new HashSet<>(Collections.singletonList(Allergen.DAIRY)), SpiceLevel.NONE);

    // Taco 1: Carnitas Classic (Price: $6.00, Mild, Stock: 10)
    tacoCarnitas = new Taco();
    tacoCarnitas.setId("T1");
    tacoCarnitas.setName("Carnitas Classic");
    tacoCarnitas.setPrice(new BigDecimal("6.00"));
    tacoCarnitas.setStock(10);
    tacoCarnitas.setAvailable(true);
    tacoCarnitas.setIngredients(Arrays.asList(flourTortilla, carnitas, cheddar));

    // Taco 2: Veggie Delight (Price: $1.80, Mild, Stock: 25, Vegan, Gluten Free)
    tacoVeggie = new Taco();
    tacoVeggie.setId("T2");
    tacoVeggie.setName("Veggie Delight");
    tacoVeggie.setPrice(new BigDecimal("1.80"));
    tacoVeggie.setStock(25);
    tacoVeggie.setAvailable(true);
    tacoVeggie.setIngredients(Arrays.asList(cornTortilla, salsa));

    // Taco 3: Spicy Beast (Price: $6.50, Extra Hot, Stock: 5)
    tacoSpicyBeast = new Taco();
    tacoSpicyBeast.setId("T3");
    tacoSpicyBeast.setName("Spicy Beast");
    tacoSpicyBeast.setPrice(new BigDecimal("6.50"));
    tacoSpicyBeast.setStock(5);
    tacoSpicyBeast.setAvailable(true);
    tacoSpicyBeast.setIngredients(Arrays.asList(cornTortilla, carnitas, habanero));

    // Taco 4: Beefy Supreme (Price: $5.70, None, Stock: 0, Unavailable)
    tacoBeefy = new Taco();
    tacoBeefy.setId("T4");
    tacoBeefy.setName("Beefy Supreme");
    tacoBeefy.setPrice(new BigDecimal("5.70"));
    tacoBeefy.setStock(0);
    tacoBeefy.setAvailable(false);
    tacoBeefy.setIngredients(Arrays.asList(flourTortilla, beef, cheddar));

    // Taco 5: Al Pastor (Price: $5.00, Mild, Stock: 8)
    tacoAlPastor = new Taco();
    tacoAlPastor.setId("T5");
    tacoAlPastor.setName("Al Pastor");
    tacoAlPastor.setPrice(new BigDecimal("5.00"));
    tacoAlPastor.setStock(8);
    tacoAlPastor.setAvailable(true);
    tacoAlPastor.setIngredients(Arrays.asList(cornTortilla, carnitas, salsa));

    List<Taco> allTacos = Arrays.asList(tacoCarnitas, tacoVeggie, tacoSpicyBeast, tacoBeefy, tacoAlPastor);
    when(tacoRepo.findAll()).thenReturn(Flux.fromIterable(allTacos));
  }

  @Test
  public void searchByName_shouldReturnMatchingTacos() {
    testClient.get()
        .uri("/api/tacos?search=classic")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Total-Count", "1")
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(1)
        .jsonPath("$.content[0].name").isEqualTo("Carnitas Classic");
  }

  @Test
  public void searchByNameOrIngredient_shouldMatchMultipleTacos() {
    // 'carnitas' is present in Carnitas Classic (name and ingredient), Spicy Beast (ingredient), and Al Pastor (ingredient)
    testClient.get()
        .uri("/api/tacos?search=carnitas")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Total-Count", "3")
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(3)
        .jsonPath("$.content.length()").isEqualTo(3);
  }

  @Test
  public void searchByIngredientName_shouldMatchTacoContainingIngredient() {
    testClient.get()
        .uri("/api/tacos?search=beef")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Total-Count", "1")
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(1)
        .jsonPath("$.content[0].name").isEqualTo("Beefy Supreme");
  }

  @Test
  public void filterByPriceRange_shouldReturnTacosWithinRange() {
    testClient.get()
        .uri("/api/tacos?minPrice=5.00&maxPrice=6.00&sortBy=price&sortDir=asc")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Total-Count", "3")
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(3)
        .jsonPath("$.content[0].name").isEqualTo("Al Pastor")
        .jsonPath("$.content[1].name").isEqualTo("Beefy Supreme")
        .jsonPath("$.content[2].name").isEqualTo("Carnitas Classic");
  }

  @Test
  public void filterByAvailabilityAndStock_shouldExcludeOutOfStockTacos() {
    testClient.get()
        .uri("/api/tacos?available=true&inStock=true")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(4)
        .jsonPath("$.content[?(@.name == 'Beefy Supreme')]").doesNotExist();
  }

  @Test
  public void filterByDietaryAndExcludeAllergen_shouldReturnVeganGlutenFreeTacos() {
    testClient.get()
        .uri("/api/tacos?dietary=VEGAN&excludeAllergen=GLUTEN")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(1)
        .jsonPath("$.content[0].name").isEqualTo("Veggie Delight");
  }

  @Test
  public void filterByMaxSpice_shouldExcludeExtraHotTacos() {
    testClient.get()
        .uri("/api/tacos?maxSpice=MILD")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.content[?(@.name == 'Spicy Beast')]").doesNotExist();
  }

  @Test
  public void sorting_byPriceDesc_shouldReturnSortedTacos() {
    testClient.get()
        .uri("/api/tacos?sortBy=price&sortDir=desc")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.content[0].name").isEqualTo("Spicy Beast") // 6.50
        .jsonPath("$.content[1].name").isEqualTo("Carnitas Classic") // 6.00
        .jsonPath("$.content[2].name").isEqualTo("Beefy Supreme") // 5.70
        .jsonPath("$.content[3].name").isEqualTo("Al Pastor") // 5.00
        .jsonPath("$.content[4].name").isEqualTo("Veggie Delight"); // 1.80
  }

  @Test
  public void pagination_shouldSliceAndReturnMetadata() {
    // Page 0, Size 2 (Sorted by name asc: Al Pastor, Beefy Supreme, Carnitas Classic, Spicy Beast, Veggie Delight)
    testClient.get()
        .uri("/api/tacos?page=0&size=2&sortBy=name&sortDir=asc")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Total-Count", "5")
        .expectHeader().valueEquals("X-Total-Pages", "3")
        .expectHeader().valueEquals("X-Current-Page", "0")
        .expectHeader().valueEquals("X-Page-Size", "2")
        .expectBody()
        .jsonPath("$.page").isEqualTo(0)
        .jsonPath("$.size").isEqualTo(2)
        .jsonPath("$.totalElements").isEqualTo(5)
        .jsonPath("$.totalPages").isEqualTo(3)
        .jsonPath("$.first").isEqualTo(true)
        .jsonPath("$.last").isEqualTo(false)
        .jsonPath("$.hasNext").isEqualTo(true)
        .jsonPath("$.hasPrevious").isEqualTo(false)
        .jsonPath("$.content.length()").isEqualTo(2)
        .jsonPath("$.content[0].name").isEqualTo("Al Pastor")
        .jsonPath("$.content[1].name").isEqualTo("Beefy Supreme");

    // Page 1, Size 2
    testClient.get()
        .uri("/api/tacos?page=1&size=2&sortBy=name&sortDir=asc")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Current-Page", "1")
        .expectBody()
        .jsonPath("$.page").isEqualTo(1)
        .jsonPath("$.first").isEqualTo(false)
        .jsonPath("$.last").isEqualTo(false)
        .jsonPath("$.hasNext").isEqualTo(true)
        .jsonPath("$.hasPrevious").isEqualTo(true)
        .jsonPath("$.content.length()").isEqualTo(2)
        .jsonPath("$.content[0].name").isEqualTo("Carnitas Classic")
        .jsonPath("$.content[1].name").isEqualTo("Spicy Beast");

    // Page 2, Size 2
    testClient.get()
        .uri("/api/tacos?page=2&size=2&sortBy=name&sortDir=asc")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Current-Page", "2")
        .expectBody()
        .jsonPath("$.page").isEqualTo(2)
        .jsonPath("$.first").isEqualTo(false)
        .jsonPath("$.last").isEqualTo(true)
        .jsonPath("$.hasNext").isEqualTo(false)
        .jsonPath("$.hasPrevious").isEqualTo(true)
        .jsonPath("$.content.length()").isEqualTo(1)
        .jsonPath("$.content[0].name").isEqualTo("Veggie Delight");
  }

  @Test
  public void recentTacos_remainsBackwardCompatible() {
    testClient.get()
        .uri("/api/tacos?recent")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$").isArray()
        .jsonPath("$.length()").isEqualTo(5);
  }
}
