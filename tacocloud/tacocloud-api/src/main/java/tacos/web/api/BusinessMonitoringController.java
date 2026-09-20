package tacos.web.api;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tacos.actuator.BusinessMetricsService;
import tacos.actuator.InventoryHealthIndicator;
import tacos.actuator.OrdersHealthIndicator;
import tacos.actuator.OutboxHealthIndicator;

// Ejercicio 32: Métricas y salud que explican el negocio
@RestController
@RequestMapping(path = "/api/business", produces = "application/json")
@Slf4j
public class BusinessMonitoringController {

  private final InventoryHealthIndicator inventoryHealth;
  private final OrdersHealthIndicator ordersHealth;
  private final OutboxHealthIndicator outboxHealth;
  private final BusinessMetricsService metricsService;

  @Autowired
  public BusinessMonitoringController(
      InventoryHealthIndicator inventoryHealth,
      OrdersHealthIndicator ordersHealth,
      OutboxHealthIndicator outboxHealth,
      BusinessMetricsService metricsService) {
    this.inventoryHealth = inventoryHealth;
    this.ordersHealth = ordersHealth;
    this.outboxHealth = outboxHealth;
    this.metricsService = metricsService;
  }

  @GetMapping(path = "/health-and-metrics")
  public Mono<Map<String, Object>> getBusinessHealthAndMetrics() {
    Mono<Health> invMono = inventoryHealth != null ? inventoryHealth.health() : Mono.just(Health.unknown().build());
    Mono<Health> ordMono = ordersHealth != null ? ordersHealth.health() : Mono.just(Health.unknown().build());
    Mono<Health> outMono = outboxHealth != null ? outboxHealth.health() : Mono.just(Health.unknown().build());

    return Mono.zip(invMono, ordMono, outMono)
        .map(tuple -> {
          Health inv = tuple.getT1();
          Health ord = tuple.getT2();
          Health out = tuple.getT3();

          Map<String, Object> result = new LinkedHashMap<>();
          result.put("timestamp", new Date());

          // Estado global del negocio: Si alguno es DOWN o OUT_OF_SERVICE, el negocio está comprometido
          Status overallStatus = Status.UP;
          if (inv.getStatus() == Status.DOWN || ord.getStatus() == Status.DOWN || out.getStatus() == Status.DOWN) {
            overallStatus = Status.DOWN;
          } else if (inv.getStatus() == Status.OUT_OF_SERVICE || ord.getStatus() == Status.OUT_OF_SERVICE || out.getStatus() == Status.OUT_OF_SERVICE) {
            overallStatus = Status.OUT_OF_SERVICE;
          }
          result.put("status", overallStatus.getCode());

          Map<String, Object> components = new LinkedHashMap<>();
          components.put("inventory", formatHealth(inv));
          components.put("orders", formatHealth(ord));
          components.put("outbox", formatHealth(out));
          result.put("components", components);

          if (metricsService != null) {
            result.put("metricsSummary", metricsService.getMetricsSummary());
          }

          log.debug("// Ejercicio 32: Consulta de salud de negocio consolidada. Estado general: {}", overallStatus);
          return result;
        });
  }

  private Map<String, Object> formatHealth(Health h) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("status", h.getStatus().getCode());
    if (h.getDetails() != null && !h.getDetails().isEmpty()) {
      map.put("details", h.getDetails());
    }
    return map;
  }

}
