package tacos.web.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import tacos.data.TacoRepository;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

public class ProblemDetailsExceptionHandlerTest {

  @Test
  public void postTaco_whenValidationFails_shouldReturnProblemDetailsWithInvalidParams() {
    TacoRepository tacoRepo = mock(TacoRepository.class);

    // Configuramos WebTestClient enlazando TacoController junto con el ProblemDetailsExceptionHandler
    WebTestClient testClient = WebTestClient.bindToController(new TacoController(tacoRepo))
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .build();

    // Enviamos un JSON inválido:
    // 1. name tiene solo 2 letras (mínimo requerido: 5)
    // 2. ingredients está vacío (mínimo requerido: 1)
    String invalidJson = "{\"name\": \"No\", \"ingredients\": []}";

    testClient.post()
        .uri("/api/tacos")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalidJson)
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
          .jsonPath("$.type").isEqualTo("https://tacocloud.com/errors/validation-failed")
          .jsonPath("$.title").isEqualTo("Validation Failed")
          .jsonPath("$.status").isEqualTo(400)
          .jsonPath("$.invalidParams").isArray()
          .jsonPath("$.invalidParams[?(@.name == 'name')].reason").isNotEmpty()
          .jsonPath("$.invalidParams[?(@.name == 'ingredients')].reason").isNotEmpty();
  }

  @Test
  public void getTacoById_whenNotFound_shouldReturnProblemDetails() {
    TacoRepository tacoRepo = mock(TacoRepository.class);

    when(tacoRepo.findById("999")).thenReturn(Mono.error(
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Taco no encontrado")));

    WebTestClient testClient = WebTestClient.bindToController(new TacoController(tacoRepo))
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .build();

    testClient.get()
        .uri("/api/tacos/999")
        .exchange()
        .expectStatus().isNotFound()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
          .jsonPath("$.type").isEqualTo("https://tacocloud.com/errors/not_found")
          .jsonPath("$.title").isEqualTo("Not Found")
          .jsonPath("$.status").isEqualTo(404)
          .jsonPath("$.detail").isEqualTo("Taco no encontrado");
  }
}
