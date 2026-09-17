package tacos.web.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.data.IngredientRepository;
import tacos.physics.PhysicsResult;
import tacos.physics.TacoPhysicsRule;
import tacos.physics.TacoPhysicsRules;

// Ejercicio 18: Taco Physics: reglas componibles de diseño
@Service
public class TacoPhysicsEngine {

  private final IngredientRepository ingredientRepo;
  private TacoPhysicsRule ruleChain;

  public TacoPhysicsEngine() {
    this(null, TacoPhysicsRules.standard());
  }

  @Autowired
  public TacoPhysicsEngine(IngredientRepository ingredientRepo) {
    this(ingredientRepo, TacoPhysicsRules.standard());
  }

  public TacoPhysicsEngine(IngredientRepository ingredientRepo, TacoPhysicsRule ruleChain) {
    this.ingredientRepo = ingredientRepo;
    this.ruleChain = ruleChain != null ? ruleChain : TacoPhysicsRules.standard();
  }

  public void setRuleChain(TacoPhysicsRule ruleChain) {
    this.ruleChain = ruleChain != null ? ruleChain : TacoPhysicsRules.standard();
  }

  public TacoPhysicsRule getRuleChain() {
    return ruleChain;
  }

  /**
   * Hidrata los ingredientes del taco consultando el repositorio si carecen de 'Type'
   * y evalúa las leyes componibles de la física del taco.
   */
  public Mono<PhysicsResult> evaluate(Taco taco) {
    if (taco == null) {
      return Mono.just(PhysicsResult.invalid("Taco is null."));
    }

    return hydrateIngredients(taco)
        .map(hydratedTaco -> ruleChain.validate(hydratedTaco));
  }

  /**
   * Valida la física del taco; si viola las reglas, emite 400 Bad Request.
   */
  public Mono<Taco> validateAndPass(Taco taco) {
    return evaluate(taco)
        .flatMap(result -> {
          if (!result.isValid()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getReason()));
          }
          return Mono.just(taco);
        });
  }

  private Mono<Taco> hydrateIngredients(Taco taco) {
    if (taco.getIngredients() == null || taco.getIngredients().isEmpty() || ingredientRepo == null) {
      return Mono.just(taco);
    }

    boolean needsHydration = false;
    for (Ingredient ing : taco.getIngredients()) {
      if (ing != null && (ing.getType() == null || ing.getName() == null)) {
        needsHydration = true;
        break;
      }
    }

    if (!needsHydration) {
      return Mono.just(taco);
    }

    return Flux.fromIterable(taco.getIngredients())
        .flatMap(ing -> {
          if (ing.getId() != null) {
            return ingredientRepo.findById(ing.getId())
                .defaultIfEmpty(ing);
          }
          return Mono.just(ing);
        })
        .collectList()
        .map(hydratedIngredients -> {
          taco.setIngredients(hydratedIngredients);
          return taco;
        });
  }
}
