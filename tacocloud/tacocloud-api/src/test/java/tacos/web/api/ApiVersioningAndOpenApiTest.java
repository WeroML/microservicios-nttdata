package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.User;
import tacos.actuator.BusinessMetricsService;
import tacos.data.IngredientRepository;
import tacos.data.OrderRepository;
import tacos.data.TacoRepository;
import tacos.data.UserRepository;
import tacos.idempotency.OrderIdempotencyService;
import tacos.messaging.OrderMessagingService;
import tacos.openapi.OpenApiController;
import tacos.openapi.OpenApiContractService;
import tacos.openapi.SwaggerUiController;
import tacos.outbox.TransactionalOutboxService;
import tacos.versioning.ApiVersionController;
import tacos.versioning.ApiVersioningWebFilter;
import tacos.web.api.dto.v2.OrderV2Dto.AddressDto;
import tacos.web.api.dto.v2.OrderV2Dto.OrderItemV2Request;
import tacos.web.api.dto.v2.OrderV2Dto.OrderV2Request;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;
import tacos.web.api.v2.OrderApiV2Controller;

// Ejercicio 35: Versionar la API y publicar contrato OpenAPI
public class ApiVersioningAndOpenApiTest {

  private OrderRepository orderRepo;
  private OrderMessagingService orderMessages;
  private EmailOrderService emailOrderService;
  private IngredientRepository ingredientRepo;
  private TacoRepository tacoRepo;
  private InventoryService inventoryService;
  private UserRepository userRepo;
  private TransactionalOutboxService outboxService;
  private BusinessMetricsService metricsService;
  private OrderIdempotencyService idempotencyService;

  private OrderApiController v1Controller;
  private OrderApiV2Controller v2Controller;
  private ApiVersionController versionController;
  private OpenApiController openApiController;
  private SwaggerUiController swaggerUiController;
  private OpenApiContractService contractService;

  private WebTestClient testClient;

  @BeforeEach
  void setUp() {
    orderRepo = mock(OrderRepository.class);
    orderMessages = mock(OrderMessagingService.class);
    emailOrderService = mock(EmailOrderService.class);
    ingredientRepo = mock(IngredientRepository.class);
    tacoRepo = mock(TacoRepository.class);
    inventoryService = mock(InventoryService.class);
    userRepo = mock(UserRepository.class);
    outboxService = mock(TransactionalOutboxService.class);

    metricsService = new BusinessMetricsService(new SimpleMeterRegistry());
    idempotencyService = new OrderIdempotencyService(null, metricsService);

    User userAlice = new User("alice", "password", "Alice Smith", "123 Main", "CDMX", "CDMX", "06500", "555-1111", "alice@test.com");
    userAlice.setId("USER_ALICE");
    when(userRepo.findByUsername("alice")).thenReturn(Mono.just(userAlice));

    Ingredient flto = new Ingredient("FLTO", "Flour Tortilla", Ingredient.Type.WRAP, new BigDecimal("10.00"), true, 50);
    Ingredient grbf = new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, new BigDecimal("25.00"), true, 40);
    when(ingredientRepo.findById(any(String.class))).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      if ("FLTO".equals(id)) return Mono.just(flto);
      if ("GRBF".equals(id)) return Mono.just(grbf);
      return Mono.just(new Ingredient(id, id, Ingredient.Type.WRAP, BigDecimal.TEN, true, 10));
    });
    when(tacoRepo.findById(any(String.class))).thenReturn(Mono.empty());

    when(inventoryService.reserveInventory(any(TacoOrder.class)))
        .thenAnswer(inv -> Mono.just(inv.getArgument(0)));
    when(outboxService.enqueueOrder(any(TacoOrder.class), any()))
        .thenReturn(Mono.just(new tacos.outbox.OutboxMessage()));

    when(orderRepo.save(any(TacoOrder.class))).thenAnswer(inv -> {
      TacoOrder ord = inv.getArgument(0);
      if (ord.getId() == null) {
        ord.setId("ORD-" + System.nanoTime());
      }
      return Mono.just(ord);
    });

    when(orderRepo.findById(any(String.class))).thenAnswer(inv -> {
      String id = inv.getArgument(0);
      TacoOrder ord = new TacoOrder();
      ord.setId(id);
      ord.setDeliveryName("Alice Smith");
      ord.setDeliveryStreet("123 Main");
      ord.setDeliveryCity("CDMX");
      ord.setDeliveryState("CDMX");
      ord.setDeliveryZip("06500");
      ord.setUser(userAlice);
      ord.setStatus(TacoOrder.OrderStatus.CONFIRMED);
      return Mono.just(ord);
    });

    when(orderRepo.findAll()).thenAnswer(inv -> {
      TacoOrder ord = new TacoOrder();
      ord.setId("ORD-ALICE-1");
      ord.setDeliveryName("Alice Smith");
      ord.setDeliveryStreet("123 Main");
      ord.setDeliveryCity("CDMX");
      ord.setDeliveryState("CDMX");
      ord.setDeliveryZip("06500");
      ord.setUser(userAlice);
      ord.setStatus(TacoOrder.OrderStatus.CONFIRMED);
      return Flux.just(ord);
    });

    v1Controller = new OrderApiController(
        orderRepo,
        orderMessages,
        emailOrderService,
        ingredientRepo,
        tacoRepo,
        null,
        inventoryService,
        null,
        userRepo,
        null,
        outboxService,
        metricsService,
        idempotencyService
    );

    v2Controller = new OrderApiV2Controller(v1Controller, orderRepo);
    versionController = new ApiVersionController();
    contractService = new OpenApiContractService();
    openApiController = new OpenApiController(contractService);
    swaggerUiController = new SwaggerUiController();

    testClient = WebTestClient.bindToController(
        v1Controller,
        v2Controller,
        versionController,
        openApiController,
        swaggerUiController
    )
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .webFilter(new tacos.web.api.correlation.CorrelationIdWebFilter())
        .webFilter(new ApiVersioningWebFilter())
        .webFilter((exchange, chain) -> {
          String testUser = exchange.getRequest().getHeaders().getFirst("X-Test-User");
          if (testUser != null && !testUser.trim().isEmpty()) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            Authentication auth = new UsernamePasswordAuthenticationToken(testUser.trim(), "password", authorities);
            return chain.filter(exchange.mutate().principal(Mono.just(auth)).build());
          }
          return chain.filter(exchange);
        })
        .build();
  }

  // ==========================================
  // CONTRATO OPENAPI 3.0 (JSON Y YAML)
  // ==========================================

  @Test
  @DisplayName("1. Contrato OpenAPI 3.0 unificado (/v3/api-docs): Retorna especificación JSON válida")
  void testOpenApiContract_UnifiedSpec_ReturnsValidOpenApi3Json() {
    testClient.get()
        .uri("/v3/api-docs")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.openapi").isEqualTo("3.0.3")
        .jsonPath("$.info.title").isNotEmpty()
        .jsonPath("$.paths['/api/v1/orders']").exists()
        .jsonPath("$.paths['/api/v2/orders']").exists()
        .jsonPath("$.paths['/api/versions']").exists()
        .jsonPath("$.components.schemas['TacoOrder']").exists()
        .jsonPath("$.components.schemas['OrderV2Request']").exists()
        .jsonPath("$.components.schemas['OrderV2Response']").exists()
        .jsonPath("$.components.schemas['ProblemDetails']").exists()
        .jsonPath("$.components.securitySchemes['basicAuth']").exists();
  }

  @Test
  @DisplayName("2. Contratos OpenAPI 3.0 separados por versión (/v3/api-docs/v1 y /v3/api-docs/v2)")
  void testOpenApiContract_VersionedV1AndV2_SeparateContracts() {
    // V1 Spec
    testClient.get()
        .uri("/v3/api-docs/v1")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.openapi").isEqualTo("3.0.3")
        .jsonPath("$.paths['/api/v1/orders']").exists()
        .jsonPath("$.paths['/api/v2/orders']").doesNotExist();

    // V2 Spec
    testClient.get()
        .uri("/v3/api-docs/v2")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.openapi").isEqualTo("3.0.3")
        .jsonPath("$.paths['/api/v2/orders']").exists()
        .jsonPath("$.paths['/api/v1/orders']").doesNotExist();
  }

  @Test
  @DisplayName("3. Contrato OpenAPI en formato YAML (/v3/api-docs.yaml y /api/openapi.yaml)")
  void testOpenApiContract_YamlFormat() {
    byte[] yamlBytes = testClient.get()
        .uri("/v3/api-docs.yaml")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .returnResult()
        .getResponseBody();

    assertNotNull(yamlBytes);
    String yaml = new String(yamlBytes);
    assertTrue(yaml.contains("openapi: 3.0.3"));
    assertTrue(yaml.contains("/api/v1/orders:"));
    assertTrue(yaml.contains("/api/v2/orders:"));
  }

  // ==========================================
  // SWAGGER UI INTERACTIVO
  // ==========================================

  @Test
  @DisplayName("4. Swagger UI interactivo (/swagger-ui.html): Retorna HTML con explorador")
  void testSwaggerUi_RendersHtmlSuccessfully() {
    byte[] htmlBytes = testClient.get()
        .uri("/swagger-ui.html")
        .accept(MediaType.TEXT_HTML)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
        .expectBody()
        .returnResult()
        .getResponseBody();

    assertNotNull(htmlBytes);
    String html = new String(htmlBytes);
    assertTrue(html.contains("Taco Cloud"));
    assertTrue(html.contains("OpenAPI Contract"));
    assertTrue(html.contains("/v3/api-docs"));
    assertTrue(html.contains("/api/v2/orders"));
  }

  // ==========================================
  // METADATOS DE VERSIONES (/api/versions)
  // ==========================================

  @Test
  @DisplayName("5. Endpoint de versiones (/api/versions): Retorna metadatos de ciclo de vida de la API")
  void testApiVersionsEndpoint_ReturnsMetadata() {
    testClient.get()
        .uri("/api/versions")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .jsonPath("$.service").isEqualTo("Taco Cloud REST API")
        .jsonPath("$.currentVersion").isEqualTo("v2")
        .jsonPath("$.supportedVersions[0]").isEqualTo("v1")
        .jsonPath("$.supportedVersions[1]").isEqualTo("v2")
        .jsonPath("$.versions[?(@.version == 'v1')].status").isEqualTo("SUPPORTED")
        .jsonPath("$.versions[?(@.version == 'v2')].status").isEqualTo("CURRENT");
  }

  // ==========================================
  // FILTRO DE VERSIONADO Y RESPALDO DE CABECERAS
  // ==========================================

  @Test
  @DisplayName("6. Filtro de versionado: Inyecta cabeceras X-API-Version y X-Supported-Versions")
  void testApiVersioningFilter_ResponseHeaders() {
    // V1 Request
    testClient.get()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_API_VERSION, "1.0")
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_SUPPORTED_VERSIONS, "1.0, 2.0")
        .expectHeader().exists(ApiVersioningWebFilter.HEADER_SUNSET);

    // V2 Request
    testClient.get()
        .uri("/api/v2/orders")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_API_VERSION, "2.0")
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_SUPPORTED_VERSIONS, "1.0, 2.0");
  }

  // ==========================================
  // COMPATIBILIDAD V1 Y EVOLUCIÓN V2
  // ==========================================

  @Test
  @DisplayName("7. Creación de orden V1 (/api/v1/orders): Retrocompatible con modelo canónico TacoOrder")
  void testOrderCreation_V1Endpoint_BackwardCompatible() {
    TacoOrder v1Order = new TacoOrder();
    v1Order.setDeliveryName("Alice Smith");
    v1Order.setDeliveryStreet("123 Main");
    v1Order.setDeliveryCity("CDMX");
    v1Order.setDeliveryState("CDMX");
    v1Order.setDeliveryZip("06500");

    Taco taco = new Taco();
    taco.setName("Carnitas");
    taco.setQuantity(2);
    taco.setIngredients(Arrays.asList(
        new Ingredient("FLTO", "Flour Tortilla", Ingredient.Type.WRAP, BigDecimal.TEN, true, 50),
        new Ingredient("GRBF", "Ground Beef", Ingredient.Type.PROTEIN, BigDecimal.valueOf(25), true, 40)
    ));
    v1Order.addTaco(taco);

    testClient.post()
        .uri("/api/v1/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-V1-001")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(v1Order)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("Idempotency-Key", "IDEMP-V1-001")
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_API_VERSION, "1.0")
        .expectBody()
        .jsonPath("$.id").isNotEmpty()
        .jsonPath("$.deliveryName").isEqualTo("Alice Smith");
  }

  @Test
  @DisplayName("8. Creación de orden V2 (/api/v2/orders): Acepta OrderV2Request y retorna OrderV2Response estructurado con HATEOAS")
  void testOrderCreation_V2Endpoint_AcceptsStructuredDtoAndReturnsV2Response() {
    OrderV2Request v2Request = OrderV2Request.builder()
        .deliveryName("Alice Smith")
        .deliveryAddress(AddressDto.builder()
            .street("123 Main")
            .city("CDMX")
            .state("CDMX")
            .zip("06500")
            .build())
        .items(Arrays.asList(
            OrderItemV2Request.builder()
                .name("Al Pastor V2")
                .quantity(2)
                .ingredientIds(Arrays.asList("FLTO", "GRBF"))
                .build()
        ))
        .couponCode("TACO10")
        .idempotencyKey("IDEMP-V2-001")
        .build();

    testClient.post()
        .uri("/api/v2/orders")
        .header("X-Test-User", "alice")
        .header("Idempotency-Key", "IDEMP-V2-001")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(v2Request)
        .exchange()
        .expectStatus().isCreated()
        .expectHeader().valueEquals("Idempotency-Key", "IDEMP-V2-001")
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_API_VERSION, "2.0")
        .expectBody()
        .jsonPath("$.id").isNotEmpty()
        .jsonPath("$.version").isEqualTo("2.0")
        .jsonPath("$.status").isEqualTo("CONFIRMED")
        .jsonPath("$.customerName").isEqualTo("Alice Smith")
        .jsonPath("$.deliveryAddress.street").isEqualTo("123 Main")
        .jsonPath("$.deliveryAddress.city").isEqualTo("CDMX")
        .jsonPath("$.pricingBreakdown").exists()
        .jsonPath("$.items[0].name").isEqualTo("Al Pastor V2")
        .jsonPath("$.items[0].quantity").isEqualTo(2)
        .jsonPath("$.tracking.statusUrl").isNotEmpty()
        .jsonPath("$.links.self").isNotEmpty()
        .jsonPath("$.links.v1_equivalent").isNotEmpty();
  }

  @Test
  @DisplayName("9. Negociación por cabecera: X-API-Version resuelve versión correspondiente")
  void testHeaderVersioning_Negotiation() {
    testClient.get()
        .uri("/api/orders")
        .header("X-Test-User", "alice")
        .header("X-API-Version", "2.0")
        .exchange()
        .expectStatus().isOk()
        .expectHeader().valueEquals(ApiVersioningWebFilter.HEADER_API_VERSION, "2.0");
  }

  @Test
  @DisplayName("10. Consulta V2 por ID (/api/v2/orders/{id}): Retorna respuesta estructurada")
  void testOrderV2_GetById_ReturnsStructuredResponse() {
    testClient.get()
        .uri("/api/v2/orders/ORD-TEST-999")
        .header("X-Test-User", "alice")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.id").isEqualTo("ORD-TEST-999")
        .jsonPath("$.version").isEqualTo("2.0")
        .jsonPath("$.customerName").isEqualTo("Alice Smith")
        .jsonPath("$.links.self").isEqualTo("/api/v2/orders/ORD-TEST-999");
  }
}
