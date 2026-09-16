package tacos.data;

import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.web.bind.annotation.CrossOrigin;

import reactor.core.publisher.Flux;
import tacos.Ingredient;

@CrossOrigin(origins="http://localhost:8080")
public interface IngredientRepository 
         extends ReactiveCrudRepository<Ingredient, String> {

  // Ejercicio 13: Catálogo con precio, disponibilidad y stock
  Flux<Ingredient> findByAvailableTrue();
  Flux<Ingredient> findByAvailableTrueAndStockGreaterThan(int stock);
  Flux<Ingredient> findByType(Ingredient.Type type);

}
