package tacos.outbox;

// Ejercicio 29: Outbox transaccional para no perder órdenes
public enum OutboxStatus {
  PENDING,
  PUBLISHED,
  FAILED,
  DEAD_LETTER
}
