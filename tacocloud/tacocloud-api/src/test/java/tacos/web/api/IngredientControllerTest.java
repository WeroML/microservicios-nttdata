package tacos.web.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Ingredient.Type;
import tacos.data.IngredientRepository;

public class IngredientControllerTest {

//Mockear para que encuentre el ID, ejecutar PUT y esperar isOk() y que se haya guardado el ingrediente
  @Test
  public void update_existing_returns_200_and_persists() {
    //Arrange
    // 1. Mockear el IngredientRepository
    IngredientRepository repo = Mockito.mock(IngredientRepository.class);

    //Crear el ingrediente original y el ingrediente actualizado
    Ingredient originalIngredient = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP);
    Ingredient updatedIngredient = new Ingredient("FLTO", "Updated Flour Tortilla", Type.WRAP);

    //Preparar lo que se espera que haga el Mock del repositorio
    //Cuando le pidan al Mock que busque por ID, devolver el ingrediente original
    when(repo.findById("FLTO")).thenReturn(Mono.just(originalIngredient));

    //Cuando le pidan al Mock que guarde el ingrediente actualizado, devolver el ingrediente actualizado
    when(repo.save(updatedIngredient)).thenReturn(Mono.just(updatedIngredient));

    // 2. Configurar el WebTestClient.bindToController(new IngredientController(repo)).build()
    WebTestClient testClient = WebTestClient.bindToController(new IngredientController(repo)).build();

    //Act and Assert

    // 3. Ejecutar testClient.put()... y verificar 200 OK
    testClient.put()
        .uri("/api/ingredients/FLTO")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(updatedIngredient)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.id").isEqualTo("FLTO")
        .jsonPath("$.name").isEqualTo("Updated Flour Tortilla")
        .jsonPath("$.type").isEqualTo("WRAP");
  }

  // Mockear para que no encuentre el ID, ejecutar PUT y esperar isNotFound()
  @Test
  public void update_missing_returns_404_without_save() {
    //Arrange
    // 1. Mockear el IngredientRepository
    IngredientRepository repo = Mockito.mock(IngredientRepository.class);

    //Crear el ingrediente actualizado
    Ingredient updatedIngredient = new Ingredient("FLTO", "Updated Flour Tortilla", Type.WRAP);

    //Preparar lo que se espera que haga el Mock del repositorio
    //Cuando le pidan al Mock que busque por ID, devolver Mono.empty() para simular que no se encontró el ingrediente
    when(repo.findById("FLTO")).thenReturn(Mono.empty());

    // 2. Configurar el WebTestClient.bindToController(new IngredientController(repo)).build()
    WebTestClient testClient = WebTestClient.bindToController(new IngredientController(repo)).build();

    //Act and Assert

    // 3. Ejecutar testClient.put()... y verificar 404 Not Found
    testClient.put()
        .uri("/api/ingredients/FLTO")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(updatedIngredient)
        .exchange()
        .expectStatus().isNotFound();

    //Verificar que no se haya llamado a repo.save() ya que el ingrediente no existía
    verify(repo, never()).save(any(Ingredient.class));
  }

  // Enviar IDs diferentes en URL y Body, esperar isBadRequest()
  @Test
  public void update_mismatched_id_returns_400() {
    //Arrange
    // 1. Mockear el IngredientRepository
    IngredientRepository repo = Mockito.mock(IngredientRepository.class);

    //Crear el ingrediente actualizado con un ID diferente al de la URL
    Ingredient updatedIngredient = new Ingredient("FLTO", "Updated Flour Tortilla", Type.WRAP);

    // 2. Configurar el WebTestClient.bindToController(new IngredientController(repo)).build()
    WebTestClient testClient = WebTestClient.bindToController(new IngredientController(repo)).build();

    //Act and Assert

    // 3. Ejecutar testClient.put()... y verificar 400 Bad Request
    testClient.put()
        .uri("/api/ingredients/WRNG") // ID diferente al del Body
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(updatedIngredient)
        .exchange()
        .expectStatus().isBadRequest();

    //Verificar que no se haya llamado a repo.save() ya que los IDs no coinciden
    verify(repo, never()).save(any(Ingredient.class));
  }
}