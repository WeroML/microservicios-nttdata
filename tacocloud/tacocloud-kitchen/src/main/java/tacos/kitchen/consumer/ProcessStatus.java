package tacos.kitchen.consumer;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
public enum ProcessStatus {
  RECEIVED,
  PROCESSING,
  PROCESSED,
  DUPLICATE_SKIPPED,
  FAILED_DLQ
}
