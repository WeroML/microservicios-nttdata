package tacos.web.api;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.TacoRating;
import tacos.User;
import tacos.data.TacoRatingRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.web.api.dto.RatingRequest;
import tacos.web.api.dto.RatingResponse;
import tacos.web.api.dto.TacoRankingItem;
import tacos.web.api.dto.TacoRatingSummary;
import tacos.web.api.dto.TacoResponse;

// Ejercicio 22: Calificaciones y ranking de tacos
@Service
public class TacoRatingService {

  private final TacoRatingRepository ratingRepo;
  private final TacoRepository tacoRepo;
  private final UserRepository userRepo;

  @Autowired
  public TacoRatingService(TacoRatingRepository ratingRepo,
                           TacoRepository tacoRepo,
                           UserRepository userRepo) {
    this.ratingRepo = ratingRepo;
    this.tacoRepo = tacoRepo;
    this.userRepo = userRepo;
  }

  public Mono<User> resolveAuthenticatedUser(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    if (principal instanceof Authentication) {
      Object p = ((Authentication) principal).getPrincipal();
      if (p instanceof User) {
        return Mono.just((User) p);
      }
    }

    return userRepo.findByUsername(principal.getName().trim())
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Usuario autenticado no encontrado: " + principal.getName())));
  }

  public Mono<RatingResponse> submitRating(Principal principal, String tacoId, RatingRequest request) {
    if (tacoId == null || tacoId.trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del taco es obligatorio"));
    }
    if (request == null || request.getRating() < 1 || request.getRating() > 5) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "La calificación debe ser entre 1 y 5 estrellas"));
    }

    return resolveAuthenticatedUser(principal)
        .flatMap(user -> tacoRepo.findById(tacoId.trim())
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Taco no encontrado: " + tacoId)))
            .flatMap(taco -> ratingRepo.findByTacoIdAndUserId(taco.getId(), user.getId())
                .flatMap(existingRating -> {
                  // Actualizar calificación existente (upsert)
                  existingRating.setRating(request.getRating());
                  existingRating.setComment(request.getComment());
                  existingRating.setUpdatedAt(new Date());
                  return ratingRepo.save(existingRating);
                })
                .switchIfEmpty(Mono.defer(() -> {
                  // Crear nueva calificación
                  TacoRating newRating = new TacoRating(taco.getId(), user.getId(), user.getUsername(),
                      request.getRating(), request.getComment());
                  return ratingRepo.save(newRating);
                }))
                .map(RatingResponse::fromEntity)
            )
        );
  }

  public Flux<RatingResponse> getTacoRatings(String tacoId) {
    if (tacoId == null || tacoId.trim().isEmpty()) {
      return Flux.empty();
    }
    return tacoRepo.findById(tacoId.trim())
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Taco no encontrado: " + tacoId)))
        .flatMapMany(taco -> ratingRepo.findByTacoId(taco.getId()).map(RatingResponse::fromEntity));
  }

  public Mono<TacoRatingSummary> getRatingSummary(String tacoId) {
    if (tacoId == null || tacoId.trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del taco es obligatorio"));
    }

    return tacoRepo.findById(tacoId.trim())
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Taco no encontrado: " + tacoId)))
        .flatMap(taco -> ratingRepo.findByTacoId(taco.getId())
            .collectList()
            .map(ratings -> {
              int total = ratings.size();
              Map<Integer, Long> distribution = new HashMap<>();
              for (int star = 1; star <= 5; star++) {
                distribution.put(star, 0L);
              }

              if (total == 0) {
                return TacoRatingSummary.builder()
                    .tacoId(taco.getId())
                    .tacoName(taco.getName())
                    .averageRating(0.0)
                    .totalRatings(0)
                    .starDistribution(distribution)
                    .formattedSummary("Sin calificaciones aún")
                    .build();
              }

              double sum = 0;
              for (TacoRating r : ratings) {
                sum += r.getRating();
                distribution.put(r.getRating(), distribution.getOrDefault(r.getRating(), 0L) + 1);
              }

              double avg = Math.round((sum / total) * 10.0) / 10.0;
              String formatted = String.format("%.1f ★ (%d %s)", avg, total,
                  total == 1 ? "calificación" : "calificaciones");

              return TacoRatingSummary.builder()
                  .tacoId(taco.getId())
                  .tacoName(taco.getName())
                  .averageRating(avg)
                  .totalRatings(total)
                  .starDistribution(distribution)
                  .formattedSummary(formatted)
                  .build();
            })
        );
  }

  public Mono<List<TacoRankingItem>> getTacoRanking(int limit) {
    int safeLimit = (limit <= 0) ? 10 : Math.min(limit, 100);

    return Mono.zip(
        tacoRepo.findAll().collectList(),
        ratingRepo.findAll().collectList()
    ).map(tuple -> {
      List<Taco> allTacos = tuple.getT1();
      List<TacoRating> allRatings = tuple.getT2();

      if (allTacos.isEmpty()) {
        return Collections.emptyList();
      }

      // Agrupar calificaciones por tacoId
      Map<String, List<TacoRating>> ratingsByTaco = allRatings.stream()
          .collect(Collectors.groupingBy(TacoRating::getTacoId));

      List<TacoRankingCandidate> candidates = new ArrayList<>();
      for (Taco taco : allTacos) {
        List<TacoRating> tacoRatings = ratingsByTaco.getOrDefault(taco.getId(), Collections.emptyList());
        int count = tacoRatings.size();
        double avg = 0.0;
        if (count > 0) {
          double sum = tacoRatings.stream().mapToInt(TacoRating::getRating).sum();
          avg = Math.round((sum / count) * 10.0) / 10.0;
        }
        candidates.add(new TacoRankingCandidate(taco, avg, count));
      }

      // Ordenar ranking:
      // 1. Mayor promedio de estrellas (averageRating desc)
      // 2. Mayor cantidad de votos como desempate (totalRatings desc)
      // 3. Nombre del taco alfabéticamente
      candidates.sort(
          Comparator.comparingDouble(TacoRankingCandidate::getAverageRating).reversed()
              .thenComparing(Comparator.comparingInt(TacoRankingCandidate::getTotalRatings).reversed())
              .thenComparing(c -> c.getTaco().getName() != null ? c.getTaco().getName() : "")
      );

      List<TacoRankingItem> ranking = new ArrayList<>();
      int rank = 1;
      for (int i = 0; i < Math.min(safeLimit, candidates.size()); i++) {
        TacoRankingCandidate c = candidates.get(i);
        String formatted = c.getTotalRatings() > 0
            ? String.format("%.1f ★ (%d)", c.getAverageRating(), c.getTotalRatings())
            : "Sin calificar";

        ranking.add(TacoRankingItem.builder()
            .rank(rank++)
            .taco(TacoResponse.fromEntity(c.getTaco()))
            .averageRating(c.getAverageRating())
            .totalRatings(c.getTotalRatings())
            .formattedRating(formatted)
            .build());
      }

      return ranking;
    });
  }

  public Mono<Void> deleteRating(Principal principal, String tacoId) {
    if (tacoId == null || tacoId.trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del taco es obligatorio"));
    }

    return resolveAuthenticatedUser(principal)
        .flatMap(user -> ratingRepo.deleteByTacoIdAndUserId(tacoId.trim(), user.getId()));
  }

  private static class TacoRankingCandidate {
    private final Taco taco;
    private final double averageRating;
    private final int totalRatings;

    public TacoRankingCandidate(Taco taco, double averageRating, int totalRatings) {
      this.taco = taco;
      this.averageRating = averageRating;
      this.totalRatings = totalRatings;
    }

    public Taco getTaco() {
      return taco;
    }

    public double getAverageRating() {
      return averageRating;
    }

    public int getTotalRatings() {
      return totalRatings;
    }
  }
}
