package tacos.web.api;

import java.security.Principal;
import java.util.Collections;
import java.util.Map;

import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import tacos.web.api.dto.FavoriteRequest;
import tacos.web.api.dto.FavoriteResponse;

// Ejercicio 21: Favoritos por usuario sin confiar en userId del cliente
@RestController
@RequestMapping(path = "/api/favorites", produces = "application/json")
@CrossOrigin(origins = "http://localhost:8080")
public class FavoritesController {

  private final FavoriteService favoriteService;

  @Autowired
  public FavoritesController(FavoriteService favoriteService) {
    this.favoriteService = favoriteService;
  }

  @GetMapping
  public Flux<FavoriteResponse> getFavorites(Principal principal) {
    return favoriteService.getUserFavorites(principal);
  }

  @PostMapping(consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<FavoriteResponse> addFavorite(@Valid @RequestBody FavoriteRequest request, Principal principal) {
    return favoriteService.addFavorite(principal, request);
  }

  @PostMapping("/{tacoId}")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<FavoriteResponse> addFavoriteByPath(@PathVariable("tacoId") String tacoId, Principal principal) {
    return favoriteService.addFavorite(principal, new FavoriteRequest(tacoId));
  }

  @DeleteMapping("/{tacoId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> removeFavorite(@PathVariable("tacoId") String tacoId,
                                  @RequestParam(name = "userId", required = false) String untrustedUserId,
                                  Principal principal) {
    return favoriteService.removeFavorite(principal, tacoId, untrustedUserId);
  }

  @GetMapping("/{tacoId}")
  public Mono<Map<String, Object>> checkFavorite(@PathVariable("tacoId") String tacoId, Principal principal) {
    return favoriteService.isFavorite(principal, tacoId)
        .map(isFav -> Collections.singletonMap("isFavorite", (Object) isFav));
  }
}
