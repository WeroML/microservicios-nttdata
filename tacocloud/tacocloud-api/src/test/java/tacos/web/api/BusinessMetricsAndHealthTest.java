package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import io.micrometer.core.instrument.Counter;
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
import tacos.actuator.InventoryHealthIndicator;
import tacos.actuator.OrdersHealthIndicator;
import tacos.actuator.OutboxHealthIndicator;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.OutboxRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.messaging.OrderMessagingService;
import tacos.outbox.OrderOutboxService;
import tacos.outbox.OutboxMessage;
import tacos.outbox.OutboxStatus;
import tacos.web.api.dto.OutboxMetrics;
import tacos.web.api.dto.UpdateOrderStatusRequest;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 32: Métricas y salud que explican el negocio
public class BusinessMetricsAndHealthTest {

  private SimpleMeterRegistry meterRegistry;
  private BusinessMetricsService metricsService;

  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private OrderRepository orderRepo;
  private OutboxRepository outboxRepo;
  private OrderMessagingService messagingService;
  private UserRepository userRepo;

  private InventoryService inventoryService;
  private OrderOutboxService outboxService;
  private OrderApiController orderController;
  private BusinessMonitoringController monitoringController;

  private InventoryHealthIndicator inventoryHealth;
  private OrdersHealthIndicator ordersHealth;
  private OutboxHealthIndicator outboxHealth;

  private User adminUser;
  private WebTestClient webTestClient;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    metricsService = new BusinessMetricsService(meterRegistry);

    ingredientRepo = mock(IngredientRepository.class);
    tacoRepo = mock(TacoRepository.class);
    orderRepo = mock(OrderRepository.class);
    outboxRepo = mock(OutboxRepository.class);
    messagingService = mock(OrderMessagingService.class);
    userRepo = mock(UserRepository.class);

    inventoryService = new InventoryService(ingredientRepo, tacoRepo, metricsService);
    outboxService = new OrderOutboxService(outboxRepo, messagingService, null, metricsService);

    adminUser = new User("admin", "adminPass", "Admin Chef", "Admin Street", "CDMX", "CDMX", "06500", "555-9999", "admin@tacocloud.com");
    adminUser.setId("USER_ADMIN");
    when(userRepo.findByUsername("admin")).thenReturn(Mono.just(adminUser));

    orderController = new OrderApiController(
        orderRepo,
        messagingService,
        null,
        ingredientRepo,
        tacoRepo,
        null,
        inventoryService,
        null,
        userRepo,
        null,
        outboxService,
        metricsService
    );

    inventoryHealth = new InventoryHealthIndicator(ingredientRepo, metricsService);
    ordersHealth = new OrdersHealthIndicator(orderRepo);
    outboxHealth = new OutboxHealthIndicator(outboxService);

    monitoringController = new BusinessMonitoringController(inventoryHealth, ordersHealth, outboxHealth, metricsService);

    webTestClient = WebTestClient.bindToController(monitoringController)
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .build();
  }

  // ==========================================
  // RETO 16: SALUD Y MÉTRICAS DE INVENTARIO
  // ==========================================

  @Test
  @DisplayName("1. Salud de Inventario: UP cuando todos los ingredientes tienen stock saludable")
  void testInventoryHealth_AllStocked_ReturnsUp() {
    Ingredient ing1 = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("10.00"), true, 50);
    Ingredient ing2 = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("20.00"), true, 30);
    when(ingredientRepo.findAll()).thenReturn(Flux.just(ing1, ing2));

    Health health = inventoryHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.UP, health.getStatus());
    assertEquals(2, health.getDetails().get("totalIngredients"));
    assertEquals(2, health.getDetails().get("healthyStockCount"));
    assertEquals(0, health.getDetails().get("lowStockCount"));
    assertEquals(0, health.getDetails().get("outOfStockCount"));
  }

  @Test
  @DisplayName("2. Salud de Inventario: OUT_OF_SERVICE cuando faltan insumos (no vender aire)")
  void testInventoryHealth_OutOfStock_ReturnsOutOfService() {
    Ingredient ing1 = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("10.00"), false, 0); // Agotado
    Ingredient ing2 = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("20.00"), true, 25);
    when(ingredientRepo.findAll()).thenReturn(Flux.just(ing1, ing2));

    Health health = inventoryHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
    assertEquals(1, health.getDetails().get("outOfStockCount"));
    assertTrue(((List<?>) health.getDetails().get("outOfStock")).get(0).toString().contains("FLTO"));
  }

  @Test
  @DisplayName("3. Salud de Inventario: UP con advertencia cuando hay stock bajo (<= umbral)")
  void testInventoryHealth_LowStock_ReturnsUpWithLowStockDetails() {
    Ingredient ing1 = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("10.00"), true, 3); // Bajo stock
    Ingredient ing2 = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN, new BigDecimal("20.00"), true, 20);
    when(ingredientRepo.findAll()).thenReturn(Flux.just(ing1, ing2));

    Health health = inventoryHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.UP, health.getStatus());
    assertEquals(1, health.getDetails().get("lowStockCount"));
    assertTrue(((List<?>) health.getDetails().get("lowStock")).get(0).toString().contains("FLTO"));
  }

  @Test
  @DisplayName("4. Métricas de Inventario: Incrementa contadores de reserva, fallo y liberación")
  void testInventoryMetrics_ReservationSuccessFailureAndRelease() {
    Ingredient ingFlto = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("10.00"), true, 10);
    when(ingredientRepo.findById("FLTO")).thenReturn(Mono.just(ingFlto));
    when(ingredientRepo.save(any(Ingredient.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    Taco taco = new Taco();
    taco.setName("Taco Pastor");
    taco.setQuantity(2);
    taco.setIngredients(Collections.singletonList(ingFlto));

    TacoOrder order = new TacoOrder();
    order.setId("ORD-TEST-1");
    order.setTacos(Collections.singletonList(taco));

    // A. Reserva exitosa
    inventoryService.reserveInventory(order).block();
    Counter reservedCounter = meterRegistry.find("tacocloud.inventory.reserved")
        .tag("item", "FLTO")
        .counter();
    assertNotNull(reservedCounter);
    assertEquals(2.0, reservedCounter.count());

    // B. Reserva fallida por falta de stock ("vender aire")
    ingFlto.setStock(1); // Solo queda 1 pero orden requiere 2
    assertThrows(ResponseStatusException.class, () -> inventoryService.reserveInventory(order).block());
    Counter failedCounter = meterRegistry.find("tacocloud.inventory.failed")
        .tag("item", "FLTO")
        .counter();
    assertNotNull(failedCounter);
    assertEquals(2.0, failedCounter.count());

    // C. Liberación de inventario (por cancelación)
    inventoryService.releaseInventory(order).block();
    Counter releasedCounter = meterRegistry.find("tacocloud.inventory.released")
        .tag("item", "FLTO")
        .counter();
    assertNotNull(releasedCounter);
    assertEquals(2.0, releasedCounter.count());
  }

  // ==========================================
  // RETO 25: SALUD Y MÉTRICAS DE ÓRDENES
  // ==========================================

  @Test
  @DisplayName("5. Salud de Órdenes: UP con flujo operativo normal de pedidos")
  void testOrdersHealth_NormalFlow_ReturnsUp() {
    TacoOrder o1 = createOrderWithStatus("ORD-1", OrderStatus.CONFIRMED);
    TacoOrder o2 = createOrderWithStatus("ORD-2", OrderStatus.PREPARING);
    TacoOrder o3 = createOrderWithStatus("ORD-3", OrderStatus.DELIVERED);
    when(orderRepo.findAll()).thenReturn(Flux.just(o1, o2, o3));

    Health health = ordersHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.UP, health.getStatus());
    assertEquals(3, health.getDetails().get("totalOrders"));
    assertEquals(2L, health.getDetails().get("activeOrdersCount"));
    assertEquals(1L, health.getDetails().get("deliveredOrdersCount"));
    assertEquals(0L, health.getDetails().get("cancelledOrdersCount"));
    assertEquals("0.0%", health.getDetails().get("cancellationRate"));
  }

  @Test
  @DisplayName("6. Salud de Órdenes: DOWN si la tasa de cancelación es crítica (>= 40%)")
  void testOrdersHealth_CriticalCancellationRate_ReturnsDown() {
    TacoOrder o1 = createOrderWithStatus("ORD-1", OrderStatus.CANCELLED);
    TacoOrder o2 = createOrderWithStatus("ORD-2", OrderStatus.CANCELLED);
    TacoOrder o3 = createOrderWithStatus("ORD-3", OrderStatus.CANCELLED);
    TacoOrder o4 = createOrderWithStatus("ORD-4", OrderStatus.DELIVERED);
    TacoOrder o5 = createOrderWithStatus("ORD-5", OrderStatus.CONFIRMED);
    when(orderRepo.findAll()).thenReturn(Flux.just(o1, o2, o3, o4, o5)); // 3/5 = 60%

    Health health = ordersHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("60.0%", health.getDetails().get("cancellationRate"));
  }

  @Test
  @DisplayName("7. Salud de Órdenes: OUT_OF_SERVICE si la cocina se satura de órdenes activas")
  void testOrdersHealth_OverwhelmedKitchen_ReturnsOutOfService() {
    ordersHealth.setMaxActiveOrdersThreshold(2);
    TacoOrder o1 = createOrderWithStatus("ORD-1", OrderStatus.CONFIRMED);
    TacoOrder o2 = createOrderWithStatus("ORD-2", OrderStatus.PREPARING);
    TacoOrder o3 = createOrderWithStatus("ORD-3", OrderStatus.READY);
    when(orderRepo.findAll()).thenReturn(Flux.just(o1, o2, o3)); // 3 activos > 2

    Health health = ordersHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
  }

  @Test
  @DisplayName("8. Métricas de Órdenes: Registra órdenes creadas, transiciones de estado y cancelaciones")
  void testOrderMetrics_CreationTransitionAndCancellation() {
    TacoOrder order = createOrderWithStatus("ORD-FLOW-1", OrderStatus.CONFIRMED);
    order.setTotal(new BigDecimal("120.00"));
    order.setUser(adminUser);

    when(orderRepo.findById("ORD-FLOW-1")).thenReturn(Mono.just(order));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    when(outboxRepo.save(any(OutboxMessage.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    Principal principal = new UsernamePasswordAuthenticationToken(adminUser.getUsername(), "pwd",
        Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

    // A. Orden colocada
    orderController.postOrder(order, principal).block();
    Counter placedCounter = meterRegistry.find("tacocloud.orders.placed").counter();
    assertNotNull(placedCounter);
    assertEquals(1.0, placedCounter.count());

    // B. Transición a PREPARING
    UpdateOrderStatusRequest prepReq = new UpdateOrderStatusRequest(OrderStatus.PREPARING, "Cocinando");
    orderController.updateOrderStatus("ORD-FLOW-1", prepReq, principal).block();

    Counter transCounter = meterRegistry.find("tacocloud.orders.status.transitions")
        .tag("from", "CONFIRMED")
        .tag("to", "PREPARING")
        .counter();
    assertNotNull(transCounter);
    assertEquals(1.0, transCounter.count());

    // C. Transición a CANCELLED
    UpdateOrderStatusRequest cancelReq = new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Cliente desistió");
    orderController.updateOrderStatus("ORD-FLOW-1", cancelReq, principal).block();

    Counter cancelCounter = meterRegistry.find("tacocloud.orders.cancelled")
        .tag("from", "PREPARING")
        .counter();
    assertNotNull(cancelCounter);
    assertEquals(1.0, cancelCounter.count());
  }

  // ==========================================
  // RETO 29: SALUD Y MÉTRICAS DE OUTBOX
  // ==========================================

  @Test
  @DisplayName("9. Salud de Outbox: UP cuando la cola está vacía o despachada normalmente")
  void testOutboxHealth_HealthyBacklog_ReturnsUp() {
    when(outboxRepo.count()).thenReturn(Mono.just(10L));
    when(outboxRepo.countByStatus(OutboxStatus.PENDING)).thenReturn(Mono.just(0L));
    when(outboxRepo.countByStatus(OutboxStatus.PUBLISHED)).thenReturn(Mono.just(10L));
    when(outboxRepo.countByStatus(OutboxStatus.FAILED)).thenReturn(Mono.just(0L));
    when(outboxRepo.countByStatus(OutboxStatus.DEAD_LETTER)).thenReturn(Mono.just(0L));

    Health health = outboxHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.UP, health.getStatus());
    assertEquals(0L, health.getDetails().get("pendingCount"));
    assertEquals(10L, health.getDetails().get("publishedCount"));
    assertEquals(true, health.getDetails().get("backlogHealthy"));
  }

  @Test
  @DisplayName("10. Salud de Outbox: DOWN cuando hay mensajes en DEAD_LETTER o FAILED")
  void testOutboxHealth_DeadLetterOrFailed_ReturnsDown() {
    when(outboxRepo.count()).thenReturn(Mono.just(5L));
    when(outboxRepo.countByStatus(OutboxStatus.PENDING)).thenReturn(Mono.just(1L));
    when(outboxRepo.countByStatus(OutboxStatus.PUBLISHED)).thenReturn(Mono.just(3L));
    when(outboxRepo.countByStatus(OutboxStatus.FAILED)).thenReturn(Mono.just(0L));
    when(outboxRepo.countByStatus(OutboxStatus.DEAD_LETTER)).thenReturn(Mono.just(1L)); // Poison pill

    Health health = outboxHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.DOWN, health.getStatus());
    assertEquals(1L, health.getDetails().get("deadLetterCount"));
  }

  @Test
  @DisplayName("11. Salud de Outbox: OUT_OF_SERVICE si hay acumulación crítica de mensajes pendientes")
  void testOutboxHealth_PendingBacklogSaturated_ReturnsOutOfService() {
    outboxHealth.setMaxPendingThreshold(5);
    when(outboxRepo.count()).thenReturn(Mono.just(20L));
    when(outboxRepo.countByStatus(OutboxStatus.PENDING)).thenReturn(Mono.just(12L)); // 12 > 5
    when(outboxRepo.countByStatus(OutboxStatus.PUBLISHED)).thenReturn(Mono.just(8L));
    when(outboxRepo.countByStatus(OutboxStatus.FAILED)).thenReturn(Mono.just(0L));
    when(outboxRepo.countByStatus(OutboxStatus.DEAD_LETTER)).thenReturn(Mono.just(0L));

    Health health = outboxHealth.health().block();
    assertNotNull(health);
    assertEquals(Status.OUT_OF_SERVICE, health.getStatus());
  }

  @Test
  @DisplayName("12. Métricas de Outbox: Encolado, despacho y reintentos instrumentados")
  void testOutboxMetrics_EnqueuedDispatchedAndRetried() {
    when(outboxRepo.save(any(OutboxMessage.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    when(outboxRepo.findById("OUTBOX-1")).thenReturn(Mono.just(
        OutboxMessage.builder()
            .id("OUTBOX-1")
            .eventId("EVT-1")
            .eventType(OrderEventType.ORDER_CREATED)
            .status(OutboxStatus.FAILED)
            .maxRetries(5)
            .retryCount(1)
            .build()
    ));

    OrderEvent testEvent = OrderEvent.builder()
        .eventId("EVT-100")
        .eventType(OrderEventType.ORDER_CREATED)
        .source("api-test")
        .correlationId("CID-100")
        .build();

    // A. Encolado y despacho exitoso
    outboxService.enqueueEvent(testEvent).block();
    Counter enqueuedCounter = meterRegistry.find("tacocloud.outbox.enqueued")
        .tag("eventType", "ORDER_CREATED")
        .counter();
    assertNotNull(enqueuedCounter);
    assertEquals(1.0, enqueuedCounter.count());

    Counter dispatchedCounter = meterRegistry.find("tacocloud.outbox.dispatched")
        .tag("status", "SUCCESS")
        .counter();
    assertNotNull(dispatchedCounter);
    assertEquals(1.0, dispatchedCounter.count());

    // B. Reintento manual
    outboxService.retryMessage("OUTBOX-1").block();
    Counter retriedCounter = meterRegistry.find("tacocloud.outbox.retried")
        .tag("trigger", "MANUAL")
        .counter();
    assertNotNull(retriedCounter);
    assertEquals(1.0, retriedCounter.count());
  }

  // ==========================================
  // ENDPOINT CONSOLIDADO DE SALUD Y MÉTRICAS
  // ==========================================

  @Test
  @DisplayName("13. Endpoint Consolidado: GET /api/business/health-and-metrics entrega visión global")
  void testBusinessMonitoringEndpoint_ConsolidatedReport() {
    Ingredient ing1 = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new BigDecimal("10.00"), true, 50);
    when(ingredientRepo.findAll()).thenReturn(Flux.just(ing1));

    TacoOrder o1 = createOrderWithStatus("ORD-1", OrderStatus.CONFIRMED);
    when(orderRepo.findAll()).thenReturn(Flux.just(o1));

    when(outboxRepo.count()).thenReturn(Mono.just(5L));
    when(outboxRepo.countByStatus(OutboxStatus.PENDING)).thenReturn(Mono.just(0L));
    when(outboxRepo.countByStatus(OutboxStatus.PUBLISHED)).thenReturn(Mono.just(5L));
    when(outboxRepo.countByStatus(OutboxStatus.FAILED)).thenReturn(Mono.just(0L));
    when(outboxRepo.countByStatus(OutboxStatus.DEAD_LETTER)).thenReturn(Mono.just(0L));

    webTestClient.get()
        .uri("/api/business/health-and-metrics")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.status").isEqualTo("UP")
        .jsonPath("$.components.inventory.status").isEqualTo("UP")
        .jsonPath("$.components.orders.status").isEqualTo("UP")
        .jsonPath("$.components.outbox.status").isEqualTo("UP")
        .jsonPath("$.metricsSummary").exists();
  }

  private TacoOrder createOrderWithStatus(String id, OrderStatus status) {
    TacoOrder order = new TacoOrder();
    order.setId(id);
    order.setStatus(status);
    order.setDeliveryName("Test User");
    order.setDeliveryStreet("Test Street");
    order.setDeliveryCity("CDMX");
    order.setDeliveryState("CDMX");
    order.setDeliveryZip("06500");
    order.setTotal(new BigDecimal("100.00"));
    return order;
  }

}
