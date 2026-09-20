package tacos.actuator;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tacos.outbox.OrderOutboxService;
import tacos.web.api.dto.OutboxMetrics;

// Ejercicio 29: Outbox transaccional para no perder órdenes
// Ejercicio 32: Métricas y salud que explican el negocio
@Component("outbox")
@Slf4j
public class OutboxHealthIndicator implements ReactiveHealthIndicator {

  private final OrderOutboxService outboxService;

  @Value("${tacocloud.outbox.max-pending-threshold:50}")
  private int maxPendingThreshold = 50;

  @Autowired
  public OutboxHealthIndicator(OrderOutboxService outboxService) {
    this.outboxService = outboxService;
  }

  @Override
  public Mono<Health> health() {
    if (outboxService == null) {
      return Mono.just(Health.unknown().withDetail("reason", "OrderOutboxService no disponible").build());
    }

    return outboxService.getMetrics()
        .map(this::evaluateOutboxHealth)
        .onErrorResume(err -> {
          log.error("// Ejercicio 32: Error consultando salud de outbox", err);
          return Mono.just(Health.down()
              .withException(err)
              .withDetail("error", "Error consultando outbox: " + err.getMessage())
              .build());
        });
  }

  private Health evaluateOutboxHealth(OutboxMetrics metrics) {
    long pending = metrics.getPendingCount();
    long published = metrics.getPublishedCount();
    long failed = metrics.getFailedCount();
    long deadLetter = metrics.getDeadLetterCount();
    long total = metrics.getTotalCount();
    String activeBroker = metrics.getActiveBroker();

    Health.Builder builder;
    if (deadLetter > 0) {
      // Reto 29: Eventos no pudieron entregarse tras agotar reintentos
      builder = Health.down()
          .withDetail("reason", "Se detectaron " + deadLetter + " mensajes en cola muerta (DEAD_LETTER) del Outbox");
      log.warn("// Ejercicio 32: Salud de outbox en DOWN por mensajes en DEAD_LETTER: {}", deadLetter);
    } else if (failed > 0) {
      // Reto 29: Fallos de despacho activos hacia el broker
      builder = Health.down()
          .withDetail("reason", "Se detectaron " + failed + " mensajes en estado FAILED pendientes de reintento hacia broker: " + activeBroker);
      log.warn("// Ejercicio 32: Salud de outbox en DOWN por despachos fallidos: {}", failed);
    } else if (pending > maxPendingThreshold) {
      // Reto 29: Saturación de cola o retraso en relay
      builder = Health.outOfService()
          .withDetail("reason", "Saturación en cola de outbox: " + pending + " mensajes pendientes superan el umbral (" + maxPendingThreshold + ")");
      log.warn("// Ejercicio 32: Salud de outbox OUT_OF_SERVICE por saturación de pendientes: {}", pending);
    } else {
      builder = Health.up();
    }

    builder.withDetail("totalCount", total)
           .withDetail("pendingCount", pending)
           .withDetail("publishedCount", published)
           .withDetail("failedCount", failed)
           .withDetail("deadLetterCount", deadLetter)
           .withDetail("activeBroker", activeBroker)
           .withDetail("backlogHealthy", deadLetter == 0 && failed == 0 && pending <= maxPendingThreshold);

    return builder.build();
  }

  public void setMaxPendingThreshold(int maxPendingThreshold) {
    this.maxPendingThreshold = maxPendingThreshold;
  }

  public int getMaxPendingThreshold() {
    return maxPendingThreshold;
  }

}
