package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.outbox.OutboxMessage;
import tacos.outbox.OutboxStatus;

// Ejercicio 29: Outbox transaccional para no perder órdenes
public interface OutboxRepository extends ReactiveCrudRepository<OutboxMessage, String> {

  Flux<OutboxMessage> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

  Flux<OutboxMessage> findByStatus(OutboxStatus status);

  Flux<OutboxMessage> findByOrderIdOrderByCreatedAtAsc(String orderId);

  Mono<OutboxMessage> findByEventId(String eventId);

  Mono<Long> countByStatus(OutboxStatus status);

}
