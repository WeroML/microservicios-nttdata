package tacos.web.api;

import java.util.List;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.data.IngredientRepository;
import tacos.data.TacoRepository;

// Ejercicio 13: Catálogo con precio, disponibilidad y stock
@RestController
@RequestMapping(path = "/api/catalog", produces = "application/json")
@CrossOrigin(origins = "http://localhost:8080")
public class CatalogController {

  private final IngredientRepository ingredientRepo;
  private final TacoRepository tacoRepo;

  public CatalogController(IngredientRepository ingredientRepo, TacoRepository tacoRepo) {
    this.ingredientRepo = ingredientRepo;
    this.tacoRepo = tacoRepo;
  }

  @GetMapping
  public Mono<CatalogResponse> getCatalog(
      @RequestParam(name = "onlyAvailable", required = false, defaultValue = "false") boolean onlyAvailable) {
    Flux<Ingredient> ingredientsFlux = onlyAvailable
        ? ingredientRepo.findByAvailableTrueAndStockGreaterThan(0)
        : ingredientRepo.findAll();

    Flux<Taco> tacosFlux = onlyAvailable
        ? tacoRepo.findByAvailableTrue()
        : tacoRepo.findAll();

    return Mono.zip(
        ingredientsFlux.collectList(),
        tacosFlux.collectList(),
        CatalogResponse::new
    );
  }

  @GetMapping("/ingredients")
  public Flux<Ingredient> getIngredientsCatalog(
      @RequestParam(name = "onlyAvailable", required = false, defaultValue = "false") boolean onlyAvailable) {
    if (onlyAvailable) {
      return ingredientRepo.findByAvailableTrueAndStockGreaterThan(0);
    }
    return ingredientRepo.findAll();
  }

  @GetMapping("/tacos")
  public Flux<Taco> getTacosCatalog() {
    return tacoRepo.findAll();
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CatalogResponse {
    private List<Ingredient> ingredients;
    private List<Taco> tacos;
  }
}
