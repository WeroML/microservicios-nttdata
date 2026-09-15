import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.data.OrderRepository;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.EmailOrderService;
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
}