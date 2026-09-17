package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.User;
import tacos.data.OrderRepository;
import tacos.data.UserRepository;
import tacos.events.OrderEvent;
import tacos.events.OrderEventCustomer;
import tacos.events.OrderEventDelivery;
import tacos.events.OrderEventKitchen;
import tacos.events.OrderEventPayload;
import tacos.events.OrderEventPricing;
import tacos.events.OrderEventTaco;
import tacos.events.OrderEventType;
import tacos.messaging.NoOpOrderMessagingService;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.dto.UpdateOrderStatusRequest;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 27: Contrato único de eventos de orden
public class OrderEventContractTest {

  private OrderRepository orderRepo;
  private UserRepository userRepo;
  private OrderMessagingService orderMessages;
  private KitchenService kitchenService;
  private WebTestClient testClient;
  private ObjectMapper objectMapper;

  private User userAlice;
  private User userAdmin;
  private TacoOrder sampleOrder;

  @BeforeEach
  public void setUp() {
    orderRepo = Mockito.mock(OrderRepository.class);
    userRepo = Mockito.mock(UserRepository.class);
    orderMessages = Mockito.mock(OrderMessagingService.class);
    objectMapper = new ObjectMapper();

    kitchenService = new KitchenService(orderRepo, orderMessages);

    OrderApiController orderApiController = new OrderApiController(
        orderRepo,
        orderMessages,
        null, // emailOrderService
        null, // ingredientRepo
        null, // tacoRepo
        null, // couponEngine
        null, // inventoryService
        null, // physicsEngine
        userRepo,
        kitchenService
    );

    KitchenApiController kitchenApiController = new KitchenApiController(kitchenService, userRepo);

    testClient = WebTestClient.bindToController(orderApiController, kitchenApiController)
        .controllerAdvice(new ProblemDetailsExceptionHandler())
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

    userAlice = new User("alice", "SUPER_SECRET_PASSWORD_123", "Alice Smith", "123 Main", "Austin", "TX", "78701", "555-1111", "alice@example.com");
    userAlice.setId("usr-alice-777");

    userAdmin = new User("admin", "ADMIN_SECRET_KEY_999", "Admin Chef", "Kitchen Central", "Austin", "TX", "78701", "555-9999", "admin@tacocloud.com");
    userAdmin.setId("usr-admin-001");

    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));
    when(userRepo.findByUsername("admin")).thenReturn(Mono.just(userAdmin));

    // Crear orden con datos completos
    sampleOrder = new TacoOrder();
    sampleOrder.setId("ORD-2026-999");
    sampleOrder.setUser(userAlice);
    sampleOrder.setDeliveryName("Alice Smith");
    sampleOrder.setDeliveryStreet("123 Main St");
    sampleOrder.setDeliveryCity("Austin");
    sampleOrder.setDeliveryState("TX");
    sampleOrder.setDeliveryZip("78701");
    sampleOrder.setStatus(OrderStatus.CONFIRMED);
    sampleOrder.setPlacedAt(new Date());
    sampleOrder.setSubTotal(new BigDecimal("25.00"));
    sampleOrder.setDiscount(new BigDecimal("5.00"));
    sampleOrder.setTotal(new BigDecimal("20.00"));
    sampleOrder.setCouponCode("SUMMER20");
    sampleOrder.setClaimedBy("chef_mario");
    sampleOrder.setClaimedAt(new Date());
    sampleOrder.setEstimatedPrepMinutes(12);
    sampleOrder.setEstimatedReadyAt(new Date(System.currentTimeMillis() + 720000L));

    Ingredient wrap = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP);
    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN);
    Taco taco = new Taco();
    taco.setId("taco-1");
    taco.setName("Carnitas Especial");
    taco.setQuantity(2);
    taco.setPrice(new BigDecimal("10.00"));
    taco.setIngredients(Arrays.asList(wrap, beef));
    sampleOrder.setTacos(Arrays.asList(taco));
  }

  // 1. Estructura y metadatos del contrato canónico OrderEvent
  @Test
  public void testCanonicalOrderEventStructureAndMetadata() {
    OrderEvent event = OrderEvent.fromOrder(sampleOrder, OrderEventType.ORDER_CREATED, "tacocloud-api", "corr-12345");

    assertNotNull(event.getEventId(), "eventId no debe ser nulo");
    assertNotNull(UUID.fromString(event.getEventId()), "eventId debe ser un UUID válido");
    assertEquals(OrderEventType.ORDER_CREATED, event.getEventType());
    assertEquals("1.0", event.getVersion());
    assertEquals("tacocloud-api", event.getSource());
    assertEquals("corr-12345", event.getCorrelationId());
    assertEquals("ORD-2026-999", event.getOrderId());
    assertNotNull(event.getTimestamp());

    OrderEventPayload payload = event.getPayload();
    assertNotNull(payload, "Payload no debe ser nulo");
    assertEquals("ORD-2026-999", payload.getOrderId());
    assertEquals("CONFIRMED", payload.getStatus());
    assertNotNull(payload.getPlacedAt());

    // Delivery
    OrderEventDelivery delivery = payload.getDelivery();
    assertNotNull(delivery);
    assertEquals("Alice Smith", delivery.getDeliveryName());
    assertEquals("123 Main St", delivery.getDeliveryStreet());
    assertEquals("Austin", delivery.getDeliveryCity());
    assertEquals("TX", delivery.getDeliveryState());
    assertEquals("78701", delivery.getDeliveryZip());

    // Customer
    OrderEventCustomer customer = payload.getCustomer();
    assertNotNull(customer);
    assertEquals("usr-alice-777", customer.getCustomerId());
    assertEquals("alice", customer.getUsername());
    assertEquals("Alice Smith", customer.getFullname());
    assertEquals("alice@example.com", customer.getEmail());
    assertEquals("555-1111", customer.getPhoneNumber());

    // Items / Tacos
    List<OrderEventTaco> tacos = payload.getTacos();
    assertNotNull(tacos);
    assertEquals(1, tacos.size());
    OrderEventTaco tacoItem = tacos.get(0);
    assertEquals("taco-1", tacoItem.getTacoId());
    assertEquals("Carnitas Especial", tacoItem.getName());
    assertEquals(2, tacoItem.getQuantity());
    assertEquals(new BigDecimal("10.00"), tacoItem.getPrice());
    assertEquals(2, tacoItem.getIngredients().size());
    assertTrue(tacoItem.getIngredients().contains("Flour Tortilla"));

    // Pricing
    OrderEventPricing pricing = payload.getPricing();
    assertNotNull(pricing);
    assertEquals(new BigDecimal("25.00"), pricing.getSubTotal());
    assertEquals(new BigDecimal("5.00"), pricing.getDiscount());
    assertEquals(new BigDecimal("20.00"), pricing.getTotal());
    assertEquals("SUMMER20", pricing.getCouponCode());

    // Kitchen
    OrderEventKitchen kitchen = payload.getKitchen();
    assertNotNull(kitchen);
    assertEquals("chef_mario", kitchen.getClaimedBy());
    assertNotNull(kitchen.getClaimedAt());
    assertEquals(12, kitchen.getEstimatedPrepMinutes());
    assertNotNull(kitchen.getEstimatedReadyAt());
  }

  // 2. Privacidad y Zero-Trust: Ningún campo de credenciales (password) se filtra en el evento
  @Test
  public void testZeroTrustPrivacyNoPasswordsOrSecretsInEvent() {
    OrderEvent event = OrderEvent.fromOrder(sampleOrder, OrderEventType.ORDER_CREATED);
    OrderEventCustomer customer = event.getPayload().getCustomer();

    assertNotNull(customer);
    // Verificar que la clase OrderEventCustomer ni siquiera posee campo password
    boolean hasPasswordField = Arrays.stream(OrderEventCustomer.class.getDeclaredFields())
        .anyMatch(f -> f.getName().toLowerCase().contains("password"));
    assertFalse(hasPasswordField, "OrderEventCustomer NO debe contener ningún campo de contraseña");
  }

  // 3. Serialización y deserialización JSON bidireccional (Round-Trip Jackson)
  @Test
  public void testBidirectionalJsonSerializationRoundtrip() throws Exception {
    OrderEvent originalEvent = OrderEvent.fromOrder(sampleOrder, OrderEventType.ORDER_CONFIRMED, "tacocloud-api", "corr-99");

    String json = objectMapper.writeValueAsString(originalEvent);
    assertNotNull(json);
    assertTrue(json.contains("\"eventType\":\"ORDER_CONFIRMED\""));
    assertTrue(json.contains("\"version\":\"1.0\""));
    assertTrue(json.contains("\"orderId\":\"ORD-2026-999\""));

    OrderEvent deserialized = objectMapper.readValue(json, OrderEvent.class);
    assertNotNull(deserialized);
    assertEquals(originalEvent.getEventId(), deserialized.getEventId());
    assertEquals(originalEvent.getEventType(), deserialized.getEventType());
    assertEquals(originalEvent.getOrderId(), deserialized.getOrderId());
    assertEquals(originalEvent.getVersion(), deserialized.getVersion());
    assertEquals(originalEvent.getSource(), deserialized.getSource());
    assertEquals(originalEvent.getCorrelationId(), deserialized.getCorrelationId());

    assertEquals(originalEvent.getPayload().getDelivery().getDeliveryName(),
        deserialized.getPayload().getDelivery().getDeliveryName());
    assertEquals(originalEvent.getPayload().getCustomer().getEmail(),
        deserialized.getPayload().getCustomer().getEmail());
    assertEquals(originalEvent.getPayload().getTacos().size(),
        deserialized.getPayload().getTacos().size());
  }

  // 4. Mapeo determinista de OrderEventType a partir de OrderStatus
  @Test
  public void testOrderEventTypeFromOrderStatusMapping() {
    assertEquals(OrderEventType.ORDER_CREATED, OrderEventType.fromOrderStatus(OrderStatus.PENDING));
    assertEquals(OrderEventType.ORDER_CONFIRMED, OrderEventType.fromOrderStatus(OrderStatus.CONFIRMED));
    assertEquals(OrderEventType.ORDER_PREPARING, OrderEventType.fromOrderStatus(OrderStatus.PREPARING));
    assertEquals(OrderEventType.ORDER_READY, OrderEventType.fromOrderStatus(OrderStatus.READY));
    assertEquals(OrderEventType.ORDER_DELIVERING, OrderEventType.fromOrderStatus(OrderStatus.DELIVERING));
    assertEquals(OrderEventType.ORDER_DELIVERED, OrderEventType.fromOrderStatus(OrderStatus.DELIVERED));
    assertEquals(OrderEventType.ORDER_CANCELLED, OrderEventType.fromOrderStatus(OrderStatus.CANCELLED));
    assertEquals(OrderEventType.ORDER_CREATED, OrderEventType.fromOrderStatus(null));
  }

  // 5. Emisión de evento canónico ORDER_CREATED al reordenar una orden previa
  @Test
  public void testReorderEmitsCanonicalOrderCreatedEvent() {
    when(orderRepo.findById("ORD-PREV")).thenReturn(Mono.just(sampleOrder));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    testClient.post()
        .uri("/api/orders/ORD-PREV/reorder")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .exchange()
        .expectStatus().isCreated();

    ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
    verify(orderMessages, atLeastOnce()).sendOrderEvent(eventCaptor.capture());

    OrderEvent captured = eventCaptor.getValue();
    assertNotNull(captured);
    assertEquals(OrderEventType.ORDER_CREATED, captured.getEventType());
    assertNotNull(captured.getPayload());
    assertEquals("Alice Smith", captured.getPayload().getDelivery().getDeliveryName());

    // Retrocompatibilidad: también se invoca sendOrder(TacoOrder)
    verify(orderMessages, atLeastOnce()).sendOrder(any(TacoOrder.class));
  }

  // 6. Emisión de eventos canónicos en cambios de estado de la orden
  @Test
  public void testStatusUpdateEmitsCanonicalEvent() {
    sampleOrder.setStatus(OrderStatus.CONFIRMED);
    when(orderRepo.findById("ORD-2026-999")).thenReturn(Mono.just(sampleOrder));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.PREPARING, "Cocinero asignado");

    testClient.patch()
        .uri("/api/orders/ORD-2026-999/status")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(req)
        .exchange()
        .expectStatus().isOk();

    ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
    verify(orderMessages, atLeastOnce()).sendOrderEvent(eventCaptor.capture());

    OrderEvent captured = eventCaptor.getValue();
    assertNotNull(captured);
    assertEquals(OrderEventType.ORDER_PREPARING, captured.getEventType());
    assertEquals("ORD-2026-999", captured.getOrderId());
  }

  // 7. Emisión de evento canónico enriquecido con datos de cocina al hacer claim
  @Test
  public void testKitchenClaimEmitsCanonicalOrderPreparingEventWithChefData() {
    sampleOrder.setStatus(OrderStatus.CONFIRMED);
    sampleOrder.setClaimedBy(null);
    sampleOrder.setClaimedAt(null);

    when(orderRepo.findById("ORD-2026-999")).thenReturn(Mono.just(sampleOrder));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    testClient.post()
        .uri("/api/orders/ORD-2026-999/claim")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isOk();

    ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
    verify(orderMessages, atLeastOnce()).sendOrderEvent(eventCaptor.capture());

    OrderEvent captured = eventCaptor.getValue();
    assertEquals(OrderEventType.ORDER_PREPARING, captured.getEventType());
    assertNotNull(captured.getPayload().getKitchen());
    assertEquals("admin", captured.getPayload().getKitchen().getClaimedBy());
    assertNotNull(captured.getPayload().getKitchen().getEstimatedPrepMinutes());
  }

  // 8. Emisión de evento canónico ORDER_CONFIRMED al liberar ticket (unclaim)
  @Test
  public void testKitchenUnclaimEmitsCanonicalOrderConfirmedEvent() {
    sampleOrder.setStatus(OrderStatus.PREPARING);
    sampleOrder.setClaimedBy("admin");
    sampleOrder.setClaimedAt(new Date());

    when(orderRepo.findById("ORD-2026-999")).thenReturn(Mono.just(sampleOrder));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    testClient.post()
        .uri("/api/orders/ORD-2026-999/unclaim")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isOk();

    ArgumentCaptor<OrderEvent> eventCaptor = ArgumentCaptor.forClass(OrderEvent.class);
    verify(orderMessages, atLeastOnce()).sendOrderEvent(eventCaptor.capture());

    OrderEvent captured = eventCaptor.getValue();
    assertEquals(OrderEventType.ORDER_CONFIRMED, captured.getEventType());
    assertNull(captured.getPayload().getKitchen().getClaimedBy());
  }

  // 9. Idempotencia y deduplicación basada en UUID eventId
  @Test
  public void testEventIdUniquenessAndIdempotency() {
    OrderEvent event1 = OrderEvent.fromOrder(sampleOrder, OrderEventType.ORDER_CREATED);
    OrderEvent event2 = OrderEvent.fromOrder(sampleOrder, OrderEventType.ORDER_CREATED);

    assertNotNull(event1.getEventId());
    assertNotNull(event2.getEventId());
    assertFalse(event1.getEventId().equals(event2.getEventId()),
        "Cada evento debe generar un eventId único para permitir deduplicación");
  }

  // 10. NoOpOrderMessagingService registra y retiene eventos para inspección
  @Test
  public void testNoOpOrderMessagingServiceRetainsEvents() {
    NoOpOrderMessagingService noOpService = new NoOpOrderMessagingService();
    noOpService.clearEvents();

    assertEquals(0, noOpService.getEventHistory().size());
    assertNull(noOpService.getLastEvent());

    // Legacy sendOrder genera evento canónico en histórico
    noOpService.sendOrder(sampleOrder);

    assertEquals(1, noOpService.getEventHistory().size());
    OrderEvent recorded = noOpService.getLastEvent();
    assertNotNull(recorded);
    assertEquals(OrderEventType.ORDER_CREATED, recorded.getEventType());
    assertEquals(sampleOrder.getId(), recorded.getOrderId());

    // Envío explícito de evento
    OrderEvent customEvent = OrderEvent.fromOrder(sampleOrder, OrderEventType.ORDER_DELIVERED);
    noOpService.sendOrderEvent(customEvent);

    assertEquals(2, noOpService.getEventHistory().size());
    assertEquals(OrderEventType.ORDER_DELIVERED, noOpService.getLastEvent().getEventType());
  }
}
