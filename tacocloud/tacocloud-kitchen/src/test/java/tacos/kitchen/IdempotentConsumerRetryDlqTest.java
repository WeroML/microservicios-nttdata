package tacos.kitchen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import tacos.Taco;
import tacos.TacoOrder;
import tacos.kitchen.consumer.ConsumeResult;
import tacos.kitchen.consumer.DeadLetterQueueService;
import tacos.kitchen.consumer.DeadLetterRecord;
import tacos.kitchen.consumer.DeadLetterStatus;
import tacos.kitchen.consumer.IdempotentConsumerRegistry;
import tacos.kitchen.consumer.IdempotentOrderConsumerEngine;
import tacos.kitchen.consumer.ProcessStatus;
import tacos.kitchen.controller.DeadLetterQueueController;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
public class IdempotentConsumerRetryDlqTest {

  private KitchenUI mockUi;
  private IdempotentConsumerRegistry registry;
  private DeadLetterQueueService dlqService;
  private IdempotentOrderConsumerEngine consumerEngine;
  private DeadLetterQueueController controller;

  @BeforeEach
  void setUp() {
    mockUi = mock(KitchenUI.class);
    registry = new IdempotentConsumerRegistry();
    dlqService = new DeadLetterQueueService();
    consumerEngine = new IdempotentOrderConsumerEngine(registry, dlqService, mockUi);
    consumerEngine.setMaxAttempts(3);
    consumerEngine.setBackoffDelayMs(0L); // 0ms en tests unitarios para máxima velocidad
    controller = new DeadLetterQueueController(dlqService, registry, consumerEngine);
  }

  private TacoOrder createSampleOrder(String id, String customerName) {
    TacoOrder order = new TacoOrder();
    order.setId(id);
    order.setDeliveryName(customerName);
    order.setDeliveryStreet("123 Main St");
    order.setDeliveryCity("Mexico City");
    order.setDeliveryState("CDMX");
    order.setDeliveryZip("01000");
    order.setPlacedAt(new Date(1700000000000L));
    order.setTotal(new BigDecimal("150.00"));

    Taco taco = new Taco();
    taco.setName("Carnitas");
    order.setTacos(Collections.singletonList(taco));
    return order;
  }

  @Test
  @DisplayName("1. Idempotencia: Los mensajes duplicados son omitidos sin volver a preparar la orden")
  void testIdempotency_DuplicateOrderSkipped() {
    TacoOrder order = createSampleOrder("ORDER-IDEMP-001", "Carlos Gomez");

    // Primer consumo: Exitoso
    ConsumeResult result1 = consumerEngine.consumeOrder(order, "KAFKA", "ORDER-IDEMP-001");
    assertTrue(result1.isSuccess());
    assertEquals(ProcessStatus.PROCESSED, result1.getStatus());
    assertEquals(1, result1.getAttempts());
    verify(mockUi, times(1)).displayOrder(order);

    // Segundo consumo del mismo mensaje (redelivery por Kafka o timeout)
    ConsumeResult result2 = consumerEngine.consumeOrder(order, "KAFKA", "ORDER-IDEMP-001");
    assertTrue(result2.isSuccess());
    assertEquals(ProcessStatus.DUPLICATE_SKIPPED, result2.getStatus());
    assertEquals(0, result2.getAttempts());

    // Tercer consumo del mismo mensaje
    ConsumeResult result3 = consumerEngine.consumeOrder(order, "KAFKA", "ORDER-IDEMP-001");
    assertEquals(ProcessStatus.DUPLICATE_SKIPPED, result3.getStatus());

    // KitchenUI NO debió ejecutarse de nuevo (solo 1 vez en total)
    verify(mockUi, times(1)).displayOrder(order);

    // Verificación de contadores de idempotencia
    assertEquals(1, registry.getProcessedCount());
    assertEquals(2, registry.getDuplicateCount());
    assertEquals(0, dlqService.getDlqCount());
  }

  @Test
  @DisplayName("2. Reintentos Limitados: Fallo transitorio recuperado en el 3er intento")
  void testRetry_TransientFailure_SuccessOnThirdAttempt() {
    TacoOrder order = createSampleOrder("ORDER-RETRY-002", "Ana Lopez");

    // Simulador: falla en intento 1 y 2 con excepción transitoria, éxito en intento 3
    consumerEngine.setFailureSimulator((key, attempt) -> {
      if (attempt < 3) {
        throw new RuntimeException("Fallo temporal de conexión a impresora de cocina en intento " + attempt);
      }
    });

    ConsumeResult result = consumerEngine.consumeOrder(order, "RABBITMQ", "ORDER-RETRY-002");

    assertTrue(result.isSuccess());
    assertEquals(ProcessStatus.PROCESSED, result.getStatus());
    assertEquals(3, result.getAttempts());

    // KitchenUI se ejecutó exitosamente al 3er intento
    verify(mockUi, times(1)).displayOrder(order);
    assertEquals(1, registry.getProcessedCount());
    assertEquals(0, dlqService.getDlqCount());
  }

  @Test
  @DisplayName("3. Dead Letter Queue: Mensaje venenoso agota 3 reintentos y es enrutado a DLQ")
  void testDeadLetterQueue_PoisonPillExhaustsRetries_RoutesToDlq() {
    TacoOrder order = createSampleOrder("ORDER-POISON-003", "Beto Hacker");

    // Simulador: fallo permanente / poison pill
    consumerEngine.setFailureSimulator((key, attempt) -> {
      throw new IllegalArgumentException("Poison pill: receta corrupta con ingredientes prohibidos");
    });

    ConsumeResult result = consumerEngine.consumeOrder(order, "JMS", "ORDER-POISON-003");

    // No debe lanzar excepción no capturada (el consumidor sobrevive y aísla el veneno)
    assertFalse(result.isSuccess());
    assertEquals(ProcessStatus.FAILED_DLQ, result.getStatus());
    assertEquals(3, result.getAttempts());
    assertNotNull(result.getDeadLetterId());
    assertTrue(result.getErrorMessage().contains("Poison pill"));

    // KitchenUI nunca pudo completar la orden
    verify(mockUi, times(0)).displayOrder(order);

    // Verificación del contenido de la DLQ
    assertEquals(1, dlqService.getDlqCount());
    List<DeadLetterRecord> deadLetters = dlqService.getAllDeadLetters();
    assertEquals(1, deadLetters.size());

    DeadLetterRecord dlq = deadLetters.get(0);
    assertEquals(result.getDeadLetterId(), dlq.getDlqId());
    assertEquals("ORDER-POISON-003", dlq.getMessageKey());
    assertEquals("JMS", dlq.getBroker());
    assertEquals(3, dlq.getAttempts());
    assertEquals(DeadLetterStatus.DEAD_LETTER, dlq.getStatus());
    assertEquals("java.lang.IllegalArgumentException", dlq.getExceptionClass());
    assertTrue(dlq.getStackTrace().contains("Poison pill: receta corrupta"));
    assertNotNull(dlq.getFailedAt());
    assertEquals(order, dlq.getPayload());
  }

  @Test
  @DisplayName("4. DLQ Replay: Reprocesamiento manual de mensaje venenoso tras resolver causa de fallo")
  void testDeadLetterQueue_ReplaySuccess() {
    TacoOrder order = createSampleOrder("ORDER-REPLAY-004", "David Chef");

    // Primero falla y va a DLQ
    consumerEngine.setFailureSimulator((key, attempt) -> {
      throw new RuntimeException("Error simulado para enviar a DLQ");
    });
    ConsumeResult failResult = consumerEngine.consumeOrder(order, "KAFKA", "ORDER-REPLAY-004");
    assertEquals(ProcessStatus.FAILED_DLQ, failResult.getStatus());
    String dlqId = failResult.getDeadLetterId();

    // Ahora se resuelve la causa del fallo en cocina
    consumerEngine.setFailureSimulator(null);

    // Replay del mensaje desde la DLQ
    ConsumeResult replayResult = consumerEngine.replay(dlqId);
    assertTrue(replayResult.isSuccess());
    assertEquals(ProcessStatus.PROCESSED, replayResult.getStatus());

    // La UI fue invocada
    verify(mockUi, times(1)).displayOrder(order);

    // Registro DLQ actualizado a REPLAYED
    Optional<DeadLetterRecord> dlqRecordOpt = dlqService.getDeadLetter(dlqId);
    assertTrue(dlqRecordOpt.isPresent());
    assertEquals(DeadLetterStatus.REPLAYED, dlqRecordOpt.get().getStatus());
    assertNotNull(dlqRecordOpt.get().getReplayedAt());
  }

  @Test
  @DisplayName("5. DLQ Discard: Descarte administrativo de mensaje inservible")
  void testDeadLetterQueue_Discard() {
    TacoOrder order = createSampleOrder("ORDER-DISCARD-005", "Usuario Invalido");
    consumerEngine.setFailureSimulator((key, attempt) -> {
      throw new RuntimeException("Error irremediable");
    });
    ConsumeResult failResult = consumerEngine.consumeOrder(order, "KAFKA", "ORDER-DISCARD-005");
    String dlqId = failResult.getDeadLetterId();

    boolean discarded = dlqService.discardDeadLetter(dlqId);
    assertTrue(discarded);

    DeadLetterRecord record = dlqService.getDeadLetter(dlqId).orElse(null);
    assertNotNull(record);
    assertEquals(DeadLetterStatus.DISCARDED, record.getStatus());
  }

  @Test
  @DisplayName("6. Resolución determinista de clave de orden cuando no se pasa clave explícita")
  void testResolveKey_Determinism() {
    TacoOrder order1 = createSampleOrder(null, "Maria Torres");
    TacoOrder order2 = createSampleOrder(null, "Maria Torres");

    String key1 = consumerEngine.resolveKey(order1, null);
    String key2 = consumerEngine.resolveKey(order2, null);

    assertNotNull(key1);
    assertTrue(key1.startsWith("order-hash-"));
    assertEquals(key1, key2, "Dos órdenes idénticas deben generar la misma huella determinista");

    // Si tiene id asignado, se usa el id
    order1.setId("CUSTOM-ID-777");
    assertEquals("CUSTOM-ID-777", consumerEngine.resolveKey(order1, null));

    // Si se pasa clave explícita, tiene máxima prioridad
    assertEquals("EXPLICIT-KEY-999", consumerEngine.resolveKey(order1, "EXPLICIT-KEY-999"));
  }

  @Test
  @DisplayName("7. Integración con Listeners de Kafka, RabbitMQ y JMS")
  void testListeners_Integration() {
    // Kafka Listener
    tacos.kitchen.messaging.kafka.listener.OrderListener kafkaListener =
        new tacos.kitchen.messaging.kafka.listener.OrderListener(mockUi, consumerEngine);
    TacoOrder kafkaOrder = createSampleOrder("ORDER-KAFKA-1", "Cliente Kafka");
    ConsumerRecord<String, TacoOrder> mockRecord = new ConsumerRecord<>("tacocloud.orders.topic", 0, 100L, "ORDER-KAFKA-1", kafkaOrder);
    kafkaListener.handle(kafkaOrder, mockRecord);
    verify(mockUi, times(1)).displayOrder(kafkaOrder);

    // RabbitMQ Listener
    tacos.kitchen.messaging.rabbit.listener.OrderListener rabbitListener =
        new tacos.kitchen.messaging.rabbit.listener.OrderListener(mockUi, consumerEngine);
    TacoOrder rabbitOrder = createSampleOrder("ORDER-RABBIT-1", "Cliente Rabbit");
    rabbitListener.receiveOrder(rabbitOrder);
    verify(mockUi, times(1)).displayOrder(rabbitOrder);

    // JMS Listener
    tacos.kitchen.messaging.jms.listener.OrderListener jmsListener =
        new tacos.kitchen.messaging.jms.listener.OrderListener(mockUi, consumerEngine);
    TacoOrder jmsOrder = createSampleOrder("ORDER-JMS-1", "Cliente JMS");
    jmsListener.receiveOrder(jmsOrder);
    verify(mockUi, times(1)).displayOrder(jmsOrder);

    assertEquals(3, registry.getProcessedCount());
  }

  @Test
  @DisplayName("8. Endpoints REST de DLQ e Idempotencia (Controller)")
  void testDeadLetterQueueController_RestEndpoints() {
    TacoOrder poisonOrder = createSampleOrder("ORDER-CTRL-001", "Cliente Error");
    consumerEngine.setFailureSimulator((key, attempt) -> {
      throw new RuntimeException("Fallo en controlador");
    });
    ConsumeResult failResult = consumerEngine.consumeOrder(poisonOrder, "RABBITMQ", "ORDER-CTRL-001");
    String dlqId = failResult.getDeadLetterId();

    // GET /api/kitchen/dlq
    List<DeadLetterRecord> list = controller.getDeadLetters();
    assertEquals(1, list.size());

    // GET /api/kitchen/dlq/{id}
    ResponseEntity<DeadLetterRecord> detail = controller.getDeadLetterById(dlqId);
    assertEquals(HttpStatus.OK, detail.getStatusCode());
    assertEquals("ORDER-CTRL-001", detail.getBody().getMessageKey());

    // GET /api/kitchen/idempotency/stats
    Map<String, Object> stats = controller.getIdempotencyStats();
    assertEquals(0L, stats.get("processedCount"));
    assertEquals(1, stats.get("dlqCount"));

    // POST /api/kitchen/dlq/{id}/replay (corregir y reprocesar)
    consumerEngine.setFailureSimulator(null);
    ResponseEntity<ConsumeResult> replayResp = controller.replayDeadLetter(dlqId);
    assertEquals(HttpStatus.OK, replayResp.getStatusCode());
    assertTrue(replayResp.getBody().isSuccess());

    // DELETE /api/kitchen/dlq/{id}
    ResponseEntity<Void> discardResp = controller.discardDeadLetter(dlqId);
    assertEquals(HttpStatus.NO_CONTENT, discardResp.getStatusCode());
  }

}
