package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.User;
import tacos.actuator.BusinessMetricsService;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.idempotency.OrderIdempotencyService;
import tacos.messaging.OrderMessagingService;
import tacos.outbox.TransactionalOutboxService;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 34: Idempotency-Key en creación de órdenes
public class OrderIdempotencyTest {

  private OrderRepository orderRepo;
  private OrderMessagingService orderMessages;
  private EmailOrderService emailOrderService;
  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private InventoryService inventoryService;
  private UserRepository userRepo;
  private TransactionalOutboxService outboxService;
  private BusinessMetricsService metricsService;
  private OrderIdempotencyService idempotencyService;
  private OrderApiController controller;
  private WebTestClient testClient;

  private User userAlice;
  private User userBob;

  @BeforeEach
  void setUp() {
    orderRepo = mock(OrderRepository.class);
    orderMessages = mock(OrderMessagingService.class);
    emailOrderService = mock(EmailOrderService.class);
    ingredientRepo = mock(IngredientRepository.class);
    tacoRepo = mock(TacoRepository.class);
    inventoryService = mock(InventoryService.class);
    userRepo = mock(UserRepository.class);
    outboxService = mock(TransactionalOutboxService.class);

    metricsService = new BusinessMetricsService(new SimpleMeterRegistry());
    idempotencyService = new OrderIdempotencyService(null, metricsService);
    idempotencyService.clear();

    userAlice = new User("alice", "password", "Alice Smith", "123 Main", "CDMX", "CDMX", "06500", "555-1111", "alice@test.com");
    userAlice.setId("USER_ALICE");

    userBob = new User("bob", "password", "Bob Jones", "456 Oak", "CDMX", "CDMX", "06500", "555-2222", "bob@test.com");
    userBob.setId("USER_BOB");

    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));
    when(userRepo.findByUsername("bob")).thenReturn(Mono.just(userBob));

    Ingredient flto = new Ingredient("FLTO", "Flour Tortilla", Ingredient.Type.WRAP, new BigDecimal("10.00"), true, 50);
    Ingredient grbf = new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, new BigDecimal("25.00"), true, 40);
    when(ingredientRepo.findById(any(String.class))).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      if ("FLTO".equals(id)) return Mono.just(flto);
      if ("GRBF".equals(id)) return Mono.just(grbf);
      return Mono.just(new Ingredient(id, id, Ingredient.Type.WRAP, BigDecimal.TEN, true, 10));
    });
    when(tacoRepo.findById(any(String.class))).thenReturn(Mono.empty());

    when(inventoryService.reserveInventory(any(TacoOrder.class)))
        .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    when(outboxService.enqueueOrder(any(TacoOrder.class), any()))
        .thenReturn(Mono.just(new tacos.outbox.OutboxMessage()));

    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> {
      TacoOrder ord = inv.getArgument(0);
      if (ord.getId() == null) {
        ord.setId("ORD-" + System.nanoTime());
      }
      return Mono.just(ord);
    });

    controller = new OrderApiController(
        orderRepo,
        orderMessages,
        emailOrderService,
        ingredientRepo,
        tacoRepo,
        null, // couponEngine
        inventoryService,
        null, // physicsEngine
        userRepo,
        null, // kitchenService
        outboxService,
        metricsService,
        idempotencyService
    );

    testClient = WebTestClient.bindToController(controller)
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .webFilter(new tacos.web.api.correlation.CorrelationIdWebFilter())
        .webFilter((exchange, chain) -> {
          String testUser = exchange.getRequest().getHeaders().getFirst("X-Test-User");
          String testRole = exchange.getRequest().getHeaders().getFirst("X-Test-Role");
          if (testUser != null && !testUser.trim().isEmpty()) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (testRole != null && !testRole.trim().isEmpty()) {
              authorities.add(new SimpleGrantedAuthority(testRole.trim()));
            } else {
              authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
            Authentication auth = new UsernamePasswordAuthenticationToken(testUser.trim(), "password", authorities);
            return chain.filter(exchange.mutate().principal(Mono.just(auth)).build());
          }
          return chain.filter(exchange);
        })
        .build();
  }

  // ==========================================
  // COMPORTAMIENTO SIN IDEMPOTENCY-KEY
  // ==========================================

  @Test
  @DisplayName("1. Creación Normal: Sin Idempotency-Key procesa normalmente sin cabecera de replay")
  void testCreateOrder_WithoutIdempotencyKey_NormalExecution() {
    TacoOrder order = createSampleOrder("Carnitas");

    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().doesNotExist(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY)
        .expectHeader().doesNotExist(OrderIdempotencyService.HEADER_IDEMPOTENCY_REPLAYED)
        .expectBody()
        .jsonPath("$.id").exists()
        .jsonPath("$.deliveryName").isEqualTo("Alice Smith");

    verify(inventoryService, times(1)).reserveInventory(any(TacoOrder.class));
    verify(orderRepo, times(1)).save(any(TacoOrder.class));
  }

  // ==========================================
  // PRIMER ENVÍO CON IDEMPOTENCY-KEY (CACHE MISS)
  // ==========================================

  @Test
  @DisplayName("2. Primer Envío (Cache MISS): Retorna 201 Created y cabecera Idempotency-Key")
  void testCreateOrder_FirstTimeWithIdempotencyKey_CacheMissAndReturnsKey() {
    TacoOrder order = createSampleOrder("Al Pastor");
    String key = "IDEMP-KEY-TEST-001";

    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .expectHeader().doesNotExist(OrderIdempotencyService.HEADER_IDEMPOTENCY_REPLAYED)
        .expectBody()
        .jsonPath("$.id").exists()
        .jsonPath("$.idempotencyKey").isEqualTo(key);

    verify(inventoryService, times(1)).reserveInventory(any(TacoOrder.class));
    verify(orderRepo, times(1)).save(any(TacoOrder.class));
  }

  // ==========================================
  // REINTENTO CON LA MISMA CLAVE Y MISMO PAYLOAD (CACHE HIT / REPLAY)
  // ==========================================

  @Test
  @DisplayName("3. Reintento Idéntico (Cache HIT): Retorna orden previa con Idempotency-Replayed sin efectos secundarios")
  void testCreateOrder_RetryWithSameKeyAndSamePayload_CacheHitReplayNoSideEffects() {
    TacoOrder order1 = createSampleOrder("Al Pastor");
    String key = "IDEMP-RETRY-002";

    // Primer envío (Creación)
    byte[] responseBody1 = testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order1)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .expectHeader().doesNotExist(OrderIdempotencyService.HEADER_IDEMPOTENCY_REPLAYED)
        .expectBody()
        .jsonPath("$.id").isNotEmpty()
        .returnResult()
        .getResponseBody();

    String createdOrderId = com.jayway.jsonpath.JsonPath.read(new String(responseBody1), "$.id");
    assertNotNull(createdOrderId);

    // Reintento con pedido exactamente idéntico y misma clave
    TacoOrder order2 = createSampleOrder("Al Pastor");

    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order2)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .expectHeader().valueEquals(OrderIdempotencyService.HEADER_IDEMPOTENCY_REPLAYED, "true")
        .expectBody()
        .jsonPath("$.id").isEqualTo(createdOrderId);

    // Verificación crítica: los efectos secundarios se ejecutaron EXACTAMENTE 1 VEZ en total
    verify(inventoryService, times(1)).reserveInventory(any(TacoOrder.class));
    verify(orderRepo, times(1)).save(any(TacoOrder.class));
    verify(outboxService, times(1)).enqueueOrder(any(TacoOrder.class), any());
  }

  // ==========================================
  // COLISIÓN: MISMA CLAVE CON DISTINTO PAYLOAD (409 CONFLICT)
  // ==========================================

  @Test
  @DisplayName("4. Colisión de Payload: Misma clave con orden diferente retorna HTTP 409 Conflict")
  void testCreateOrder_SameKeyDifferentPayload_Returns409ConflictProblemDetails() {
    String key = "IDEMP-CONFLICT-003";

    // 1. Crear orden original con Pastor
    TacoOrder orderOriginal = createSampleOrder("Al Pastor");
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderOriginal)
        .exchange()
        .expectStatus().isCreated();

    // 2. Intentar usar la misma clave con tacos de Suadero y diferente precio/cantidad
    TacoOrder orderDifferent = createSampleOrder("Suadero");
    orderDifferent.getTacos().get(0).setQuantity(10); // Payload modificado

    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderDifferent)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(409)
        .jsonPath("$.title").isEqualTo("Conflict");
  }

  // ==========================================
  // VALIDACIÓN DE FORMATO DE CLAVE (400 BAD REQUEST)
  // ==========================================

  @Test
  @DisplayName("5. Validación: Clave vacía o solo espacios retorna HTTP 400 Bad Request")
  void testCreateOrder_InvalidBlankKey_Returns400BadRequestProblemDetails() {
    TacoOrder order = createSampleOrder("Carnitas");

    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, "   ")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400);
  }

  @Test
  @DisplayName("6. Validación: Clave excesivamente larga (>128 caracteres) retorna HTTP 400")
  void testCreateOrder_KeyTooLong_Returns400BadRequestProblemDetails() {
    TacoOrder order = createSampleOrder("Carnitas");
    String longKey = String.join("", Collections.nCopies(15, "0123456789")); // 150 caracteres

    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, longKey)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400);
  }

  // ==========================================
  // SOPORTE DE ENCABEZADO ALTERNATIVO (X-Idempotency-Key)
  // ==========================================

  @Test
  @DisplayName("7. Encabezado Alternativo: Soporta X-Idempotency-Key transparentemente")
  void testCreateOrder_AlternativeHeader_XIdempotencyKeySupported() {
    TacoOrder order = createSampleOrder("Bistec");
    String key = "ALT-KEY-555";

    // 1. Envío inicial con X-Idempotency-Key
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_X_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key);

    // 2. Reintento con X-Idempotency-Key
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_X_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals(OrderIdempotencyService.HEADER_IDEMPOTENCY_REPLAYED, "true");
  }

  // ==========================================
  // AISLAMIENTO POR USUARIO (IDOR PREVENTION)
  // ==========================================

  @Test
  @DisplayName("8. Aislamiento de Usuario: Clave registrada por Alice no puede ser reusada por Bob (409 Conflict)")
  void testCreateOrder_UserIsolation_KeyOwnedByAnotherUserReturnsConflict() {
    String key = "KEY-SHARED-TENANT";

    // 1. Alice registra la orden con la clave
    TacoOrder orderAlice = createSampleOrder("Gringa");
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderAlice)
        .exchange()
        .expectStatus().isCreated();

    // 2. Bob intenta usar la misma clave de Alice
    TacoOrder orderBob = createSampleOrder("Gringa");
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "bob")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderBob)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.status").isEqualTo(409);
  }

  // ==========================================
  // LIBERACIÓN EN CASO DE ERROR
  // ==========================================

  @Test
  @DisplayName("9. Tolerancia a Fallos: Error en el pipeline libera la clave permitiendo reintento")
  void testCreateOrder_PipelineError_ReleasesIdempotencyKeyForRetry() {
    String key = "KEY-RETRY-FAIL";
    TacoOrder order = createSampleOrder("Cochinita");

    // Simular que el inventario falla en el primer intento
    when(inventoryService.reserveInventory(any(TacoOrder.class)))
        .thenReturn(Mono.error(new RuntimeException("Falla transitoria de inventario")))
        .thenAnswer(inv -> Mono.just(inv.getArgument(0))); // Segundo intento tiene éxito

    // 1. Primer intento falla
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().is5xxServerError();

    // 2. Segundo intento con la misma clave debe poder ejecutarse porque la clave se liberó
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key);
  }

  // ==========================================
  // OBSERVABILIDAD Y MÉTRICAS DE IDEMPOTENCIA
  // ==========================================

  @Test
  @DisplayName("10. Métricas de Negocio: Incrementa contadores de hits, misses y conflicts")
  void testIdempotencyMetrics_HitsAndMissesIncremented() {
    String key = "KEY-METRICS-001";
    TacoOrder order = createSampleOrder("Campechano");

    // 1. MISS
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated();

    // 2. HIT
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated();

    // 3. CONFLICT (distinto payload)
    TacoOrder conflictOrder = createSampleOrder("Campechano");
    conflictOrder.setDeliveryCity("Guadalajara");
    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header(OrderIdempotencyService.HEADER_IDEMPOTENCY_KEY, key)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(conflictOrder)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT);

    java.util.Map<String, Object> summary = metricsService.getMetricsSummary();
    assertEquals(1L, summary.get("idempotentMisses"));
    assertEquals(1L, summary.get("idempotentHits"));
    assertEquals(1L, summary.get("idempotentConflicts"));
  }

  private TacoOrder createSampleOrder(String tacoName) {
    Ingredient tortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("10.00"), true, 50);
    Ingredient carne = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("25.00"), true, 40);

    Taco taco = new Taco();
    taco.setName(tacoName);
    taco.setQuantity(2);
    taco.setIngredients(Arrays.asList(tortilla, carne));

    TacoOrder order = new TacoOrder();
    order.setDeliveryName("Alice Smith");
    order.setDeliveryStreet("123 Main");
    order.setDeliveryCity("CDMX");
    order.setDeliveryState("CDMX");
    order.setDeliveryZip("06500");
    order.setTacos(Collections.singletonList(taco));
    return order;
  }
}
