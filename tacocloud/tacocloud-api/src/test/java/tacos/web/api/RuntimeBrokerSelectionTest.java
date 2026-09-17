package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.web.reactive.server.WebTestClient;

import tacos.TacoOrder;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.messaging.DynamicOrderMessagingRouter;
import tacos.messaging.JmsOrderMessagingService;
import tacos.messaging.KafkaOrderMessagingService;
import tacos.messaging.NoOpOrderMessagingService;
import tacos.messaging.RabbitOrderMessagingService;
import tacos.web.api.dto.SelectBrokerRequest;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
public class RuntimeBrokerSelectionTest {

  private NoOpOrderMessagingService noopService;
  private JmsOrderMessagingService jmsService;
  private RabbitOrderMessagingService rabbitService;
  private KafkaOrderMessagingService kafkaService;

  private JmsTemplate mockJmsTemplate;
  private RabbitTemplate mockRabbitTemplate;
  @SuppressWarnings("rawtypes")
  private KafkaTemplate mockKafkaTemplate;

  private DynamicOrderMessagingRouter router;
  private BrokerManagementController controller;
  private WebTestClient client;

  @BeforeEach
  @SuppressWarnings("unchecked")
  public void setUp() {
    noopService = new NoOpOrderMessagingService();

    mockJmsTemplate = mock(JmsTemplate.class);
    jmsService = new JmsOrderMessagingService(mockJmsTemplate);

    mockRabbitTemplate = mock(RabbitTemplate.class);
    rabbitService = new RabbitOrderMessagingService(mockRabbitTemplate);

    mockKafkaTemplate = mock(KafkaTemplate.class);
    kafkaService = new KafkaOrderMessagingService(mockKafkaTemplate);

    router = new DynamicOrderMessagingRouter(
        "",
        null,
        noopService,
        jmsService,
        rabbitService,
        kafkaService
    );

    controller = new BrokerManagementController(router);
    client = WebTestClient.bindToController(controller)
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .build();
  }

  @Test
  public void defaultSelection_isNoop() {
    assertEquals(DynamicOrderMessagingRouter.BROKER_NOOP, router.getActiveBroker());
    Set<String> available = router.getAvailableBrokers();
    assertTrue(available.contains("noop"));
    assertTrue(available.contains("jms"));
    assertTrue(available.contains("rabbitmq"));
    assertTrue(available.contains("kafka"));

    // Enviar orden a través del router
    TacoOrder order = new TacoOrder();
    order.setId("ORDER-NOOP-1");
    router.sendOrder(order);

    assertEquals(1, noopService.getEventHistory().size());
    assertEquals("ORDER-NOOP-1", noopService.getLastEvent().getOrderId());
  }

  @Test
  public void switchBrokerAtRuntime_toJms() {
    String switched = router.setActiveBroker("jms");
    assertEquals("jms", switched);
    assertEquals("jms", router.getActiveBroker());

    TacoOrder order = new TacoOrder();
    order.setId("ORDER-JMS-1");
    router.sendOrder(order);

    verify(mockJmsTemplate).convertAndSend(eq("tacocloud.order.queue"), eq(order), any());
  }

  @Test
  public void switchBrokerAtRuntime_toRabbit() {
    String switched = router.setActiveBroker("rabbitmq");
    assertEquals("rabbitmq", switched);
    assertEquals("rabbitmq", router.getActiveBroker());

    OrderEvent event = OrderEvent.builder()
        .eventId("EVT-RABBIT-1")
        .orderId("ORDER-RABBIT-1")
        .eventType(OrderEventType.ORDER_CONFIRMED)
        .build();
    router.sendOrderEvent(event);

    verify(mockRabbitTemplate).convertAndSend(eq("tacocloud.order.queue"), eq(event), any(MessagePostProcessor.class));
  }

  @Test
  @SuppressWarnings("unchecked")
  public void switchBrokerAtRuntime_toKafka() {
    String switched = router.setActiveBroker("kafka");
    assertEquals("kafka", switched);
    assertEquals("kafka", router.getActiveBroker());

    TacoOrder order = new TacoOrder();
    order.setId("ORDER-KAFKA-1");
    router.sendOrder(order);

    verify(mockKafkaTemplate).send(eq("tacocloud.orders.topic"), eq(order));

    OrderEvent event = OrderEvent.builder()
        .eventId("EVT-KAFKA-1")
        .orderId("ORDER-KAFKA-1")
        .eventType(OrderEventType.ORDER_CREATED)
        .build();
    router.sendOrderEvent(event);

    verify(mockKafkaTemplate).send(eq("tacocloud.orders.topic"), eq("ORDER-KAFKA-1"), eq(event));
  }

  @Test
  public void switchBrokerAtRuntime_usingAliases() {
    assertEquals("rabbitmq", router.setActiveBroker("rabbit"));
    assertEquals("rabbitmq", router.getActiveBroker());

    assertEquals("rabbitmq", router.setActiveBroker("amqp"));
    assertEquals("rabbitmq", router.getActiveBroker());

    assertEquals("jms", router.setActiveBroker("artemis"));
    assertEquals("jms", router.getActiveBroker());

    assertEquals("noop", router.setActiveBroker("none"));
    assertEquals("noop", router.getActiveBroker());
  }

  @Test
  public void switchBrokerAtRuntime_unsupported_throwsException() {
    IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
      router.setActiveBroker("sqs");
    });
    assertTrue(ex.getMessage().contains("Broker no soportado: 'sqs'"));
  }

  @Test
  public void startupSelection_byProperty() {
    DynamicOrderMessagingRouter kafkaRouter = new DynamicOrderMessagingRouter(
        "kafka",
        null,
        noopService,
        jmsService,
        rabbitService,
        kafkaService
    );
    assertEquals("kafka", kafkaRouter.getActiveBroker());
  }

  @Test
  public void startupSelection_byProfile() {
    Environment mockEnv = mock(Environment.class);
    when(mockEnv.getActiveProfiles()).thenReturn(new String[]{"docker", "rabbitmq"});

    DynamicOrderMessagingRouter profileRouter = new DynamicOrderMessagingRouter(
        "",
        mockEnv,
        noopService,
        jmsService,
        rabbitService,
        kafkaService
    );
    assertEquals("rabbitmq", profileRouter.getActiveBroker());
  }

  @Test
  public void api_getActiveBroker_returns200() {
    client.get()
        .uri("/api/messaging/broker")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.activeBroker").isEqualTo("noop")
        .jsonPath("$.availableBrokers").isArray()
        .jsonPath("$.availableBrokers[?(@ == 'noop')]").exists()
        .jsonPath("$.availableBrokers[?(@ == 'kafka')]").exists()
        .jsonPath("$.message").isNotEmpty();
  }

  @Test
  public void api_switchBroker_withRequestBody_returns200() {
    SelectBrokerRequest request = new SelectBrokerRequest("kafka");

    client.put()
        .uri("/api/messaging/broker")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.activeBroker").isEqualTo("kafka")
        .jsonPath("$.message").value(msg -> assertTrue(((String) msg).contains("Successfully switched")));

    assertEquals("kafka", router.getActiveBroker());
  }

  @Test
  public void api_switchBroker_withQueryParam_returns200() {
    client.put()
        .uri("/api/messaging/broker?type=jms")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.activeBroker").isEqualTo("jms");

    assertEquals("jms", router.getActiveBroker());
  }

  @Test
  public void api_switchBroker_invalidBroker_returns400() {
    SelectBrokerRequest request = new SelectBrokerRequest("pulsar");

    client.put()
        .uri("/api/messaging/broker")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  public void api_switchBroker_emptyPayload_returns400() {
    client.put()
        .uri("/api/messaging/broker")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  public void resilience_nullTemplatesDoNotThrowUnexpectedExceptions() {
    DynamicOrderMessagingRouter emptyRouter = new DynamicOrderMessagingRouter(
        new NoOpOrderMessagingService(),
        new JmsOrderMessagingService(null),
        new RabbitOrderMessagingService(null),
        new KafkaOrderMessagingService(null)
    );

    TacoOrder order = new TacoOrder();
    order.setId("TEST-ORDER");
    OrderEvent event = OrderEvent.builder().eventId("EVT-NULL").orderId("TEST-ORDER").build();

    // Probar envíos en todos los brokers con templates nulos
    for (String broker : new String[]{"jms", "rabbitmq", "kafka", "noop"}) {
      emptyRouter.setActiveBroker(broker);
      emptyRouter.sendOrder(order);
      emptyRouter.sendOrderEvent(event);
    }
  }

}
