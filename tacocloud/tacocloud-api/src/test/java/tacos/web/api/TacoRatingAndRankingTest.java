package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoRating;
import tacos.User;
import tacos.data.TacoRatingRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.web.api.dto.RatingRequest;
import tacos.web.api.dto.TacoRankingItem;
import tacos.web.api.dto.TacoRatingSummary;

// Ejercicio 22: Calificaciones y ranking de tacos
public class TacoRatingAndRankingTest {

  private TacoRatingRepository ratingRepo;
  private TacoRepository tacoRepo;
  private UserRepository userRepo;
  private TacoRatingService ratingService;
  private WebTestClient testClient;

  private User userAlice;
  private User userBob;
  private Taco tacoCarnitas;
  private Taco tacoVeggie;
  private Taco tacoFire;

  @BeforeEach
  public void setUp() {
    ratingRepo = Mockito.mock(TacoRatingRepository.class);
    tacoRepo = Mockito.mock(TacoRepository.class);
    userRepo = Mockito.mock(UserRepository.class);

    ratingService = new TacoRatingService(ratingRepo, tacoRepo, userRepo);
    TacoRatingController controller = new TacoRatingController(ratingService);

    testClient = WebTestClient.bindToController(controller)
        .webFilter((exchange, chain) -> {
          String testUser = exchange.getRequest().getHeaders().getFirst("X-Test-User");
          if (testUser != null && !testUser.trim().isEmpty()) {
            Principal principal = () -> testUser.trim();
            return chain.filter(exchange.mutate().principal(Mono.just(principal)).build());
          }
          return chain.filter(exchange);
        })
        .build();

    userAlice = new User("alice", "password", "Alice Smith", "Street", "City", "ST", "12345", "555-1234", "alice@test.com");
    userAlice.setId("USER_ALICE");

    userBob = new User("bob", "password", "Bob Jones", "Street", "City", "ST", "12345", "555-5678", "bob@test.com");
    userBob.setId("USER_BOB");

    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));
    when(userRepo.findByUsername("bob")).thenReturn(Mono.just(userBob));

    tacoCarnitas = new Taco();
    tacoCarnitas.setId("TACO_CARN");
    tacoCarnitas.setName("Carnitas Classic");
    tacoCarnitas.setPrice(new BigDecimal("6.00"));
    tacoCarnitas.setIngredients(Collections.singletonList(new Ingredient("FLTO", "Flour", Type.WRAP)));

    tacoVeggie = new Taco();
    tacoVeggie.setId("TACO_VEG");
    tacoVeggie.setName("Veggie Taco");
    tacoVeggie.setPrice(new BigDecimal("4.50"));
    tacoVeggie.setIngredients(Collections.singletonList(new Ingredient("COTO", "Corn", Type.WRAP)));

    tacoFire = new Taco();
    tacoFire.setId("TACO_FIRE");
    tacoFire.setName("Fire Taco");
    tacoFire.setPrice(new BigDecimal("5.50"));
    tacoFire.setIngredients(Collections.singletonList(new Ingredient("COTO", "Corn", Type.WRAP)));

    when(tacoRepo.findById("TACO_CARN")).thenReturn(Mono.just(tacoCarnitas));
    when(tacoRepo.findById("TACO_VEG")).thenReturn(Mono.just(tacoVeggie));
    when(tacoRepo.findById("TACO_FIRE")).thenReturn(Mono.just(tacoFire));
    when(tacoRepo.findById("TACO_UNKNOWN")).thenReturn(Mono.empty());
    when(tacoRepo.findAll()).thenReturn(Flux.just(tacoCarnitas, tacoVeggie, tacoFire));
  }

  @Test
  public void submitRating_validRating_shouldPersistAndReturnCreated() {
    TacoRating savedRating = new TacoRating("TACO_CARN", "USER_ALICE", "alice", 5, "¡Los mejores tacos!");
    savedRating.setId("RATE_01");

    when(ratingRepo.findByTacoIdAndUserId("TACO_CARN", "USER_ALICE")).thenReturn(Mono.empty());
    when(ratingRepo.save(any(TacoRating.class))).thenReturn(Mono.just(savedRating));

    testClient.post()
        .uri("/api/tacos/TACO_CARN/ratings")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"rating\": 5, \"comment\": \"¡Los mejores tacos!\"}")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo("RATE_01")
        .jsonPath("$.tacoId").isEqualTo("TACO_CARN")
        .jsonPath("$.userId").isEqualTo("USER_ALICE")
        .jsonPath("$.username").isEqualTo("alice")
        .jsonPath("$.rating").isEqualTo(5)
        .jsonPath("$.comment").isEqualTo("¡Los mejores tacos!");

    verify(ratingRepo).save(Mockito.argThat(r -> r.getRating() == 5 && "USER_ALICE".equals(r.getUserId())));
  }

  @Test
  public void submitRating_invalidRange_shouldReturn400BadRequest() {
    // Calificación 0 estrellas
    testClient.post()
        .uri("/api/tacos/TACO_CARN/ratings")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"rating\": 0, \"comment\": \"Pésimo\"}")
        .exchange()
        .expectStatus().isBadRequest();

    // Calificación 6 estrellas
    testClient.post()
        .uri("/api/tacos/TACO_CARN/ratings")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"rating\": 6, \"comment\": \"Excedido\"}")
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  public void submitRating_unauthenticated_shouldReturn401Unauthorized() {
    testClient.post()
        .uri("/api/tacos/TACO_CARN/ratings")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"rating\": 4}")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  public void submitRating_existingRating_shouldUpsertAndUpdate() {
    TacoRating existing = new TacoRating("TACO_CARN", "USER_ALICE", "alice", 3, "Regular");
    existing.setId("RATE_01");

    when(ratingRepo.findByTacoIdAndUserId("TACO_CARN", "USER_ALICE")).thenReturn(Mono.just(existing));
    when(ratingRepo.save(any(TacoRating.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    testClient.post()
        .uri("/api/tacos/TACO_CARN/ratings")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"rating\": 5, \"comment\": \"¡Mejoraron mucho!\"}")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo("RATE_01")
        .jsonPath("$.rating").isEqualTo(5)
        .jsonPath("$.comment").isEqualTo("¡Mejoraron mucho!");

    verify(ratingRepo).save(Mockito.argThat(r -> r.getRating() == 5 && "RATE_01".equals(r.getId())));
  }

  @Test
  public void getRatingSummary_shouldCalculateAverageAndDistribution() {
    TacoRating r1 = new TacoRating("TACO_CARN", "U1", "user1", 5, null);
    TacoRating r2 = new TacoRating("TACO_CARN", "U2", "user2", 5, null);
    TacoRating r3 = new TacoRating("TACO_CARN", "U3", "user3", 4, null);
    TacoRating r4 = new TacoRating("TACO_CARN", "U4", "user4", 3, null);

    when(ratingRepo.findByTacoId("TACO_CARN")).thenReturn(Flux.just(r1, r2, r3, r4));

    testClient.get()
        .uri("/api/tacos/TACO_CARN/rating-summary")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.tacoId").isEqualTo("TACO_CARN")
        .jsonPath("$.totalRatings").isEqualTo(4)
        .jsonPath("$.averageRating").isEqualTo(4.3) // (5+5+4+3)/4 = 4.25 -> 4.3
        .jsonPath("$.starDistribution.5").isEqualTo(2)
        .jsonPath("$.starDistribution.4").isEqualTo(1)
        .jsonPath("$.starDistribution.3").isEqualTo(1)
        .jsonPath("$.starDistribution.2").isEqualTo(0)
        .jsonPath("$.starDistribution.1").isEqualTo(0);
  }

  @Test
  public void getRanking_shouldOrderTacosByAverageAndVotes() {
    // Taco Carnitas: Calificaciones 5, 5, 5 -> Promedio 5.0 (3 votos)
    TacoRating carn1 = new TacoRating("TACO_CARN", "U1", "user1", 5, null);
    TacoRating carn2 = new TacoRating("TACO_CARN", "U2", "user2", 5, null);
    TacoRating carn3 = new TacoRating("TACO_CARN", "U3", "user3", 5, null);

    // Taco Fire: Calificaciones 5, 4 -> Promedio 4.5 (2 votos)
    TacoRating fire1 = new TacoRating("TACO_FIRE", "U1", "user1", 5, null);
    TacoRating fire2 = new TacoRating("TACO_FIRE", "U2", "user2", 4, null);

    // Taco Veggie: Calificaciones 3 -> Promedio 3.0 (1 voto)
    TacoRating veg1 = new TacoRating("TACO_VEG", "U1", "user1", 3, null);

    when(ratingRepo.findAll()).thenReturn(Flux.just(carn1, carn2, carn3, fire1, fire2, veg1));

    testClient.get()
        .uri("/api/tacos/ranking")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$").isArray()
        .jsonPath("$.length()").isEqualTo(3)
        // Rank 1: Carnitas Classic (5.0)
        .jsonPath("$[0].rank").isEqualTo(1)
        .jsonPath("$[0].taco.id").isEqualTo("TACO_CARN")
        .jsonPath("$[0].averageRating").isEqualTo(5.0)
        .jsonPath("$[0].totalRatings").isEqualTo(3)
        // Rank 2: Fire Taco (4.5)
        .jsonPath("$[1].rank").isEqualTo(2)
        .jsonPath("$[1].taco.id").isEqualTo("TACO_FIRE")
        .jsonPath("$[1].averageRating").isEqualTo(4.5)
        .jsonPath("$[1].totalRatings").isEqualTo(2)
        // Rank 3: Veggie Taco (3.0)
        .jsonPath("$[2].rank").isEqualTo(3)
        .jsonPath("$[2].taco.id").isEqualTo("TACO_VEG")
        .jsonPath("$[2].averageRating").isEqualTo(3.0)
        .jsonPath("$[2].totalRatings").isEqualTo(1);
  }

  @Test
  public void deleteRating_legitimateUser_shouldDeleteRating() {
    when(ratingRepo.deleteByTacoIdAndUserId("TACO_CARN", "USER_ALICE")).thenReturn(Mono.empty());

    testClient.delete()
        .uri("/api/tacos/TACO_CARN/ratings")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isNoContent();

    verify(ratingRepo).deleteByTacoIdAndUserId("TACO_CARN", "USER_ALICE");
  }

  @Test
  public void submitRating_tacoNotFound_returns404() {
    testClient.post()
        .uri("/api/tacos/TACO_UNKNOWN/ratings")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"rating\": 5}")
        .exchange()
        .expectStatus().isNotFound();
  }
}
