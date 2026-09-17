package tacos.web.api;

import javax.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.data.TacoRepository;
import tacos.web.api.dto.TacoRequest;
import tacos.web.api.dto.TacoResponse;

@RestController
@RequestMapping(path = "/api/tacos", produces = "application/json")
@CrossOrigin(origins="http://localhost:8080")
public class TacoController {
  private TacoRepository tacoRepo;
  // Ejercicio 18: Taco Physics: reglas componibles de diseño
  private TacoPhysicsEngine physicsEngine;

  public TacoController(TacoRepository tacoRepo) {
    this(tacoRepo, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public TacoController(TacoRepository tacoRepo, TacoPhysicsEngine physicsEngine) {
    this.tacoRepo = tacoRepo;
    this.physicsEngine = physicsEngine;
  }

  @GetMapping(params="recent")
  public Flux<TacoResponse> recentTacos() {
    return tacoRepo.findAll()
        .take(12)
        .map(TacoResponse::fromEntity);
  }

  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  @GetMapping
  public Flux<TacoResponse> allTacos(
      @RequestParam(name = "dietary", required = false) tacos.DietaryLabel dietary,
      @RequestParam(name = "excludeAllergen", required = false) tacos.Allergen excludeAllergen,
      @RequestParam(name = "maxSpice", required = false) tacos.SpiceLevel maxSpice) {
    return tacoRepo.findAll()
        .filter(taco -> {
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
        })
        .map(TacoResponse::fromEntity);
  }

  // Ejercicio 8: Separar DTOs de entrada, respuesta y persistencia
  // Ejercicio 18: Taco Physics: reglas componibles de diseño
  @PostMapping(consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<TacoResponse> postTaco(@Valid @RequestBody TacoRequest request) {
    Taco taco = request.toEntity();
    Mono<Taco> validatedTaco = physicsEngine != null 
        ? physicsEngine.validateAndPass(taco) 
        : Mono.just(taco);

    return validatedTaco
        .flatMap(tacoRepo::save)
        .map(TacoResponse::fromEntity);
  }

  // Ejercicio 18: Taco Physics: reglas componibles de diseño
  @PostMapping(path = "/validate-physics", consumes = "application/json")
  public Mono<tacos.physics.PhysicsResult> validatePhysics(@RequestBody TacoRequest request) {
    Taco taco = request.toEntity();
    if (physicsEngine != null) {
      return physicsEngine.evaluate(taco);
    }
    return Mono.just(taco.validatePhysics());
  }

  @GetMapping("/{id}")
  public Mono<TacoResponse> tacoById(@PathVariable("id") String id) {
    return tacoRepo.findById(id)
        .map(TacoResponse::fromEntity);
  }

}
