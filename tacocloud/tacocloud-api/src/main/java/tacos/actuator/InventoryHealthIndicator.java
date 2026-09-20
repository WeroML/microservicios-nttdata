package tacos.actuator;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.data.IngredientRepository;

// Ejercicio 16: Reservar y liberar inventario sin vender aire
// Ejercicio 32: Métricas y salud que explican el negocio
@Component("inventory")
@Slf4j
public class InventoryHealthIndicator implements ReactiveHealthIndicator {

  private final IngredientRepository ingredientRepo;
  private final BusinessMetricsService metricsService;

  @Value("${tacocloud.inventory.low-stock-threshold:5}")
  private int lowStockThreshold = 5;

  public InventoryHealthIndicator(IngredientRepository ingredientRepo) {
    this(ingredientRepo, null);
  }

  @Autowired
  public InventoryHealthIndicator(IngredientRepository ingredientRepo,
                                  @Autowired(required = false) BusinessMetricsService metricsService) {
    this.ingredientRepo = ingredientRepo;
    this.metricsService = metricsService;
  }

  @Override
  public Mono<Health> health() {
    if (ingredientRepo == null) {
      return Mono.just(Health.unknown().withDetail("reason", "IngredientRepository no disponible").build());
    }

    return ingredientRepo.findAll()
        .collectList()
        .map(ingredients -> {
          int total = ingredients.size();
          List<String> outOfStock = new ArrayList<>();
          List<String> lowStock = new ArrayList<>();
          int healthyCount = 0;

          for (Ingredient ing : ingredients) {
            int stock = ing.getStock() != null ? ing.getStock() : 0;
            if (stock <= 0) {
              outOfStock.add(ing.getId() + " (" + ing.getName() + ")");
              if (metricsService != null) {
                metricsService.recordLowStockAlert(ing.getId());
              }
            } else if (stock <= lowStockThreshold) {
              lowStock.add(ing.getId() + " (" + ing.getName() + ": " + stock + ")");
              if (metricsService != null) {
                metricsService.recordLowStockAlert(ing.getId());
              }
            } else {
              healthyCount++;
            }
          }

          Health.Builder builder;
          if (!outOfStock.isEmpty()) {
            // Reto 16: Evitar vender aire. Si hay insumos agotados, el negocio no puede operar plenamente.
            builder = Health.outOfService()
                .withDetail("reason", "Insumos agotados en inventario impiden despachar órdenes")
                .withDetail("outOfStock", outOfStock);
            log.warn("// Ejercicio 32: Salud de inventario degradada (OUT_OF_SERVICE) por agotamiento de: {}", outOfStock);
          } else {
            builder = Health.up();
          }

          builder.withDetail("totalIngredients", total)
                 .withDetail("healthyStockCount", healthyCount)
                 .withDetail("lowStockCount", lowStock.size())
                 .withDetail("outOfStockCount", outOfStock.size());

          if (!lowStock.isEmpty()) {
            builder.withDetail("lowStock", lowStock);
          }

          return builder.build();
        })
        .onErrorResume(err -> {
          log.error("// Ejercicio 32: Error al consultar salud de inventario", err);
          return Mono.just(Health.down()
              .withException(err)
              .withDetail("error", "Error consultando inventario: " + err.getMessage())
              .build());
        });
  }

  public void setLowStockThreshold(int lowStockThreshold) {
    this.lowStockThreshold = lowStockThreshold;
  }

  public int getLowStockThreshold() {
    return lowStockThreshold;
  }

}
