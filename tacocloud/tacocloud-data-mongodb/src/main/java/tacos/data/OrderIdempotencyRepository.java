package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Mono;
import tacos.idempotency.OrderIdempotencyRecord;

// Ejercicio 34: Idempotency-Key en creación de órdenes
public interface OrderIdempotencyRepository extends ReactiveCrudRepository<OrderIdempotencyRecord, String> {

  Mono<OrderIdempotencyRecord> findByKey(String key);

  Mono<OrderIdempotencyRecord> findByKeyAndUserId(String key, String userId);

  Mono<Void> deleteByKey(String key);
}
