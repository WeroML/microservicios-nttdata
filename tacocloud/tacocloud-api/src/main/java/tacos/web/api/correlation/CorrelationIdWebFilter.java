package tacos.web.api.correlation;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

// Ejercicio 31: Correlation ID de HTTP a evento y logs
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class CorrelationIdWebFilter implements WebFilter {

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
    String header = exchange.getRequest().getHeaders().getFirst(CorrelationIdSupport.CORRELATION_ID_HEADER);
    if (header == null || header.trim().isEmpty()) {
      header = exchange.getRequest().getHeaders().getFirst(CorrelationIdSupport.REQUEST_ID_HEADER);
    }

    final String correlationId = CorrelationIdSupport.resolveOrGenerate(header);

    // Asegurar que el encabezado viaje en la respuesta HTTP
    exchange.getResponse().getHeaders().set(CorrelationIdSupport.CORRELATION_ID_HEADER, correlationId);
    exchange.getAttributes().put(CorrelationIdSupport.CORRELATION_ID_KEY, correlationId);

    log.info("// Ejercicio 31: [HTTP IN] method={}, path={}, correlationId={}",
        exchange.getRequest().getMethod(),
        exchange.getRequest().getPath(),
        correlationId);

    return chain.filter(exchange)
        .doFinally(signalType -> {
          log.info("// Ejercicio 31: [HTTP OUT] status={}, correlationId={}, signal={}",
              exchange.getResponse().getStatusCode(),
              correlationId,
              signalType);
          MDC.remove("correlationId");
        })
        .contextWrite(Context.of(CorrelationIdSupport.CORRELATION_ID_KEY, correlationId));
  }

}
