package tacos.data;

import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.User;

public interface OrderRepository 
         extends ReactiveCrudRepository<TacoOrder, String> {

  Flux<TacoOrder> findByUserOrderByPlacedAtDesc(
          User user, Pageable pageable);

  // Ejercicio 23: Historial paginado y privado de órdenes
  Flux<TacoOrder> findByUserOrderByPlacedAtDesc(User user);

  Flux<TacoOrder> findByUser(User user);

  Mono<Long> countByUser(User user);

  // Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
  Flux<TacoOrder> findByStatusOrderByPlacedAtAsc(TacoOrder.OrderStatus status);

  Flux<TacoOrder> findByStatusInOrderByPlacedAtAsc(java.util.Collection<TacoOrder.OrderStatus> statuses);

  Mono<Long> countByStatus(TacoOrder.OrderStatus status);
}
