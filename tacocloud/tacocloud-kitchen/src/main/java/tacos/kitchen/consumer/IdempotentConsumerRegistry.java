package tacos.kitchen.consumer;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Component
@Slf4j
public class IdempotentConsumerRegistry {

  private final Map<String, ProcessedMessageRecord> store = new ConcurrentHashMap<>();
  private final AtomicLong processedCount = new AtomicLong(0);
  private final AtomicLong duplicateCount = new AtomicLong(0);

  /**
   * Verifica si una clave de mensaje ya ha sido procesada exitosamente con anterioridad.
   */
  public boolean isAlreadyProcessed(String key) {
    if (key == null) {
      return false;
    }
    ProcessedMessageRecord record = store.get(key);
    return record != null && record.getStatus() == ProcessStatus.PROCESSED;
  }

  /**
   * Registra el inicio del procesamiento de un mensaje si no ha sido procesado.
   * Retorna true si se puede proceder, o false si ya fue procesado (duplicado).
   */
  public boolean markProcessing(String key, String broker) {
    if (key == null) {
      return true;
    }
    if (isAlreadyProcessed(key)) {
      return false;
    }
    store.compute(key, (k, existing) -> {
      if (existing == null) {
        return ProcessedMessageRecord.builder()
            .messageKey(key)
            .broker(broker)
            .status(ProcessStatus.PROCESSING)
            .firstReceivedAt(new Date())
            .attempts(1)
            .build();
      }
      existing.setStatus(ProcessStatus.PROCESSING);
      existing.setAttempts(existing.getAttempts() + 1);
      return existing;
    });
    return true;
  }

  /**
   * Marca el mensaje como procesado exitosamente en el registro de idempotencia.
   */
  public void markSuccess(String key, int attempts) {
    if (key == null) {
      return;
    }
    store.compute(key, (k, existing) -> {
      Date now = new Date();
      if (existing == null) {
        return ProcessedMessageRecord.builder()
            .messageKey(key)
            .status(ProcessStatus.PROCESSED)
            .firstReceivedAt(now)
            .processedAt(now)
            .attempts(attempts)
            .build();
      }
      existing.setStatus(ProcessStatus.PROCESSED);
      existing.setProcessedAt(now);
      existing.setAttempts(attempts);
      return existing;
    });
    processedCount.incrementAndGet();
  }

  /**
   * Marca el mensaje como fallido y enrutado a DLQ.
   */
  public void markDlq(String key, int attempts, String error) {
    if (key == null) {
      return;
    }
    store.compute(key, (k, existing) -> {
      if (existing == null) {
        return ProcessedMessageRecord.builder()
            .messageKey(key)
            .status(ProcessStatus.FAILED_DLQ)
            .firstReceivedAt(new Date())
            .attempts(attempts)
            .lastError(error)
            .build();
      }
      existing.setStatus(ProcessStatus.FAILED_DLQ);
      existing.setAttempts(attempts);
      existing.setLastError(error);
      return existing;
    });
  }

  /**
   * Registra una ocurrencia de mensaje duplicado omitido.
   */
  public void recordDuplicate(String key) {
    duplicateCount.incrementAndGet();
    log.info("// Ejercicio 30: Consumidor idempotente: Mensaje duplicado omitido para clave={}", key);
  }

  public long getProcessedCount() {
    return processedCount.get();
  }

  public long getDuplicateCount() {
    return duplicateCount.get();
  }

  public Optional<ProcessedMessageRecord> getRecord(String key) {
    if (key == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(store.get(key));
  }

  public List<ProcessedMessageRecord> getAllRecords() {
    return new ArrayList<>(store.values());
  }

  public void remove(String key) {
    if (key != null) {
      store.remove(key);
    }
  }

  public void clear() {
    store.clear();
    processedCount.set(0);
    duplicateCount.set(0);
  }

}
