package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.BindingContext;
import org.springframework.web.reactive.result.method.SyncHandlerMethodArgumentResolver;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.User;
import tacos.UserFavorite;
import tacos.data.TacoRepository;
import tacos.data.UserFavoriteRepository;
import tacos.data.UserRepository;
import tacos.web.api.dto.FavoriteRequest;
import tacos.web.api.dto.FavoriteResponse;

// Ejercicio 21: Favoritos por usuario sin confiar en userId del cliente
public class FavoritesSecurityTest {

  private UserFavoriteRepository favoriteRepo;
  private TacoRepository tacoRepo;
  private UserRepository userRepo;
  private FavoriteService favoriteService;
  private WebTestClient testClient;

  private User userAlice;
  private User userBob;
  private Taco tacoCarnitas;
  private Taco tacoVeggie;

  @BeforeEach
  public void setUp() {
    favoriteRepo = Mockito.mock(UserFavoriteRepository.class);
    tacoRepo = Mockito.mock(TacoRepository.class);
    userRepo = Mockito.mock(UserRepository.class);

    favoriteService = new FavoriteService(favoriteRepo, tacoRepo, userRepo);
    FavoritesController controller = new FavoritesController(favoriteService);

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

    // User Alice (ID: USER_ALICE)
    userAlice = new User("alice", "password", "Alice Smith", "123 Main", "City", "ST", "12345", "555-1234", "alice@test.com");
    userAlice.setId("USER_ALICE");

    // User Bob (ID: USER_BOB)
    userBob = new User("bob", "password", "Bob Jones", "456 Oak", "City", "ST", "12345", "555-5678", "bob@test.com");
    userBob.setId("USER_BOB");

    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));
    when(userRepo.findByUsername("bob")).thenReturn(Mono.just(userBob));

    // Tacos
    tacoCarnitas = new Taco();
    tacoCarnitas.setId("TACO_CARNITAS");
    tacoCarnitas.setName("Carnitas Classic");
    tacoCarnitas.setPrice(new BigDecimal("6.50"));
    tacoCarnitas.setIngredients(Collections.singletonList(new Ingredient("FLTO", "Flour Tortilla", Type.WRAP)));

    tacoVeggie = new Taco();
    tacoVeggie.setId("TACO_VEGGIE");
    tacoVeggie.setName("Veggie Taco");
    tacoVeggie.setPrice(new BigDecimal("4.50"));
    tacoVeggie.setIngredients(Collections.singletonList(new Ingredient("COTO", "Corn Tortilla", Type.WRAP)));

    when(tacoRepo.findById("TACO_CARNITAS")).thenReturn(Mono.just(tacoCarnitas));
    when(tacoRepo.findById("TACO_VEGGIE")).thenReturn(Mono.just(tacoVeggie));
    when(tacoRepo.findById("TACO_UNKNOWN")).thenReturn(Mono.empty());
  }

  @Test
  public void unauthenticatedRequest_shouldBeRejectedWith401Unauthorized() {
    testClient.get()
        .uri("/api/favorites")
        .exchange()
        .expectStatus().isUnauthorized();

    testClient.post()
        .uri("/api/favorites")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new FavoriteRequest("TACO_CARNITAS"))
        .exchange()
        .expectStatus().isUnauthorized();

    testClient.delete()
        .uri("/api/favorites/TACO_CARNITAS")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  public void addFavorite_legitimateUser_savesWithAuthenticatedUserId() {
    UserFavorite savedFav = new UserFavorite("USER_ALICE", "alice", "TACO_CARNITAS");
    savedFav.setId("FAV_01");

    when(favoriteRepo.findByUserIdAndTacoId("USER_ALICE", "TACO_CARNITAS")).thenReturn(Mono.empty());
    when(favoriteRepo.save(any(UserFavorite.class))).thenReturn(Mono.just(savedFav));

    testClient.post()
        .uri("/api/favorites")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"tacoId\":\"TACO_CARNITAS\"}")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.userId").isEqualTo("USER_ALICE")
        .jsonPath("$.username").isEqualTo("alice")
        .jsonPath("$.taco.id").isEqualTo("TACO_CARNITAS");

    verify(favoriteRepo).save(Mockito.argThat(f -> "USER_ALICE".equals(f.getUserId()) && "TACO_CARNITAS".equals(f.getTacoId())));
  }

  @Test
  public void addFavorite_withSpoofedUserId_isRejectedWith403Forbidden() {
    // Alice está autenticada, pero intenta inyectar el userId de Bob ("USER_BOB") en el body
    testClient.post()
        .uri("/api/favorites")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"tacoId\":\"TACO_CARNITAS\",\"userId\":\"USER_BOB\"}")
        .exchange()
        .expectStatus().isForbidden();

    // Verificamos que NUNCA se guardó nada en la base de datos
    verify(favoriteRepo, never()).save(any(UserFavorite.class));
  }

  @Test
  public void getFavorites_isolatesUsersAndReturnsOnlyAuthenticatedUserFavorites() {
    UserFavorite favAlice = new UserFavorite("USER_ALICE", "alice", "TACO_CARNITAS");
    favAlice.setId("FAV_A");

    when(favoriteRepo.findByUserId("USER_ALICE")).thenReturn(Flux.just(favAlice));

    testClient.get()
        .uri("/api/favorites")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$").isArray()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].userId").isEqualTo("USER_ALICE")
        .jsonPath("$[0].taco.id").isEqualTo("TACO_CARNITAS");

    verify(favoriteRepo).findByUserId("USER_ALICE");
    verify(favoriteRepo, never()).findByUserId("USER_BOB");
  }

  @Test
  public void removeFavorite_withSpoofedUserIdParam_isRejectedWith403Forbidden() {
    // Alice intenta eliminar el favorito de Bob pasando ?userId=USER_BOB
    testClient.delete()
        .uri("/api/favorites/TACO_CARNITAS?userId=USER_BOB")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isForbidden();

    verify(favoriteRepo, never()).deleteByUserIdAndTacoId(any(), any());
  }

  @Test
  public void removeFavorite_legitimateUser_deletesOnlyAuthenticatedUserFavorite() {
    when(favoriteRepo.deleteByUserIdAndTacoId("USER_ALICE", "TACO_CARNITAS")).thenReturn(Mono.empty());

    testClient.delete()
        .uri("/api/favorites/TACO_CARNITAS")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isNoContent();

    verify(favoriteRepo).deleteByUserIdAndTacoId("USER_ALICE", "TACO_CARNITAS");
  }

  @Test
  public void checkFavorite_returnsCorrectStatusForAuthenticatedUser() {
    when(favoriteRepo.existsByUserIdAndTacoId("USER_ALICE", "TACO_CARNITAS")).thenReturn(Mono.just(true));
    when(favoriteRepo.existsByUserIdAndTacoId("USER_ALICE", "TACO_VEGGIE")).thenReturn(Mono.just(false));

    testClient.get()
        .uri("/api/favorites/TACO_CARNITAS")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.isFavorite").isEqualTo(true);

    testClient.get()
        .uri("/api/favorites/TACO_VEGGIE")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.isFavorite").isEqualTo(false);
  }

  @Test
  public void addFavorite_tacoNotFound_returns404NotFound() {
    testClient.post()
        .uri("/api/favorites")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"tacoId\":\"TACO_UNKNOWN\"}")
        .exchange()
        .expectStatus().isNotFound();
  }
}
