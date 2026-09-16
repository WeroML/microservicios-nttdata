//Ejercicio 9: Validación y errores tipo Problem Details
package tacos.web.api.errors;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Estructura de error estandarizada según RFC 7807 (Problem Details for HTTP APIs).
 * Proporciona un formato uniforme para comunicar errores y fallos de validación en la API.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetails {

  /**
   * URI absoluta o relativa que identifica el tipo de problema.
   */
  @Builder.Default
  private URI type = URI.create("about:blank");

  /**
   * Resumen corto y legible por humanos del problema.
   */
  private String title;

  /**
   * Código de estado HTTP correspondiente (ej. 400, 404, 500).
   */
  private int status;

  /**
   * Explicación detallada específica para esta ocurrencia del problema.
   */
  private String detail;

  /**
   * URI que identifica la ruta o recurso donde ocurrió el problema.
   */
  private URI instance;

  /**
   * Momento exacto (UTC) en el que se generó el error.
   */
  @Builder.Default
  private Instant timestamp = Instant.now();

  /**
   * Extensión RFC 7807: Lista de parámetros que no cumplieron las validaciones.
   */
  @Builder.Default
  private List<InvalidParam> invalidParams = new ArrayList<>();

  @Data
  @NoArgsConstructor
  @AllArgsConstructor
  public static class InvalidParam {
    private String name;
    private String reason;
  }
}
