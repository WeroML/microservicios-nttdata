package tacos.kitchen.consumer;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
public enum DeadLetterStatus {
  DEAD_LETTER,
  REPLAYING,
  REPLAYED,
  DISCARDED
}
