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
import tacos.TacoOrder;
import tacos.User;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.dto.PagedResponse;

// Ejercicio 23: Historial paginado y privado de órdenes
public class OrderHistorySecurityTest {

  private OrderRepository orderRepo;
  private OrderMessagingService orderMessages;
  private EmailOrderService emailOrderService;
  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private UserRepository userRepo;
  private WebTestClient testClient;

  private User userAlice;
  private User userBob;
  private TacoOrder orderAlice1;
  private TacoOrder orderAlice2;
  private TacoOrder orderBob1;

  @BeforeEach
  public void setUp() {
    orderRepo = Mockito.mock(OrderRepository.class);
    orderMessages = Mockito.mock(OrderMessagingService.class);
    emailOrderService = Mockito.mock(EmailOrderService.class);
    ingredientRepo = Mockito.mock(IngredientRepository.class);
    tacoRepo = Mockito.mock(TacoRepository.class);
    userRepo = Mockito.mock(UserRepository.class);

    OrderApiController controller = new OrderApiController(
        orderRepo,
        orderMessages,
        emailOrderService,
        ingredientRepo,
        tacoRepo,
        null, // couponEngine
        null, // inventoryService
        null, // physicsEngine
        userRepo
    );

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

    // Setup Users
    userAlice = new User("alice", "password", "Alice Smith", "123 Main", "City", "ST", "12345", "555-1234", "alice@test.com");
    userAlice.setId("USER_ALICE");

    userBob = new User("bob", "password", "Bob Jones", "456 Oak", "City", "ST", "12345", "555-5678", "bob@test.com");
    userBob.setId("USER_BOB");

    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));
    when(userRepo.findByUsername("bob")).thenReturn(Mono.just(userBob));
    when(userRepo.findByUsername("unknown")).thenReturn(Mono.empty());

    // Setup Orders
    orderAlice1 = new TacoOrder();
    orderAlice1.setId("ORDER_ALICE_1");
    orderAlice1.setDeliveryName("Alice");
    orderAlice1.setUser(userAlice);
    orderAlice1.setPlacedAt(new Date(1000000000000L)); // Older

    orderAlice2 = new TacoOrder();
    orderAlice2.setId("ORDER_ALICE_2");
    orderAlice2.setDeliveryName("Alice");
    orderAlice2.setUser(userAlice);
    orderAlice2.setPlacedAt(new Date(2000000000000L)); // Newer

    orderBob1 = new TacoOrder();
    orderBob1.setId("ORDER_BOB_1");
    orderBob1.setDeliveryName("Bob");
    orderBob1.setUser(userBob);
    orderBob1.setPlacedAt(new Date(1500000000000L));

    when(orderRepo.findById("ORDER_ALICE_1")).thenReturn(Mono.just(orderAlice1));
    when(orderRepo.findById("ORDER_ALICE_2")).thenReturn(Mono.just(orderAlice2));
    when(orderRepo.findById("ORDER_BOB_1")).thenReturn(Mono.just(orderBob1));
    when(orderRepo.findById("ORDER_UNKNOWN")).thenReturn(Mono.empty());

    when(orderRepo.deleteById(any(String.class))).thenReturn(Mono.empty());
  }

  @Test
  public void unauthenticatedRequests_shouldReturn401Unauthorized() {
    testClient.get()
        .uri("/api/orders")
        .exchange()
        .expectStatus().isUnauthorized();

    testClient.get()
        .uri("/api/orders/history")
        .exchange()
        .expectStatus().isUnauthorized();

    testClient.get()
        .uri("/api/orders/ORDER_ALICE_1")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  public void getOrders_shouldIsolateUsersOrdersAndSortByDateDesc() {
    when(orderRepo.findAll()).thenReturn(Flux.just(orderAlice1, orderBob1, orderAlice2));

    testClient.get()
        .uri("/api/orders?page=0&size=10")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Total-Count", "2")
        .expectHeader().valueEquals("X-Total-Pages", "1")
        .expectHeader().valueEquals("X-Current-Page", "0")
        .expectHeader().valueEquals("X-Page-Size", "10")
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(2)
        .jsonPath("$.page").isEqualTo(0)
        .jsonPath("$.size").isEqualTo(10)
        .jsonPath("$.content[0].id").isEqualTo("ORDER_ALICE_2") // Newer first
        .jsonPath("$.content[1].id").isEqualTo("ORDER_ALICE_1");
  }

  @Test
  public void getOrders_paginationSlicingAndHeaders() {
    TacoOrder o1 = createOrderForAlice("O1", 1000L);
    TacoOrder o2 = createOrderForAlice("O2", 2000L);
    TacoOrder o3 = createOrderForAlice("O3", 3000L);
    TacoOrder o4 = createOrderForAlice("O4", 4000L);
    TacoOrder o5 = createOrderForAlice("O5", 5000L);

    when(orderRepo.findAll()).thenReturn(Flux.just(o1, o2, o3, o4, o5));

    // Page 0, Size 2 -> items 5 and 4
    testClient.get()
        .uri("/api/orders?page=0&size=2")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Total-Count", "5")
        .expectHeader().valueEquals("X-Total-Pages", "3")
        .expectHeader().valueEquals("X-Current-Page", "0")
        .expectHeader().valueEquals("X-Page-Size", "2")
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(5)
        .jsonPath("$.totalPages").isEqualTo(3)
        .jsonPath("$.page").isEqualTo(0)
        .jsonPath("$.size").isEqualTo(2)
        .jsonPath("$.hasNext").isEqualTo(true)
        .jsonPath("$.hasPrevious").isEqualTo(false)
        .jsonPath("$.content[0].id").isEqualTo("O5")
        .jsonPath("$.content[1].id").isEqualTo("O4");

    // Page 2, Size 2 -> item 1
    testClient.get()
        .uri("/api/orders?page=2&size=2")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals("X-Total-Count", "5")
        .expectHeader().valueEquals("X-Total-Pages", "3")
        .expectHeader().valueEquals("X-Current-Page", "2")
        .expectHeader().valueEquals("X-Page-Size", "2")
        .expectBody()
        .jsonPath("$.totalElements").isEqualTo(5)
        .jsonPath("$.totalPages").isEqualTo(3)
        .jsonPath("$.page").isEqualTo(2)
        .jsonPath("$.size").isEqualTo(2)
        .jsonPath("$.hasNext").isEqualTo(false)
        .jsonPath("$.hasPrevious").isEqualTo(true)
        .jsonPath("$.content[0].id").isEqualTo("O1");
  }

  @Test
  public void getOrderById_ownerCanAccess_nonOwnerGets403Forbidden() {
    // Alice accesses Alice's order -> 200 OK
    testClient.get()
        .uri("/api/orders/ORDER_ALICE_1")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.id").isEqualTo("ORDER_ALICE_1")
        .jsonPath("$.deliveryName").isEqualTo("Alice");

    // Alice accesses Bob's order -> 403 Forbidden (IDOR prevented)
    testClient.get()
        .uri("/api/orders/ORDER_BOB_1")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isForbidden();

    // Alice accesses non-existent order -> 404 Not Found
    testClient.get()
        .uri("/api/orders/ORDER_UNKNOWN")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  public void deleteOrder_ownerCanDelete_nonOwnerGets403Forbidden() {
    // Alice attempts to delete Bob's order -> 403 Forbidden
    testClient.delete()
        .uri("/api/orders/ORDER_BOB_1")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isForbidden();

    verify(orderRepo, never()).deleteById("ORDER_BOB_1");

    // Alice deletes Alice's order -> 204 No Content
    testClient.delete()
        .uri("/api/orders/ORDER_ALICE_1")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isNoContent();

    verify(orderRepo).deleteById("ORDER_ALICE_1");
  }

  @Test
  public void postOrder_authenticated_shouldAutoBindUser() {
    TacoOrder newOrder = new TacoOrder();
    newOrder.setDeliveryName("Alice Smith");
    newOrder.setDeliveryStreet("123 Main");

    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(invocation -> {
      TacoOrder saved = invocation.getArgument(0);
      saved.setId("ORDER_NEW_1");
      return Mono.just(saved);
    });

    testClient.post()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(newOrder)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isEqualTo("ORDER_NEW_1")
        .jsonPath("$.user.id").isEqualTo("USER_ALICE")
        .jsonPath("$.user.username").isEqualTo("alice");
  }

  private TacoOrder createOrderForAlice(String id, long timestamp) {
    TacoOrder o = new TacoOrder();
    o.setId(id);
    o.setUser(userAlice);
    o.setPlacedAt(new Date(timestamp));
    return o;
  }
}
