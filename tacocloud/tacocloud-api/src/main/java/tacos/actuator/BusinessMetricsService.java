package tacos.actuator;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.events.OrderEventType;

// Ejercicio 32: Métricas y salud que explican el negocio
@Service
@Slf4j
public class BusinessMetricsService {

  private final MeterRegistry meterRegistry;

  public BusinessMetricsService() {
    this(new SimpleMeterRegistry());
  }

  @Autowired
  public BusinessMetricsService(@Autowired(required = false) MeterRegistry meterRegistry) {
    this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
  }

  // ==========================================
  // RETO 16: Inventario sin vender aire
  // ==========================================

  public void recordInventoryReserved(String item, int quantity, String type) {
    meterRegistry.counter("tacocloud.inventory.reserved",
        "item", item != null ? item : "UNKNOWN",
        "type", type != null ? type : "INGREDIENT")
        .increment(quantity);
    log.debug("// Ejercicio 32: Métrica registrada - Inventario reservado: item={}, qty={}, type={}", item, quantity, type);
  }

  public void recordInventoryReservationFailed(String item, int quantity, String reason) {
    meterRegistry.counter("tacocloud.inventory.failed",
        "item", item != null ? item : "UNKNOWN",
        "reason", reason != null ? reason : "INSUFFICIENT_STOCK")
        .increment(quantity);
    log.warn("// Ejercicio 32: Métrica registrada - Venta evitada por falta de stock: item={}, qty={}, reason={}", item, quantity, reason);
  }

  public void recordInventoryReleased(String item, int quantity, String type) {
    meterRegistry.counter("tacocloud.inventory.released",
        "item", item != null ? item : "UNKNOWN",
        "type", type != null ? type : "INGREDIENT")
        .increment(quantity);
    log.debug("// Ejercicio 32: Métrica registrada - Inventario liberado/restituido: item={}, qty={}, type={}", item, quantity, type);
  }

  public void recordLowStockAlert(String item) {
    meterRegistry.counter("tacocloud.inventory.low_stock.alerts",
        "item", item != null ? item : "UNKNOWN")
        .increment();
  }

  // ==========================================
  // RETO 25: Flujo de estados de órdenes
  // ==========================================

  public void recordOrderPlaced(TacoOrder order) {
    meterRegistry.counter("tacocloud.orders.placed",
        "status", order != null && order.getStatus() != null ? order.getStatus().name() : "CONFIRMED")
        .increment();

    if (order != null && order.getTotal() != null) {
      DistributionSummary.builder("tacocloud.orders.revenue")
          .description("Monto monetario acumulado de órdenes")
          .baseUnit("MXN")
          .register(meterRegistry)
          .record(order.getTotal().doubleValue());
    }
    log.debug("// Ejercicio 32: Métrica registrada - Orden colocada: id={}", order != null ? order.getId() : null);
  }

  public void recordOrderStatusTransition(String orderId, OrderStatus from, OrderStatus to) {
    meterRegistry.counter("tacocloud.orders.status.transitions",
        "from", from != null ? from.name() : "UNKNOWN",
        "to", to != null ? to.name() : "UNKNOWN")
        .increment();
    log.debug("// Ejercicio 32: Métrica registrada - Transición de orden: id={}, from={}, to={}", orderId, from, to);
  }

  public void recordOrderCancelled(String orderId, OrderStatus previousStatus, String cancelledByRole) {
    meterRegistry.counter("tacocloud.orders.cancelled",
        "from", previousStatus != null ? previousStatus.name() : "UNKNOWN",
        "role", cancelledByRole != null ? cancelledByRole : "ROLE_USER")
        .increment();
    log.info("// Ejercicio 32: Métrica registrada - Orden cancelada: id={}, from={}, role={}", orderId, previousStatus, cancelledByRole);
  }

  // ==========================================
  // RETO 29: Outbox transaccional
  // ==========================================

  public void recordOutboxEnqueued(OrderEventType eventType) {
    meterRegistry.counter("tacocloud.outbox.enqueued",
        "eventType", eventType != null ? eventType.name() : "UNKNOWN")
        .increment();
    log.debug("// Ejercicio 32: Métrica registrada - Evento encolado en outbox: type={}", eventType);
  }

  public void recordOutboxDispatched(String broker, boolean success) {
    meterRegistry.counter("tacocloud.outbox.dispatched",
        "broker", broker != null ? broker : "default",
        "status", success ? "SUCCESS" : "FAILURE")
        .increment();
    log.debug("// Ejercicio 32: Métrica registrada - Despacho de outbox: broker={}, success={}", broker, success);
  }

  public void recordOutboxRetried(String trigger) {
    meterRegistry.counter("tacocloud.outbox.retried",
        "trigger", trigger != null ? trigger : "MANUAL")
        .increment();
    log.debug("// Ejercicio 32: Métrica registrada - Reintento de outbox: trigger={}", trigger);
  }

  // ==========================================
  // Resumen y utilidades
  // ==========================================

  public Map<String, Object> getMetricsSummary() {
    Map<String, Object> summary = new HashMap<>();

    double ordersPlaced = sumCounter("tacocloud.orders.placed");
    double ordersCancelled = sumCounter("tacocloud.orders.cancelled");
    double invReserved = sumCounter("tacocloud.inventory.reserved");
    double invFailed = sumCounter("tacocloud.inventory.failed");
    double invReleased = sumCounter("tacocloud.inventory.released");
    double outboxEnqueued = sumCounter("tacocloud.outbox.enqueued");
    double outboxDispatched = sumCounter("tacocloud.outbox.dispatched");
    double outboxRetried = sumCounter("tacocloud.outbox.retried");

    summary.put("ordersPlaced", (long) ordersPlaced);
    summary.put("ordersCancelled", (long) ordersCancelled);
    summary.put("inventoryUnitsReserved", (long) invReserved);
    summary.put("inventoryStockoutPrevented", (long) invFailed);
    summary.put("inventoryUnitsReleased", (long) invReleased);
    summary.put("outboxEnqueued", (long) outboxEnqueued);
    summary.put("outboxDispatched", (long) outboxDispatched);
    summary.put("outboxRetried", (long) outboxRetried);

    return summary;
  }

  private double sumCounter(String name) {
    try {
      return meterRegistry.find(name).counters().stream()
          .mapToDouble(Counter::count)
          .sum();
    } catch (Exception e) {
      return 0.0;
    }
  }

  public MeterRegistry getMeterRegistry() {
    return meterRegistry;
  }

}
