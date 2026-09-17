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
import java.util.ArrayList;
import java.util.Collections;
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

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.TacoOrder.OrderStatus;
import tacos.User;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.dto.UpdateOrderStatusRequest;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 25: Flujo de estados de una orden
// Ejercicio 8: Separar DTOs de entrada, respuesta y persistencia
// Ejercicio 9: Validación y errores tipo Problem Details
// Ejercicio 11: Autorización deny-by-default y roles útiles
public class OrderStateFlowSecurityTest {

  private OrderRepository orderRepo;
  private OrderMessagingService orderMessages;
  private EmailOrderService emailOrderService;
  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private InventoryService inventoryService;
  private UserRepository userRepo;
  private WebTestClient testClient;

  private User userAlice;
  private User userBob;
  private User userAdmin;
  private TacoOrder orderAlice;
  private TacoOrder orderBob;

  @BeforeEach
  public void setUp() {
    orderRepo = Mockito.mock(OrderRepository.class);
    orderMessages = Mockito.mock(OrderMessagingService.class);
    emailOrderService = Mockito.mock(EmailOrderService.class);
    ingredientRepo = Mockito.mock(IngredientRepository.class);
    tacoRepo = Mockito.mock(TacoRepository.class);
    inventoryService = Mockito.mock(InventoryService.class);
    userRepo = Mockito.mock(UserRepository.class);

    when(inventoryService.releaseInventory(any(TacoOrder.class))).thenReturn(Mono.empty());

    OrderApiController controller = new OrderApiController(
        orderRepo,
        orderMessages,
        emailOrderService,
        ingredientRepo,
        tacoRepo,
        null, // couponEngine
        inventoryService,
        null, // physicsEngine
        userRepo
    );

    testClient = WebTestClient.bindToController(controller)
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

    // Users
    userAlice = new User("alice", "password", "Alice Smith", "123 Main", "City", "ST", "12345", "555-1234", "alice@test.com");
    userAlice.setId("USER_ALICE");

    userBob = new User("bob", "password", "Bob Jones", "456 Oak", "City", "ST", "12345", "555-5678", "bob@test.com");
    userBob.setId("USER_BOB");

    userAdmin = new User("admin", "adminPass", "Admin Chef", "Admin Street", "City", "ST", "12345", "555-9999", "admin@test.com");
    userAdmin.setId("USER_ADMIN");

    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));
    when(userRepo.findByUsername("bob")).thenReturn(Mono.just(userBob));
    when(userRepo.findByUsername("admin")).thenReturn(Mono.just(userAdmin));

    // Orders
    Taco taco = new Taco();
    taco.setId("TACO_1");
    taco.setName("Carnitas");
    taco.setPrice(new BigDecimal("7.50"));
    taco.setQuantity(2);
    taco.setIngredients(Collections.singletonList(new Ingredient("FLTO", "Flour Tortilla", Type.WRAP)));

    orderAlice = new TacoOrder();
    orderAlice.setId("ORDER_ALICE_1");
    orderAlice.setUser(userAlice);
    orderAlice.setStatus(OrderStatus.CONFIRMED);
    orderAlice.setDeliveryName("Alice Smith");
    orderAlice.setDeliveryStreet("123 Main");
    orderAlice.setSubTotal(new BigDecimal("15.00"));
    orderAlice.setTotal(new BigDecimal("15.00"));
    orderAlice.setTacos(Collections.singletonList(taco));

    orderBob = new TacoOrder();
    orderBob.setId("ORDER_BOB_1");
    orderBob.setUser(userBob);
    orderBob.setStatus(OrderStatus.CONFIRMED);
    orderBob.setDeliveryName("Bob Jones");

    when(orderRepo.findById("ORDER_ALICE_1")).thenReturn(Mono.just(orderAlice));
    when(orderRepo.findById("ORDER_BOB_1")).thenReturn(Mono.just(orderBob));
    when(orderRepo.findById("ORDER_NOT_FOUND")).thenReturn(Mono.empty());

    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
  }

  @Test
  public void unauthenticatedRequests_shouldReturn401Unauthorized() {
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "test"))
        .exchange()
        .expectStatus().isUnauthorized();

    testClient.get()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .exchange()
        .expectStatus().isUnauthorized();

    testClient.get()
        .uri("/api/orders/ORDER_ALICE_1/details")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  public void updateStatus_withNullStatus_shouldReturn400ProblemDetails() {
    // Ejercicio 8 y 9: Validación de DTO y error Problem Details RFC 7807
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"status\": null}")
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.title").isEqualTo("Validation Failed")
        .jsonPath("$.invalidParams[0].name").isEqualTo("status");
  }

  @Test
  public void admin_canAdvanceOrderThroughFullLifecycle() {
    // Ejercicio 11: Roles útiles (ROLE_ADMIN para flujo de cocina)
    // 1. CONFIRMED -> PREPARING
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.PREPARING, "Iniciando cocción"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("PREPARING")
        .jsonPath("$.previousStatus").isEqualTo("CONFIRMED")
        .jsonPath("$.terminal").isEqualTo(false);

    // 2. PREPARING -> READY
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.READY, "Tacos listos"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("READY");

    // 3. READY -> DELIVERING
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.DELIVERING, "Repartidor en camino"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("DELIVERING");

    // 4. DELIVERING -> DELIVERED (Terminal)
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.DELIVERED, "Entregado a satisfacción"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("DELIVERED")
        .jsonPath("$.terminal").isEqualTo(true);
  }

  @Test
  public void invalidTransition_shouldReturn400ProblemDetails() {
    // Ejercicio 9: RFC 7807 Problem Details ante transición inválida (CONFIRMED -> DELIVERED)
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.DELIVERED, "Salto ilegal"))
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.detail").value(val -> assertTrue(val.toString().contains("Transición de estado inválida")));
  }

  @Test
  public void customer_canCancelOwnConfirmedOrder_andReleasesInventory() {
    // Ejercicio 11: ROLE_USER puede cancelar orden propia en CONFIRMED
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Cambié de opinión"))
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.currentStatus").isEqualTo("CANCELLED")
        .jsonPath("$.previousStatus").isEqualTo("CONFIRMED")
        .jsonPath("$.terminal").isEqualTo(true);

    verify(inventoryService).releaseInventory(orderAlice);
    verify(orderRepo).save(orderAlice);
  }

  @Test
  public void customer_cannotAdvanceKitchenStatus_denyByDefault() {
    // Ejercicio 11: Deny-by-default (ROLE_USER intentando pasar a PREPARING -> 403 Forbidden)
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.PREPARING, "Cliente haciéndose pasar por chef"))
        .exchange()
        .expectStatus().isForbidden()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(403);
  }

  @Test
  public void customer_cannotCancelOrderAlreadyInPreparation() {
    orderAlice.setStatus(OrderStatus.PREPARING);

    // Cliente no puede cancelar si ya está en preparación -> 400 Bad Request
    testClient.patch()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Cancelar tarde"))
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.detail").value(val -> assertTrue(val.toString().contains("ya se encuentra en proceso")));
  }

  @Test
  public void customer_cannotModifyOtherUserOrder_IDORProtection() {
    // Alice intenta cancelar la orden de Bob -> 403 Forbidden
    testClient.patch()
        .uri("/api/orders/ORDER_BOB_1/status")
        .header("X-Test-User", "alice")
        .header("X-Test-Role", "ROLE_USER")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(new UpdateOrderStatusRequest(OrderStatus.CANCELLED, "Ataque IDOR"))
        .exchange()
        .expectStatus().isForbidden()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(403);

    verify(orderRepo, never()).save(orderBob);
  }

  @Test
  public void getStatus_shouldReturnAllowedNextStates() {
    testClient.get()
        .uri("/api/orders/ORDER_ALICE_1/status")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.orderId").isEqualTo("ORDER_ALICE_1")
        .jsonPath("$.currentStatus").isEqualTo("CONFIRMED")
        .jsonPath("$.terminal").isEqualTo(false)
        .jsonPath("$.allowedNextStates").isArray();
  }

  @Test
  public void getOrderDetails_shouldReturnOrderResponseDTO() {
    // Ejercicio 8: Separar DTO de respuesta OrderResponse
    testClient.get()
        .uri("/api/orders/ORDER_ALICE_1/details")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.id").isEqualTo("ORDER_ALICE_1")
        .jsonPath("$.status").isEqualTo("CONFIRMED")
        .jsonPath("$.deliveryName").isEqualTo("Alice Smith")
        .jsonPath("$.tacos[0].name").isEqualTo("Carnitas")
        .jsonPath("$.total").isEqualTo(15.00);
  }
}
