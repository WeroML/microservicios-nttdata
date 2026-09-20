package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.User;
import tacos.actuator.BusinessMetricsService;
import tacos.data.CouponRepository;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.OutboxRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.idempotency.OrderIdempotencyService;
import tacos.messaging.OrderMessagingService;
import tacos.openapi.OpenApiContractService;
import tacos.openapi.OpenApiController;
import tacos.openapi.SwaggerUiController;
import tacos.outbox.OrderOutboxService;
import tacos.outbox.OutboxMessage;
import tacos.outbox.OutboxStatus;
import tacos.versioning.ApiVersionController;
import tacos.versioning.ApiVersioningWebFilter;
import tacos.web.api.dto.ClaimOrderRequest;
import tacos.web.api.dto.KitchenQueueItem;
import tacos.web.api.dto.KitchenQueueResponse;
import tacos.web.api.dto.UpdateOrderStatusRequest;
import tacos.web.api.dto.v2.OrderV2Dto.AddressDto;
import tacos.web.api.dto.v2.OrderV2Dto.OrderItemV2Request;
import tacos.web.api.dto.v2.OrderV2Dto.OrderV2Request;
import tacos.web.api.dto.v2.OrderV2Dto.OrderV2Response;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;
import tacos.web.api.v2.OrderApiV2Controller;

// Ejercicio 36: Suite de integración que detenga regresiones reales
public class RealRegressionIntegrationTest {

  // Repositorios en memoria reactivos para integración pura y determinista
  private Map<String, TacoOrder> orderStore;
  private Map<String, Ingredient> ingredientStore;
  private Map<String, Taco> tacoStore;
  private Map<String, OutboxMessage> outboxStore;
  private Map<String, User> userStore;

  private OrderRepository orderRepo;
  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private OutboxRepository outboxRepo;
  private UserRepository userRepo;
  private CouponRepository couponRepo;

  // Mensajería y Broker simulado con captura y capacidad de inyectar fallos
  private RecordingOrderMessagingService messagingService;

  // Servicios de negocio reales
  private MeterRegistry meterRegistry;
  private BusinessMetricsService metricsService;
  private InventoryService inventoryService;
  private OrderIdempotencyService idempotencyService;
  private OrderOutboxService outboxService;
  private KitchenService kitchenService;
  private OpenApiContractService openApiContractService;

  // Controladores reales
  private OrderApiController orderApiController;
  private OrderApiV2Controller orderApiV2Controller;
  private KitchenApiController kitchenApiController;
  private ApiVersionController apiVersionController;
  private OpenApiController openApiController;
  private SwaggerUiController swaggerUiController;

  // Cliente HTTP reactivo
  private WebTestClient webTestClient;

  // Usuarios del sistema
  private User userAlice;
  private User userBob;
  private User userChef;
  private User userAdmin;

  @BeforeEach
  void setUp() {
    orderStore = new ConcurrentHashMap<>();
    ingredientStore = new ConcurrentHashMap<>();
    tacoStore = new ConcurrentHashMap<>();
    outboxStore = new ConcurrentHashMap<>();
    userStore = new ConcurrentHashMap<>();

    // Inicializar ingredientes en catálogo con stock controlado
    seedIngredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("10.00"), 20);
    seedIngredient("COTO", "Corn Tortilla", Type.WRAP, new BigDecimal("8.00"), 20);
    seedIngredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("25.00"), 20);
    seedIngredient("CARN", "Carnitas", Type.PROTEIN, new BigDecimal("30.00"), 10);
    seedIngredient("CHED", "Cheddar Cheese", Type.CHEESE, new BigDecimal("12.00"), 2); // Escaso para probar sobreventa
    seedIngredient("JACK", "Monterrey Jack", Type.CHEESE, new BigDecimal("12.00"), 20);
    seedIngredient("SLSA", "Salsa Salsa", Type.SAUCE, new BigDecimal("5.00"), 10);

    // Inicializar usuarios
    userAlice = new User("alice", "password", "Alice Smith", "123 Main St", "CDMX", "CDMX", "06500", "555-1111", "alice@tacocloud.com");
    userAlice.setId("USER_ALICE");
    userStore.put("alice", userAlice);

    userBob = new User("bob", "password", "Bob Jones", "456 Oak Ave", "CDMX", "CDMX", "06500", "555-2222", "bob@tacocloud.com");
    userBob.setId("USER_BOB");
    userStore.put("bob", userBob);

    userChef = new User("chef_luis", "password", "Chef Luis", "789 Kitchen Blvd", "CDMX", "CDMX", "06500", "555-3333", "luis@tacocloud.com");
    userChef.setId("USER_CHEF");
    userStore.put("chef_luis", userChef);

    userAdmin = new User("admin", "password", "Admin System", "1 Headquarters Way", "CDMX", "CDMX", "06500", "555-0000", "admin@tacocloud.com");
    userAdmin.setId("USER_ADMIN");
    userStore.put("admin", userAdmin);

    // Mock/Stub repositorios con backing en memoria reactivo
    buildReactiveRepositories();

    // Servicios de observabilidad y negocio
    meterRegistry = new SimpleMeterRegistry();
    metricsService = new BusinessMetricsService(meterRegistry);
    inventoryService = new InventoryService(ingredientRepo, tacoRepo, metricsService);
    idempotencyService = new OrderIdempotencyService(null, metricsService);
    messagingService = new RecordingOrderMessagingService();
    outboxService = new OrderOutboxService(outboxRepo, messagingService, new ObjectMapper(), metricsService);
    kitchenService = new KitchenService(orderRepo, messagingService, outboxService);

    // Controladores
    orderApiController = new OrderApiController(
        orderRepo,
        messagingService,
        null, // emailOrderService
        ingredientRepo,
        tacoRepo,
        null, // couponEngine
        inventoryService,
        null, // physicsEngine
        userRepo,
        kitchenService,
        outboxService,
        metricsService,
        idempotencyService
    );

    orderApiV2Controller = new OrderApiV2Controller(orderApiController, orderRepo);
    kitchenApiController = new KitchenApiController(kitchenService, userRepo);
    apiVersionController = new ApiVersionController();
    openApiContractService = new OpenApiContractService();
    openApiController = new OpenApiController(openApiContractService);
    swaggerUiController = new SwaggerUiController();

    // Ensamblar WebTestClient con filtros de Correlation ID, Versionado y Seguridad simulada
    webTestClient = WebTestClient.bindToController(
        orderApiController,
        orderApiV2Controller,
        kitchenApiController,
        apiVersionController,
        openApiController,
        swaggerUiController
    )
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .webFilter(new tacos.web.api.correlation.CorrelationIdWebFilter())
        .webFilter(new ApiVersioningWebFilter())
        .webFilter((exchange, chain) -> {
          String testUser = exchange.getRequest().getHeaders().getFirst("X-Test-User");
          String testRole = exchange.getRequest().getHeaders().getFirst("X-Test-Role");
          if (testUser != null && !testUser.trim().isEmpty()) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if ("ROLE_ADMIN".equalsIgnoreCase(testRole) || "admin".equalsIgnoreCase(testUser)) {
              authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            } else if ("ROLE_KITCHEN".equalsIgnoreCase(testRole) || testUser.startsWith("chef_")) {
              authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
              authorities.add(new SimpleGrantedAuthority("ROLE_KITCHEN"));
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

  private void seedIngredient(String id, String name, Type type, BigDecimal price, int stock) {
    Ingredient ing = new Ingredient(id, name, type, price, true, stock);
    ingredientStore.put(id, ing);
  }

  private void buildReactiveRepositories() {
    // OrderRepository reactivo en memoria
    orderRepo = Mockito.mock(OrderRepository.class);
    Mockito.when(orderRepo.save(Mockito.any(TacoOrder.class))).thenAnswer(inv -> {
      TacoOrder ord = inv.getArgument(0);
      if (ord.getId() == null) {
        ord.setId("ORD-" + System.nanoTime());
      }
      if (ord.getPlacedAt() == null) {
        ord.setPlacedAt(new Date());
      }
      orderStore.put(ord.getId(), ord);
      return Mono.just(ord);
    });
    Mockito.when(orderRepo.findById(Mockito.anyString())).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      return Mono.justOrEmpty(orderStore.get(id));
    });
    Mockito.when(orderRepo.findAll()).thenAnswer(inv -> Flux.fromIterable(orderStore.values()));

    // IngredientRepository reactivo en memoria
    ingredientRepo = Mockito.mock(IngredientRepository.class);
    Mockito.when(ingredientRepo.save(Mockito.any(Ingredient.class))).thenAnswer(inv -> {
      Ingredient ing = inv.getArgument(0);
      ingredientStore.put(ing.getId(), ing);
      return Mono.just(ing);
    });
    Mockito.when(ingredientRepo.findById(Mockito.anyString())).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      return Mono.justOrEmpty(ingredientStore.get(id));
    });
    Mockito.when(ingredientRepo.findAll()).thenAnswer(inv -> Flux.fromIterable(ingredientStore.values()));

    // TacoRepository
    tacoRepo = Mockito.mock(TacoRepository.class);
    Mockito.when(tacoRepo.findById(Mockito.anyString())).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      return Mono.justOrEmpty(tacoStore.get(id));
    });

    // OutboxRepository reactivo en memoria
    AtomicLong outboxSeq = new AtomicLong(1);
    outboxRepo = Mockito.mock(OutboxRepository.class);
    Mockito.when(outboxRepo.save(Mockito.any(OutboxMessage.class))).thenAnswer(inv -> {
      OutboxMessage msg = inv.getArgument(0);
      if (msg.getId() == null) {
        msg.setId("OBX-" + outboxSeq.getAndIncrement());
      }
      outboxStore.put(msg.getId(), msg);
      return Mono.just(msg);
    });
    Mockito.when(outboxRepo.findById(Mockito.anyString())).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      return Mono.justOrEmpty(outboxStore.get(id));
    });
    Mockito.when(outboxRepo.findByStatus(Mockito.any(OutboxStatus.class))).thenAnswer(inv -> {
      OutboxStatus status = inv.getArgument(0);
      List<OutboxMessage> matches = new ArrayList<>();
      for (OutboxMessage m : outboxStore.values()) {
        if (m.getStatus() == status) {
          matches.add(m);
        }
      }
      return Flux.fromIterable(matches);
    });
    Mockito.when(outboxRepo.findByStatusOrderByCreatedAtAsc(Mockito.any(OutboxStatus.class))).thenAnswer(inv -> {
      OutboxStatus status = inv.getArgument(0);
      List<OutboxMessage> matches = new ArrayList<>();
      for (OutboxMessage m : outboxStore.values()) {
        if (m.getStatus() == status) {
          matches.add(m);
        }
      }
      return Flux.fromIterable(matches);
    });
    Mockito.when(outboxRepo.findAll()).thenAnswer(inv -> Flux.fromIterable(outboxStore.values()));

    // UserRepository reactivo en memoria
    userRepo = Mockito.mock(UserRepository.class);
    Mockito.when(userRepo.findByUsername(Mockito.anyString())).thenAnswer(inv -> {
      String username = inv.getArgument(0);
      return Mono.justOrEmpty(userStore.get(username));
    });
  }

  // =========================================================================
  // SERVICIO DE MENSAJERÍA GRABADOR (RECORDING) CON SIMULACIÓN DE CAÍDAS
  // =========================================================================
  static class RecordingOrderMessagingService implements OrderMessagingService {
    private final List<OrderEvent> sentEvents = new CopyOnWriteArrayList<>();
    private final List<TacoOrder> sentOrders = new CopyOnWriteArrayList<>();
    private volatile boolean failOnSend = false;

    public void setFailOnSend(boolean failOnSend) {
      this.failOnSend = failOnSend;
    }

    public List<OrderEvent> getSentEvents() {
      return sentEvents;
    }

    public List<TacoOrder> getSentOrders() {
      return sentOrders;
    }

    @Override
    public void sendOrder(TacoOrder order) {
      if (failOnSend) {
        throw new RuntimeException("Simulated Broker Outage: Broker connection refused");
      }
      sentOrders.add(order);
    }

    @Override
    public void sendOrderEvent(OrderEvent event) {
      if (failOnSend) {
        throw new RuntimeException("Simulated Broker Outage: Broker connection refused");
      }
      sentEvents.add(event);
    }
  }

  // Helper para crear orden V1
  private TacoOrder createSampleV1Order(String tacoName, int tacoQuantity, List<String> ingredientIds) {
    TacoOrder order = new TacoOrder();
    order.setDeliveryName("Alice Smith");
    order.setDeliveryStreet("123 Main St");
    order.setDeliveryCity("CDMX");
    order.setDeliveryState("CDMX");
    order.setDeliveryZip("06500");

    Taco taco = new Taco();
    taco.setName(tacoName);
    taco.setQuantity(tacoQuantity);
    List<Ingredient> ingredients = new ArrayList<>();
    for (String id : ingredientIds) {
      Ingredient ing = ingredientStore.get(id);
      if (ing != null) {
        ingredients.add(ing);
      }
    }
    taco.setIngredients(ingredients);
    order.addTaco(taco);
    return order;
  }

  // =========================================================================
  // 1. FLUJO END-TO-END COMPLETO (HAPPY PATH INTEGRATION)
  // =========================================================================

  @Test
  @DisplayName("1. Integración End-to-End: Ciclo de vida completo desde HTTP hasta Outbox, Cola de Cocina y Entrega")
  void testEndToEndOrderFlow_HappyPath_FromHttpToOutboxKitchenAndDelivery() {
    // 1. Stock inicial de FLTO y GRBF = 20
    assertEquals(20, ingredientStore.get("FLTO").getStock());
    assertEquals(20, ingredientStore.get("GRBF").getStock());

    // 2. Cliente Alice crea orden con 2 tacos de harina y carne
    TacoOrder orderReq = createSampleV1Order("Taco Supremo", 2, Arrays.asList("FLTO", "GRBF"));
    AtomicReference<String> orderIdRef = new AtomicReference<>();

    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-E2E-001")
        .header("X-Correlation-ID", "CID-E2E-1001")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("X-Correlation-ID", "CID-E2E-1001")
        .expectHeader().valueEquals("Idempotency-Key", "IDEMP-E2E-001")
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_API_VERSION, "1.0")
        .expectBody()
        .jsonPath("$.id").value(id -> orderIdRef.set(id.toString()))
        .jsonPath("$.status").isEqualTo("CONFIRMED")
        .jsonPath("$.correlationId").isEqualTo("CID-E2E-1001");

    String orderId = orderIdRef.get();
    assertNotNull(orderId);
    TacoOrder createdOrder = orderStore.get(orderId);
    assertNotNull(createdOrder);
    assertEquals(OrderStatus.CONFIRMED, createdOrder.getStatus());
    assertEquals("CID-E2E-1001", createdOrder.getCorrelationId());

    // 3. Verificación de reserva de inventario: FLTO: 20 -> 18, GRBF: 20 -> 18
    assertEquals(18, ingredientStore.get("FLTO").getStock());
    assertEquals(18, ingredientStore.get("GRBF").getStock());

    // 4. Verificación de Transactional Outbox y Broker
    assertEquals(1, messagingService.getSentEvents().size());
    OrderEvent publishedEvent = messagingService.getSentEvents().get(0);
    assertEquals(orderId, publishedEvent.getOrderId());
    assertEquals("CID-E2E-1001", publishedEvent.getCorrelationId());
    assertEquals(OrderEventType.ORDER_CREATED, publishedEvent.getEventType());

    // 5. Cocina: Chef Luis consulta la cola FIFO (/api/kitchen/queue)
    webTestClient.get()
        .uri("/api/kitchen/queue")
        .header("X-Test-User", "chef_luis")
        .header("X-Test-Role", "ROLE_KITCHEN")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.totalInQueue").isEqualTo(1)
        .jsonPath("$.orders[0].orderId").isEqualTo(orderId)
        .jsonPath("$.orders[0].status").isEqualTo("CONFIRMED")
        .jsonPath("$.orders[0].queuePosition").isEqualTo(1);

    // 6. Chef Luis reclama atómicamente la orden (/api/orders/{orderId}/claim)
    webTestClient.post()
        .uri("/api/orders/" + orderId + "/claim")
        .header("X-Test-User", "chef_luis")
        .header("X-Test-Role", "ROLE_KITCHEN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new ClaimOrderRequest("Prioridad regular"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.orderId").isEqualTo(orderId)
        .jsonPath("$.claimedBy").isEqualTo("chef_luis")
        .jsonPath("$.status").isEqualTo("PREPARING");

    // 7. Transiciones de estado operativas hacia DELIVERED
    // PREPARING -> READY
    webTestClient.patch()
        .uri("/api/orders/" + orderId + "/status")
        .header("X-Test-User", "chef_luis")
        .header("X-Test-Role", "ROLE_KITCHEN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.READY, "Tacos cocinados"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("READY");

    // READY -> DELIVERING
    webTestClient.patch()
        .uri("/api/orders/" + orderId + "/status")
        .header("X-Test-User", "chef_luis")
        .header("X-Test-Role", "ROLE_KITCHEN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.DELIVERING, "Repartidor en camino"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("DELIVERING");

    // DELIVERING -> DELIVERED (Terminal)
    webTestClient.patch()
        .uri("/api/orders/" + orderId + "/status")
        .header("X-Test-User", "chef_luis")
        .header("X-Test-Role", "ROLE_KITCHEN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.DELIVERED, "Entregado a Alice"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("DELIVERED")
        .jsonPath("$.terminal").isEqualTo(true);

    // 8. Verificación de Métricas de Negocio registradas
    assertTrue(meterRegistry.find("tacocloud.orders.placed").counter().count() >= 1.0);
    double totalReserved = meterRegistry.find("tacocloud.inventory.reserved").counters().stream()
        .mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    assertTrue(totalReserved >= 4.0, "El inventario reservado total debe ser >= 4");
  }

  // =========================================================================
  // 2. DETENCIÓN DE REGRESIÓN DE INVENTARIO: PREVENCIÓN DE VENDER AIRE
  // =========================================================================

  @Test
  @DisplayName("2. Regresión de Inventario: Rechaza sobreventa atómicamente y protege existencias (No vender aire)")
  void testInventoryRegression_OvercommitRejected_PreventsSellingAir() {
    // Queso Cheddar CHED tiene un stock escaso de 2 unidades
    assertEquals(2, ingredientStore.get("CHED").getStock());

    // Cliente intenta pedir 5 tacos con queso cheddar
    TacoOrder overcommitOrder = createSampleV1Order("Taco Queso Extremo", 5, Arrays.asList("FLTO", "CHED"));

    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-OVERCOMMIT-001")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(overcommitOrder)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.title").isEqualTo("Conflict")
        .jsonPath("$.detail").value(val -> assertTrue(val.toString().contains("Stock insuficiente")));

    // REGRESIÓN CRÍTICA A DETENER: El stock remanente NO debe alterarse ni decrementar
    assertEquals(2, ingredientStore.get("CHED").getStock(), "El stock de CHED debió mantenerse en 2 sin alteraciones");
    assertEquals(20, ingredientStore.get("FLTO").getStock(), "El stock de FLTO debió mantenerse en 20 sin reservas parciales");

    // No debe haberse insertado ninguna orden en base de datos
    assertTrue(orderStore.isEmpty(), "No debió persistirse ninguna orden rechazada");

    // No debe haberse emitido ningún evento al broker ni al outbox
    assertTrue(messagingService.getSentEvents().isEmpty(), "No debió enviarse ningún evento al broker");
    assertTrue(outboxStore.isEmpty(), "No debió registrarse mensaje en outbox");

    // Métrica de rechazo por inventario registrada
    assertTrue(meterRegistry.find("tacocloud.inventory.failed").counter().count() >= 1.0);
  }

  // =========================================================================
  // 3. DETENCIÓN DE REGRESIÓN DE INVENTARIO: ROLLBACK EN CANCELACIÓN
  // =========================================================================

  @Test
  @DisplayName("3. Regresión de Inventario: La cancelación restituye el 100% del inventario reservado (Rollback)")
  void testInventoryRegression_CancellationRestoresFullStock() {
    // Stock inicial de Carnitas CARN = 10
    assertEquals(10, ingredientStore.get("CARN").getStock());

    // Alice crea orden con 4 tacos de carnitas
    TacoOrder orderReq = createSampleV1Order("Tacos Carnitas", 4, Arrays.asList("FLTO", "CARN"));
    AtomicReference<String> orderIdRef = new AtomicReference<>();

    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-CANCEL-001")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").value(id -> orderIdRef.set(id.toString()));

    String orderId = orderIdRef.get();
    assertNotNull(orderId);

    // El stock bajó de 10 a 6
    assertEquals(6, ingredientStore.get("CARN").getStock());
    assertEquals(16, ingredientStore.get("FLTO").getStock());

    // Alice cancela la orden
    webTestClient.patch()
        .uri("/api/orders/" + orderId + "/status")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "El cliente canceló a tiempo"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("CANCELLED")
        .jsonPath("$.terminal").isEqualTo(true);

    // REGRESIÓN CRÍTICA A DETENER: El stock de CARN debe regresar exactamente a 10 y FLTO a 20
    assertEquals(10, ingredientStore.get("CARN").getStock(), "El inventario de CARN debió restituirse al 100%");
    assertEquals(20, ingredientStore.get("FLTO").getStock(), "El inventario de FLTO debió restituirse al 100%");

    // Evento ORDER_CANCELLED registrado en outbox
    assertTrue(messagingService.getSentEvents().stream()
        .anyMatch(e -> e.getEventType() == OrderEventType.ORDER_CANCELLED && e.getOrderId().equals(orderId)));

    // Métrica de cancelación y liberación de inventario registradas
    assertTrue(meterRegistry.find("tacocloud.orders.cancelled").counter().count() >= 1.0);
    assertTrue(meterRegistry.find("tacocloud.inventory.released").counter().count() >= 4.0);
  }

  // =========================================================================
  // 4. DETENCIÓN DE REGRESIÓN DE IDEMPOTENCIA: REPLAY CON CERO EFECTOS SECUNDARIOS
  // =========================================================================

  @Test
  @DisplayName("4. Regresión de Idempotencia: Reintentos idénticos no duplican reservas, BD, eventos ni métricas")
  void testIdempotencyRegression_IdenticalReplay_ZeroDuplicatedSideEffects() {
    int initialStockSalsa = ingredientStore.get("SLSA").getStock(); // 10
    int initialStockFlto = ingredientStore.get("FLTO").getStock(); // 20

    TacoOrder orderReq = createSampleV1Order("Taco Salsa", 2, Arrays.asList("FLTO", "SLSA"));
    AtomicReference<String> orderIdRef = new AtomicReference<>();

    // 1. Primer intento exitoso
    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-RETRY-777")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().doesNotExist("Idempotency-Replayed")
        .expectBody()
        .jsonPath("$.id").value(id -> orderIdRef.set(id.toString()));

    String originalOrderId = orderIdRef.get();
    assertNotNull(originalOrderId);
    assertEquals(initialStockSalsa - 2, ingredientStore.get("SLSA").getStock());
    assertEquals(1, orderStore.size());
    assertEquals(1, messagingService.getSentEvents().size());

    // 2. Reintento por fallo de red del cliente con la misma clave y payload idéntico
    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-RETRY-777")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("Idempotency-Replayed", "true")
        .expectBody()
        .jsonPath("$.id").isEqualTo(originalOrderId);

    // REGRESIÓN CRÍTICA A DETENER: Cero efectos colaterales duplicados
    assertEquals(initialStockSalsa - 2, ingredientStore.get("SLSA").getStock(), "El stock no debe descontarse dos veces");
    assertEquals(initialStockFlto - 2, ingredientStore.get("FLTO").getStock(), "El stock no debe descontarse dos veces");
    assertEquals(1, orderStore.size(), "No debe persistirse una segunda orden en BD");
    assertEquals(1, messagingService.getSentEvents().size(), "No debe publicarse un segundo evento al broker");

    // Contador de hits de idempotencia incrementado
    assertTrue(meterRegistry.find("tacocloud.orders.idempotent.hits").counter().count() >= 1.0);
  }

  // =========================================================================
  // 5. DETENCIÓN DE REGRESIÓN DE IDEMPOTENCIA: CONFLICTO POR PAYLOAD MUTADO
  // =========================================================================

  @Test
  @DisplayName("5. Regresión de Idempotencia: Reusar la misma clave con diferente contenido retorna 409 Conflict")
  void testIdempotencyRegression_PayloadMismatch_ReturnsConflict() {
    TacoOrder order1 = createSampleV1Order("Taco Pastor", 1, Collections.singletonList("FLTO"));
    TacoOrder order2 = createSampleV1Order("Taco Carnitas Modificado", 3, Arrays.asList("COTO", "CARN"));

    // Primera solicitud con clave
    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-MISMATCH-999")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order1)
        .exchange()
        .expectStatus().isCreated();

    // Segunda solicitud con MISMA CLAVE pero DISTINTO PAYLOAD
    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-MISMATCH-999")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order2)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.title").isEqualTo("Conflict")
        .jsonPath("$.detail").value(detail -> assertTrue(detail.toString().contains("Idempotency-Key")));

    // Métrica de conflicto registrada
    assertTrue(meterRegistry.find("tacocloud.orders.idempotent.conflicts").counter().count() >= 1.0);
  }

  // =========================================================================
  // 6. DETENCIÓN DE REGRESIÓN DE MÁQUINA DE ESTADOS: TRANSICIONES ILEGALES
  // =========================================================================

  @Test
  @DisplayName("6. Regresión de Máquina de Estados: Detiene saltos ilegales y modificaciones en estados terminales")
  void testStateMachineRegression_IllegalTransitionsAndTerminalStatesRejected() {
    TacoOrder orderReq = createSampleV1Order("Taco Estado", 1, Collections.singletonList("FLTO"));
    AtomicReference<String> orderIdRef = new AtomicReference<>();

    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-STATE-001")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").value(id -> orderIdRef.set(id.toString()));

    String orderId = orderIdRef.get();
    assertNotNull(orderId);

    // 1. Salto ilegal: CONFIRMED -> DELIVERED (sin pasar por PREPARING / READY / DELIVERING)
    webTestClient.patch()
        .uri("/api/orders/" + orderId + "/status")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.DELIVERED, "Salto directo no permitido"))
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.detail").value(d -> assertTrue(d.toString().contains("Transición de estado inválida")));

    // 2. Transición válida a PREPARING -> READY -> DELIVERED
    orderStore.get(orderId).setStatus(OrderStatus.DELIVERED); // Llevamos a terminal

    // 3. Modificación ilegal en estado terminal DELIVERED
    webTestClient.patch()
        .uri("/api/orders/" + orderId + "/status")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.PREPARING, "Reactivar orden entregada"))
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody()
        .jsonPath("$.detail").value(d -> assertTrue(d.toString().contains("Transición de estado inválida")));
  }

  // =========================================================================
  // 7. DETENCIÓN DE REGRESIÓN DE SEGURIDAD: AISLAMIENTO MULTI-TENANT (IDOR)
  // =========================================================================

  @Test
  @DisplayName("7. Regresión de Seguridad: Impide acceso cruzado (IDOR) y escalamiento de privilegios entre usuarios")
  void testSecurityRegression_CrossTenantAccessAndRoleEscalationBlocked() {
    // Alice crea una orden privada
    TacoOrder orderAlice = createSampleV1Order("Taco Alice Privado", 1, Collections.singletonList("FLTO"));
    AtomicReference<String> orderIdRef = new AtomicReference<>();

    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-SEC-001")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderAlice)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").value(id -> orderIdRef.set(id.toString()));

    String aliceOrderId = orderIdRef.get();
    assertNotNull(aliceOrderId);

    // 1. Bob intenta consultar la orden de Alice por ID -> 403 Forbidden
    webTestClient.get()
        .uri("/api/orders/" + aliceOrderId)
        .header("X-Test-User", "bob")
        .exchange()
        .expectStatus().isForbidden();

    // 2. Bob intenta cancelar la orden de Alice -> 403 Forbidden
    webTestClient.patch()
        .uri("/api/orders/" + aliceOrderId + "/status")
        .header("X-Test-User", "bob")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Bob intentando cancelar"))
        .exchange()
        .expectStatus().isForbidden();

    // 3. Alice (usuario común sin rol cocina) intenta avanzar orden a PREPARING -> 403 Forbidden
    webTestClient.patch()
        .uri("/api/orders/" + aliceOrderId + "/status")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.PREPARING, "Alice quiere cocinar"))
        .exchange()
        .expectStatus().isForbidden();

    // 4. Bob consulta su propio historial: NO debe ver la orden de Alice
    webTestClient.get()
        .uri("/api/orders/history")
        .header("X-Test-User", "bob")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(0);
  }

  // =========================================================================
  // 8. DETENCIÓN DE REGRESIÓN DE COCINA: CLAIM ATÓMICO ANTI-COLISIÓN
  // =========================================================================

  @Test
  @DisplayName("8. Regresión de Cocina: Claim atómico impide que dos cocineros preparen la misma orden (Anti-colisión)")
  void testKitchenQueueRegression_AtomicClaim_PreventsDoublePreparation() {
    TacoOrder orderReq = createSampleV1Order("Taco Cola", 1, Collections.singletonList("FLTO"));
    AtomicReference<String> orderIdRef = new AtomicReference<>();

    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-CLAIM-001")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").value(id -> orderIdRef.set(id.toString()));

    String orderId = orderIdRef.get();
    assertNotNull(orderId);

    // Cocinero 1 (Chef Luis) realiza el reclamo exitosamente
    webTestClient.post()
        .uri("/api/kitchen/orders/" + orderId + "/claim")
        .header("X-Test-User", "chef_luis")
        .header("X-Test-Role", "ROLE_KITCHEN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new ClaimOrderRequest("Tomada por Chef Luis"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.claimedBy").isEqualTo("chef_luis")
        .jsonPath("$.status").isEqualTo("PREPARING");

    // REGRESIÓN CRÍTICA A DETENER: Cocinero 2 intenta tomar la misma orden -> 409 Conflict
    webTestClient.post()
        .uri("/api/kitchen/orders/" + orderId + "/claim")
        .header("X-Test-User", "chef_mario")
        .header("X-Test-Role", "ROLE_KITCHEN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new ClaimOrderRequest("Tomada por Chef Mario"))
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.detail").value(d -> assertTrue(d.toString().contains("reclamada")));
  }

  // =========================================================================
  // 9. DETENCIÓN DE REGRESIÓN DE OUTBOX: RESILIENCIA ANTE CAÍDA DEL BROKER
  // =========================================================================

  @Test
  @DisplayName("9. Regresión de Outbox: Caída temporal del broker no pierde órdenes y el relay despacha al recuperarse")
  void testTransactionalOutboxRegression_BrokerFailureResilienceAndRelay() {
    // 1. Simular broker de mensajería caído (Connection refused / Timeout)
    messagingService.setFailOnSend(true);

    TacoOrder orderReq = createSampleV1Order("Taco Broker Down", 1, Collections.singletonList("FLTO"));
    AtomicReference<String> orderIdRef = new AtomicReference<>();

    // La creación de orden HTTP del cliente NO debe fallar
    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-OUTBOX-BROKER-DOWN")
        .header("X-Correlation-ID", "CID-BROKER-DOWN-101")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").value(id -> orderIdRef.set(id.toString()));

    String orderId = orderIdRef.get();
    assertNotNull(orderId);

    // La orden quedó salvaguardada en BD
    assertNotNull(orderStore.get(orderId));

    // El OutboxMessage quedó en estado PENDING con el error registrado
    OutboxMessage pendingMessage = outboxStore.values().stream()
        .filter(m -> orderId.equals(m.getOrderId()))
        .findFirst()
        .orElse(null);

    assertNotNull(pendingMessage);
    assertEquals(OutboxStatus.PENDING, pendingMessage.getStatus());
    assertTrue(pendingMessage.getRetryCount() > 0);
    assertNotNull(pendingMessage.getLastError());

    // 2. El broker se recupera
    messagingService.setFailOnSend(false);

    // Ejecutar el servicio de Relay del Outbox
    outboxService.processPendingMessages().block();

    // 3. El mensaje se publicó exitosamente y cambió a PUBLISHED
    assertEquals(OutboxStatus.PUBLISHED, pendingMessage.getStatus());
    assertNotNull(pendingMessage.getPublishedAt());
    assertEquals(1, messagingService.getSentEvents().size());
    assertEquals("CID-BROKER-DOWN-101", messagingService.getSentEvents().get(0).getCorrelationId());
  }

  // =========================================================================
  // 10. DETENCIÓN DE REGRESIÓN DE CORRELATION ID DE EXTREMO A EXTREMO
  // =========================================================================

  @Test
  @DisplayName("10. Regresión de Correlation ID: Se propaga intacto a respuesta HTTP, entidad y evento de Outbox")
  void testCorrelationIdRegression_EndToEndPropagationAcrossHttpAndOutbox() {
    String clientCorrelationId = "CID-REAL-INTG-7788-TRACE";

    TacoOrder orderReq = createSampleV1Order("Taco Traza", 1, Collections.singletonList("FLTO"));
    AtomicReference<String> orderIdRef = new AtomicReference<>();

    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("X-Correlation-ID", clientCorrelationId)
        .header("Idempotency-Key", "IDEMP-CID-TEST")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("X-Correlation-ID", clientCorrelationId)
        .expectBody()
        .jsonPath("$.id").value(id -> orderIdRef.set(id.toString()))
        .jsonPath("$.correlationId").isEqualTo(clientCorrelationId);

    String orderId = orderIdRef.get();
    assertNotNull(orderId);

    // Verificación en base de datos
    TacoOrder persisted = orderStore.get(orderId);
    assertNotNull(persisted);
    assertEquals(clientCorrelationId, persisted.getCorrelationId());

    // Verificación en Outbox
    OutboxMessage outboxMsg = outboxStore.values().stream()
        .filter(m -> orderId.equals(m.getOrderId()))
        .findFirst()
        .orElse(null);
    assertNotNull(outboxMsg);
    assertEquals(clientCorrelationId, outboxMsg.getCorrelationId());

    // Verificación en evento publicado
    OrderEvent sentEvent = messagingService.getSentEvents().stream()
        .filter(e -> orderId.equals(e.getOrderId()))
        .findFirst()
        .orElse(null);
    assertNotNull(sentEvent);
    assertEquals(clientCorrelationId, sentEvent.getCorrelationId());
  }

  // =========================================================================
  // 11. DETENCIÓN DE REGRESIÓN DE VERSIONADO Y CONTRATO OPENAPI (V1 VS V2)
  // =========================================================================

  @Test
  @DisplayName("11. Regresión de API: Contratos V1 (canónico) y V2 (estructurado HATEOAS) coexisten sin ruptura")
  void testApiVersioningRegression_V1AndV2ContractCompatibility() {
    // 1. Endpoint V1: responde formato canónico TacoOrder con encabezados de ciclo de vida
    TacoOrder v1Req = createSampleV1Order("Taco V1 Legacy", 1, Collections.singletonList("FLTO"));
    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(v1Req)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_API_VERSION, "1.0")
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_SUPPORTED_VERSIONS, "1.0, 2.0")
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_SUNSET, ApiVersioningWebFilter.V1_SUNSET_DATE)
        .expectBody()
        .jsonPath("$.id").isNotEmpty()
        .jsonPath("$.deliveryName").isEqualTo("Alice Smith");

    // 2. Endpoint V2: acepta OrderV2Request y responde OrderV2Response estructurado con HATEOAS
    OrderV2Request v2Req = OrderV2Request.builder()
        .deliveryName("Alice Smith")
        .deliveryAddress(AddressDto.builder()
            .street("123 Main St")
            .city("CDMX")
            .state("CDMX")
            .zip("06500")
            .build())
        .items(Collections.singletonList(
            OrderItemV2Request.builder()
                .name("Taco Pastor V2")
                .quantity(2)
                .ingredientIds(Arrays.asList("FLTO", "GRBF"))
                .build()
        ))
        .build();

    webTestClient.post()
        .uri("/api/v2/orders")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(v2Req)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_API_VERSION, "2.0")
        .expectBody()
        .jsonPath("$.id").isNotEmpty()
        .jsonPath("$.version").isEqualTo("2.0")
        .jsonPath("$.status").isEqualTo("CONFIRMED")
        .jsonPath("$.pricingBreakdown").exists()
        .jsonPath("$.deliveryAddress.street").isEqualTo("123 Main St")
        .jsonPath("$.links.self").isNotEmpty()
        .jsonPath("$.links.v1_equivalent").isNotEmpty();

    // 3. Catálogo de versiones (/api/versions)
    webTestClient.get()
        .uri("/api/versions")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.versions[0].version").isEqualTo("v1")
        .jsonPath("$.versions[1].version").isEqualTo("v2")
        .jsonPath("$.currentVersion").isEqualTo("v2");

    // 4. Contrato OpenAPI 3.0 (/v3/api-docs y /v3/api-docs.yaml)
    webTestClient.get()
        .uri("/v3/api-docs")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.openapi").isEqualTo("3.0.3")
        .jsonPath("$.paths['/api/v1/orders']").exists()
        .jsonPath("$.paths['/api/v2/orders']").exists();

    webTestClient.get()
        .uri("/v3/api-docs.yaml")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("Content-Type", "application/yaml;charset=UTF-8");
  }

  // =========================================================================
  // 12. DETENCIÓN DE REGRESIÓN DE OBSERVABILIDAD: MÉTRICAS Y CONSISTENCIA
  // =========================================================================

  @Test
  @DisplayName("12. Regresión de Observabilidad: Métricas en Micrometer reflejan con precisión matemática el negocio")
  void testObservabilityRegression_BusinessMetricsConsistencyAcrossLifecycle() {
    double initialPlaced = meterRegistry.find("tacocloud.orders.placed").counter() != null
        ? meterRegistry.find("tacocloud.orders.placed").counter().count() : 0.0;
    double initialReserved = meterRegistry.find("tacocloud.inventory.reserved").counter() != null
        ? meterRegistry.find("tacocloud.inventory.reserved").counter().count() : 0.0;

    TacoOrder orderReq = createSampleV1Order("Taco Metricas", 1, Collections.singletonList("FLTO"));
    webTestClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderReq)
        .exchange()
        .expectStatus().isCreated();

    double postPlaced = meterRegistry.find("tacocloud.orders.placed").counter().count();
    double postReserved = meterRegistry.find("tacocloud.inventory.reserved").counter().count();

    assertEquals(initialPlaced + 1.0, postPlaced, 0.001, "La métrica de órdenes colocadas debe incrementar en 1 exactamente");
    assertEquals(initialReserved + 1.0, postReserved, 0.001, "La métrica de inventario reservado debe incrementar exactamente según los ingredientes");

    // Verificar que el resumen de métricas del negocio sea consistente
    Map<String, Object> summary = metricsService.getMetricsSummary();
    assertNotNull(summary);
    assertTrue(summary.containsKey("ordersPlaced"));
    assertTrue(summary.containsKey("inventoryUnitsReserved"));
    assertTrue(summary.containsKey("outboxEnqueued"));
  }
}
