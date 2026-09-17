package tacos.web.api;

import java.security.Principal;
import java.util.List;

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
import tacos.web.api.dto.RatingRequest;
import tacos.web.api.dto.RatingResponse;
import tacos.web.api.dto.TacoRankingItem;
import tacos.web.api.dto.TacoRatingSummary;

// Ejercicio 22: Calificaciones y ranking de tacos
@RestController
@RequestMapping(path = "/api/tacos", produces = "application/json")
@CrossOrigin(origins = "http://localhost:8080")
public class TacoRatingController {

  private final TacoRatingService ratingService;

  @Autowired
  public TacoRatingController(TacoRatingService ratingService) {
    this.ratingService = ratingService;
  }

  @PostMapping(path = "/{tacoId}/ratings", consumes = "application/json")
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<RatingResponse> rateTaco(
      @PathVariable("tacoId") String tacoId,
      @Valid @RequestBody RatingRequest request,
      Principal principal) {
    return ratingService.submitRating(principal, tacoId, request);
  }

  @GetMapping(path = "/{tacoId}/ratings")
  public Flux<RatingResponse> getRatings(@PathVariable("tacoId") String tacoId) {
    return ratingService.getTacoRatings(tacoId);
  }

  @GetMapping(path = "/{tacoId}/rating-summary")
  public Mono<TacoRatingSummary> getRatingSummary(@PathVariable("tacoId") String tacoId) {
    return ratingService.getRatingSummary(tacoId);
  }

  @DeleteMapping(path = "/{tacoId}/ratings")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public Mono<Void> deleteRating(@PathVariable("tacoId") String tacoId, Principal principal) {
    return ratingService.deleteRating(principal, tacoId);
  }

  @GetMapping(path = {"/ranking", "/leaderboard"})
  public Mono<List<TacoRankingItem>> getRanking(
      @RequestParam(name = "limit", defaultValue = "10") int limit) {
    return ratingService.getTacoRanking(limit);
  }
}
