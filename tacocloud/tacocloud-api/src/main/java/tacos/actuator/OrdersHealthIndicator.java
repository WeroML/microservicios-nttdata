package tacos.actuator;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.data.OrderRepository;

// Ejercicio 25: Flujo de estados de una orden
// Ejercicio 32: Métricas y salud que explican el negocio
@Component("orders")
@Slf4j
public class OrdersHealthIndicator implements ReactiveHealthIndicator {

  private final OrderRepository orderRepo;

  @Value("${tacocloud.orders.max-active-threshold:50}")
  private int maxActiveOrdersThreshold = 50;

  @Value("${tacocloud.orders.max-cancellation-rate:0.40}")
  private double maxCancellationRate = 0.40;

  @Autowired
  public OrdersHealthIndicator(OrderRepository orderRepo) {
    this.orderRepo = orderRepo;
  }

  @Override
  public Mono<Health> health() {
    if (orderRepo == null) {
      return Mono.just(Health.unknown().withDetail("reason", "OrderRepository no disponible").build());
    }

    return orderRepo.findAll()
        .collectList()
        .map(this::evaluateOrdersHealth)
        .onErrorResume(err -> {
          log.error("// Ejercicio 32: Error consultando salud de órdenes", err);
          return Mono.just(Health.down()
              .withException(err)
              .withDetail("error", "Error consultando base de órdenes: " + err.getMessage())
              .build());
        });
  }

  private Health evaluateOrdersHealth(List<TacoOrder> orders) {
    int total = orders.size();
    if (total == 0) {
      return Health.up()
          .withDetail("totalOrders", 0)
          .withDetail("activeOrdersCount", 0)
          .withDetail("cancellationRate", "0.0%")
          .withDetail("message", "Operación lista sin órdenes registradas aún")
          .build();
    }

    Map<OrderStatus, Long> countsByStatus = new EnumMap<>(OrderStatus.class);
    for (OrderStatus st : OrderStatus.values()) {
      countsByStatus.put(st, 0L);
    }

    long activeCount = 0;
    long deliveredCount = 0;
    long cancelledCount = 0;

    for (TacoOrder o : orders) {
      OrderStatus status = o.getStatus() != null ? o.getStatus() : OrderStatus.CONFIRMED;
      countsByStatus.put(status, countsByStatus.get(status) + 1);

      if (status == OrderStatus.DELIVERED) {
        deliveredCount++;
      } else if (status == OrderStatus.CANCELLED) {
        cancelledCount++;
      } else {
        activeCount++;
      }
    }

    double cancellationRate = (double) cancelledCount / total;

    Health.Builder builder;
    if (total >= 5 && cancellationRate >= maxCancellationRate) {
      // Reto 25: Alerta comercial por cancelaciones excesivas
      builder = Health.down()
          .withDetail("reason", "Tasa crítica de cancelaciones (" + String.format("%.1f%%", cancellationRate * 100) + ") excede el umbral tolerado");
      log.warn("// Ejercicio 32: Salud de órdenes en DOWN por alta tasa de cancelación: {}%", cancellationRate * 100);
    } else if (activeCount > maxActiveOrdersThreshold) {
      // Reto 25 & 26: Cuello de botella en cocina
      builder = Health.outOfService()
          .withDetail("reason", "Capacidad operativa de cocina sobrepasada: " + activeCount + " órdenes en proceso");
      log.warn("// Ejercicio 32: Salud de órdenes OUT_OF_SERVICE por saturación de cocina: {}", activeCount);
    } else {
      builder = Health.up();
    }

    builder.withDetail("totalOrders", total)
           .withDetail("activeOrdersCount", activeCount)
           .withDetail("deliveredOrdersCount", deliveredCount)
           .withDetail("cancelledOrdersCount", cancelledCount)
           .withDetail("cancellationRate", String.format("%.1f%%", cancellationRate * 100))
           .withDetail("countsByStatus", countsByStatus);

    return builder.build();
  }

  public void setMaxActiveOrdersThreshold(int maxActiveOrdersThreshold) {
    this.maxActiveOrdersThreshold = maxActiveOrdersThreshold;
  }

  public void setMaxCancellationRate(double maxCancellationRate) {
    this.maxCancellationRate = maxCancellationRate;
  }

}
