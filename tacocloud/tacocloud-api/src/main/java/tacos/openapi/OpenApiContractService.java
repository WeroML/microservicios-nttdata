package tacos.openapi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

// Ejercicio 35: Versionar la API y publicar contrato OpenAPI
@Service
public class OpenApiContractService {

  private final ObjectMapper objectMapper;

  public OpenApiContractService() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
  }

  /**
   * Genera el contrato OpenAPI 3.0.3 completo como Map.
   */
  public Map<String, Object> generateContract(String versionFilter) {
    Map<String, Object> doc = new LinkedHashMap<>();
    doc.put("openapi", "3.0.3");

    // Info
    Map<String, Object> info = new LinkedHashMap<>();
    String title = "Taco Cloud Enterprise API";
    String version = (versionFilter != null && !versionFilter.trim().isEmpty()) ? versionFilter.trim() : "2.0.0";
    info.put("title", title + (versionFilter != null ? " (" + versionFilter + ")" : ""));
    info.put("version", version);
    info.put("description", "Especificación formal OpenAPI 3.0 para la plataforma de microservicios Taco Cloud. "
        + "Incluye gestión integral de órdenes, Taco Physics, reservación de inventario, cupones, anuncios operativos, "
        + "idempotencia HTTP y versionado de contratos.");
    
    Map<String, String> contact = new LinkedHashMap<>();
    contact.put("name", "Taco Cloud Architecture Team");
    contact.put("email", "api-support@tacocloud.com");
    contact.put("url", "https://tacocloud.com/developer");
    info.put("contact", contact);

    Map<String, String> license = new LinkedHashMap<>();
    license.put("name", "Apache 2.0");
    license.put("url", "https://www.apache.org/licenses/LICENSE-2.0.html");
    info.put("license", license);

    doc.put("info", info);

    // Servers
    List<Map<String, String>> servers = new ArrayList<>();
    servers.add(createServerMap("http://localhost:8080", "Entorno de desarrollo local"));
    servers.add(createServerMap("https://api.tacocloud.com", "Servidor de producción"));
    doc.put("servers", servers);

    // Tags
    List<Map<String, String>> tags = new ArrayList<>();
    tags.add(createTagMap("Orders V1", "Gestión de órdenes con contrato canónico TacoOrder"));
    tags.add(createTagMap("Orders V2", "Gestión de órdenes evolucionada con DTOs estructurados y HATEOAS"));
    tags.add(createTagMap("Tacos & Ingredients", "Catálogo de ingredientes y recetas de tacos"));
    tags.add(createTagMap("Coupons", "Validación y motor de reglas de cupones de descuento"));
    tags.add(createTagMap("Operational Announcements", "Anuncios operativos seguros con RBAC"));
    tags.add(createTagMap("API Versions", "Información de ciclo de vida de versiones de API"));
    doc.put("tags", tags);

    // Paths
    Map<String, Object> paths = buildPaths(versionFilter);
    doc.put("paths", paths);

    // Components
    Map<String, Object> components = buildComponents();
    doc.put("components", components);

    return doc;
  }

  public String generateContractJson(String versionFilter) {
    try {
      return objectMapper.writeValueAsString(generateContract(versionFilter));
    } catch (JsonProcessingException e) {
      throw new RuntimeException("Error al serializar especificación OpenAPI a JSON", e);
    }
  }

  public String generateContractYaml(String versionFilter) {
    Map<String, Object> contract = generateContract(versionFilter);
    StringBuilder sb = new StringBuilder();
    toYaml(contract, sb, 0);
    return sb.toString();
  }

  private Map<String, String> createServerMap(String url, String description) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("url", url);
    m.put("description", description);
    return m;
  }

  private Map<String, String> createTagMap(String name, String description) {
    Map<String, String> m = new LinkedHashMap<>();
    m.put("name", name);
    m.put("description", description);
    return m;
  }

  private Map<String, Object> buildPaths(String versionFilter) {
    Map<String, Object> paths = new LinkedHashMap<>();
    boolean includeV1 = versionFilter == null || "v1".equalsIgnoreCase(versionFilter);
    boolean includeV2 = versionFilter == null || "v2".equalsIgnoreCase(versionFilter);

    // API Versions endpoint
    paths.put("/api/versions", buildApiVersionsPathItem());

    // V1 Endpoints
    if (includeV1) {
      paths.put("/api/v1/orders", buildOrdersV1PathItem());
      paths.put("/api/v1/orders/{id}", buildOrderV1ByIdPathItem());
      paths.put("/api/v1/orders/{id}/status", buildOrderStatusPathItem());
      paths.put("/api/v1/tacos", buildTacosPathItem());
      paths.put("/api/v1/ingredients", buildIngredientsPathItem());
      paths.put("/api/v1/coupons/{code}", buildCouponsPathItem());
      paths.put("/api/v1/announcements", buildAnnouncementsPathItem());
    }

    // V2 Endpoints
    if (includeV2) {
      paths.put("/api/v2/orders", buildOrdersV2PathItem());
      paths.put("/api/v2/orders/{id}", buildOrderV2ByIdPathItem());
    }

    return paths;
  }

  private Map<String, Object> buildApiVersionsPathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("API Versions"));
    get.put("summary", "Listar versiones soportadas de la API y su ciclo de vida");
    get.put("description", "Devuelve información de las versiones activas, sunset dates y enlaces a contratos OpenAPI.");
    get.put("operationId", "getApiVersions");
    
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", createResponse("Información de versiones recuperada exitosamente", "#/components/schemas/ApiVersionsList"));
    get.put("responses", responses);

    pathItem.put("get", get);
    return pathItem;
  }

  private Map<String, Object> buildOrdersV1PathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();

    // POST /api/v1/orders
    Map<String, Object> post = new LinkedHashMap<>();
    post.put("tags", Collections.singletonList("Orders V1"));
    post.put("summary", "Crear una nueva orden (V1 - Contrato TacoOrder)");
    post.put("description", "Crea una orden con validación Taco Physics, reserva de inventario, Outbox transaccional y soporte para Idempotency-Key.");
    post.put("operationId", "postOrderV1");

    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(createHeaderParam("Idempotency-Key", "Clave única de idempotencia para evitar duplicados en reintentos", false));
    parameters.add(createHeaderParam("X-Correlation-ID", "Identificador de correlación para observabilidad distribuida", false));
    post.put("parameters", parameters);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("required", true);
    Map<String, Object> content = new LinkedHashMap<>();
    Map<String, Object> json = new LinkedHashMap<>();
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("$ref", "#/components/schemas/TacoOrder");
    json.put("schema", schema);
    content.put("application/json", json);
    requestBody.put("content", content);
    post.put("requestBody", requestBody);

    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("201", createResponse("Orden creada exitosamente o rejugada por caché idempotente", "#/components/schemas/TacoOrder"));
    responses.put("400", createResponse("Datos inválidos o clave de idempotencia errónea", "#/components/schemas/ProblemDetails"));
    responses.put("409", createResponse("Conflicto de idempotencia (payload mismatch o en progreso)", "#/components/schemas/ProblemDetails"));
    post.put("responses", responses);

    pathItem.put("post", post);

    // GET /api/v1/orders
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("Orders V1"));
    get.put("summary", "Listar órdenes del usuario autenticado");
    get.put("operationId", "getOrdersV1");
    Map<String, Object> getResponses = new LinkedHashMap<>();
    getResponses.put("200", createArrayResponse("Listado de órdenes", "#/components/schemas/TacoOrder"));
    get.put("responses", getResponses);
    pathItem.put("get", get);

    return pathItem;
  }

  private Map<String, Object> buildOrderV1ByIdPathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("Orders V1"));
    get.put("summary", "Obtener detalle de una orden por ID");
    get.put("operationId", "getOrderV1ById");
    
    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(createPathParam("id", "Identificador único de la orden", true));
    get.put("parameters", parameters);

    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", createResponse("Orden encontrada", "#/components/schemas/TacoOrder"));
    responses.put("404", createResponse("Orden no encontrada", "#/components/schemas/ProblemDetails"));
    get.put("responses", responses);

    pathItem.put("get", get);
    return pathItem;
  }

  private Map<String, Object> buildOrderStatusPathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();
    Map<String, Object> patch = new LinkedHashMap<>();
    patch.put("tags", Collections.singletonList("Orders V1"));
    patch.put("summary", "Actualizar estado de una orden (Cocina / Reparto)");
    patch.put("operationId", "updateOrderStatus");

    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(createPathParam("id", "Identificador de la orden", true));
    patch.put("parameters", parameters);

    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", createResponse("Estado de la orden actualizado", "#/components/schemas/OrderStatusResponse"));
    patch.put("responses", responses);

    pathItem.put("patch", patch);
    return pathItem;
  }

  private Map<String, Object> buildOrdersV2PathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();

    // POST /api/v2/orders
    Map<String, Object> post = new LinkedHashMap<>();
    post.put("tags", Collections.singletonList("Orders V2"));
    post.put("summary", "Crear una nueva orden (V2 - Contrato OrderV2Request estructurado)");
    post.put("description", "Crea una orden con estructura de dirección desacoplada, desglose financiero y enlaces HATEOAS.");
    post.put("operationId", "postOrderV2");

    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(createHeaderParam("Idempotency-Key", "Clave única de idempotencia", false));
    parameters.add(createHeaderParam("X-Correlation-ID", "Identificador de correlación", false));
    post.put("parameters", parameters);

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("required", true);
    Map<String, Object> content = new LinkedHashMap<>();
    Map<String, Object> json = new LinkedHashMap<>();
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("$ref", "#/components/schemas/OrderV2Request");
    json.put("schema", schema);
    content.put("application/json", json);
    requestBody.put("content", content);
    post.put("requestBody", requestBody);

    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("201", createResponse("Orden V2 creada exitosamente", "#/components/schemas/OrderV2Response"));
    responses.put("400", createResponse("Datos inválidos", "#/components/schemas/ProblemDetails"));
    responses.put("409", createResponse("Conflicto de idempotencia", "#/components/schemas/ProblemDetails"));
    post.put("responses", responses);

    pathItem.put("post", post);

    // GET /api/v2/orders
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("Orders V2"));
    get.put("summary", "Listar órdenes en formato V2 con enlaces HATEOAS");
    get.put("operationId", "allOrdersV2");
    Map<String, Object> getResponses = new LinkedHashMap<>();
    getResponses.put("200", createArrayResponse("Listado de órdenes V2", "#/components/schemas/OrderV2Response"));
    get.put("responses", getResponses);
    pathItem.put("get", get);

    return pathItem;
  }

  private Map<String, Object> buildOrderV2ByIdPathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("Orders V2"));
    get.put("summary", "Obtener detalle V2 de una orden con HATEOAS");
    get.put("operationId", "getOrderV2ById");

    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(createPathParam("id", "Identificador de la orden", true));
    get.put("parameters", parameters);

    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", createResponse("Orden V2 encontrada", "#/components/schemas/OrderV2Response"));
    responses.put("404", createResponse("Orden no encontrada", "#/components/schemas/ProblemDetails"));
    get.put("responses", responses);

    pathItem.put("get", get);
    return pathItem;
  }

  private Map<String, Object> buildTacosPathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("Tacos & Ingredients"));
    get.put("summary", "Listar tacos recientes y recetas destacadas");
    get.put("operationId", "getRecentTacos");
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", createArrayResponse("Listado de tacos", "#/components/schemas/Taco"));
    get.put("responses", responses);
    pathItem.put("get", get);
    return pathItem;
  }

  private Map<String, Object> buildIngredientsPathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("Tacos & Ingredients"));
    get.put("summary", "Listar catálogo de ingredientes disponibles");
    get.put("operationId", "getAllIngredients");
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", createArrayResponse("Listado de ingredientes", "#/components/schemas/Ingredient"));
    get.put("responses", responses);
    pathItem.put("get", get);
    return pathItem;
  }

  private Map<String, Object> buildCouponsPathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("Coupons"));
    get.put("summary", "Validar cupón de descuento y reglas asociadas");
    get.put("operationId", "getCouponByCode");
    List<Map<String, Object>> parameters = new ArrayList<>();
    parameters.add(createPathParam("code", "Código del cupón", true));
    get.put("parameters", parameters);
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", createResponse("Cupón válido", "#/components/schemas/Coupon"));
    responses.put("404", createResponse("Cupón inexistente o expirado", "#/components/schemas/ProblemDetails"));
    get.put("responses", responses);
    pathItem.put("get", get);
    return pathItem;
  }

  private Map<String, Object> buildAnnouncementsPathItem() {
    Map<String, Object> pathItem = new LinkedHashMap<>();
    Map<String, Object> get = new LinkedHashMap<>();
    get.put("tags", Collections.singletonList("Operational Announcements"));
    get.put("summary", "Consultar anuncios operativos vigentes para cocina y reparto");
    get.put("operationId", "getActiveAnnouncements");
    Map<String, Object> responses = new LinkedHashMap<>();
    responses.put("200", createArrayResponse("Listado de anuncios operativos", "#/components/schemas/OperationalAnnouncement"));
    get.put("responses", responses);
    pathItem.put("get", get);
    return pathItem;
  }

  private Map<String, Object> buildComponents() {
    Map<String, Object> components = new LinkedHashMap<>();

    // Schemas
    Map<String, Object> schemas = new LinkedHashMap<>();
    schemas.put("TacoOrder", createTacoOrderSchema());
    schemas.put("OrderV2Request", createOrderV2RequestSchema());
    schemas.put("OrderV2Response", createOrderV2ResponseSchema());
    schemas.put("Taco", createTacoSchema());
    schemas.put("Ingredient", createIngredientSchema());
    schemas.put("ProblemDetails", createProblemDetailsSchema());
    schemas.put("OperationalAnnouncement", createAnnouncementSchema());
    schemas.put("ApiVersionsList", createApiVersionsListSchema());
    schemas.put("OrderStatusResponse", createOrderStatusResponseSchema());
    components.put("schemas", schemas);

    // Security Schemes
    Map<String, Object> securitySchemes = new LinkedHashMap<>();
    Map<String, Object> basicAuth = new LinkedHashMap<>();
    basicAuth.put("type", "http");
    basicAuth.put("scheme", "basic");
    basicAuth.put("description", "Autenticación HTTP Basic estándar para usuarios y operadores");
    securitySchemes.put("basicAuth", basicAuth);
    components.put("securitySchemes", securitySchemes);

    return components;
  }

  private Map<String, Object> createTacoOrderSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("id", createProp("string", "Identificador único de la orden", "ORD-12345"));
    props.put("deliveryName", createProp("string", "Nombre del cliente", "Gustavo"));
    props.put("deliveryStreet", createProp("string", "Calle y número", "123 Taco St"));
    props.put("deliveryCity", createProp("string", "Ciudad", "CDMX"));
    props.put("deliveryState", createProp("string", "Estado", "CDMX"));
    props.put("deliveryZip", createProp("string", "Código postal", "06500"));
    props.put("status", createProp("string", "Estado de la orden (CONFIRMED, PREPARING, READY, etc.)", "CONFIRMED"));
    props.put("total", createProp("number", "Total calculado del pedido", "125.00"));
    props.put("idempotencyKey", createProp("string", "Clave de idempotencia asociada", "IDEMP-001"));
    props.put("correlationId", createProp("string", "Correlation ID de trazabilidad", "cid-789"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createOrderV2RequestSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("required", Arrays.asList("deliveryName", "deliveryAddress", "items"));
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("deliveryName", createProp("string", "Nombre del cliente", "Gustavo"));
    
    Map<String, Object> addr = new LinkedHashMap<>();
    addr.put("type", "object");
    Map<String, Object> addrProps = new LinkedHashMap<>();
    addrProps.put("street", createProp("string", "Calle y número", "123 Taco St"));
    addrProps.put("city", createProp("string", "Ciudad", "CDMX"));
    addrProps.put("state", createProp("string", "Estado", "CDMX"));
    addrProps.put("zip", createProp("string", "Código postal", "06500"));
    addr.put("properties", addrProps);
    props.put("deliveryAddress", addr);

    props.put("couponCode", createProp("string", "Código de cupón opcional", "TACO10"));
    props.put("paymentToken", createProp("string", "Token de pago seguro (PCI-DSS)", "tok_visa_4242"));
    props.put("idempotencyKey", createProp("string", "Clave de idempotencia", "KEY-V2-001"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createOrderV2ResponseSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("id", createProp("string", "ID de la orden", "ORD-V2-999"));
    props.put("version", createProp("string", "Versión del contrato de respuesta", "2.0"));
    props.put("status", createProp("string", "Estado actual", "CONFIRMED"));
    props.put("customerName", createProp("string", "Nombre del cliente", "Gustavo"));
    props.put("correlationId", createProp("string", "Correlation ID", "cid-123"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createTacoSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("id", createProp("string", "ID del taco", "TACO-001"));
    props.put("name", createProp("string", "Nombre del taco", "Al Pastor"));
    props.put("price", createProp("number", "Precio del taco", "35.00"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createIngredientSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("id", createProp("string", "Identificador de ingrediente", "FLTO"));
    props.put("name", createProp("string", "Nombre", "Flour Tortilla"));
    props.put("type", createProp("string", "Tipo (WRAP, PROTEIN, CHEESE, VEGGIES, SAUCE)", "WRAP"));
    props.put("price", createProp("number", "Precio", "10.00"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createProblemDetailsSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    schema.put("description", "Formato estándar RFC 7807 Problem Details");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("type", createProp("string", "URI del tipo de error", "about:blank"));
    props.put("title", createProp("string", "Título corto del error", "Conflict"));
    props.put("status", createProp("integer", "Código de estado HTTP", "409"));
    props.put("detail", createProp("string", "Detalle explicativo del error", "Idempotency conflict"));
    props.put("instance", createProp("string", "URI de la solicitud", "/api/v1/orders"));
    props.put("correlationId", createProp("string", "Correlation ID del error", "cid-123"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createAnnouncementSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("id", createProp("string", "UUID del anuncio", "b5a76e01-1234-4567-89ab-cdef01234567"));
    props.put("title", createProp("string", "Título", "Mantenimiento en parrilla 2"));
    props.put("message", createProp("string", "Mensaje", "Parrilla en mantenimiento por 30 mins"));
    props.put("level", createProp("string", "Nivel (INFO, WARNING, CRITICAL)", "WARNING"));
    props.put("scope", createProp("string", "Ámbito (ALL, KITCHEN, DELIVERY, STORES)", "KITCHEN"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createApiVersionsListSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("service", createProp("string", "Nombre del servicio", "Taco Cloud REST API"));
    props.put("currentVersion", createProp("string", "Versión actual recomendada", "v2"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createOrderStatusResponseSchema() {
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "object");
    Map<String, Object> props = new LinkedHashMap<>();
    props.put("orderId", createProp("string", "ID de la orden", "ORD-123"));
    props.put("previousStatus", createProp("string", "Estado anterior", "CONFIRMED"));
    props.put("currentStatus", createProp("string", "Nuevo estado", "PREPARING"));
    schema.put("properties", props);
    return schema;
  }

  private Map<String, Object> createProp(String type, String description, String example) {
    Map<String, Object> prop = new LinkedHashMap<>();
    prop.put("type", type);
    prop.put("description", description);
    prop.put("example", example);
    return prop;
  }

  private Map<String, Object> createHeaderParam(String name, String description, boolean required) {
    Map<String, Object> param = new LinkedHashMap<>();
    param.put("name", name);
    param.put("in", "header");
    param.put("description", description);
    param.put("required", required);
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "string");
    param.put("schema", schema);
    return param;
  }

  private Map<String, Object> createPathParam(String name, String description, boolean required) {
    Map<String, Object> param = new LinkedHashMap<>();
    param.put("name", name);
    param.put("in", "path");
    param.put("description", description);
    param.put("required", required);
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "string");
    param.put("schema", schema);
    return param;
  }

  private Map<String, Object> createResponse(String description, String refSchema) {
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("description", description);
    if (refSchema != null) {
      Map<String, Object> content = new LinkedHashMap<>();
      Map<String, Object> json = new LinkedHashMap<>();
      Map<String, Object> schema = new LinkedHashMap<>();
      schema.put("$ref", refSchema);
      json.put("schema", schema);
      content.put("application/json", json);
      res.put("content", content);
    }
    return res;
  }

  private Map<String, Object> createArrayResponse(String description, String refSchema) {
    Map<String, Object> res = new LinkedHashMap<>();
    res.put("description", description);
    Map<String, Object> content = new LinkedHashMap<>();
    Map<String, Object> json = new LinkedHashMap<>();
    Map<String, Object> schema = new LinkedHashMap<>();
    schema.put("type", "array");
    Map<String, Object> items = new LinkedHashMap<>();
    items.put("$ref", refSchema);
    schema.put("items", items);
    json.put("schema", schema);
    content.put("application/json", json);
    res.put("content", content);
    return res;
  }

  @SuppressWarnings("unchecked")
  private void toYaml(Object obj, StringBuilder sb, int indent) {
    String pad = String.join("", Collections.nCopies(indent, "  "));
    if (obj instanceof Map) {
      Map<String, Object> map = (Map<String, Object>) obj;
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        String k = entry.getKey();
        Object v = entry.getValue();
        if (v instanceof Map || v instanceof List) {
          sb.append(pad).append(k).append(":\n");
          toYaml(v, sb, indent + 1);
        } else {
          sb.append(pad).append(k).append(": ").append(formatYamlScalar(v)).append("\n");
        }
      }
    } else if (obj instanceof List) {
      List<Object> list = (List<Object>) obj;
      for (Object item : list) {
        if (item instanceof Map) {
          Map<String, Object> m = (Map<String, Object>) item;
          boolean first = true;
          for (Map.Entry<String, Object> e : m.entrySet()) {
            if (first) {
              sb.append(pad).append("- ").append(e.getKey()).append(": ");
              if (e.getValue() instanceof Map || e.getValue() instanceof List) {
                sb.append("\n");
                toYaml(e.getValue(), sb, indent + 2);
              } else {
                sb.append(formatYamlScalar(e.getValue())).append("\n");
              }
              first = false;
            } else {
              sb.append(pad).append("  ").append(e.getKey()).append(": ");
              if (e.getValue() instanceof Map || e.getValue() instanceof List) {
                sb.append("\n");
                toYaml(e.getValue(), sb, indent + 2);
              } else {
                sb.append(formatYamlScalar(e.getValue())).append("\n");
              }
            }
          }
        } else {
          sb.append(pad).append("- ").append(formatYamlScalar(item)).append("\n");
        }
      }
    }
  }

  private String formatYamlScalar(Object val) {
    if (val == null) return "null";
    if (val instanceof Boolean || val instanceof Number) return String.valueOf(val);
    String str = String.valueOf(val);
    if (str.contains(":") || str.contains("#") || str.contains("\n") || str.contains("\"") || str.startsWith("@")) {
      return "\"" + str.replace("\"", "\\\"") + "\"";
    }
    return str;
  }
}
