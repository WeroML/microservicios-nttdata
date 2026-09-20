package tacos.versioning;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

// Ejercicio 35: Versionar la API y publicar contrato OpenAPI
@RestController
@RequestMapping(path = "/api/versions", produces = "application/json")
@CrossOrigin(origins = "*")
public class ApiVersionController {

  private final List<ApiVersionInfo> versions = Arrays.asList(
      ApiVersionInfo.builder()
          .version("v1")
          .status("SUPPORTED")
          .description("API Taco Cloud v1 - Endpoints canónicos y retrocompatibles con modelo TacoOrder.")
          .releaseDate("2026-01-15")
          .sunsetDate("2027-12-31")
          .documentationUrl("/swagger-ui.html?urls.primaryName=Taco%20Cloud%20API%20v1")
          .openApiUrl("/v3/api-docs/v1")
          .supportedFeatures(Arrays.asList(
              "URI path versioning (/api/v1/...)",
              "Legacy unversioned alias (/api/...)",
              "Idempotency-Key support",
              "Correlation-ID tracking",
              "RFC 7807 Problem Details"
          ))
          .build(),
      ApiVersionInfo.builder()
          .version("v2")
          .status("CURRENT")
          .description("API Taco Cloud v2 - DTOs estructurados y desacoplados (OrderV2Request/Response) con HATEOAS.")
          .releaseDate("2026-09-19")
          .sunsetDate(null)
          .documentationUrl("/swagger-ui.html?urls.primaryName=Taco%20Cloud%20API%20v2")
          .openApiUrl("/v3/api-docs/v2")
          .supportedFeatures(Arrays.asList(
              "URI path versioning (/api/v2/...)",
              "Header version negotiation (X-API-Version: 2)",
              "Structured OrderV2Request / OrderV2Response",
              "Pricing breakdown (subTotal, discount, tax, total)",
              "HATEOAS navigational links",
              "Real-time kitchen tracking & ETA estimation"
          ))
          .build()
  );

  @GetMapping
  public Mono<Map<String, Object>> getApiVersions() {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("service", "Taco Cloud REST API");
    response.put("currentVersion", "v2");
    response.put("supportedVersions", Arrays.asList("v1", "v2"));
    response.put("openApiDocs", "/v3/api-docs");
    response.put("swaggerUi", "/swagger-ui.html");
    response.put("versions", versions);
    return Mono.just(response);
  }
}
