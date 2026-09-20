package tacos.kitchen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tacos.Taco;
import tacos.TacoOrder;
import tacos.kitchen.consumer.ConsumeResult;
import tacos.kitchen.consumer.DeadLetterQueueService;
import tacos.kitchen.consumer.DeadLetterRecord;
import tacos.kitchen.consumer.DeadLetterStatus;
import tacos.kitchen.consumer.IdempotentConsumerRegistry;
import tacos.kitchen.consumer.IdempotentOrderConsumerEngine;
import tacos.kitchen.consumer.ProcessStatus;

// Ejercicio 36: Suite de integración que detenga regresiones reales
public class KitchenRegressionIntegrationTest {

  private KitchenUI mockUi;
  private IdempotentConsumerRegistry registry;
  private DeadLetterQueueService dlqService;
  private IdempotentOrderConsumerEngine consumerEngine;

  @BeforeEach
  void setUp() {
    mockUi = mock(KitchenUI.class);
    registry = new IdempotentConsumerRegistry();
    dlqService = new DeadLetterQueueService();
    consumerEngine = new IdempotentOrderConsumerEngine(registry, dlqService, mockUi);
    consumerEngine.setMaxAttempts(3);
    consumerEngine.setBackoffDelayMs(0L); // 0ms para velocidad determinista de tests
  }

  private TacoOrder createSampleOrder(String id, String customerName, String correlationId) {
    TacoOrder order = new TacoOrder();
    order.setId(id);
    order.setDeliveryName(customerName);
    order.setDeliveryStreet("123 Main St");
    order.setDeliveryCity("CDMX");
    order.setDeliveryState("CDMX");
    order.setDeliveryZip("06500");
    order.setPlacedAt(new Date());
    order.setTotal(new BigDecimal("150.00"));
    order.setCorrelationId(correlationId);

    Taco taco = new Taco();
    taco.setName("Carnitas");
    order.setTacos(Collections.singletonList(taco));
    return order;
  }

  // =========================================================================
  // 1. DETENCIÓN DE REGRESIÓN: MENSAJES VENENOSOS Y DESVÍO A DLQ
  // =========================================================================

  @Test
  @DisplayName("1. Regresión de Cocina: Mensaje venenoso se reintenta hasta el límite (3) y se enruta a DLQ sin colapsar")
  void testKitchenConsumerRegression_PoisonMessageRoutesToDlq_AfterMaxRetries() {
    String orderId = "ORDER-POISON-001";
    String correlationId = "CID-POISON-TRACE-99";
    TacoOrder poisonOrder = createSampleOrder(orderId, "Bad Payload User", correlationId);

    // Simular error fatal durante el procesamiento en cocina
    consumerEngine.setFailureSimulator((key, attempt) -> {
      if (orderId.equals(key)) {
        throw new IllegalStateException("Fallo de renderizado en terminal de cocina en intento " + attempt);
      }
    });

    // Consumir mensaje desde el broker KAFKA
    ConsumeResult result = consumerEngine.consumeOrder(poisonOrder, "KAFKA", orderId, correlationId);

    // Verificaciones del resultado del consumidor
    assertNotNull(result);
    assertFalse(result.isSuccess());
    assertEquals(ProcessStatus.FAILED_DLQ, result.getStatus());
    assertEquals(3, result.getAttempts());
    assertNotNull(result.getDeadLetterId());

    // REGRESIÓN CRÍTICA A DETENER: El mensaje no debe prepararse en UI si falló
    verify(mockUi, never()).displayOrder(poisonOrder);

    // Verificación en Dead Letter Queue
    List<DeadLetterRecord> dlqList = dlqService.getAllDeadLetters();
    assertEquals(1, dlqList.size());
    DeadLetterRecord record = dlqList.get(0);
    assertEquals(orderId, record.getMessageKey());
    assertEquals(correlationId, record.getCorrelationId());
    assertEquals("KAFKA", record.getBroker());
    assertEquals(3, record.getAttempts());
    assertEquals(DeadLetterStatus.DEAD_LETTER, record.getStatus());
    assertTrue(record.getErrorMessage().contains("Fallo de renderizado"));
  }

  // =========================================================================
  // 2. DETENCIÓN DE REGRESIÓN: COLA NO SE BLOQUEA TRAS DESVÍO A DLQ
  // =========================================================================

  @Test
  @DisplayName("2. Regresión de Cocina: Tras desviar un mensaje a DLQ, los siguientes pedidos sanos fluyen con éxito")
  void testKitchenConsumerRegression_HealthyOrdersNotBlockedByPoisonMessage() {
    // 1. Procesar orden venenosa
    String poisonId = "ORDER-POISON-002";
    consumerEngine.setFailureSimulator((key, attempt) -> {
      if (poisonId.equals(key)) {
        throw new RuntimeException("Error en impresora térmica de cocina");
      }
    });

    TacoOrder poisonOrder = createSampleOrder(poisonId, "Poison User", "CID-POISON-002");
    ConsumeResult poisonResult = consumerEngine.consumeOrder(poisonOrder, "RABBITMQ", poisonId, "CID-POISON-002");
    assertEquals(ProcessStatus.FAILED_DLQ, poisonResult.getStatus());

    // 2. Procesar orden sana inmediatamente después
    String healthyId = "ORDER-HEALTHY-003";
    TacoOrder healthyOrder = createSampleOrder(healthyId, "Healthy User", "CID-HEALTHY-003");
    ConsumeResult healthyResult = consumerEngine.consumeOrder(healthyOrder, "RABBITMQ", healthyId, "CID-HEALTHY-003");

    // REGRESIÓN CRÍTICA A DETENER: La orden sana debe tener éxito en intento 1
    assertTrue(healthyResult.isSuccess());
    assertEquals(ProcessStatus.PROCESSED, healthyResult.getStatus());
    assertEquals(1, healthyResult.getAttempts());
    verify(mockUi, times(1)).displayOrder(healthyOrder);

    // El DLQ sólo debe tener la orden venenosa
    assertEquals(1, dlqService.getAllDeadLetters().size());
  }

  // =========================================================================
  // 3. DETENCIÓN DE REGRESIÓN: REPROCESAMIENTO DE DLQ CON PRESERVACIÓN DE TRAZA
  // =========================================================================

  @Test
  @DisplayName("3. Regresión de Cocina: Mensaje en DLQ se reprocesa exitosamente preservando Correlation ID")
  void testKitchenConsumerRegression_DlqMessageReprocessingWithCorrelationId() {
    String orderId = "ORDER-REPROCESS-004";
    String correlationId = "CID-REPROCESS-TRACE-444";
    TacoOrder order = createSampleOrder(orderId, "Customer Reprocess", correlationId);

    // Falla inicial
    consumerEngine.setFailureSimulator((key, attempt) -> {
      throw new RuntimeException("Error temporal resuelto");
    });
    ConsumeResult failResult = consumerEngine.consumeOrder(order, "JMS", orderId, correlationId);
    assertEquals(ProcessStatus.FAILED_DLQ, failResult.getStatus());

    String dlqId = failResult.getDeadLetterId();
    assertNotNull(dlqId);

    // Se resuelve el problema técnico (eliminar simulador de fallos)
    consumerEngine.setFailureSimulator(null);

    // Reprocesar mediante consumerEngine.replay(dlqId)
    ConsumeResult replayResult = consumerEngine.replay(dlqId);
    assertTrue(replayResult.isSuccess());
    assertEquals(ProcessStatus.PROCESSED, replayResult.getStatus());

    // REGRESIÓN CRÍTICA A DETENER: La orden se procesó y la entrada en DLQ quedó REPLAYED
    Optional<DeadLetterRecord> optRecord = dlqService.getDeadLetter(dlqId);
    assertTrue(optRecord.isPresent());
    assertEquals(DeadLetterStatus.REPLAYED, optRecord.get().getStatus());
    assertEquals(correlationId, optRecord.get().getCorrelationId());

    // La UI ahora sí debió recibir la orden preparada
    verify(mockUi, times(1)).displayOrder(order);
  }

  // =========================================================================
  // 4. DETENCIÓN DE REGRESIÓN: IDEMPOTENCIA EN CONSUMIDOR DE MENSAJES
  // =========================================================================

  @Test
  @DisplayName("4. Regresión de Cocina: Mensajes repetidos son omitidos sin preparar órdenes duplicadas")
  void testKitchenConsumerRegression_DuplicateOrderIgnoredWithoutProcessing() {
    String orderId = "ORDER-DUP-005";
    TacoOrder order = createSampleOrder(orderId, "Carlos Dup", "CID-DUP-555");

    // Primer consumo: Éxito
    ConsumeResult first = consumerEngine.consumeOrder(order, "KAFKA", orderId, "CID-DUP-555");
    assertTrue(first.isSuccess());
    assertEquals(ProcessStatus.PROCESSED, first.getStatus());
    verify(mockUi, times(1)).displayOrder(order);

    // Segundo consumo (re-entrega por broker): Omitido por idempotencia
    ConsumeResult second = consumerEngine.consumeOrder(order, "KAFKA", orderId, "CID-DUP-555");
    assertTrue(second.isSuccess());
    assertEquals(ProcessStatus.DUPLICATE_SKIPPED, second.getStatus());

    // Tercer consumo: Omitido por idempotencia
    ConsumeResult third = consumerEngine.consumeOrder(order, "KAFKA", orderId, "CID-DUP-555");
    assertTrue(third.isSuccess());
    assertEquals(ProcessStatus.DUPLICATE_SKIPPED, third.getStatus());

    // REGRESIÓN CRÍTICA A DETENER: mockUi.displayOrder sólo debió llamarse EXACTAMENTE UNA VEZ
    verify(mockUi, times(1)).displayOrder(order);
  }
}
