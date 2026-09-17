package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;

import reactor.core.publisher.Flux;
import tacos.Taco;


public interface TacoRepository 
         extends ReactiveCrudRepository<Taco, String> {

  // Ejercicio 13: Catálogo con precio, disponibilidad y stock
  Flux<Taco> findByAvailableTrue();
  Flux<Taco> findByAvailableTrueAndStockGreaterThan(int stock);

  // Ejercicio 19: Buscar, filtrar, ordenar y paginar tacos
  Flux<Taco> findByNameContainingIgnoreCase(String name);
}
