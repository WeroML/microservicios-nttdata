package tacos.openapi;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Mono;

// Ejercicio 35: Versionar la API y publicar contrato OpenAPI
@RestController
@CrossOrigin(origins = "*")
public class OpenApiController {

  private final OpenApiContractService contractService;

  @Autowired
  public OpenApiController(OpenApiContractService contractService) {
    this.contractService = contractService;
  }

  @GetMapping(path = {"/v3/api-docs", "/api/openapi.json"}, produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<Map<String, Object>> getOpenApiDocs() {
    return Mono.just(contractService.generateContract(null));
  }

  @GetMapping(path = "/v3/api-docs/{version}", produces = MediaType.APPLICATION_JSON_VALUE)
  public Mono<Map<String, Object>> getOpenApiDocsByVersion(@PathVariable("version") String version) {
    return Mono.just(contractService.generateContract(version));
  }

  @GetMapping(path = {"/v3/api-docs.yaml", "/api/openapi.yaml"}, produces = {"application/yaml", "text/yaml", "text/plain"})
  public Mono<String> getOpenApiDocsYaml() {
    return Mono.just(contractService.generateContractYaml(null));
  }

  @GetMapping(path = "/v3/api-docs/{version}.yaml", produces = {"application/yaml", "text/yaml", "text/plain"})
  public Mono<String> getOpenApiDocsYamlByVersion(@PathVariable("version") String version) {
    return Mono.just(contractService.generateContractYaml(version));
  }
}
