package tacos.web.api.correlation;

import java.util.UUID;

import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

// Ejercicio 31: Correlation ID de HTTP a evento y logs
public final class CorrelationIdSupport {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  public static final String REQUEST_ID_HEADER = "X-Request-ID";
  public static final String CORRELATION_ID_KEY = "CORRELATION_ID";

  private CorrelationIdSupport() {
    // Utility class
  }

  /**
   * Obtiene el Correlation ID del contexto reactivo de Reactor (ContextView).
   * Si no está presente, genera un nuevo identificador UUID.
   */
  public static Mono<String> getCorrelationId() {
    return Mono.deferContextual(ctx -> {
      if (ctx.hasKey(CORRELATION_ID_KEY)) {
        String cid = ctx.get(CORRELATION_ID_KEY);
        if (cid != null && !cid.trim().isEmpty()) {
          return Mono.just(cid.trim());
        }
      }
      return Mono.just(UUID.randomUUID().toString());
    });
  }

  /**
   * Resuelve el Correlation ID desde un valor de encabezado o genera uno nuevo si es nulo o vacío.
   */
  public static String resolveOrGenerate(String headerValue) {
    if (headerValue != null && !headerValue.trim().isEmpty()) {
      return headerValue.trim();
    }
    return UUID.randomUUID().toString();
  }

  /**
   * Extrae el Correlation ID de los atributos o encabezados de ServerWebExchange.
   */
  public static String extractFromExchange(ServerWebExchange exchange) {
    if (exchange == null) {
      return UUID.randomUUID().toString();
    }
    Object attr = exchange.getAttribute(CORRELATION_ID_KEY);
    if (attr instanceof String && !((String) attr).trim().isEmpty()) {
      return (String) attr;
    }
    String header = exchange.getRequest().getHeaders().getFirst(CORRELATION_ID_HEADER);
    if (header == null || header.trim().isEmpty()) {
      header = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
    }
    return resolveOrGenerate(header);
  }

}
