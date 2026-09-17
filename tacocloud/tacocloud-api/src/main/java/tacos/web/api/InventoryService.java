package tacos.web.api;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.data.IngredientRepository;
import tacos.data.TacoRepository;

// Ejercicio 16: Reservar y liberar inventario sin vender aire
@Service
public class InventoryService {

  private final IngredientRepository ingredientRepo;
  private final TacoRepository tacoRepo;

  public InventoryService(IngredientRepository ingredientRepo, TacoRepository tacoRepo) {
    this.ingredientRepo = ingredientRepo;
    this.tacoRepo = tacoRepo;
  }

  /**
   * Reserva inventario para la orden.
   * Valida atómicamente que todos los insumos cuenten con stock disponible suficiente.
   * Si alguno falta, rechaza con 409 Conflict evitando "vender aire".
   */
  public Mono<TacoOrder> reserveInventory(TacoOrder order) {
    if (order == null || order.getTacos() == null || order.getTacos().isEmpty()) {
      return Mono.justOrEmpty(order);
    }

    if (ingredientRepo == null) {
      return Mono.just(order);
    }

    Map<String, Integer> ingredientQuantities = aggregateIngredientQuantities(order);
    Map<String, Integer> tacoQuantities = aggregateTacoQuantities(order);

    if (ingredientQuantities.isEmpty() && tacoQuantities.isEmpty()) {
      return Mono.just(order);
    }

    return Flux.fromIterable(ingredientQuantities.entrySet())
        .flatMap(entry -> 
            ingredientRepo.findById(entry.getKey())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Ingrediente no encontrado: " + entry.getKey())))
                .map(ingredient -> new AbstractMap.SimpleEntry<>(ingredient, entry.getValue()))
        )
        .collectList()
        .flatMap(ingredientEntries -> {
          // Fase 1: Validación estricta previa sin mutar nada en la BD
          for (Map.Entry<Ingredient, Integer> entry : ingredientEntries) {
            Ingredient ingredient = entry.getKey();
            int requiredQty = entry.getValue();
            if (!ingredient.hasSufficientStock(requiredQty)) {
              return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                  "Stock insuficiente para el ingrediente '" + ingredient.getName() + "'. Requerido: " + requiredQty + ", disponible: " + (ingredient.getStock() != null ? ingredient.getStock() : 0)));
            }
          }

          // Fase 2: Aplicar descuento y persistir
          return Flux.fromIterable(ingredientEntries)
              .flatMap(entry -> {
                Ingredient ingredient = entry.getKey();
                ingredient.decrementStock(entry.getValue());
                return ingredientRepo.save(ingredient);
              })
              .then(reserveTacoStock(tacoQuantities))
              .thenReturn(order);
        });
  }

  private Mono<Void> reserveTacoStock(Map<String, Integer> tacoQuantities) {
    if (tacoRepo == null || tacoQuantities == null || tacoQuantities.isEmpty()) {
      return Mono.empty();
    }

    return Flux.fromIterable(tacoQuantities.entrySet())
        .flatMap(entry -> 
            tacoRepo.findById(entry.getKey())
                .map(taco -> new AbstractMap.SimpleEntry<>(taco, entry.getValue()))
        )
        .collectList()
        .flatMap(tacoEntries -> {
          for (Map.Entry<Taco, Integer> entry : tacoEntries) {
            Taco taco = entry.getKey();
            int requiredQty = entry.getValue();
            if (taco.getStock() != null && !taco.hasSufficientStock(requiredQty)) {
              return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                  "Stock insuficiente para el taco '" + taco.getName() + "'. Requerido: " + requiredQty + ", disponible: " + taco.getStock()));
            }
          }

          return Flux.fromIterable(tacoEntries)
              .flatMap(entry -> {
                Taco taco = entry.getKey();
                taco.decrementStock(entry.getValue());
                return tacoRepo.save(taco);
              })
              .then();
        });
  }

  /**
   * Libera y restituye el inventario cuando una orden es cancelada o eliminada.
   */
  public Mono<Void> releaseInventory(TacoOrder order) {
    if (order == null || order.getTacos() == null || order.getTacos().isEmpty()) {
      return Mono.empty();
    }

    if (ingredientRepo == null) {
      return Mono.empty();
    }

    Map<String, Integer> ingredientQuantities = aggregateIngredientQuantities(order);
    Map<String, Integer> tacoQuantities = aggregateTacoQuantities(order);

    Mono<Void> releaseIngredients = Flux.fromIterable(ingredientQuantities.entrySet())
        .flatMap(entry -> 
            ingredientRepo.findById(entry.getKey())
                .flatMap(ingredient -> {
                  ingredient.incrementStock(entry.getValue());
                  return ingredientRepo.save(ingredient);
                })
        )
        .then();

    Mono<Void> releaseTacos = Mono.empty();
    if (tacoRepo != null && !tacoQuantities.isEmpty()) {
      releaseTacos = Flux.fromIterable(tacoQuantities.entrySet())
          .flatMap(entry -> 
              tacoRepo.findById(entry.getKey())
                  .flatMap(taco -> {
                    taco.incrementStock(entry.getValue());
                    return tacoRepo.save(taco);
                  })
          )
          .then();
    }

    return releaseIngredients.then(releaseTacos);
  }

  private Map<String, Integer> aggregateIngredientQuantities(TacoOrder order) {
    Map<String, Integer> map = new HashMap<>();
    if (order.getTacos() != null) {
      for (Taco taco : order.getTacos()) {
        int tacoQty = (taco.getQuantity() != null && taco.getQuantity() > 0) ? taco.getQuantity() : 1;
        if (taco.getIngredients() != null) {
          for (Ingredient ing : taco.getIngredients()) {
            if (ing != null && ing.getId() != null) {
              map.merge(ing.getId(), tacoQty, Integer::sum);
            }
          }
        }
      }
    }
    return map;
  }

  private Map<String, Integer> aggregateTacoQuantities(TacoOrder order) {
    Map<String, Integer> map = new HashMap<>();
    if (order.getTacos() != null) {
      for (Taco taco : order.getTacos()) {
        if (taco.getId() != null) {
          int tacoQty = (taco.getQuantity() != null && taco.getQuantity() > 0) ? taco.getQuantity() : 1;
          map.merge(taco.getId(), tacoQty, Integer::sum);
        }
      }
    }
    return map;
  }
}
