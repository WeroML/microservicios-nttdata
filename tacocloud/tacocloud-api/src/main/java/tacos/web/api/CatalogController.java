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
// Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
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
      @RequestParam(name = "onlyAvailable", required = false, defaultValue = "false") boolean onlyAvailable,
      @RequestParam(name = "dietary", required = false) tacos.DietaryLabel dietary,
      @RequestParam(name = "excludeAllergen", required = false) tacos.Allergen excludeAllergen,
      @RequestParam(name = "maxSpice", required = false) tacos.SpiceLevel maxSpice) {
    Flux<Ingredient> ingredientsFlux = (onlyAvailable
        ? ingredientRepo.findByAvailableTrueAndStockGreaterThan(0)
        : ingredientRepo.findAll())
        .filter(ing -> filterIngredient(ing, dietary, excludeAllergen, maxSpice));

    Flux<Taco> tacosFlux = (onlyAvailable
        ? tacoRepo.findByAvailableTrue()
        : tacoRepo.findAll())
        .doOnNext(Taco::updateDietaryAndAllergenInfo)
        .filter(taco -> filterTaco(taco, dietary, excludeAllergen, maxSpice));

    return Mono.zip(
        ingredientsFlux.collectList(),
        tacosFlux.collectList(),
        CatalogResponse::new
    );
  }

  @GetMapping("/ingredients")
  public Flux<Ingredient> getIngredientsCatalog(
      @RequestParam(name = "onlyAvailable", required = false, defaultValue = "false") boolean onlyAvailable,
      @RequestParam(name = "dietary", required = false) tacos.DietaryLabel dietary,
      @RequestParam(name = "excludeAllergen", required = false) tacos.Allergen excludeAllergen,
      @RequestParam(name = "maxSpice", required = false) tacos.SpiceLevel maxSpice) {
    Flux<Ingredient> flux = onlyAvailable
        ? ingredientRepo.findByAvailableTrueAndStockGreaterThan(0)
        : ingredientRepo.findAll();
    return flux.filter(ing -> filterIngredient(ing, dietary, excludeAllergen, maxSpice));
  }

  @GetMapping("/tacos")
  public Flux<Taco> getTacosCatalog(
      @RequestParam(name = "onlyAvailable", required = false, defaultValue = "false") boolean onlyAvailable,
      @RequestParam(name = "dietary", required = false) tacos.DietaryLabel dietary,
      @RequestParam(name = "excludeAllergen", required = false) tacos.Allergen excludeAllergen,
      @RequestParam(name = "maxSpice", required = false) tacos.SpiceLevel maxSpice) {
    Flux<Taco> flux = onlyAvailable
        ? tacoRepo.findByAvailableTrue()
        : tacoRepo.findAll();
    return flux
        .doOnNext(Taco::updateDietaryAndAllergenInfo)
        .filter(taco -> filterTaco(taco, dietary, excludeAllergen, maxSpice));
  }

  private boolean filterIngredient(Ingredient ing, tacos.DietaryLabel dietary, tacos.Allergen excludeAllergen, tacos.SpiceLevel maxSpice) {
    if (dietary != null && !ing.hasDietaryLabel(dietary)) {
      return false;
    }
    if (excludeAllergen != null && ing.hasAllergen(excludeAllergen)) {
      return false;
    }
    if (maxSpice != null && ing.getSpiceLevel() != null && ing.getSpiceLevel().getLevel() > maxSpice.getLevel()) {
      return false;
    }
    return true;
  }

  private boolean filterTaco(Taco taco, tacos.DietaryLabel dietary, tacos.Allergen excludeAllergen, tacos.SpiceLevel maxSpice) {
    if (dietary != null && !taco.hasDietaryLabel(dietary)) {
      return false;
    }
    if (excludeAllergen != null && taco.hasAllergen(excludeAllergen)) {
      return false;
    }
    if (maxSpice != null && taco.computeSpiceLevel().getLevel() > maxSpice.getLevel()) {
      return false;
    }
    return true;
  }

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class CatalogResponse {
    private List<Ingredient> ingredients;
    private List<Taco> tacos;
  }
}
