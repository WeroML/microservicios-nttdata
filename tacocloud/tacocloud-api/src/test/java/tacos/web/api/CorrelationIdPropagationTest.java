package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import tacos.Ingredient;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.OutboxRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.messaging.DynamicOrderMessagingRouter;
import tacos.messaging.JmsOrderMessagingService;
import tacos.messaging.KafkaOrderMessagingService;
import tacos.messaging.OrderMessagingService;
import tacos.messaging.RabbitOrderMessagingService;
import tacos.outbox.OrderOutboxService;
import tacos.outbox.OutboxMessage;
import tacos.web.api.correlation.CorrelationIdSupport;
import tacos.web.api.correlation.CorrelationIdWebFilter;
import tacos.web.api.dto.ClaimOrderRequest;
import tacos.web.api.dto.UpdateOrderStatusRequest;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 31: Correlation ID de HTTP a evento y logs
public class CorrelationIdPropagationTest {

  private OrderRepository orderRepo;
  private OutboxRepository outboxRepo;
  private OrderMessagingService orderMessages;
  private OrderOutboxService outboxService;
  private OrderApiController controller;
  private KitchenService kitchenService;
  private WebTestClient webTestClient;
  private tacos.User adminUser;

  @BeforeEach
  void setUp() {
    orderRepo = mock(OrderRepository.class);
    outboxRepo = mock(OutboxRepository.class);
    orderMessages = mock(OrderMessagingService.class);

    when(orderRepo.save(any(TacoOrder.class)))
        .thenAnswer(inv -> {
          TacoOrder o = inv.getArgument(0);
          if (o.getId() == null) {
            o.setId("ORDER-123");
          }
          return Mono.just(o);
        });

    when(outboxRepo.save(any(OutboxMessage.class)))
        .thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    outboxService = new OrderOutboxService(outboxRepo, orderMessages);

    UserRepository userRepo = mock(UserRepository.class);
    adminUser = new tacos.User("admin", "pwd", "Admin Chef", "Street", "City", "ST", "12345", "555-0000", "admin@tacocloud.com");
    adminUser.setId("USER_ADMIN");
    when(userRepo.findByUsername("admin")).thenReturn(Mono.just(adminUser));

    controller = new OrderApiController(
        orderRepo,
        orderMessages,
        null,
        mock(IngredientRepository.class),
        mock(TacoRepository.class),
        null,
        null,
        null,
        userRepo,
        null,
        outboxService
    );

    kitchenService = new KitchenService(orderRepo, orderMessages, outboxService);

    webTestClient = WebTestClient.bindToController(controller)
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .webFilter(new CorrelationIdWebFilter())
        .webFilter((exchange, chain) -> {
          String testUser = exchange.getRequest().getHeaders().getFirst("X-Test-User");
          String testRole = exchange.getRequest().getHeaders().getFirst("X-Test-Role");
          if (testUser != null && !testUser.trim().isEmpty()) {
            java.util.List<org.springframework.security.core.GrantedAuthority> authorities = new java.util.ArrayList<>();
            if (testRole != null && !testRole.trim().isEmpty()) {
              authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority(testRole.trim()));
            } else {
              authorities.add(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"));
            }
            org.springframework.security.core.Authentication auth =
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(testUser.trim(), "pwd", authorities);
            return chain.filter(exchange.mutate().principal(Mono.just(auth)).build());
          }
          return chain.filter(exchange);
        })
        .build();
  }

  private TacoOrder createSampleOrder() {
    TacoOrder order = new TacoOrder();
    order.setDeliveryName("Carlos Slim");
    order.setDeliveryStreet("Paseo Reforma 100");
    order.setDeliveryCity("CDMX");
    order.setDeliveryState("CDMX");
    order.setDeliveryZip("06500");
    Taco taco = new Taco();
    taco.setName("Pastor");
    taco.setPrice(new BigDecimal("25.00"));
    taco.setQuantity(2);
    order.setTacos(Collections.singletonList(taco));
    order.setTotal(new BigDecimal("50.00"));
    return order;
  }

  @Test
  @DisplayName("1. HTTP Inbound con X-Correlation-ID: Propaga en respuesta, TacoOrder, OrderEvent y OutboxMessage")
  void testHttpWithCorrelationIdHeader_PropagatesToEndpoints() {
    String clientCorrelationId = "CLIENT-CORR-998877";

    webTestClient.post()
        .uri("/api/orders")
        .header("X-Correlation-ID", clientCorrelationId)
        .bodyValue(createSampleOrder())
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("X-Correlation-ID", clientCorrelationId)
        .expectBody()
        .jsonPath("$.id").isEqualTo("ORDER-123");

    // Verificar que outbox guardó el correlationId
    ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outboxRepo, atLeastOnce()).save(outboxCaptor.capture());
    OutboxMessage savedMessage = outboxCaptor.getValue();
    assertNotNull(savedMessage);
    assertEquals(clientCorrelationId, savedMessage.getCorrelationId());
    assertNotNull(savedMessage.getEvent());
    assertEquals(clientCorrelationId, savedMessage.getEvent().getCorrelationId());
  }

  @Test
  @DisplayName("2. HTTP Inbound sin encabezado: Genera automáticamente UUID en respuesta y eventos")
  void testHttpWithoutHeader_GeneratesAndPropagatesUUID() {
    webTestClient.post()
        .uri("/api/orders")
        .bodyValue(createSampleOrder())
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().exists("X-Correlation-ID")
        .expectBody()
        .consumeWith(result -> {
          String responseCid = result.getResponseHeaders().getFirst("X-Correlation-ID");
          assertNotNull(responseCid);
          assertFalse(responseCid.trim().isEmpty());

          ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
          verify(outboxRepo, atLeastOnce()).save(outboxCaptor.capture());
          OutboxMessage savedMessage = outboxCaptor.getValue();
          assertEquals(responseCid, savedMessage.getCorrelationId());
          assertEquals(responseCid, savedMessage.getEvent().getCorrelationId());
        });
  }

  @Test
  @DisplayName("3. HTTP Inbound con X-Request-ID alternativo: Lo adopta como Correlation ID")
  void testHttpWithRequestIdHeader_AdoptsAsCorrelationId() {
    String altRequestId = "REQ-ALT-554433";

    webTestClient.post()
        .uri("/api/orders")
        .header("X-Request-ID", altRequestId)
        .bodyValue(createSampleOrder())
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("X-Correlation-ID", altRequestId);

    ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outboxRepo, atLeastOnce()).save(outboxCaptor.capture());
    assertEquals(altRequestId, outboxCaptor.getValue().getCorrelationId());
  }

  @Test
  @DisplayName("4. Cambio de estado de orden (PATCH): Propaga Correlation ID en evento y respuesta")
  void testOrderStatusPatch_PropagatesCorrelationId() {
    TacoOrder existing = createSampleOrder();
    existing.setId("ORD-STATUS-1");
    existing.setStatus(OrderStatus.CONFIRMED);
    existing.setUser(adminUser);

    when(orderRepo.findById("ORD-STATUS-1")).thenReturn(Mono.just(existing));

    String statusCid = "STATUS-CORR-443322";

    UpdateOrderStatusRequest updateReq = new UpdateOrderStatusRequest(OrderStatus.PREPARING, "Iniciando cocinado");

    webTestClient.patch()
        .uri("/api/orders/ORD-STATUS-1/status")
        .header("X-Correlation-ID", statusCid)
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .bodyValue(updateReq)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Correlation-ID", statusCid);

    ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outboxRepo, atLeastOnce()).save(outboxCaptor.capture());
    assertEquals(statusCid, outboxCaptor.getValue().getCorrelationId());
    assertEquals(statusCid, outboxCaptor.getValue().getEvent().getCorrelationId());
    assertEquals(OrderEventType.ORDER_PREPARING, outboxCaptor.getValue().getEventType());
  }

  @Test
  @DisplayName("5. KitchenService (Claim y Unclaim): Propaga Correlation ID desde contexto reactivo")
  void testKitchenServiceClaim_PropagatesCorrelationId() {
    TacoOrder existing = createSampleOrder();
    existing.setId("ORD-CLAIM-1");
    existing.setStatus(OrderStatus.CONFIRMED);

    when(orderRepo.findById("ORD-CLAIM-1")).thenReturn(Mono.just(existing));

    String claimCid = "CLAIM-CORR-112233";

    ClaimOrderRequest claimReq = new ClaimOrderRequest();
    claimReq.setNotes("Tomada por Chef");

    kitchenService.claimOrder("ORD-CLAIM-1", "chef_luis", claimReq)
        .contextWrite(Context.of(CorrelationIdSupport.CORRELATION_ID_KEY, claimCid))
        .block();

    ArgumentCaptor<OutboxMessage> outboxCaptor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outboxRepo, atLeastOnce()).save(outboxCaptor.capture());
    assertEquals(claimCid, outboxCaptor.getValue().getCorrelationId());
    assertEquals(claimCid, outboxCaptor.getValue().getEvent().getCorrelationId());
  }

  @Test
  @DisplayName("6. Emisores de Broker (Kafka, RabbitMQ, JMS): Inyectan Correlation ID en cabeceras de broker")
  void testBrokerEmitters_AttachCorrelationIdHeaders() throws Exception {
    OrderEvent testEvent = OrderEvent.fromOrder(createSampleOrder(), OrderEventType.ORDER_CREATED, "tacocloud-api", "BROKER-CID-777");

    // Kafka
    KafkaTemplate mockKafka = mock(KafkaTemplate.class);
    KafkaOrderMessagingService kafkaService = new KafkaOrderMessagingService(mockKafka);
    kafkaService.sendOrderEvent(testEvent);

    ArgumentCaptor<ProducerRecord> kafkaCaptor = ArgumentCaptor.forClass(ProducerRecord.class);
    verify(mockKafka).send(kafkaCaptor.capture());
    ProducerRecord capturedKafka = kafkaCaptor.getValue();
    Header kafkaHeader = capturedKafka.headers().lastHeader("X_CORRELATION_ID");
    assertNotNull(kafkaHeader);
    assertEquals("BROKER-CID-777", new String(kafkaHeader.value(), StandardCharsets.UTF_8));

    // RabbitMQ
    RabbitTemplate mockRabbit = mock(RabbitTemplate.class);
    RabbitOrderMessagingService rabbitService = new RabbitOrderMessagingService(mockRabbit);
    rabbitService.sendOrderEvent(testEvent);

    ArgumentCaptor<org.springframework.amqp.core.MessagePostProcessor> rabbitCaptor =
        ArgumentCaptor.forClass(org.springframework.amqp.core.MessagePostProcessor.class);
    verify(mockRabbit).convertAndSend(eq("tacocloud.order.queue"), eq(testEvent), rabbitCaptor.capture());

    org.springframework.amqp.core.Message amqpMsg = new org.springframework.amqp.core.Message(new byte[0], new MessageProperties());
    org.springframework.amqp.core.Message processedAmqp = rabbitCaptor.getValue().postProcessMessage(amqpMsg);
    assertEquals("BROKER-CID-777", processedAmqp.getMessageProperties().getHeader("X_CORRELATION_ID"));
    assertEquals("BROKER-CID-777", processedAmqp.getMessageProperties().getCorrelationId());

    // JMS
    JmsTemplate mockJms = mock(JmsTemplate.class);
    JmsOrderMessagingService jmsService = new JmsOrderMessagingService(mockJms);
    jmsService.sendOrderEvent(testEvent);

    ArgumentCaptor<org.springframework.jms.core.MessagePostProcessor> jmsCaptor =
        ArgumentCaptor.forClass(org.springframework.jms.core.MessagePostProcessor.class);
    verify(mockJms).convertAndSend(eq("tacocloud.order.queue"), eq(testEvent), jmsCaptor.capture());

    Message mockJmsMsg = mock(Message.class);
    jmsCaptor.getValue().postProcessMessage(mockJmsMsg);
    verify(mockJmsMsg).setStringProperty("X_CORRELATION_ID", "BROKER-CID-777");
    verify(mockJmsMsg).setJMSCorrelationID("BROKER-CID-777");
  }

}
