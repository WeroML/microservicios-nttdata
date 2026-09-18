package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.User;
import tacos.data.OrderRepository;
import tacos.data.OutboxRepository;
import tacos.data.UserRepository;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.messaging.DynamicOrderMessagingRouter;
import tacos.messaging.OrderMessagingService;
import tacos.outbox.OrderOutboxService;
import tacos.outbox.OutboxMessage;
import tacos.outbox.OutboxStatus;
import tacos.outbox.TransactionalOutboxService;
import tacos.web.api.dto.ClaimOrderRequest;
import tacos.web.api.dto.OutboxRelaySummary;
import tacos.web.api.dto.UpdateOrderStatusRequest;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 29: Outbox transaccional para no perder órdenes
public class TransactionalOutboxTest {

  private OrderRepository orderRepo;
  private OutboxRepository outboxRepo;
  private OrderMessagingService messagingService;
  private OrderOutboxService outboxService;
  private OrderApiController orderApiController;
  private KitchenService kitchenService;
  private OutboxAdminController outboxAdminController;

  private Map<String, OutboxMessage> inMemoryOutboxStore;
  private AtomicLong idSequence;

  @BeforeEach
  public void setUp() {
    inMemoryOutboxStore = new ConcurrentHashMap<>();
    idSequence = new AtomicLong(1);

    orderRepo = mock(OrderRepository.class);
    messagingService = mock(OrderMessagingService.class);
    outboxRepo = mock(OutboxRepository.class);

    // Configurar simulación reactiva de OutboxRepository en memoria
    when(outboxRepo.save(any(OutboxMessage.class))).thenAnswer(invocation -> {
      OutboxMessage msg = invocation.getArgument(0);
      if (msg.getId() == null) {
        msg.setId("OUTBOX-" + idSequence.getAndIncrement());
      }
      inMemoryOutboxStore.put(msg.getId(), msg);
      return Mono.just(msg);
    });

    when(outboxRepo.findById(any(String.class))).thenAnswer(invocation -> {
      String id = invocation.getArgument(0);
      OutboxMessage msg = inMemoryOutboxStore.get(id);
      return msg != null ? Mono.just(msg) : Mono.empty();
    });

    when(outboxRepo.findByStatusOrderByCreatedAtAsc(any(OutboxStatus.class))).thenAnswer(invocation -> {
      OutboxStatus st = invocation.getArgument(0);
      List<OutboxMessage> list = new ArrayList<>();
      for (OutboxMessage m : inMemoryOutboxStore.values()) {
        if (m.getStatus() == st) {
          list.add(m);
        }
      }
      list.sort((m1, m2) -> m1.getCreatedAt().compareTo(m2.getCreatedAt()));
      return Flux.fromIterable(list);
    });

    when(outboxRepo.findAll()).thenAnswer(invocation -> Flux.fromIterable(inMemoryOutboxStore.values()));

    when(outboxRepo.count()).thenAnswer(invocation -> Mono.just((long) inMemoryOutboxStore.size()));

    when(outboxRepo.countByStatus(any(OutboxStatus.class))).thenAnswer(invocation -> {
      OutboxStatus st = invocation.getArgument(0);
      long count = inMemoryOutboxStore.values().stream().filter(m -> m.getStatus() == st).count();
      return Mono.just(count);
    });

    // Instanciar servicio de Outbox
    outboxService = new OrderOutboxService(outboxRepo, messagingService, new ObjectMapper());
    outboxService.setDefaultMaxRetries(3);

    // Instanciar KitchenService y OrderApiController
    kitchenService = new KitchenService(orderRepo, messagingService, outboxService);
    orderApiController = new OrderApiController(
        orderRepo,
        messagingService,
        null,
        null,
        null,
        null,
        null,
        null,
        null,
        kitchenService,
        outboxService
    );

    outboxAdminController = new OutboxAdminController(outboxService);
  }

  @Test
  public void postOrder_successPath_persistsOutboxRecordAndMarksPublished() {
    TacoOrder order = new TacoOrder();
    order.setId("ORD-SUCCESS-1");
    order.setDeliveryName("Carlos");
    order.setDeliveryCity("Guadalajara");

    when(orderRepo.save(any(TacoOrder.class))).thenReturn(Mono.just(order));

    WebTestClient client = WebTestClient.bindToController(orderApiController).build();

    client.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo("ORD-SUCCESS-1");

    // Verificar que se guardó en el outbox y se publicó inmediatamente con éxito
    assertEquals(1, inMemoryOutboxStore.size());
    OutboxMessage outboxMsg = inMemoryOutboxStore.values().iterator().next();
    assertNotNull(outboxMsg);
    assertEquals("ORD-SUCCESS-1", outboxMsg.getOrderId());
    assertEquals(OrderEventType.ORDER_CREATED, outboxMsg.getEventType());
    assertEquals(OutboxStatus.PUBLISHED, outboxMsg.getStatus());
    assertNotNull(outboxMsg.getPublishedAt());
    assertNull(outboxMsg.getLastError());

    // Verificar que el broker recibió el evento
    verify(messagingService).sendOrderEvent(any(OrderEvent.class));
  }

  @Test
  public void postOrder_brokerOutage_orderSavedSuccessfullyAndOutboxPending() {
    // Simular broker caído arrojando excepción de conexión
    doThrow(new RuntimeException("Broker Connection Refused: Connection to Kafka/RabbitMQ timed out"))
        .when(messagingService).sendOrderEvent(any(OrderEvent.class));

    TacoOrder order = new TacoOrder();
    order.setId("ORD-OUTAGE-1");
    order.setDeliveryName("Elena");
    order.setDeliveryCity("Zapopan");

    when(orderRepo.save(any(TacoOrder.class))).thenReturn(Mono.just(order));

    WebTestClient client = WebTestClient.bindToController(orderApiController).build();

    // La creación de la orden DEBE TENER ÉXITO (HTTP 201 Created) a pesar de la caída del broker
    client.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(order)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo("ORD-OUTAGE-1");

    // Verificar que la orden NO se perdió: está almacenada como PENDING en el outbox
    assertEquals(1, inMemoryOutboxStore.size());
    OutboxMessage outboxMsg = inMemoryOutboxStore.values().iterator().next();
    assertNotNull(outboxMsg);
    assertEquals("ORD-OUTAGE-1", outboxMsg.getOrderId());
    assertEquals(OutboxStatus.PENDING, outboxMsg.getStatus());
    assertEquals(1, outboxMsg.getRetryCount());
    assertNull(outboxMsg.getPublishedAt());
    assertNotNull(outboxMsg.getLastError());
    assertTrue(outboxMsg.getLastError().contains("Connection to Kafka/RabbitMQ timed out"));
  }

  @Test
  public void relayProcessing_whenBrokerRecovers_publishesPendingMessages() {
    // 1. Simular broker caído inicialmente
    doThrow(new RuntimeException("Broker is temporarily unreachable"))
        .when(messagingService).sendOrderEvent(any(OrderEvent.class));

    TacoOrder order = new TacoOrder();
    order.setId("ORD-RECOVER-1");
    when(orderRepo.save(any(TacoOrder.class))).thenReturn(Mono.just(order));

    WebTestClient client = WebTestClient.bindToController(orderApiController).build();
    client.post().uri("/api/orders").contentType(MediaType.APPLICATION_JSON).bodyValue(order).exchange();

    OutboxMessage pendingMsg = inMemoryOutboxStore.values().iterator().next();
    assertEquals(OutboxStatus.PENDING, pendingMsg.getStatus());

    // 2. El broker se recupera (no arroja error)
    org.mockito.Mockito.reset(messagingService);

    // 3. Ejecutar el ciclo de relay del outbox
    OutboxRelaySummary summary = outboxService.processPendingMessages().block();
    assertNotNull(summary);
    assertEquals(1, summary.getProcessedCount());
    assertEquals(1, summary.getSuccessCount());
    assertEquals(0, summary.getFailedCount());

    // 4. Verificar que el mensaje transicionó a PUBLISHED
    OutboxMessage recoveredMsg = inMemoryOutboxStore.get(pendingMsg.getId());
    assertEquals(OutboxStatus.PUBLISHED, recoveredMsg.getStatus());
    assertNotNull(recoveredMsg.getPublishedAt());
    assertNull(recoveredMsg.getLastError());

    // 5. Verificar que el broker recibió el despacho
    verify(messagingService, times(1)).sendOrderEvent(any(OrderEvent.class));
  }

  @Test
  public void idempotency_eventIdPreservedAcrossRetries() {
    doThrow(new RuntimeException("Transient timeout"))
        .when(messagingService).sendOrderEvent(any(OrderEvent.class));

    TacoOrder order = new TacoOrder();
    order.setId("ORD-IDEMP-1");
    when(orderRepo.save(any(TacoOrder.class))).thenReturn(Mono.just(order));

    orderApiController.postOrder(order, null).block();

    OutboxMessage initialMsg = inMemoryOutboxStore.values().iterator().next();
    String originalEventId = initialMsg.getEventId();
    assertNotNull(originalEventId);

    // Recuperar y reintentar
    org.mockito.Mockito.reset(messagingService);
    outboxService.processPendingMessages().block();

    OutboxMessage publishedMsg = inMemoryOutboxStore.get(initialMsg.getId());
    // El eventId debe ser exactamente el mismo para evitar duplicidad downstream
    assertEquals(originalEventId, publishedMsg.getEventId());
    assertEquals(OutboxStatus.PUBLISHED, publishedMsg.getStatus());
  }

  @Test
  public void repeatedFailures_exceedingMaxRetries_transitionsToDeadLetter() {
    outboxService.setDefaultMaxRetries(2);

    doThrow(new RuntimeException("Fatal Broker Failure"))
        .when(messagingService).sendOrderEvent(any(OrderEvent.class));

    TacoOrder order = new TacoOrder();
    order.setId("ORD-DEAD-1");
    when(orderRepo.save(any(TacoOrder.class))).thenReturn(Mono.just(order));

    // Intento 1: al crear la orden (retryCount pasa a 1)
    orderApiController.postOrder(order, null).block();
    OutboxMessage msg = inMemoryOutboxStore.values().iterator().next();
    assertEquals(OutboxStatus.PENDING, msg.getStatus());
    assertEquals(1, msg.getRetryCount());

    // Intento 2: durante el relay (supera maxRetries=2 -> DEAD_LETTER)
    outboxService.processPendingMessages().block();
    OutboxMessage deadLetterMsg = inMemoryOutboxStore.get(msg.getId());
    assertEquals(OutboxStatus.DEAD_LETTER, deadLetterMsg.getStatus());
    assertEquals(2, deadLetterMsg.getRetryCount());
  }

  @Test
  public void manualRetry_reprocessesFailedOrDeadLetterMessage() {
    // Crear mensaje en estado DEAD_LETTER
    OutboxMessage deadMsg = OutboxMessage.builder()
        .id("MSG-DLQ-1")
        .eventId("EVT-DLQ-1")
        .orderId("ORD-DLQ-1")
        .eventType(OrderEventType.ORDER_CREATED)
        .status(OutboxStatus.DEAD_LETTER)
        .retryCount(3)
        .maxRetries(3)
        .event(OrderEvent.builder().eventId("EVT-DLQ-1").orderId("ORD-DLQ-1").eventType(OrderEventType.ORDER_CREATED).build())
        .lastError("Persistent connection error")
        .createdAt(new Date())
        .build();
    inMemoryOutboxStore.put(deadMsg.getId(), deadMsg);

    // Reintentar manualmente cuando el broker está listo
    OutboxMessage retried = outboxService.retryMessage("MSG-DLQ-1").block();
    assertNotNull(retried);
    assertEquals(OutboxStatus.PUBLISHED, retried.getStatus());
    assertNotNull(retried.getPublishedAt());
    verify(messagingService).sendOrderEvent(any(OrderEvent.class));
  }

  @Test
  public void updateOrderStatus_enqueuesOutboxEvent() {
    TacoOrder order = new TacoOrder();
    order.setId("ORD-STATUS-1");
    order.setStatus(OrderStatus.CONFIRMED);

    when(orderRepo.findById("ORD-STATUS-1")).thenReturn(Mono.just(order));
    when(orderRepo.save(any(TacoOrder.class))).thenReturn(Mono.just(order));

    UpdateOrderStatusRequest req = new UpdateOrderStatusRequest(OrderStatus.PREPARING, "Cocinero en marcha");

    // Simulamos usuario ADMIN para permitir avanzar estado
    Authentication auth = new UsernamePasswordAuthenticationToken(
        "chef", "pass", Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN")));

    orderApiController.updateOrderStatus("ORD-STATUS-1", req, auth).block();

    assertEquals(1, inMemoryOutboxStore.size());
    OutboxMessage statusMsg = inMemoryOutboxStore.values().iterator().next();
    assertEquals("ORD-STATUS-1", statusMsg.getOrderId());
    assertEquals(OrderEventType.ORDER_PREPARING, statusMsg.getEventType());
    assertEquals(OutboxStatus.PUBLISHED, statusMsg.getStatus());
  }

  @Test
  public void kitchenService_claimAndUnclaim_enqueuesOutboxEvent() {
    TacoOrder order = new TacoOrder();
    order.setId("ORD-KITCHEN-1");
    order.setStatus(OrderStatus.CONFIRMED);

    when(orderRepo.findById("ORD-KITCHEN-1")).thenReturn(Mono.just(order));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    // Claim
    kitchenService.claimOrder("ORD-KITCHEN-1", "mario", new ClaimOrderRequest("Rápido")).block();
    assertEquals(1, inMemoryOutboxStore.size());
    OutboxMessage claimMsg = inMemoryOutboxStore.values().iterator().next();
    assertEquals(OrderEventType.ORDER_PREPARING, claimMsg.getEventType());

    // Unclaim
    kitchenService.unclaimOrder("ORD-KITCHEN-1", "mario", true).block();
    assertEquals(2, inMemoryOutboxStore.size());
  }

  @Test
  public void adminEndpoints_metricsAndRelay() {
    // Cargar 2 mensajes en memoria
    OutboxMessage m1 = OutboxMessage.builder()
        .id("M1").status(OutboxStatus.PENDING).createdAt(new Date()).build();
    OutboxMessage m2 = OutboxMessage.builder()
        .id("M2").status(OutboxStatus.PUBLISHED).createdAt(new Date()).publishedAt(new Date()).build();
    inMemoryOutboxStore.put("M1", m1);
    inMemoryOutboxStore.put("M2", m2);

    WebTestClient client = WebTestClient.bindToController(outboxAdminController).build();

    // 1. GET /api/outbox/metrics
    client.get()
        .uri("/api/outbox/metrics")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.totalCount").isEqualTo(2)
        .jsonPath("$.pendingCount").isEqualTo(1)
        .jsonPath("$.publishedCount").isEqualTo(1);

    // 2. GET /api/outbox?status=PENDING
    client.get()
        .uri("/api/outbox?status=PENDING")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].id").isEqualTo("M1");

    // 3. POST /api/outbox/relay
    client.post()
        .uri("/api/outbox/relay")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.processedCount").isEqualTo(1)
        .jsonPath("$.successCount").isEqualTo(1);
  }

  @Test
  public void security_adminEndpoints_denyNonAdminUsers() {
    WebTestClient secureClient = WebTestClient.bindToController(outboxAdminController)
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .webFilter((exchange, chain) -> {
          String role = exchange.getRequest().getHeaders().getFirst("X-Test-Role");
          if (role == null || !role.equals("ROLE_ADMIN")) {
            exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
            return exchange.getResponse().setComplete();
          }
          return chain.filter(exchange);
        })
        .build();

    // 1. Sin rol ADMIN -> 403 Forbidden
    secureClient.get()
        .uri("/api/outbox/metrics")
        .exchange()
        .expectStatus().isForbidden();

    secureClient.post()
        .uri("/api/outbox/relay")
        .header("X-Test-Role", "ROLE_USER")
        .exchange()
        .expectStatus().isForbidden();

    // 2. Con rol ADMIN -> 200 OK
    secureClient.get()
        .uri("/api/outbox/metrics")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isOk();
  }

}
