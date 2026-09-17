import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

import reactor.core.publisher.Mono;
import tacos.Coupon;
import tacos.Ingredient;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.CouponEngine;
import tacos.web.api.EmailOrder;
import tacos.web.api.EmailOrderService;
import tacos.web.api.InventoryService;
import tacos.web.api.OrderApiController;

public class OrderApiControllerTest {

  @Test
  public void patchOrder_shouldUpdateZipCorrectlyWithoutMutatingWithState() {
    // 1. ARRANGE (Preparar mocks)
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);

    // Creamos la orden original existente en BD
    TacoOrder existingOrder = new TacoOrder();
    existingOrder.setId("ORDER1");
    existingOrder.setDeliveryName("Gustavo");
    existingOrder.setDeliveryState("Jalisco");
    existingOrder.setDeliveryZip("11111");

    // Simulamos que la BD encuentra la orden y que al guardar devuelve la orden modificada
    when(repo.findById("ORDER1")).thenReturn(Mono.just(existingOrder));
    when(repo.save(any(TacoOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService))
        .build();

    // 2. ACT & 3. ASSERT (Enviar PATCH enviando solo el nuevo ZIP "44100")
    testClient.patch()
        .uri("/api/orders/ORDER1")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"deliveryZip\": \"44100\"}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.deliveryZip").isEqualTo("44100")   // Verifica que el ZIP se actualizó a 44100
          .jsonPath("$.deliveryState").isEqualTo("Jalisco"); // Verifica que el Estado NO mutó el ZIP

    verify(repo).save(any(TacoOrder.class));
  }

  @Test
  public void patchOrder_whenOrderNotFound_shouldReturn404() {
    // ARRANGE
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);

    // Simulamos que la orden 999 NO existe
    when(repo.findById("999")).thenReturn(Mono.empty());

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService))
        .build();

    // ACT & ASSERT
    testClient.patch()
        .uri("/api/orders/999")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"deliveryZip\": \"44100\"}")
        .exchange()
        .expectStatus().isNotFound(); // Verifica que devuelve 404
  }

  //Test para el ejercicio 5: Put con identidad consistente
    @Test
  public void putOrder_shouldSetConsistentIdentityAndSave() {
    // ARRANGE
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);

    TacoOrder inputOrder = new TacoOrder();
    inputOrder.setDeliveryName("Gustavo");

    // Simulamos que al guardar se retorna la misma orden recibida
    when(repo.save(any(TacoOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService))
        .build();

    // ACT & ASSERT
    testClient.put()
        .uri("/api/orders/ORDER123")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(inputOrder)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.id").isEqualTo("ORDER123")               //Verifica Identidad Consistente
          .jsonPath("$.deliveryName").isEqualTo("Gustavo");

    verify(repo).save(any(TacoOrder.class));
  }

  //Test para el ejercicio 5: Delete con identidad consistente
  @Test
  public void deleteOrder_shouldReturn204NoContent() {
    // ARRANGE
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);

    when(repo.deleteById("ORDER123")).thenReturn(Mono.empty());

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService))
        .build();

    // ACT & ASSERT
    testClient.delete()
        .uri("/api/orders/ORDER123")
        .exchange()
        .expectStatus().isNoContent();                         //Verifica 204 No Content

    verify(repo).deleteById("ORDER123");                       //Verifica que se ejecutó el borrado real
  }

  // Test para el ejercicio 6: Una sola suscripción para guardar y publicar (postOrder)
  @Test
  public void postOrder_shouldSaveAndPublishOrderInSingleFlow() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);

    TacoOrder inputOrder = new TacoOrder();
    inputOrder.setId("ORDER_REGULAR");
    inputOrder.setDeliveryName("Gustavo");

    when(repo.save(any(TacoOrder.class))).thenReturn(Mono.just(inputOrder));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService))
        .build();

    testClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(inputOrder)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
          .jsonPath("$.id").isEqualTo("ORDER_REGULAR");

    verify(repo).save(any(TacoOrder.class));
    verify(messagingService).sendOrder(inputOrder);
  }

  // Test para el ejercicio 6: Una sola suscripción para guardar y publicar (postOrderFromEmail)
  @Test
  public void postOrderFromEmail_shouldConvertSaveAndPublishInSingleFlow() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);

    EmailOrder emailOrder = new EmailOrder();
    emailOrder.setEmail("craig@habuma.com");

    TacoOrder domainOrder = new TacoOrder();
    domainOrder.setId("ORDER_FROM_EMAIL");
    domainOrder.setDeliveryName("Craig Walls");

    when(emailService.convertEmailOrderToDomainOrder(any())).thenReturn(Mono.just(domainOrder));
    when(repo.save(any(TacoOrder.class))).thenReturn(Mono.just(domainOrder));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService))
        .build();

    testClient.post()
        .uri("/api/orders/fromEmail")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(emailOrder)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
          .jsonPath("$.id").isEqualTo("ORDER_FROM_EMAIL");

    verify(emailService).convertEmailOrderToDomainOrder(any());
    verify(repo).save(domainOrder);
    verify(messagingService).sendOrder(domainOrder);
  }

  // Test para ejercicio 12: Tokenizar pago y eliminar PAN/CVV del dominio
  @Test
  public void patchOrder_shouldUpdatePaymentTokenAndLast4Correctly() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);

    TacoOrder existingOrder = new TacoOrder();
    existingOrder.setId("ORDER_PAY");
    existingOrder.setPaymentToken("tok_old");

    when(repo.findById("ORDER_PAY")).thenReturn(Mono.just(existingOrder));
    when(repo.save(any(TacoOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService))
        .build();

    testClient.patch()
        .uri("/api/orders/ORDER_PAY")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"paymentToken\": \"tok_new_1234\", \"last4\": \"1234\", \"ccExpiration\": \"12/28\"}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.paymentToken").isEqualTo("tok_new_1234")
          .jsonPath("$.last4").isEqualTo("1234")
          .jsonPath("$.ccExpiration").isEqualTo("12/28");

    verify(repo).save(any(TacoOrder.class));
  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor
  @Test
  public void postOrder_shouldCalculatePricesAndQuantitiesOnServerSide() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);

    Ingredient tortilla = new Ingredient("FLTO", "Flour Tortilla", Ingredient.Type.WRAP, new BigDecimal("1.00"), true, 10);
    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, new BigDecimal("2.50"), true, 10);

    when(ingredientRepo.findById("FLTO")).thenReturn(Mono.just(tortilla));
    when(ingredientRepo.findById("GRBF")).thenReturn(Mono.just(beef));
    when(repo.save(any(TacoOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo))
        .build();

    // Enviamos una orden con 2 tacos con cantidad 2 (esperado: precio unitario 3.50, total orden 7.00)
    String jsonPayload = "{"
        + "\"deliveryName\": \"Gustavo\","
        + "\"tacos\": [{"
        + "  \"name\": \"Custom Beef Taco\","
        + "  \"quantity\": 2,"
        + "  \"ingredients\": [{\"id\": \"FLTO\"}, {\"id\": \"GRBF\"}]"
        + "}]"
        + "}";

    testClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(jsonPayload)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
          .jsonPath("$.tacos[0].quantity").isEqualTo(2)
          .jsonPath("$.tacos[0].price").isEqualTo(3.50)
          .jsonPath("$.total").isEqualTo(7.00);

    verify(repo).save(any(TacoOrder.class));
    verify(messagingService).sendOrder(any(TacoOrder.class));
  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor (Protección contra manipulación)
  @Test
  public void postOrder_shouldOverwriteTamperedPricesAndTotal() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);

    Ingredient tortilla = new Ingredient("FLTO", "Flour Tortilla", Ingredient.Type.WRAP, new BigDecimal("0.75"), true, 10);
    Ingredient cheese = new Ingredient("CHED", "Cheddar", Ingredient.Type.CHEESE, new BigDecimal("0.90"), true, 10);

    when(ingredientRepo.findById("FLTO")).thenReturn(Mono.just(tortilla));
    when(ingredientRepo.findById("CHED")).thenReturn(Mono.just(cheese));
    when(repo.save(any(TacoOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo))
        .build();

    // Atacante intenta enviar price: 0.01 y total: 0.01 en el payload
    String tamperedPayload = "{"
        + "\"deliveryName\": \"Attacker\","
        + "\"total\": 0.01,"
        + "\"tacos\": [{"
        + "  \"name\": \"Cheesy Taco\","
        + "  \"price\": 0.01,"
        + "  \"quantity\": 1,"
        + "  \"ingredients\": [{\"id\": \"FLTO\"}, {\"id\": \"CHED\"}]"
        + "}]"
        + "}";

    testClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(tamperedPayload)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
          // El servidor debe ignorar el 0.01 y calcular 0.75 + 0.90 = 1.65
          .jsonPath("$.tacos[0].price").isEqualTo(1.65)
          .jsonPath("$.total").isEqualTo(1.65);

    verify(repo).save(any(TacoOrder.class));
  }

  // Ejercicio 14: Calcular precios y cantidades del lado servidor (Normalización de cantidad)
  @Test
  public void postOrder_shouldDefaultQuantityToOneWhenMissingOrInvalid() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);

    Ingredient tortilla = new Ingredient("FLTO", "Flour Tortilla", Ingredient.Type.WRAP, new BigDecimal("1.50"), true, 10);

    when(ingredientRepo.findById("FLTO")).thenReturn(Mono.just(tortilla));
    when(repo.save(any(TacoOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo))
        .build();

    // Payload con cantidad 0 o negativa
    String invalidQuantityPayload = "{"
        + "\"deliveryName\": \"Gustavo\","
        + "\"tacos\": [{"
        + "  \"name\": \"Single Wrap\","
        + "  \"quantity\": 0,"
        + "  \"ingredients\": [{\"id\": \"FLTO\"}]"
        + "}]"
        + "}";

    testClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalidQuantityPayload)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
          .jsonPath("$.tacos[0].quantity").isEqualTo(1)
          .jsonPath("$.tacos[0].price").isEqualTo(1.50)
          .jsonPath("$.total").isEqualTo(1.50);

    verify(repo).save(any(TacoOrder.class));
  }

  // Ejercicio 14: PUT recalcula precios y total
  @Test
  public void putOrder_shouldRecalculatePricesAndTotal() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);

    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, new BigDecimal("2.50"), true, 10);
    when(ingredientRepo.findById("GRBF")).thenReturn(Mono.just(beef));
    when(repo.save(any(TacoOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo))
        .build();

    String putPayload = "{"
        + "\"deliveryName\": \"Gustavo\","
        + "\"tacos\": [{"
        + "  \"name\": \"Beef Only\","
        + "  \"quantity\": 3,"
        + "  \"ingredients\": [{\"id\": \"GRBF\"}]"
        + "}]"
        + "}";

    testClient.put()
        .uri("/api/orders/ORDER_PUT_1")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(putPayload)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.id").isEqualTo("ORDER_PUT_1")
          .jsonPath("$.tacos[0].quantity").isEqualTo(3)
          .jsonPath("$.tacos[0].price").isEqualTo(2.50)
          .jsonPath("$.total").isEqualTo(7.50);

    verify(repo).save(any(TacoOrder.class));
  }

  // Ejercicio 15: Motor de cupones con reglas y fecha de expiración (Orden con cupón válido)
  @Test
  public void postOrder_withValidCoupon_shouldApplyDiscountAndReduceTotal() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);
    CouponEngine couponEngine = Mockito.mock(CouponEngine.class);

    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, new BigDecimal("2.50"), true, 10);
    when(ingredientRepo.findById("GRBF")).thenReturn(Mono.just(beef));
    when(repo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    // Simulamos que el CouponEngine valida y aplica $2.00 de descuento sobre los $10.00 de subtotal
    when(couponEngine.applyCoupon(any(TacoOrder.class), any(Date.class))).thenAnswer(inv -> {
      TacoOrder order = inv.getArgument(0);
      order.setCouponCode("SAVE2");
      order.setDiscount(new BigDecimal("2.00"));
      order.calculateTotal(); // subTotal: 10.00, discount: 2.00, total: 8.00
      return Mono.just(order);
    });

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo, couponEngine))
        .build();

    String orderJson = "{"
        + "\"deliveryName\": \"Gustavo\","
        + "\"couponCode\": \"SAVE2\","
        + "\"tacos\": [{"
        + "  \"name\": \"Beef Taco\","
        + "  \"quantity\": 4,"
        + "  \"ingredients\": [{\"id\": \"GRBF\"}]"
        + "}]"
        + "}";

    testClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderJson)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
          .jsonPath("$.couponCode").isEqualTo("SAVE2")
          .jsonPath("$.subTotal").isEqualTo(10.00)
          .jsonPath("$.discount").isEqualTo(2.00)
          .jsonPath("$.total").isEqualTo(8.00);

    verify(couponEngine).applyCoupon(any(TacoOrder.class), any(Date.class));
    verify(repo).save(any(TacoOrder.class));
  }

  // Ejercicio 15: Motor de cupones con reglas y fecha de expiración (Rechazo de cupón expirado)
  @Test
  public void postOrder_withExpiredCoupon_shouldReturn400BadRequest() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);
    CouponEngine couponEngine = Mockito.mock(CouponEngine.class);

    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, new BigDecimal("2.50"), true, 10);
    when(ingredientRepo.findById("GRBF")).thenReturn(Mono.just(beef));

    when(couponEngine.applyCoupon(any(TacoOrder.class), any(Date.class)))
        .thenReturn(Mono.error(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "El cupón 'EXPIRED' ha expirado.")));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo, couponEngine))
        .build();

    String orderJson = "{"
        + "\"deliveryName\": \"Gustavo\","
        + "\"couponCode\": \"EXPIRED\","
        + "\"tacos\": [{"
        + "  \"name\": \"Beef Taco\","
        + "  \"quantity\": 2,"
        + "  \"ingredients\": [{\"id\": \"GRBF\"}]"
        + "}]"
        + "}";

    testClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderJson)
        .exchange()
        .expectStatus().isBadRequest();
  }

  // Ejercicio 16: Reservar y liberar inventario sin vender aire (Reserva exitosa de stock)
  @Test
  public void postOrder_shouldReserveInventoryWhenStockIsAvailable() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);
    InventoryService inventoryService = Mockito.mock(InventoryService.class);

    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, new BigDecimal("2.50"), true, 20);
    when(ingredientRepo.findById("GRBF")).thenReturn(Mono.just(beef));

    when(inventoryService.reserveInventory(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    when(repo.save(any(TacoOrder.class))).thenAnswer(inv -> Mono.just(inv.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo, null, inventoryService))
        .build();

    String orderJson = "{"
        + "\"deliveryName\": \"Gustavo\","
        + "\"tacos\": [{"
        + "  \"name\": \"Beef Taco\","
        + "  \"quantity\": 2,"
        + "  \"ingredients\": [{\"id\": \"GRBF\"}]"
        + "}]"
        + "}";

    testClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderJson)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
          .jsonPath("$.deliveryName").isEqualTo("Gustavo");

    verify(inventoryService).reserveInventory(any(TacoOrder.class));
    verify(repo).save(any(TacoOrder.class));
  }

  // Ejercicio 16: Reservar y liberar inventario sin vender aire (Rechazo con 409 Conflict si falta stock)
  @Test
  public void postOrder_whenStockInsufficient_shouldReturn409ConflictWithoutSellingAir() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);
    InventoryService inventoryService = Mockito.mock(InventoryService.class);

    Ingredient beef = new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, new BigDecimal("2.50"), true, 1);
    when(ingredientRepo.findById("GRBF")).thenReturn(Mono.just(beef));

    when(inventoryService.reserveInventory(any(TacoOrder.class)))
        .thenReturn(Mono.error(new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.CONFLICT, "Stock insuficiente para el ingrediente 'Ground Beef'.")));

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo, null, inventoryService))
        .build();

    String orderJson = "{"
        + "\"deliveryName\": \"Gustavo\","
        + "\"tacos\": [{"
        + "  \"name\": \"Beef Taco\","
        + "  \"quantity\": 5,"
        + "  \"ingredients\": [{\"id\": \"GRBF\"}]"
        + "}]"
        + "}";

    testClient.post()
        .uri("/api/orders")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(orderJson)
        .exchange()
        .expectStatus().isEqualTo(org.springframework.http.HttpStatus.CONFLICT);

    // Verificamos que NO se guardó la orden (no se vendió aire)
    Mockito.verify(repo, Mockito.never()).save(any(TacoOrder.class));
  }

  // Ejercicio 16: Reservar y liberar inventario sin vender aire (Liberar stock al eliminar orden)
  @Test
  public void deleteOrder_shouldReleaseInventoryBeforeDeleting() {
    OrderRepository repo = Mockito.mock(OrderRepository.class);
    OrderMessagingService messagingService = Mockito.mock(OrderMessagingService.class);
    EmailOrderService emailService = Mockito.mock(EmailOrderService.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    TacoRepository tacoRepo = Mockito.mock(TacoRepository.class);
    CouponEngine couponEngine = Mockito.mock(CouponEngine.class);
    InventoryService inventoryService = Mockito.mock(InventoryService.class);

    TacoOrder existingOrder = new TacoOrder();
    existingOrder.setId("ORDER_TO_CANCEL");

    when(repo.findById("ORDER_TO_CANCEL")).thenReturn(Mono.just(existingOrder));
    when(inventoryService.releaseInventory(existingOrder)).thenReturn(Mono.empty());
    when(repo.deleteById("ORDER_TO_CANCEL")).thenReturn(Mono.empty());

    WebTestClient testClient = WebTestClient.bindToController(
        new OrderApiController(repo, messagingService, emailService, ingredientRepo, tacoRepo, couponEngine, inventoryService))
        .build();

    testClient.delete()
        .uri("/api/orders/ORDER_TO_CANCEL")
        .exchange()
        .expectStatus().isNoContent();

    verify(inventoryService).releaseInventory(existingOrder);
    verify(repo).deleteById("ORDER_TO_CANCEL");
  }
}