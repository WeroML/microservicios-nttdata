package tacos.web.api.errors;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

/**
 * Manejador global de excepciones para traducir fallos de validación
 * y excepciones HTTP a respuestas estructuradas bajo Problem Details (RFC 7807).
 */
@RestControllerAdvice
public class ProblemDetailsExceptionHandler {

  public static final MediaType PROBLEM_JSON_MEDIA_TYPE = MediaType.parseMediaType("application/problem+json");

  /**
   * Captura fallos de validación en entornos reactivos (Spring WebFlux).
   */
  @ExceptionHandler(WebExchangeBindException.class)
  public ResponseEntity<ProblemDetails> handleWebExchangeBindException(
      WebExchangeBindException ex, ServerWebExchange exchange) {
    URI path = (exchange != null && exchange.getRequest() != null) ? exchange.getRequest().getURI() : null;
    return buildValidationProblemDetails(ex.getBindingResult(), path, HttpStatus.BAD_REQUEST);
  }

  /**
   * Captura fallos de validación en entornos Servlet tradicionales (Spring MVC).
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetails> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException ex) {
    return buildValidationProblemDetails(ex.getBindingResult(), null, HttpStatus.BAD_REQUEST);
  }

  /**
   * Captura excepciones de estado HTTP (ResponseStatusException).
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<ProblemDetails> handleResponseStatusException(
      ResponseStatusException ex, ServerWebExchange exchange) {
    HttpStatus status = ex.getStatus();
    URI path = (exchange != null && exchange.getRequest() != null) ? exchange.getRequest().getURI() : null;

    ProblemDetails problem = ProblemDetails.builder()
        .type(URI.create("https://tacocloud.com/errors/" + status.name().toLowerCase()))
        .title(status.getReasonPhrase())
        .status(status.value())
        .detail(ex.getReason() != null ? ex.getReason() : status.getReasonPhrase())
        .instance(path)
        .build();

    return ResponseEntity.status(status)
        .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON_MEDIA_TYPE.toString())
        .body(problem);
  }

  private ResponseEntity<ProblemDetails> buildValidationProblemDetails(
      BindingResult bindingResult, URI requestUri, HttpStatus status) {

    List<ProblemDetails.InvalidParam> invalidParams = bindingResult.getFieldErrors().stream()
        .map(fieldError -> new ProblemDetails.InvalidParam(
            fieldError.getField(),
            fieldError.getDefaultMessage()))
        .collect(Collectors.toList());

    ProblemDetails problem = ProblemDetails.builder()
        .type(URI.create("https://tacocloud.com/errors/validation-failed"))
        .title("Validation Failed")
        .status(status.value())
        .detail("Input validation failed with " + invalidParams.size() + " error(s)")
        .instance(requestUri)
        .invalidParams(invalidParams)
        .build();

    return ResponseEntity.status(status)
        .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON_MEDIA_TYPE.toString())
        .body(problem);
  }
}
