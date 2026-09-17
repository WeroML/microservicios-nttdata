package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Coupon;
import tacos.Coupon.DiscountType;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.User;
import tacos.data.CouponRepository;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.messaging.OrderMessagingService;
import tacos.physics.PhysicsResult;
import tacos.web.api.dto.ReorderRequest;

// Ejercicio 24: Reordenar una compra anterior con reglas actuales
public class ReorderSecurityAndRulesTest {

  private OrderRepository orderRepo;
  private OrderMessagingService orderMessages;
  private EmailOrderService emailOrderService;
  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private CouponRepository couponRepo;
  private CouponEngine couponEngine;
  private InventoryService inventoryService;
  private TacoPhysicsEngine physicsEngine;
  private UserRepository userRepo;
  private WebTestClient testClient;

  private User userAlice;
  private User userBob;
  private TacoOrder historicalOrderAlice;
  private TacoOrder historicalOrderBob;
  private Taco catalogTacoCarnitas;

  @BeforeEach
  public void setUp() {
    orderRepo = Mockito.mock(OrderRepository.class);
    orderMessages = Mockito.mock(OrderMessagingService.class);
    emailOrderService = Mockito.mock(EmailOrderService.class);
    ingredientRepo = Mockito.mock(IngredientRepository.class);
    tacoRepo = Mockito.mock(TacoRepository.class);
    couponRepo = Mockito.mock(CouponRepository.class);
    couponEngine = new CouponEngine(couponRepo);
    inventoryService = Mockito.mock(InventoryService.class);
    physicsEngine = Mockito.mock(TacoPhysicsEngine.class);
    userRepo = Mockito.mock(UserRepository.class);

    // Default: physics passes
    when(physicsEngine.validateAndPass(any(Taco.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    // Default: inventory reserves successfully
    when(inventoryService.reserveInventory(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    OrderApiController controller = new OrderApiController(
        orderRepo,
        orderMessages,
        emailOrderService,
        ingredientRepo,
        tacoRepo,
        couponEngine,
        inventoryService,
        physicsEngine,
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

    // Users
    userAlice = new User("alice", "password", "Alice Smith", "123 Main", "City", "ST", "12345", "555-1234", "alice@test.com");
    userAlice.setId("USER_ALICE");

    userBob = new User("bob", "password", "Bob Jones", "456 Oak", "City", "ST", "12345", "555-5678", "bob@test.com");
    userBob.setId("USER_BOB");

    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));
    when(userRepo.findByUsername("bob")).thenReturn(Mono.just(userBob));

    // Catalog Taco: Carnitas with CURRENT price $8.50 (was $5.00 in historical order)
    catalogTacoCarnitas = new Taco();
    catalogTacoCarnitas.setId("TACO_CARNITAS");
    catalogTacoCarnitas.setName("Carnitas Supreme");
    catalogTacoCarnitas.setPrice(new BigDecimal("8.50"));
    Ingredient wrap = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP);
    wrap.setPrice(new BigDecimal("1.50"));
    catalogTacoCarnitas.setIngredients(Collections.singletonList(wrap));

    when(tacoRepo.findById("TACO_CARNITAS")).thenReturn(Mono.just(catalogTacoCarnitas));

    // Historical Order for Alice (old price $5.00, created in 2024)
    historicalOrderAlice = new TacoOrder();
    historicalOrderAlice.setId("HISTORICAL_ORDER_ALICE");
    historicalOrderAlice.setUser(userAlice);
    historicalOrderAlice.setPlacedAt(new Date(1600000000000L));
    historicalOrderAlice.setDeliveryName("Alice Smith");
    historicalOrderAlice.setDeliveryStreet("123 Main");
    historicalOrderAlice.setDeliveryCity("Mexico");
    historicalOrderAlice.setDeliveryState("DF");
    historicalOrderAlice.setDeliveryZip("01000");
    historicalOrderAlice.setPaymentToken("tok_old_123");
    historicalOrderAlice.setLast4("1111");
    historicalOrderAlice.setCcExpiration("12/24");
    historicalOrderAlice.setSubTotal(new BigDecimal("5.00"));
    historicalOrderAlice.setTotal(new BigDecimal("5.00"));

    Taco oldTaco = new Taco();
    oldTaco.setId("TACO_CARNITAS");
    oldTaco.setName("Old Carnitas");
    oldTaco.setPrice(new BigDecimal("5.00"));
    oldTaco.setQuantity(1);
    oldTaco.setIngredients(Collections.singletonList(wrap));
    historicalOrderAlice.setTacos(Collections.singletonList(oldTaco));

    // Historical Order for Bob
    historicalOrderBob = new TacoOrder();
    historicalOrderBob.setId("HISTORICAL_ORDER_BOB");
    historicalOrderBob.setUser(userBob);
    historicalOrderBob.setDeliveryName("Bob Jones");
    historicalOrderBob.setTacos(Collections.singletonList(oldTaco));

    when(orderRepo.findById("HISTORICAL_ORDER_ALICE")).thenReturn(Mono.just(historicalOrderAlice));
    when(orderRepo.findById("HISTORICAL_ORDER_BOB")).thenReturn(Mono.just(historicalOrderBob));
    when(orderRepo.findById("ORDER_NOT_FOUND")).thenReturn(Mono.empty());

    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> {
      TacoOrder toSave = inv.getArgument(0);
      toSave.setId("NEW_ORDER_" + System.currentTimeMillis());
      return Mono.just(toSave);
    });
  }

  @Test
  public void unauthenticatedReorder_shouldReturn401Unauthorized() {
    testClient.post()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder")
        .exchange()
        .expectStatus().isUnauthorized();

    testClient.get()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder-preview")
        .exchange()
        .expectStatus().isUnauthorized();
  }

  @Test
  public void reorderNonExistentOrder_shouldReturn404NotFound() {
    testClient.post()
        .uri("/api/orders/ORDER_NOT_FOUND/reorder")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  public void reorderOtherUserOrder_shouldReturn403Forbidden_IDORProtection() {
    // Alice tries to reorder Bob's order -> 403 Forbidden
    testClient.post()
        .uri("/api/orders/HISTORICAL_ORDER_BOB/reorder")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isForbidden();

    verify(orderRepo, never()).save(any(TacoOrder.class));
    verify(orderMessages, never()).sendOrder(any(TacoOrder.class));
  }

  @Test
  public void reorder_shouldApplyCurrentCatalogPricesAndCreateNewOrder() {
    // Historical order had price $5.00, current catalog price is $8.50
    testClient.post()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").value(id -> assertNotEquals("HISTORICAL_ORDER_ALICE", id))
        .jsonPath("$.user.id").isEqualTo("USER_ALICE")
        .jsonPath("$.tacos[0].name").isEqualTo("Carnitas Supreme") // updated from catalog
        .jsonPath("$.tacos[0].price").isEqualTo(8.50)              // current price, not $5.00!
        .jsonPath("$.subTotal").isEqualTo(8.50)
        .jsonPath("$.total").isEqualTo(8.50)
        .jsonPath("$.deliveryStreet").isEqualTo("123 Main")
        .jsonPath("$.paymentToken").isEqualTo("tok_old_123");

    verify(orderRepo).save(any(TacoOrder.class));
    verify(orderMessages).sendOrder(any(TacoOrder.class));
    verify(inventoryService).reserveInventory(any(TacoOrder.class));
  }

  @Test
  public void reorder_withTacoPhysicsViolation_shouldReturn400BadRequest() {
    // Current Taco Physics rejects taco
    when(physicsEngine.validateAndPass(any(Taco.class)))
        .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Taco physics violation: missing wrap")));

    testClient.post()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isBadRequest();

    verify(orderRepo, never()).save(any(TacoOrder.class));
    verify(inventoryService, never()).reserveInventory(any(TacoOrder.class));
  }

  @Test
  public void reorder_withInsufficientInventory_shouldReturn409Conflict() {
    // Current stock is exhausted today
    when(inventoryService.reserveInventory(any(TacoOrder.class)))
        .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "Stock insuficiente para el ingrediente 'Flour Tortilla'")));

    testClient.post()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isEqualTo(HttpStatus.CONFLICT);

    verify(orderRepo, never()).save(any(TacoOrder.class));
  }

  @Test
  public void reorder_withExpiredCoupon_shouldRejectByDefault() {
    // Historical order had expired coupon
    historicalOrderAlice.setCouponCode("EXPIRED_COUPON");
    Coupon expiredCoupon = new Coupon("EXPIRED_COUPON", "Expired Promo", DiscountType.FIXED_AMOUNT,
        new BigDecimal("2.00"), BigDecimal.ZERO, new Date(1000000000000L), new Date(1100000000000L), 100, true);
    when(couponRepo.findByCodeIgnoreCase("EXPIRED_COUPON")).thenReturn(Mono.just(expiredCoupon));

    // Default reorder without dropExpiredCoupon -> fails 400 Bad Request
    testClient.post()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isBadRequest();

    verify(orderRepo, never()).save(any(TacoOrder.class));
  }

  @Test
  public void reorder_withDropExpiredCoupon_shouldSucceedAtFullCurrentPrice() {
    historicalOrderAlice.setCouponCode("EXPIRED_COUPON");
    Coupon expiredCoupon = new Coupon("EXPIRED_COUPON", "Expired Promo", DiscountType.FIXED_AMOUNT,
        new BigDecimal("2.00"), BigDecimal.ZERO, new Date(1000000000000L), new Date(1100000000000L), 100, true);
    when(couponRepo.findByCodeIgnoreCase("EXPIRED_COUPON")).thenReturn(Mono.just(expiredCoupon));

    ReorderRequest req = new ReorderRequest();
    req.setDropExpiredCoupon(true);

    // With dropExpiredCoupon=true -> drops expired coupon and recalculates at $8.50
    testClient.post()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(req)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.couponCode").doesNotExist()
        .jsonPath("$.subTotal").isEqualTo(8.50)
        .jsonPath("$.total").isEqualTo(8.50);

    verify(orderRepo).save(any(TacoOrder.class));
  }

  @Test
  public void reorder_withNewCouponAndOverriddenDelivery_shouldApplyNewRules() {
    Coupon activeCoupon = new Coupon("PROMO2026", "2026 Promo", DiscountType.FIXED_AMOUNT,
        new BigDecimal("2.50"), BigDecimal.ZERO, new Date(1000000000000L), new Date(System.currentTimeMillis() + 100000000L), 100, true);
    when(couponRepo.findByCodeIgnoreCase("PROMO2026")).thenReturn(Mono.just(activeCoupon));
    when(couponRepo.save(any(Coupon.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    ReorderRequest req = new ReorderRequest();
    req.setDeliveryStreet("789 Reforma Ave");
    req.setDeliveryCity("CDMX");
    req.setPaymentToken("tok_fresh_456");
    req.setCouponCode("PROMO2026");

    testClient.post()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder")
        .header("X-Test-User", "alice")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(req)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.deliveryStreet").isEqualTo("789 Reforma Ave")
        .jsonPath("$.deliveryCity").isEqualTo("CDMX")
        .jsonPath("$.paymentToken").isEqualTo("tok_fresh_456")
        .jsonPath("$.couponCode").isEqualTo("PROMO2026")
        .jsonPath("$.subTotal").isEqualTo(8.50)
        .jsonPath("$.discount").isEqualTo(2.50)
        .jsonPath("$.total").isEqualTo(6.00); // 8.50 - 2.50 = 6.00
  }

  @Test
  public void previewReorder_shouldCalculateCurrentTotalsWithoutSavingOrReservingStock() {
    testClient.get()
        .uri("/api/orders/HISTORICAL_ORDER_ALICE/reorder-preview")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.tacos[0].price").isEqualTo(8.50)
        .jsonPath("$.total").isEqualTo(8.50);

    // Assert preview does NOT persist or reserve inventory
    verify(orderRepo, never()).save(any(TacoOrder.class));
    verify(inventoryService, never()).reserveInventory(any(TacoOrder.class));
    verify(orderMessages, never()).sendOrder(any(TacoOrder.class));
  }
}
