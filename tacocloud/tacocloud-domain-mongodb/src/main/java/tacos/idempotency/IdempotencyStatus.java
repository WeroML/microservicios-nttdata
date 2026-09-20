package tacos.idempotency;

// Ejercicio 34: Idempotency-Key en creación de órdenes
public enum IdempotencyStatus {
  IN_PROGRESS,
  COMPLETED,
  FAILED
}
