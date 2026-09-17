package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.UserFavorite;

// Ejercicio 21: Favoritos por usuario sin confiar en userId del cliente
public interface UserFavoriteRepository extends ReactiveCrudRepository<UserFavorite, String> {

  Flux<UserFavorite> findByUserId(String userId);

  Flux<UserFavorite> findByUsername(String username);

  Mono<UserFavorite> findByUserIdAndTacoId(String userId, String tacoId);

  Mono<Boolean> existsByUserIdAndTacoId(String userId, String tacoId);

  Mono<Void> deleteByUserIdAndTacoId(String userId, String tacoId);
}
