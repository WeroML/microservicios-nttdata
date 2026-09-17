package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.TacoRating;

// Ejercicio 22: Calificaciones y ranking de tacos
public interface TacoRatingRepository extends ReactiveCrudRepository<TacoRating, String> {

  Flux<TacoRating> findByTacoId(String tacoId);

  Flux<TacoRating> findByUserId(String userId);

  Mono<TacoRating> findByTacoIdAndUserId(String tacoId, String userId);

  Mono<Void> deleteByTacoIdAndUserId(String tacoId, String userId);

  Mono<Long> countByTacoId(String tacoId);
}
