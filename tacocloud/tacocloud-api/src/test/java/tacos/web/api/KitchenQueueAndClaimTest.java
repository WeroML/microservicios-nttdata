package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.User;
import tacos.data.OrderRepository;
import tacos.data.UserRepository;
import tacos.web.api.dto.ClaimOrderRequest;
import tacos.web.api.dto.KitchenQueueItem;
import tacos.web.api.dto.KitchenQueueResponse;
import tacos.web.api.dto.OrderEtaResponse;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
public class KitchenQueueAndClaimTest {

  private OrderRepository orderRepo;
  private UserRepository userRepo;
  private KitchenService kitchenService;
  private WebTestClient testClient;

  private User userAlice;
  private User userBob;
  private User userChefMario;
  private User userChefLuigi;

  private TacoOrder order1;
  private TacoOrder order2;
  private TacoOrder order3;
  private TacoOrder orderDelivered;

  @BeforeEach
  public void setUp() {
    orderRepo = Mockito.mock(OrderRepository.class);
    userRepo = Mockito.mock(UserRepository.class);
    kitchenService = new KitchenService(orderRepo);

    OrderApiController orderApiController = new OrderApiController(
        orderRepo,
        null, // orderMessages
        null, // emailOrderService
        null, // ingredientRepo
        null, // tacoRepo
        null, // couponEngine
        null, // inventoryService
        null, // physicsEngine
        userRepo,
        kitchenService
    );

    KitchenApiController kitchenApiController = new KitchenApiController(kitchenService, userRepo);

    testClient = WebTestClient.bindToController(orderApiController, kitchenApiController)
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .webFilter((exchange, chain) -> {
          String testUser = exchange.getRequest().getHeaders().getFirst("X-Test-User");
          String testRole = exchange.getRequest().getHeaders().getFirst("X-Test-Role");
          if (testUser != null && !testUser.trim().isEmpty()) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (testRole != null && !testRole.trim().isEmpty()) {
              authorities.add(new SimpleGrantedAuthority(testRole.trim()));
            } else {
              authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
            Authentication auth = new UsernamePasswordAuthenticationToken(testUser.trim(), "password", authorities);
            return chain.filter(exchange.mutate().principal(Mono.just(auth)).build());
          }
          return chain.filter(exchange);
        })
        .build();

    userAlice = new User("alice", "password", "Alice Smith", "123 Main", "Austin", "TX", "78701", "555-1111", "alice@example.com");
    userAlice.setId("usr-alice");

    userBob = new User("bob", "password", "Bob Jones", "456 Oak", "Austin", "TX", "78702", "555-2222", "bob@example.com");
    userBob.setId("usr-bob");

    userChefMario = new User("chef_mario", "password", "Chef Mario", "Kitchen 1", "Austin", "TX", "78701", "555-3333", "mario@kitchen.com");
    userChefMario.setId("usr-chef-mario");

    userChefLuigi = new User("chef_luigi", "password", "Chef Luigi", "Kitchen 2", "Austin", "TX", "78701", "555-4444", "luigi@kitchen.com");
    userChefLuigi.setId("usr-chef-luigi");

    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));
    when(userRepo.findByUsername("bob")).thenReturn(Mono.just(userBob));
    when(userRepo.findByUsername("chef_mario")).thenReturn(Mono.just(userChefMario));
    when(userRepo.findByUsername("chef_luigi")).thenReturn(Mono.just(userChefLuigi));

    // Crear ingredientes de prueba
    Ingredient flourTortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP);
    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Type.PROTEIN);
    Ingredient cheese = new Ingredient("CHED", "Cheddar", Type.CHEESE);

    // Orden 1: Colocada hace 15 minutos, en preparación por chef_mario
    order1 = new TacoOrder();
    order1.setId("order-101");
    order1.setUser(userAlice);
    order1.setDeliveryName("Alice");
    order1.setPlacedAt(new Date(System.currentTimeMillis() - 900000L)); // Hace 15 min
    order1.setStatus(OrderStatus.PREPARING);
    order1.setClaimedBy("chef_mario");
    order1.setClaimedAt(new Date(System.currentTimeMillis() - 300000L)); // Hace 5 min
    Taco taco1 = new Taco();
    taco1.setName("Carnitas Taco");
    taco1.setQuantity(2);
    taco1.setIngredients(Arrays.asList(flourTortilla, beef, cheese));
    order1.setTacos(Arrays.asList(taco1));

    // Orden 2: Colocada hace 10 minutos, confirmada y en espera
    order2 = new TacoOrder();
    order2.setId("order-102");
    order2.setUser(userBob);
    order2.setDeliveryName("Bob");
    order2.setPlacedAt(new Date(System.currentTimeMillis() - 600000L)); // Hace 10 min
    order2.setStatus(OrderStatus.CONFIRMED);
    Taco taco2 = new Taco();
    taco2.setName("Simple Taco");
    taco2.setQuantity(1);
    taco2.setIngredients(Arrays.asList(flourTortilla, beef));
    order2.setTacos(Arrays.asList(taco2));

    // Orden 3: Colocada hace 2 minutos, confirmada y en espera
    order3 = new TacoOrder();
    order3.setId("order-103");
    order3.setUser(userAlice);
    order3.setDeliveryName("Alice");
    order3.setPlacedAt(new Date(System.currentTimeMillis() - 120000L)); // Hace 2 min
    order3.setStatus(OrderStatus.CONFIRMED);
    Taco taco3 = new Taco();
    taco3.setName("Veggie Taco");
    taco3.setQuantity(3);
    taco3.setIngredients(Arrays.asList(flourTortilla, cheese));
    order3.setTacos(Arrays.asList(taco3));

    // Orden entregada (no debe figurar en cola activa de cocina)
    orderDelivered = new TacoOrder();
    orderDelivered.setId("order-100");
    orderDelivered.setUser(userAlice);
    orderDelivered.setDeliveryName("Alice");
    orderDelivered.setPlacedAt(new Date(System.currentTimeMillis() - 3600000L));
    orderDelivered.setStatus(OrderStatus.DELIVERED);
  }

  // 1. Consulta de cola de cocina (FIFO cronológico y métricas agregadas)
  @Test
  public void testKitchenQueueOrderedFifo() {
    when(orderRepo.findAll()).thenReturn(Flux.just(order3, order1, orderDelivered, order2));

    testClient.get()
        .uri("/api/kitchen/queue")
        .header("X-Test-User", "chef_mario")
        .header("X-Test-Role", "ROLE_ADMIN")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(KitchenQueueResponse.class)
        .value(response -> {
          assertNotNull(response);
          assertEquals(3, response.getTotalInQueue(), "La cola debe contener 3 órdenes activas");
          assertEquals(2, response.getTotalWaiting(), "2 órdenes deben estar en CONFIRMED (en espera)");
          assertEquals(1, response.getTotalPreparing(), "1 orden debe estar en PREPARING");
          assertTrue(response.getEstimatedQueueWaitMinutes() > 0, "El tiempo de cola debe ser mayor a 0");

          List<KitchenQueueItem> orders = response.getOrders();
          assertEquals(3, orders.size());

          // FIFO: order1 (hace 15m) -> order2 (hace 10m) -> order3 (hace 2m)
          assertEquals("order-101", orders.get(0).getOrderId());
          assertEquals(1, orders.get(0).getQueuePosition());
          assertEquals(0, orders.get(0).getOrdersAhead());
          assertEquals(OrderStatus.PREPARING, orders.get(0).getStatus());
          assertEquals("chef_mario", orders.get(0).getClaimedBy());

          assertEquals("order-102", orders.get(1).getOrderId());
          assertEquals(2, orders.get(1).getQueuePosition());
          assertEquals(1, orders.get(1).getOrdersAhead());
          assertEquals(OrderStatus.CONFIRMED, orders.get(1).getStatus());
          assertNull(orders.get(1).getClaimedBy());

          assertEquals("order-103", orders.get(2).getOrderId());
          assertEquals(3, orders.get(2).getQueuePosition());
          assertEquals(2, orders.get(2).getOrdersAhead());
          assertEquals(OrderStatus.CONFIRMED, orders.get(2).getStatus());
        });
  }

  // 2. Control de acceso: Cliente regular (ROLE_USER) no puede consultar la cola de cocina
  @Test
  public void testKitchenQueueForbiddenForNonAdmin() {
    testClient.get()
        .uri("/api/kitchen/queue")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
  }

  // 3. Control de acceso: Solicitud anónima rechazada con 401 Unauthorized
  @Test
  public void testKitchenQueueUnauthorizedForAnonymous() {
    testClient.get()
        .uri("/api/kitchen/queue")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isUnauthorized();
  }

  // 4. Claim exitoso de una orden confirmada por un cocinero autorizado
  @Test
  public void testClaimOrderSuccess() {
    when(orderRepo.findById("order-102")).thenReturn(Mono.just(order2));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    ClaimOrderRequest req = new ClaimOrderRequest("Estación de plancha 1");

    testClient.post()
        .uri("/api/orders/order-102/claim")
        .header("X-Test-User", "chef_mario")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(req)
        .exchange()
        .expectStatus().isOk()
        .expectBody(KitchenQueueItem.class)
        .value(item -> {
          assertNotNull(item);
          assertEquals("order-102", item.getOrderId());
          assertEquals(OrderStatus.PREPARING, item.getStatus());
          assertEquals("chef_mario", item.getClaimedBy());
          assertNotNull(item.getClaimedAt());
          assertNotNull(item.getEstimatedPrepMinutes());
          assertTrue(item.getEstimatedPrepMinutes() >= 3);
          assertNotNull(item.getEstimatedReadyAt());
        });

    verify(orderRepo).save(any(TacoOrder.class));
    assertEquals(OrderStatus.PREPARING, order2.getStatus());
    assertEquals("chef_mario", order2.getClaimedBy());
  }

  // 5. Conflicto atómico (409 Conflict) si otro cocinero intenta tomar una orden ya reclamada
  @Test
  public void testAtomicClaimConflictWhenAlreadyClaimed() {
    when(orderRepo.findById("order-101")).thenReturn(Mono.just(order1));

    testClient.post()
        .uri("/api/orders/order-101/claim")
        .header("X-Test-User", "chef_luigi")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.status").isEqualTo(409)
        .jsonPath("$.detail").value(detail -> {
          String msg = (String) detail;
          assertTrue(msg.contains("chef_mario"), "El mensaje debe indicar qué chef ya la tomó");
        });

    verify(orderRepo, never()).save(any(TacoOrder.class));
  }

  // 6. Conflicto de estado (409 Conflict) si se intenta reclamar una orden que no está en CONFIRMED
  @Test
  public void testAtomicClaimConflictWhenNotInConfirmedState() {
    order2.setStatus(OrderStatus.DELIVERED);
    when(orderRepo.findById("order-102")).thenReturn(Mono.just(order2));

    testClient.post()
        .uri("/api/orders/order-102/claim")
        .header("X-Test-User", "chef_mario")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT)
        .expectBody()
        .jsonPath("$.status").isEqualTo(409);

    verify(orderRepo, never()).save(any(TacoOrder.class));
  }

  // 7. Liberación exitosa de orden tomada (Unclaim) regresándola a CONFIRMED
  @Test
  public void testUnclaimOrderSuccess() {
    when(orderRepo.findById("order-101")).thenReturn(Mono.just(order1));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    testClient.post()
        .uri("/api/orders/order-101/unclaim")
        .header("X-Test-User", "chef_mario")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isOk()
        .expectBody(KitchenQueueItem.class)
        .value(item -> {
          assertNotNull(item);
          assertEquals("order-101", item.getOrderId());
          assertEquals(OrderStatus.CONFIRMED, item.getStatus());
          assertNull(item.getClaimedBy());
          assertNull(item.getClaimedAt());
        });

    verify(orderRepo).save(any(TacoOrder.class));
    assertEquals(OrderStatus.CONFIRMED, order1.getStatus());
    assertNull(order1.getClaimedBy());
    assertNull(order1.getClaimedAt());
  }

  // 8. Cliente regular (ROLE_USER) no tiene autorización para tomar o liberar órdenes
  @Test
  public void testClaimForbiddenForNonAdmin() {
    testClient.post()
        .uri("/api/orders/order-102/claim")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);

    verify(orderRepo, never()).save(any(TacoOrder.class));
  }

  // 9. Consulta de ETA de la orden por su cliente propietario (Alice consulta orden de Alice)
  @Test
  public void testOrderEtaForCustomerOwner() {
    when(orderRepo.findById("order-103")).thenReturn(Mono.just(order3));
    when(orderRepo.findAll()).thenReturn(Flux.just(order1, order2, order3));

    testClient.get()
        .uri("/api/orders/order-103/eta")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(OrderEtaResponse.class)
        .value(eta -> {
          assertNotNull(eta);
          assertEquals("order-103", eta.getOrderId());
          assertEquals(OrderStatus.CONFIRMED, eta.getStatus());
          assertEquals(3, eta.getQueuePosition(), "Debe ser la 3ra en la cola FIFO");
          assertEquals(2, eta.getOrdersAhead(), "Tiene 2 órdenes por delante");
          assertTrue(eta.getEstimatedPrepMinutes() > 0);
          assertTrue(eta.getRemainingMinutes() > 0);
          assertNotNull(eta.getEstimatedReadyAt());
        });
  }

  // 10. Protección Zero-Trust IDOR: Bob no puede consultar el ETA de la orden de Alice
  @Test
  public void testOrderEtaIdorForbiddenForOtherCustomer() {
    when(orderRepo.findById("order-103")).thenReturn(Mono.just(order3));

    testClient.get()
        .uri("/api/orders/order-103/eta")
        .header("X-Test-User", "bob")
        .header("X-Test-Role", "ROLE_USER")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.FORBIDDEN);
  }

  // 11. Admin puede consultar el ETA de cualquier orden
  @Test
  public void testOrderEtaAccessibleByAdmin() {
    when(orderRepo.findById("order-103")).thenReturn(Mono.just(order3));
    when(orderRepo.findAll()).thenReturn(Flux.just(order1, order2, order3));

    testClient.get()
        .uri("/api/orders/order-103/eta")
        .header("X-Test-User", "chef_mario")
        .header("X-Test-Role", "ROLE_ADMIN")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(OrderEtaResponse.class)
        .value(eta -> {
          assertNotNull(eta);
          assertEquals("order-103", eta.getOrderId());
          assertEquals(3, eta.getQueuePosition());
        });
  }

  // 12. Orden entregada devuelve 0 minutos restantes
  @Test
  public void testOrderEtaDeliveredReturnsZeroRemaining() {
    when(orderRepo.findById("order-100")).thenReturn(Mono.just(orderDelivered));

    testClient.get()
        .uri("/api/orders/order-100/eta")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody(OrderEtaResponse.class)
        .value(eta -> {
          assertNotNull(eta);
          assertEquals(OrderStatus.DELIVERED, eta.getStatus());
          assertEquals(0, eta.getRemainingMinutes());
          assertEquals(0, eta.getQueuePosition());
          assertEquals(0, eta.getOrdersAhead());
        });
  }

  // 13. Endpoints vía /api/kitchen (KitchenApiController)
  @Test
  public void testKitchenEndpointsViaKitchenController() {
    when(orderRepo.findById("order-102")).thenReturn(Mono.just(order2));
    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    testClient.post()
        .uri("/api/kitchen/order-102/claim")
        .header("X-Test-User", "chef_mario")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isOk()
        .expectBody(KitchenQueueItem.class)
        .value(item -> {
          assertEquals("order-102", item.getOrderId());
          assertEquals(OrderStatus.PREPARING, item.getStatus());
          assertEquals("chef_mario", item.getClaimedBy());
        });
  }
}
