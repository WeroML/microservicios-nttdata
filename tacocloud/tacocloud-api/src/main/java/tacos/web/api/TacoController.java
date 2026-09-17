package tacos.web.api;

import java.math.BigDecimal;
import java.time.LocalDate;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
import tacos.Allergen;
import tacos.DietaryLabel;
import tacos.SpiceLevel;
import tacos.Taco;
import tacos.data.TacoRepository;
import tacos.physics.PhysicsResult;
import tacos.web.api.dto.PagedResponse;
import tacos.web.api.dto.TacoOfTheDayResponse;
import tacos.web.api.dto.TacoRequest;
import tacos.web.api.dto.TacoResponse;
import tacos.web.api.dto.TacoSearchCriteria;

@RestController
@RequestMapping(path = "/api/tacos", produces = "application/json")
@CrossOrigin(origins="http://localhost:8080")
public class TacoController {
  private TacoRepository tacoRepo;
  // Ejercicio 18: Taco Physics: reglas componibles de diseño
  private TacoPhysicsEngine physicsEngine;
  // Ejercicio 19: Buscar, filtrar, ordenar y paginar tacos
  private TacoQueryService queryService;
  // Ejercicio 20: Taco del día determinista y comprobable
  private TacoOfTheDayService tacoOfTheDayService;

  public TacoController(TacoRepository tacoRepo) {
    this(tacoRepo, null, new TacoQueryService(tacoRepo), new TacoOfTheDayService(tacoRepo));
  }

  public TacoController(TacoRepository tacoRepo, TacoPhysicsEngine physicsEngine) {
    this(tacoRepo, physicsEngine, new TacoQueryService(tacoRepo), new TacoOfTheDayService(tacoRepo));
  }

  public TacoController(TacoRepository tacoRepo, TacoPhysicsEngine physicsEngine, TacoQueryService queryService) {
    this(tacoRepo, physicsEngine, queryService, new TacoOfTheDayService(tacoRepo));
  }

  @Autowired
  public TacoController(TacoRepository tacoRepo,
                        @Autowired(required = false) TacoPhysicsEngine physicsEngine,
                        @Autowired(required = false) TacoQueryService queryService,
                        @Autowired(required = false) TacoOfTheDayService tacoOfTheDayService) {
    this.tacoRepo = tacoRepo;
    this.physicsEngine = physicsEngine;
    this.queryService = (queryService != null) ? queryService : new TacoQueryService(tacoRepo);
    this.tacoOfTheDayService = (tacoOfTheDayService != null) ? tacoOfTheDayService : new TacoOfTheDayService(tacoRepo);
  }

  @GetMapping(params="recent")
  public Flux<TacoResponse> recentTacos() {
    return tacoRepo.findAll()
        .take(12)
        .map(TacoResponse::fromEntity);
  }

  // Ejercicio 20: Taco del día determinista y comprobable
  @GetMapping(path = {"/taco-of-the-day", "/daily"})
  public Mono<ResponseEntity<TacoOfTheDayResponse>> tacoOfTheDay(
      @RequestParam(name = "date", required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
      LocalDate date) {
    return tacoOfTheDayService.getTacoOfTheDay(date)
        .map(ResponseEntity::ok)
        .defaultIfEmpty(ResponseEntity.notFound().build());
  }

  // Ejercicio 17: Etiquetas dietarias, alérgenos y nivel de picante
  // Ejercicio 19: Buscar, filtrar, ordenar y paginar tacos
  @GetMapping
  public Mono<ResponseEntity<PagedResponse<TacoResponse>>> allTacos(
      @RequestParam(name = "search", required = false) String search,
      @RequestParam(name = "q", required = false) String q,
      @RequestParam(name = "minPrice", required = false) BigDecimal minPrice,
      @RequestParam(name = "maxPrice", required = false) BigDecimal maxPrice,
      @RequestParam(name = "available", required = false) Boolean available,
      @RequestParam(name = "inStock", required = false) Boolean inStock,
      @RequestParam(name = "dietary", required = false) DietaryLabel dietary,
      @RequestParam(name = "excludeAllergen", required = false) Allergen excludeAllergen,
      @RequestParam(name = "maxSpice", required = false) SpiceLevel maxSpice,
      @RequestParam(name = "ingredient", required = false) String ingredient,
      @RequestParam(name = "sortBy", required = false, defaultValue = "name") String sortBy,
      @RequestParam(name = "sortDir", required = false, defaultValue = "asc") String sortDir,
      @RequestParam(name = "page", required = false, defaultValue = "0") int page,
      @RequestParam(name = "size", required = false, defaultValue = "10") int size) {

    String query = (search != null && !search.trim().isEmpty()) ? search : q;

    TacoSearchCriteria criteria = TacoSearchCriteria.builder()
        .search(query)
        .minPrice(minPrice)
        .maxPrice(maxPrice)
        .available(available)
        .inStock(inStock)
        .dietary(dietary)
        .excludeAllergen(excludeAllergen)
        .maxSpice(maxSpice)
        .ingredient(ingredient)
        .sortBy(sortBy)
        .sortDir(sortDir)
        .page(page)
        .size(size)
        .build();

    return queryService.searchTacos(criteria)
        .map(pagedResponse -> ResponseEntity.ok()
            .header("X-Total-Count", String.valueOf(pagedResponse.getTotalElements()))
            .header("X-Total-Pages", String.valueOf(pagedResponse.getTotalPages()))
            .header("X-Current-Page", String.valueOf(pagedResponse.getPage()))
            .header("X-Page-Size", String.valueOf(pagedResponse.getSize()))
            .body(pagedResponse));
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
  public Mono<PhysicsResult> validatePhysics(@RequestBody TacoRequest request) {
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
