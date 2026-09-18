package tacos.outbox;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.web.api.dto.OutboxMetrics;
import tacos.web.api.dto.OutboxRelaySummary;

// Ejercicio 29: Outbox transaccional para no perder órdenes
public interface TransactionalOutboxService {

  /**
   * Encola un evento generado a partir de una orden de forma atómica en el outbox.
   */
  Mono<OutboxMessage> enqueueOrder(TacoOrder order, OrderEventType eventType);

  /**
   * Encola un evento canónico de orden en el outbox.
   */
  Mono<OutboxMessage> enqueueEvent(OrderEvent event);

  /**
   * Procesa los mensajes pendientes en el outbox y los despacha al broker de mensajería activo.
   */
  Mono<OutboxRelaySummary> processPendingMessages();

  /**
   * Reintenta el despacho de un mensaje específico (por ejemplo, en estado FAILED o DEAD_LETTER).
   */
  Mono<OutboxMessage> retryMessage(String messageId);

  /**
   * Obtiene métricas del estado del outbox.
   */
  Mono<OutboxMetrics> getMetrics();

  /**
   * Lista los mensajes del outbox, opcionalmente filtrados por estado.
   */
  Flux<OutboxMessage> getMessages(OutboxStatus status);

}
