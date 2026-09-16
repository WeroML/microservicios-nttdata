package tacos.web.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Flux;
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

  // Eliminar un ingrediente existente, esperar isNoContent() y que se haya llamado a deleteById
  @Test
  public void delete_existing_returns_204_and_deletes() {
    //Arrange
    // 1. Mockear el IngredientRepository
    IngredientRepository repo = Mockito.mock(IngredientRepository.class);

    //Preparar lo que se espera que haga el Mock del repositorio
    //Cuando le pidan al Mock que busque por ID, devolver un Mono vacío para simular que el ingrediente existe
    when(repo.findById("FLTO")).thenReturn(Mono.just(new Ingredient("FLTO", "Flour Tortilla", Type.WRAP)));

    //Cuando le pidan al Mock que elimine por ID, devolver Mono.empty() para simular la eliminación exitosa
    when(repo.deleteById("FLTO")).thenReturn(Mono.empty());

    // 2. Configurar el WebTestClient.bindToController(new IngredientController(repo)).build()
    WebTestClient testClient = WebTestClient.bindToController(new IngredientController(repo)).build();

    //Act and Assert

    // 3. Ejecutar testClient.delete()... y verificar 204 No Content
    testClient.delete()
        .uri("/api/ingredients/FLTO")
        .exchange()
        .expectStatus().isNoContent();

    //Verificar que se haya llamado a repo.deleteById() con el ID correcto
    verify(repo).deleteById("FLTO");
  }

//Test para el ejercicio 3 (Método postIngredient) que verifica que se cree un ingrediente y se retorne 201 Created 
// con la cabecera Location correcta
@Test
public void postIngredient_shouldCreateIngredientAndReturn21CreatedWithLocationHeader() {
  //Arrange 
  //1. Preparamos el mock del repositorio y los ingredientes de prueba
  IngredientRepository repo = Mockito.mock(IngredientRepository.class);
  Ingredient unsaved = new Ingredient("TEST", "Test Ingredient", Type.SAUCE);
  Ingredient saved = new Ingredient("TEST", "Test Ingredient", Type.SAUCE);

  // Programamos el mock para que responda con el ingrediente guardado
  when(repo.save(any(Ingredient.class))).thenReturn(Mono.just(saved));

  WebTestClient testClient = WebTestClient.bindToController(
      new IngredientController(repo)).build();

  //Act and Assert

  //2. Ejecutamos la solicitud POST y verificamos el resultado
  testClient.post()
      .uri("/api/ingredients")
      .contentType(MediaType.APPLICATION_JSON)
      .body(Mono.just(unsaved), Ingredient.class)
      .exchange()
      .expectStatus().isCreated() // 1. Verifica HTTP 201 Created
      .expectHeader().valueEquals("Location", "/api/ingredients/TEST")
      .expectBody(Ingredient.class) // 3. Verifica el cuerpo
      .isEqualTo(saved);

  // 3. Verificamos que el método save del repositorio haya sido 
  // llamado con cualquier objeto de tipo Ingredient
  verify(repo).save(any(Ingredient.class));
}

  // Test para Ejercicio 13: Catálogo con precio, disponibilidad y stock
  @Test
  public void allIngredients_withAvailableFilter_shouldReturnOnlyAvailable() {
    IngredientRepository repo = Mockito.mock(IngredientRepository.class);
    Ingredient availableIng = new Ingredient("FLTO", "Flour Tortilla", Type.WRAP, new java.math.BigDecimal("1.50"), true, 10);
    when(repo.findByAvailableTrue()).thenReturn(Flux.just(availableIng));

    WebTestClient testClient = WebTestClient.bindToController(new IngredientController(repo)).build();

    testClient.get()
        .uri("/api/ingredients?available=true")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$[0].id").isEqualTo("FLTO")
          .jsonPath("$[0].price").isEqualTo(1.50)
          .jsonPath("$[0].available").isEqualTo(true)
          .jsonPath("$[0].stock").isEqualTo(10);

    verify(repo).findByAvailableTrue();
  }

  // Test para Ejercicio 13: Catálogo con precio, disponibilidad y stock
  @Test
  public void patchIngredient_shouldUpdatePriceAvailabilityAndStock() {
    IngredientRepository repo = Mockito.mock(IngredientRepository.class);
    Ingredient existing = new Ingredient("COTO", "Corn Tortilla", Type.WRAP, new java.math.BigDecimal("1.00"), true, 20);

    when(repo.findById("COTO")).thenReturn(Mono.just(existing));
    when(repo.save(any(Ingredient.class))).thenAnswer(i -> Mono.just(i.getArgument(0)));

    WebTestClient testClient = WebTestClient.bindToController(new IngredientController(repo)).build();

    testClient.patch()
        .uri("/api/ingredients/COTO")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"price\": 1.75, \"available\": false, \"stock\": 0}")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
          .jsonPath("$.id").isEqualTo("COTO")
          .jsonPath("$.price").isEqualTo(1.75)
          .jsonPath("$.available").isEqualTo(false)
          .jsonPath("$.stock").isEqualTo(0);

    verify(repo).save(any(Ingredient.class));
  }
}