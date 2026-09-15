package tacos.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.PaymentMethod;
import tacos.User;
import tacos.data.IngredientRepository;
import tacos.data.PaymentMethodRepository;
import tacos.data.UserRepository;
import tacos.web.api.EmailOrder.EmailTaco;

public class EmailOrderServiceTest {

    //Test para ejercicio 6: Convertir órdenes de correo sin carreras ni nulls sorpresa
  @Test
  public void convertEmailOrder_shouldConvertSuccessfullyWithoutRaceConditions() {
    // 1. ARRANGE (Mocks de repositorios)
    UserRepository userRepo = Mockito.mock(UserRepository.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    PaymentMethodRepository paymentMethodRepo = Mockito.mock(PaymentMethodRepository.class);

    // Datos simulados
    User user = new User("craig", "pass", "Craig Walls", "123 Street", "City", "State", "76227", "123", "craig@habuma.com");
    user.setId("USER1");

    PaymentMethod paymentMethod = new PaymentMethod(user, "4111111111111111", "123", "12/25");
    Ingredient flourTortilla = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP);

    // Petición de EmailOrder simulada
    EmailTaco emailTaco = new EmailTaco();
    emailTaco.setName("Taco de Prueba");
    emailTaco.setIngredients(Arrays.asList("FLTO"));

    EmailOrder emailOrder = new EmailOrder();
    emailOrder.setEmail("craig@habuma.com");
    emailOrder.setTacos(Arrays.asList(emailTaco));

    // Programación de comportamiento de los Mocks
    when(userRepo.findByEmail("craig@habuma.com")).thenReturn(Mono.just(user));
    when(paymentMethodRepo.findByUserId("USER1")).thenReturn(Mono.just(paymentMethod));
    when(ingredientRepo.findById("FLTO")).thenReturn(Mono.just(flourTortilla));

    EmailOrderService service = new EmailOrderService(userRepo, ingredientRepo, paymentMethodRepo);

    // 2. ACT & 3. ASSERT con StepVerifier
    StepVerifier.create(service.convertEmailOrderToDomainOrder(Mono.just(emailOrder)))
        .assertNext(order -> {
          // Verificar datos del usuario y dirección
          assertThat(order.getUser().getEmail()).isEqualTo("craig@habuma.com");
          assertThat(order.getDeliveryName()).isEqualTo("Craig Walls");
          assertThat(order.getCcNumber()).isEqualTo("4111111111111111");

          //VERIFICACIÓN CLAVE SIN CONDICIONES DE CARRERA:
          assertThat(order.getTacos()).hasSize(1);
          assertThat(order.getTacos().get(0).getName()).isEqualTo("Taco de Prueba");
          
          // Comprobar que los ingredientes fueron cargados completamente en la orden
          List<Ingredient> ingredients = order.getTacos().get(0).getIngredients();
          assertThat(ingredients).hasSize(1);
          assertThat(ingredients.get(0).getId()).isEqualTo("FLTO");
          assertThat(ingredients.get(0).getName()).isEqualTo("Flour Tortilla");
        })
        .verifyComplete();
  }

  //Test para ejercicio 6: Convertir órdenes de correo sin carreras ni nulls sorpresa
  @Test
  public void convertEmailOrder_whenUserNotFound_shouldReturn404() {
    // ARRANGE
    UserRepository userRepo = Mockito.mock(UserRepository.class);
    IngredientRepository ingredientRepo = Mockito.mock(IngredientRepository.class);
    PaymentMethodRepository paymentMethodRepo = Mockito.mock(PaymentMethodRepository.class);

    EmailOrder emailOrder = new EmailOrder();
    emailOrder.setEmail("desconocido@email.com");

    // Simulamos que el usuario NO existe
    when(userRepo.findByEmail("desconocido@email.com")).thenReturn(Mono.empty());

    EmailOrderService service = new EmailOrderService(userRepo, ingredientRepo, paymentMethodRepo);

    // ACT & ASSERT (Verificamos que emita un ResponseStatusException)
    StepVerifier.create(service.convertEmailOrderToDomainOrder(Mono.just(emailOrder)))
        .expectError(ResponseStatusException.class)
        .verify();
  }
}