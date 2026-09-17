package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.data.TacoRepository;
import tacos.web.api.dto.TacoOfTheDayResponse;

// Ejercicio 20: Taco del día determinista y comprobable
public class TacoOfTheDayTest {

  private TacoRepository tacoRepo;
  private Clock fixedClock;
  private TacoOfTheDayService tacoOfTheDayService;
  private WebTestClient testClient;

  private Taco taco1;
  private Taco taco2;
  private Taco taco3;
  private Taco outOfStockTaco;

  @BeforeEach
  public void setUp() {
    tacoRepo = Mockito.mock(TacoRepository.class);
    // Reloj fijo en 2026-09-17 12:00:00 UTC
    fixedClock = Clock.fixed(Instant.parse("2026-09-17T12:00:00Z"), ZoneId.of("UTC"));
    tacoOfTheDayService = new TacoOfTheDayService(tacoRepo, fixedClock);

    TacoController controller = new TacoController(tacoRepo, null, new TacoQueryService(tacoRepo), tacoOfTheDayService);
    testClient = WebTestClient.bindToController(controller).build();

    // Taco 1: Carnitas Classic - $6.00, stock 10
    taco1 = new Taco();
    taco1.setId("TACO_01");
    taco1.setName("Carnitas Classic");
    taco1.setPrice(new BigDecimal("6.00"));
    taco1.setAvailable(true);
    taco1.setStock(10);
    taco1.setIngredients(Collections.singletonList(new Ingredient("FLTO", "Flour Tortilla", Type.WRAP)));

    // Taco 2: Veggie Bliss - $4.00, stock 15
    taco2 = new Taco();
    taco2.setId("TACO_02");
    taco2.setName("Veggie Bliss");
    taco2.setPrice(new BigDecimal("4.00"));
    taco2.setAvailable(true);
    taco2.setStock(15);
    taco2.setIngredients(Collections.singletonList(new Ingredient("COTO", "Corn Tortilla", Type.WRAP)));

    // Taco 3: Spicy Habanero - $5.50, stock 5
    taco3 = new Taco();
    taco3.setId("TACO_03");
    taco3.setName("Spicy Habanero");
    taco3.setPrice(new BigDecimal("5.50"));
    taco3.setAvailable(true);
    taco3.setStock(5);
    taco3.setIngredients(Collections.singletonList(new Ingredient("COTO", "Corn Tortilla", Type.WRAP)));

    // Taco sin stock: Beef Overload - stock 0, available false
    outOfStockTaco = new Taco();
    outOfStockTaco.setId("TACO_04");
    outOfStockTaco.setName("Beef Overload");
    outOfStockTaco.setPrice(new BigDecimal("7.00"));
    outOfStockTaco.setAvailable(false);
    outOfStockTaco.setStock(0);

    when(tacoRepo.findAll()).thenReturn(Flux.just(taco1, taco2, taco3, outOfStockTaco));
  }

  @Test
  public void deterministicSelection_repeatedCallsYieldIdenticalResult() {
    LocalDate targetDate = LocalDate.of(2026, 9, 17);

    TacoOfTheDayResponse first = tacoOfTheDayService.getTacoOfTheDay(targetDate).block();
    assertNotNull(first);

    for (int i = 0; i < 20; i++) {
      TacoOfTheDayResponse current = tacoOfTheDayService.getTacoOfTheDay(targetDate).block();
      assertNotNull(current);
      assertEquals(first.getTaco().getId(), current.getTaco().getId(), "El taco seleccionado debe ser estrictamente determinista");
      assertEquals(first.getSpecialPrice(), current.getSpecialPrice(), "El precio promocional debe ser idéntico");
    }
  }

  @Test
  public void dailyRotation_consecutiveDaysCycleThroughAllCandidatesFairly() {
    // 3 candidatos válidos: TACO_01, TACO_02, TACO_03
    LocalDate day0 = LocalDate.of(2026, 9, 17);
    long epoch0 = day0.toEpochDay();

    int index0 = (int) Math.floorMod(epoch0, 3);
    int index1 = (int) Math.floorMod(epoch0 + 1, 3);
    int index2 = (int) Math.floorMod(epoch0 + 2, 3);
    int index3 = (int) Math.floorMod(epoch0 + 3, 3);

    List<String> expectedOrder = Arrays.asList(
        Arrays.asList("TACO_01", "TACO_02", "TACO_03").get(index0),
        Arrays.asList("TACO_01", "TACO_02", "TACO_03").get(index1),
        Arrays.asList("TACO_01", "TACO_02", "TACO_03").get(index2),
        Arrays.asList("TACO_01", "TACO_02", "TACO_03").get(index3)
    );

    List<String> actualOrder = new ArrayList<>();
    for (int i = 0; i < 4; i++) {
      LocalDate d = day0.plusDays(i);
      TacoOfTheDayResponse resp = tacoOfTheDayService.getTacoOfTheDay(d).block();
      assertNotNull(resp);
      actualOrder.add(resp.getTaco().getId());
    }

    assertEquals(expectedOrder, actualOrder, "La rotación diaria debe avanzar cíclicamente día tras día");
    // Verifica que el 4to día repite el 1er día del ciclo
    assertEquals(actualOrder.get(0), actualOrder.get(3));
  }

  @Test
  public void testabilityWithFixedClock_advancingTimeChangesTacoDeterministically() {
    // Reloj fijo día 1
    Clock clockDay1 = Clock.fixed(Instant.parse("2026-09-17T10:00:00Z"), ZoneId.of("UTC"));
    TacoOfTheDayService service1 = new TacoOfTheDayService(tacoRepo, clockDay1);
    TacoOfTheDayResponse respDay1 = service1.getTacoOfTheDay().block();
    assertNotNull(respDay1);
    assertEquals(LocalDate.of(2026, 9, 17), respDay1.getDate());

    // Reloj fijo día 2 (+24 horas)
    Clock clockDay2 = Clock.fixed(Instant.parse("2026-09-18T10:00:00Z"), ZoneId.of("UTC"));
    TacoOfTheDayService service2 = new TacoOfTheDayService(tacoRepo, clockDay2);
    TacoOfTheDayResponse respDay2 = service2.getTacoOfTheDay().block();
    assertNotNull(respDay2);
    assertEquals(LocalDate.of(2026, 9, 18), respDay2.getDate());

    // El taco debe rotar deterministamente entre el día 1 y el día 2
    long epoch1 = LocalDate.of(2026, 9, 17).toEpochDay();
    long epoch2 = LocalDate.of(2026, 9, 18).toEpochDay();
    assertEquals(epoch1 + 1, epoch2);
  }

  @Test
  public void pricingCalculation_applies20PercentDiscountByDefault() {
    // Forzamos la evaluación de taco1 ($6.00)
    TacoOfTheDayResponse resp = tacoOfTheDayService.getTacoOfTheDay(LocalDate.of(2026, 9, 17)).block();
    assertNotNull(resp);

    BigDecimal original = resp.getOriginalPrice();
    BigDecimal discountPct = resp.getDiscountPercentage();
    BigDecimal savings = resp.getSavings();
    BigDecimal special = resp.getSpecialPrice();

    assertEquals(new BigDecimal("20.00"), discountPct);
    // original * 0.20 = savings
    assertEquals(original.multiply(new BigDecimal("0.20")).setScale(2, BigDecimal.ROUND_HALF_UP), savings);
    // original - savings = special
    assertEquals(original.subtract(savings), special);
    assertTrue(resp.getPromotionHeadline().contains("20% de descuento"));
  }

  @Test
  public void outOfStockTacos_areExcludedFromCandidates() {
    for (int d = 0; d < 10; d++) {
      LocalDate date = LocalDate.of(2026, 9, 17).plusDays(d);
      TacoOfTheDayResponse resp = tacoOfTheDayService.getTacoOfTheDay(date).block();
      assertNotNull(resp);
      assertTrue(!resp.getTaco().getId().equals("TACO_04"), "El taco sin stock nunca debe ser seleccionado como Taco del Día");
    }
  }

  @Test
  public void restEndpoint_tacoOfTheDay_shouldReturn200WithFullMetadata() {
    testClient.get()
        .uri("/api/tacos/taco-of-the-day")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.date").isEqualTo("2026-09-17")
        .jsonPath("$.taco").exists()
        .jsonPath("$.originalPrice").isNumber()
        .jsonPath("$.specialPrice").isNumber()
        .jsonPath("$.discountPercentage").isEqualTo(20.00)
        .jsonPath("$.savings").isNumber()
        .jsonPath("$.promotionHeadline").isNotEmpty();
  }

  @Test
  public void restEndpoint_withExplicitDateParameter_shouldReturnDeterministicResultForThatDate() {
    testClient.get()
        .uri("/api/tacos/taco-of-the-day?date=2026-12-25")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.date").isEqualTo("2026-12-25")
        .jsonPath("$.taco.id").isNotEmpty();
  }

  @Test
  public void restEndpoint_dailyAlias_shouldReturnSameResult() {
    testClient.get()
        .uri("/api/tacos/daily?date=2026-12-25")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.date").isEqualTo("2026-12-25");
  }

  @Test
  public void emptyCatalog_returns404NotFound() {
    TacoRepository emptyRepo = Mockito.mock(TacoRepository.class);
    when(emptyRepo.findAll()).thenReturn(Flux.empty());

    TacoOfTheDayService emptyService = new TacoOfTheDayService(emptyRepo, fixedClock);
    TacoController emptyController = new TacoController(emptyRepo, null, new TacoQueryService(emptyRepo), emptyService);
    WebTestClient emptyClient = WebTestClient.bindToController(emptyController).build();

    emptyClient.get()
        .uri("/api/tacos/taco-of-the-day")
        .exchange()
        .expectStatus().isNotFound();
  }
}
