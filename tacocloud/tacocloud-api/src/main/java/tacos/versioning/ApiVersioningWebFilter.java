package tacos.versioning;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

// Ejercicio 35: Versionar la API y publicar contrato OpenAPI
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 15) // Just after CorrelationIdWebFilter (+10)
public class ApiVersioningWebFilter implements WebFilter {

  public static final String HEADER_API_VERSION = "X-API-Version";
  public static final String HEADER_SUPPORTED_VERSIONS = "X-Supported-Versions";
  public static final String HEADER_DEPRECATION = "Deprecation";
  public static final String HEADER_SUNSET = "Sunset";

  public static final String V1 = "1.0";
  public static final String V2 = "2.0";
  public static final String SUPPORTED_VERSIONS_VALUE = "1.0, 2.0";
  public static final String V1_SUNSET_DATE = "Wed, 31 Dec 2027 23:59:59 GMT";

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    if (path != null && path.startsWith("/api/")) {
      String resolvedVersion = resolveVersion(exchange, path);

      exchange.getResponse().beforeCommit(() -> {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        if (!headers.containsKey(HEADER_API_VERSION)) {
          headers.set(HEADER_API_VERSION, resolvedVersion);
        }
        if (!headers.containsKey(HEADER_SUPPORTED_VERSIONS)) {
          headers.set(HEADER_SUPPORTED_VERSIONS, SUPPORTED_VERSIONS_VALUE);
        }
        // Señalizar deprecación de v1 en favor de v2
        if (V1.equals(resolvedVersion) && (path.startsWith("/api/v1/") || path.startsWith("/api/orders"))) {
          if (!headers.containsKey(HEADER_SUNSET)) {
            headers.set(HEADER_SUNSET, V1_SUNSET_DATE);
          }
        }
        return Mono.empty();
      });
    }

    return chain.filter(exchange);
  }

  public String resolveVersion(ServerWebExchange exchange, String path) {
    // 1. Detección por path
    if (path.startsWith("/api/v2/") || path.equals("/api/v2")) {
      return V2;
    }
    if (path.startsWith("/api/v1/") || path.equals("/api/v1")) {
      return V1;
    }

    // 2. Detección por cabecera X-API-Version
    String headerVersion = exchange.getRequest().getHeaders().getFirst(HEADER_API_VERSION);
    if (headerVersion != null) {
      String clean = headerVersion.trim();
      if (clean.equals("2") || clean.startsWith("2.")) {
        return V2;
      }
      if (clean.equals("1") || clean.startsWith("1.")) {
        return V1;
      }
    }

    // 3. Detección por cabecera Accept (Content Negotiation)
    String accept = exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT);
    if (accept != null) {
      if (accept.contains("vnd.tacocloud.v2")) {
        return V2;
      }
      if (accept.contains("vnd.tacocloud.v1")) {
        return V1;
      }
    }

    // 4. Default retrocompatible para /api/... sin prefijo
    return V1;
  }
}
