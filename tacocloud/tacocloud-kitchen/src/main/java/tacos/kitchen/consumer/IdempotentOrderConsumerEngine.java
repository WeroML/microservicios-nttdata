package tacos.kitchen.consumer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import tacos.TacoOrder;
import tacos.kitchen.KitchenUI;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Service
@Slf4j
public class IdempotentOrderConsumerEngine {

  private final IdempotentConsumerRegistry registry;
  private final DeadLetterQueueService dlqService;
  private final KitchenUI ui;

  @Value("${tacocloud.consumer.max-attempts:3}")
  private int maxAttempts = 3;

  @Value("${tacocloud.consumer.backoff-ms:0}")
  private long backoffDelayMs = 0;

  /**
   * Simulador o hook de fallos configurable para verificar reintentos y desvío a DLQ.
   */
  public interface FailureSimulator {
    void inspectAndMaybeFail(String key, int attempt) throws Exception;
  }

  private FailureSimulator failureSimulator;

  @Autowired
  public IdempotentOrderConsumerEngine(
      IdempotentConsumerRegistry registry,
      DeadLetterQueueService dlqService,
      KitchenUI ui) {
    this.registry = registry;
    this.dlqService = dlqService;
    this.ui = ui;
  }

  /**
   * Procesa una orden asegurando idempotencia, tolerancia a fallos con reintentos y desvío a DLQ.
   */
  public ConsumeResult consumeOrder(TacoOrder order, String broker, String explicitKey) {
    return consumeOrder(order, broker, explicitKey, null);
  }

  // Ejercicio 31: Correlation ID de HTTP a evento y logs
  public ConsumeResult consumeOrder(TacoOrder order, String broker, String explicitKey, String correlationId) {
    String key = resolveKey(order, explicitKey);
    String cid = (correlationId != null && !correlationId.trim().isEmpty())
        ? correlationId.trim()
        : (order != null && order.getCorrelationId() != null && !order.getCorrelationId().trim().isEmpty()
            ? order.getCorrelationId().trim()
            : UUID.randomUUID().toString());

    if (order != null && order.getCorrelationId() == null) {
      order.setCorrelationId(cid);
    }

    org.slf4j.MDC.put("correlationId", cid);
    try {
      // 1. Verificación de Idempotencia
      if (registry.isAlreadyProcessed(key)) {
        registry.recordDuplicate(key);
        log.info("// Ejercicio 31: [IDEMPOTENCIA] Orden ya procesada previamente (clave={}, correlationId={}). Omitiendo.", key, cid);
        return ConsumeResult.duplicateSkipped(key);
      }

      registry.markProcessing(key, broker, cid);

      // 2. Reintentos Limitados con Backoff
      for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
          if (failureSimulator != null) {
            failureSimulator.inspectAndMaybeFail(key, attempt);
          }

          // Ejecución efectiva de cocina
          if (order != null) {
            ui.displayOrder(order);
          }

          registry.markSuccess(key, attempt, cid);
          log.info("// Ejercicio 31: Orden procesada exitosamente en cocina (clave={}, correlationId={}, broker={}, intento={}/{}).",
              key, cid, broker, attempt, maxAttempts);
          return ConsumeResult.success(key, attempt);

        } catch (Throwable t) {
          log.warn("// Ejercicio 31: [RETRY] Intento {}/{} falló para orden {} (correlationId={}) vía broker {}: {}",
              attempt, maxAttempts, key, cid, broker, t.getMessage());

          if (attempt < maxAttempts) {
            if (backoffDelayMs > 0) {
              try {
                Thread.sleep(backoffDelayMs);
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
              }
            }
          } else {
            // 3. Reintentos agotados: Desvío a Dead Letter Queue (DLQ)
            DeadLetterRecord dlqRecord = dlqService.sendToDlq(key, order, broker, attempt, t, cid);
            registry.markDlq(key, attempt, t.getMessage(), cid);
            log.error("// Ejercicio 31: [DLQ] Reintentos agotados para orden {}. Mensaje enrutado a DLQ (dlqId={}, correlationId={}).",
                key, dlqRecord.getDlqId(), cid);
            return ConsumeResult.failedDlq(key, attempt, dlqRecord.getDlqId(), t.getMessage());
          }
        }
      }

      return ConsumeResult.failedDlq(key, maxAttempts, null, "Reintentos agotados sin resultado");
    } finally {
      org.slf4j.MDC.remove("correlationId");
    }
  }

  /**
   * Procesa un evento canónico asegurando idempotencia, tolerancia a fallos con reintentos y desvío a DLQ.
   */
  public ConsumeResult consumeEvent(Object event, String broker, String explicitKey) {
    return consumeEvent(event, broker, explicitKey, null);
  }

  // Ejercicio 31: Correlation ID de HTTP a evento y logs
  public ConsumeResult consumeEvent(Object event, String broker, String explicitKey, String correlationId) {
    String key = explicitKey != null && !explicitKey.trim().isEmpty() ? explicitKey.trim() : "evt-" + UUID.randomUUID();
    String cid = (correlationId != null && !correlationId.trim().isEmpty())
        ? correlationId.trim()
        : UUID.randomUUID().toString();

    org.slf4j.MDC.put("correlationId", cid);
    try {
      if (registry.isAlreadyProcessed(key)) {
        registry.recordDuplicate(key);
        log.info("// Ejercicio 31: [IDEMPOTENCIA] Evento ya procesado previamente (clave={}, correlationId={}). Omitiendo.", key, cid);
        return ConsumeResult.duplicateSkipped(key);
      }

      registry.markProcessing(key, broker, cid);

      for (int attempt = 1; attempt <= maxAttempts; attempt++) {
        try {
          if (failureSimulator != null) {
            failureSimulator.inspectAndMaybeFail(key, attempt);
          }

          ui.displayOrderEvent(event);
          registry.markSuccess(key, attempt, cid);
          return ConsumeResult.success(key, attempt);

        } catch (Throwable t) {
          log.warn("// Ejercicio 31: [RETRY] Intento {}/{} falló para evento {} (correlationId={}): {}",
              attempt, maxAttempts, key, cid, t.getMessage());
          if (attempt < maxAttempts) {
            if (backoffDelayMs > 0) {
              try {
                Thread.sleep(backoffDelayMs);
              } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
              }
            }
          } else {
            DeadLetterRecord dlqRecord = dlqService.sendToDlq(key, event, broker, attempt, t, cid);
            registry.markDlq(key, attempt, t.getMessage(), cid);
            return ConsumeResult.failedDlq(key, attempt, dlqRecord.getDlqId(), t.getMessage());
          }
        }
      }

      return ConsumeResult.failedDlq(key, maxAttempts, null, "Reintentos agotados");
    } finally {
      org.slf4j.MDC.remove("correlationId");
    }
  }

  /**
   * Reejecuta (replay) un mensaje almacenado en la Dead Letter Queue.
   */
  public ConsumeResult replay(String dlqId) {
    Optional<DeadLetterRecord> opt = dlqService.getDeadLetter(dlqId);
    if (!opt.isPresent()) {
      return ConsumeResult.failedDlq("unknown", 0, dlqId, "Registro DLQ no encontrado: " + dlqId);
    }

    DeadLetterRecord dlqRecord = opt.get();
    String key = dlqRecord.getMessageKey();
    Object payload = dlqRecord.getPayload();
    String broker = dlqRecord.getBroker() != null ? dlqRecord.getBroker() : "REPLAY";
    String correlationId = dlqRecord.getCorrelationId();

    // Reiniciar estado de deduplicación para permitir el reintento
    registry.remove(key);

    ConsumeResult result;
    if (payload instanceof TacoOrder) {
      result = consumeOrder((TacoOrder) payload, broker, key, correlationId);
    } else {
      result = consumeEvent(payload, broker, key, correlationId);
    }

    if (result.isSuccess() && result.getStatus() == ProcessStatus.PROCESSED) {
      dlqService.markReplayed(dlqId);
      log.info("// Ejercicio 31: Mensaje DLQ {} reprocesado exitosamente con correlationId={}.", dlqId, correlationId);
    }

    return result;
  }

  /**
   * Resuelve la clave de deduplicación de forma determinista.
   */
  public String resolveKey(TacoOrder order, String explicitKey) {
    if (explicitKey != null && !explicitKey.trim().isEmpty()) {
      return explicitKey.trim();
    }
    if (order != null && order.getId() != null && !order.getId().trim().isEmpty()) {
      return order.getId().trim();
    }
    if (order != null) {
      String raw = (order.getPlacedAt() != null ? String.valueOf(order.getPlacedAt().getTime()) : "0")
          + "|" + (order.getDeliveryName() != null ? order.getDeliveryName() : "")
          + "|" + (order.getDeliveryZip() != null ? order.getDeliveryZip() : "")
          + "|" + (order.getTotal() != null ? order.getTotal().toString() : "0")
          + "|" + (order.getTacos() != null ? order.getTacos().size() : 0);
      return "order-hash-" + sha256Short(raw);
    }
    return "order-" + UUID.randomUUID().toString().substring(0, 8);
  }

  private String sha256Short(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (int i = 0; i < Math.min(6, hash.length); i++) {
        String hex = Integer.toHexString(0xff & hash[i]);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException e) {
      return Integer.toHexString(input.hashCode());
    }
  }

  public int getMaxAttempts() {
    return maxAttempts;
  }

  public void setMaxAttempts(int maxAttempts) {
    this.maxAttempts = maxAttempts;
  }

  public long getBackoffDelayMs() {
    return backoffDelayMs;
  }

  public void setBackoffDelayMs(long backoffDelayMs) {
    this.backoffDelayMs = backoffDelayMs;
  }

  public FailureSimulator getFailureSimulator() {
    return failureSimulator;
  }

  public void setFailureSimulator(FailureSimulator failureSimulator) {
    this.failureSimulator = failureSimulator;
  }

  public IdempotentConsumerRegistry getRegistry() {
    return registry;
  }

  public DeadLetterQueueService getDlqService() {
    return dlqService;
  }

}
