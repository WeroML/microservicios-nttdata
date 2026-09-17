package tacos.web.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.dto.TacoRequest;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
public class TacoPhysicsApiControllerTest {

  private TacoRepository tacoRepo;
  private IngredientRepository ingredientRepo;
  private TacoPhysicsEngine physicsEngine;
  private WebTestClient testClient;

  private Ingredient tortilla;
  private Ingredient beef;
  private Ingredient salsa;
  private Ingredient sourCream;
  private Ingredient cheese;
  private Ingredient habanero;

  @BeforeEach
  public void setUp() {
    tacoRepo = Mockito.mock(TacoRepository.class);
    ingredientRepo = Mockito.mock(IngredientRepository.class);

    tortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, BigDecimal.ONE, true, 10);
    beef = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, BigDecimal.ONE, true, 10);
    salsa = new Ingredient("SLSA", "Salsa", Type.SAUCE, BigDecimal.ONE, true, 10);
    sourCream = new Ingredient("SRCR", "Sour Cream", Type.SAUCE, BigDecimal.ONE, true, 10);
    cheese = new Ingredient("CHED", "Cheddar", Type.CHEESE, BigDecimal.ONE, true, 10);
    habanero = new Ingredient("HBNR", "Habanero", Type.SAUCE, BigDecimal.ONE, true, 10);

    when(ingredientRepo.findById("FLTO")).thenReturn(Mono.just(tortilla));
    when(ingredientRepo.findById("GRBF")).thenReturn(Mono.just(beef));
    when(ingredientRepo.findById("SLSA")).thenReturn(Mono.just(salsa));
    when(ingredientRepo.findById("SRCR")).thenReturn(Mono.just(sourCream));
    when(ingredientRepo.findById("CHED")).thenReturn(Mono.just(cheese));
    when(ingredientRepo.findById("HBNR")).thenReturn(Mono.just(habanero));

    physicsEngine = new TacoPhysicsEngine(ingredientRepo);

    TacoController tacoController = new TacoController(tacoRepo, physicsEngine);
    testClient = WebTestClient.bindToController(tacoController).build();
  }

  @Test
  public void postTaco_whenValidPhysics_shouldCreateTaco() {
    when(tacoRepo.save(any(Taco.class))).thenAnswer(inv -> {
      Taco t = inv.getArgument(0);
      t.setId("SAVED_ID");
      return Mono.just(t);
    });

    String validJson = "{"
        + "\"name\": \"Classic Taco\","
        + "\"ingredients\": ["
        + "  {\"id\": \"FLTO\", \"name\": \"Flour Tortilla\", \"type\": \"WRAP\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"SLSA\", \"name\": \"Salsa\", \"type\": \"SAUCE\"}"
        + "]"
        + "}";

    testClient.post()
        .uri("/api/tacos")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(validJson)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
          .jsonPath("$.id").isEqualTo("SAVED_ID")
          .jsonPath("$.name").isEqualTo("Classic Taco");
  }

  @Test
  public void postTaco_whenMissingFoundation_shouldReturn400BadRequest() {
    // Taco sin tortilla (WRAP)
    String floatingJson = "{"
        + "\"name\": \"Floating Meat\","
        + "\"ingredients\": ["
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"SLSA\", \"name\": \"Salsa\", \"type\": \"SAUCE\"}"
        + "]"
        + "}";

    testClient.post()
        .uri("/api/tacos")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(floatingJson)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  public void postTaco_whenExceedsMaxCapacity_shouldReturn400BadRequest() {
    // Taco con 9 ingredientes (> 8)
    String overloadedJson = "{"
        + "\"name\": \"Monster Taco\","
        + "\"ingredients\": ["
        + "  {\"id\": \"FLTO\", \"name\": \"Flour Tortilla\", \"type\": \"WRAP\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"}"
        + "]"
        + "}";

    testClient.post()
        .uri("/api/tacos")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(overloadedJson)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  public void postTaco_whenMoistureExcess_shouldReturn400BadRequest() {
    // Taco con 3 salsas (> 2)
    String soggyJson = "{"
        + "\"name\": \"Soggy Mess\","
        + "\"ingredients\": ["
        + "  {\"id\": \"FLTO\", \"name\": \"Flour Tortilla\", \"type\": \"WRAP\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"},"
        + "  {\"id\": \"SLSA\", \"name\": \"Salsa\", \"type\": \"SAUCE\"},"
        + "  {\"id\": \"SRCR\", \"name\": \"Sour Cream\", \"type\": \"SAUCE\"},"
        + "  {\"id\": \"HBNR\", \"name\": \"Habanero\", \"type\": \"SAUCE\"}"
        + "]"
        + "}";

    testClient.post()
        .uri("/api/tacos")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(soggyJson)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  public void validatePhysicsEndpoint_whenValid_shouldReturnValidTrue() {
    String validJson = "{"
        + "\"name\": \"Balanced Taco\","
        + "\"ingredients\": ["
        + "  {\"id\": \"FLTO\", \"name\": \"Flour Tortilla\", \"type\": \"WRAP\"},"
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"}"
        + "]"
        + "}";

    testClient.post()
        .uri("/api/tacos/validate-physics")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(validJson)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.valid").isEqualTo(true);
  }

  @Test
  public void validatePhysicsEndpoint_whenInvalid_shouldReturnValidFalseWithReason() {
    String invalidJson = "{"
        + "\"name\": \"No Wrap Taco\","
        + "\"ingredients\": ["
        + "  {\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"}"
        + "]"
        + "}";

    testClient.post()
        .uri("/api/tacos/validate-physics")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalidJson)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.valid").isEqualTo(false)
          .jsonPath("$.reason").isNotEmpty();
  }

  @Test
  public void postOrder_whenTacoViolatesPhysics_shouldReturn400BadRequest() {
    OrderRepository orderRepo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);

    OrderApiController orderController = new OrderApiController(
        orderRepo, messagingService, emailService, ingredientRepo, tacoRepo, null, null, physicsEngine);

    WebTestClient orderClient = WebTestClient.bindToController(orderController).build();

    // Orden con taco sin base
    String orderJson = "{"
        + "\"deliveryName\": \"Gustavo\","
        + "\"tacos\": [{"
        + "  \"name\": \"No Base Taco\","
        + "  \"quantity\": 1,"
        + "  \"ingredients\": [{\"id\": \"GRBF\", \"name\": \"Ground Beef\", \"type\": \"PROTEIN\"}]"
        + "}]"
        + "}";

    orderClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderJson)
        .exchange()
        .expectStatus().isBadRequest();

    Mockito.verify(orderRepo, Mockito.never()).save(any(TacoOrder.class));
  }
}
