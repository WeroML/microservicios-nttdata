package tacos.kitchen.consumer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Component
@Slf4j
public class DeadLetterQueueService {

  private final Map<String, DeadLetterRecord> dlqStore = new ConcurrentHashMap<>();
  private final AtomicInteger dlqCount = new AtomicInteger(0);

  /**
   * Enruta un mensaje fallido a la Dead Letter Queue tras agotar los reintentos permitidos.
   */
  public DeadLetterRecord sendToDlq(String key, Object payload, String broker, int attempts, Throwable error) {
    String dlqId = UUID.randomUUID().toString();

    String stackTrace = null;
    if (error != null) {
      StringWriter sw = new StringWriter();
      PrintWriter pw = new PrintWriter(sw);
      error.printStackTrace(pw);
      stackTrace = sw.toString();
    }

    DeadLetterRecord record = DeadLetterRecord.builder()
        .dlqId(dlqId)
        .messageKey(key)
        .broker(broker)
        .payload(payload)
        .payloadType(payload != null ? payload.getClass().getSimpleName() : "UNKNOWN")
        .attempts(attempts)
        .errorMessage(error != null ? error.getMessage() : "Error desconocido")
        .exceptionClass(error != null ? error.getClass().getName() : null)
        .stackTrace(stackTrace)
        .failedAt(new Date())
        .status(DeadLetterStatus.DEAD_LETTER)
        .build();

    dlqStore.put(dlqId, record);
    dlqCount.incrementAndGet();

    log.warn("// Ejercicio 30: Mensaje venenoso enrutado a DLQ: dlqId={}, key={}, broker={}, attempts={}, error={}",
        dlqId, key, broker, attempts, record.getErrorMessage());

    return record;
  }

  /**
   * Consulta todos los mensajes registrados en la Dead Letter Queue.
   */
  public List<DeadLetterRecord> getAllDeadLetters() {
    return new ArrayList<>(dlqStore.values());
  }

  /**
   * Obtiene un registro de la DLQ por su ID único.
   */
  public Optional<DeadLetterRecord> getDeadLetter(String dlqId) {
    if (dlqId == null) {
      return Optional.empty();
    }
    return Optional.ofNullable(dlqStore.get(dlqId));
  }

  /**
   * Marca un mensaje como descartado en la DLQ.
   */
  public boolean discardDeadLetter(String dlqId) {
    DeadLetterRecord record = dlqStore.get(dlqId);
    if (record != null) {
      record.setStatus(DeadLetterStatus.DISCARDED);
      return true;
    }
    return false;
  }

  /**
   * Actualiza el estado del registro en la DLQ cuando ha sido reprocesado exitosamente.
   */
  public void markReplayed(String dlqId) {
    DeadLetterRecord record = dlqStore.get(dlqId);
    if (record != null) {
      record.setStatus(DeadLetterStatus.REPLAYED);
      record.setReplayedAt(new Date());
    }
  }

  public int getDlqCount() {
    return dlqCount.get();
  }

  public void clear() {
    dlqStore.clear();
    dlqCount.set(0);
  }

}
