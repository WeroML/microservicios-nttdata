package tacos.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.TacoOrder;
import tacos.actuator.BusinessMetricsService;
import tacos.data.OrderIdempotencyRepository;

// Ejercicio 34: Idempotency-Key en creación de órdenes
@Service
public class OrderIdempotencyService {

  private static final Logger log = LoggerFactory.getLogger(OrderIdempotencyService.class);

  public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
  public static final String HEADER_X_IDEMPOTENCY_KEY = "X-Idempotency-Key";
  public static final String HEADER_IDEMPOTENCY_REPLAYED = "Idempotency-Replayed";
  public static final long DEFAULT_TTL_MILLIS = 24L * 60L * 60L * 1000L; // 24 horas

  private final OrderIdempotencyRepository repo;
  private final BusinessMetricsService metricsService;
  private final ConcurrentMap<String, OrderIdempotencyRecord> store = new ConcurrentHashMap<>();

  public OrderIdempotencyService() {
    this(null, null);
  }

  @Autowired
  public OrderIdempotencyService(
      @Autowired(required = false) OrderIdempotencyRepository repo,
      @Autowired(required = false) BusinessMetricsService metricsService) {
    this.repo = repo;
    this.metricsService = metricsService;
  }

  /**
   * Extrae la clave de idempotencia del intercambio HTTP (Idempotency-Key o X-Idempotency-Key).
   */
  public String extractIdempotencyKey(ServerWebExchange exchange) {
    if (exchange == null || exchange.getRequest() == null) {
      return null;
    }
    org.springframework.http.HttpHeaders headers = exchange.getRequest().getHeaders();
    if (headers.containsKey(HEADER_IDEMPOTENCY_KEY)) {
      return headers.getFirst(HEADER_IDEMPOTENCY_KEY);
    }
    if (headers.containsKey(HEADER_X_IDEMPOTENCY_KEY)) {
      return headers.getFirst(HEADER_X_IDEMPOTENCY_KEY);
    }
    return null;
  }

  /**
   * Valida la estructura y restricciones de la clave de idempotencia.
   */
  public void validateKey(String key) {
    if (key == null || key.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor de Idempotency-Key no puede estar vacío");
    }
    if (key.trim().length() > 128) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El valor de Idempotency-Key excede la longitud máxima permitida de 128 caracteres");
    }
  }

  /**
   * Calcula el hash SHA-256 canónico y determinista del payload de la orden.
   */
  public String computePayloadHash(TacoOrder order) {
    if (order == null) {
      return "";
    }
    StringBuilder canonical = new StringBuilder();
    canonical.append("name:").append(order.getDeliveryName() != null ? order.getDeliveryName().trim().toLowerCase() : "").append(";");
    canonical.append("street:").append(order.getDeliveryStreet() != null ? order.getDeliveryStreet().trim().toLowerCase() : "").append(";");
    canonical.append("city:").append(order.getDeliveryCity() != null ? order.getDeliveryCity().trim().toLowerCase() : "").append(";");
    canonical.append("state:").append(order.getDeliveryState() != null ? order.getDeliveryState().trim().toLowerCase() : "").append(";");
    canonical.append("zip:").append(order.getDeliveryZip() != null ? order.getDeliveryZip().trim() : "").append(";");
    canonical.append("coupon:").append(order.getCouponCode() != null ? order.getCouponCode().trim().toUpperCase() : "").append(";");
    canonical.append("token:").append(order.getToken() != null ? order.getToken().trim() : "").append(";");

    if (order.getTacos() != null && !order.getTacos().isEmpty()) {
      List<String> tacoStrings = new ArrayList<>();
      for (Taco taco : order.getTacos()) {
        if (taco == null) continue;
        StringBuilder tb = new StringBuilder();
        tb.append(taco.getName() != null ? taco.getName().trim().toLowerCase() : "unnamed");
        tb.append(":qty=").append(taco.getQuantity() != null ? taco.getQuantity() : 1);
        tb.append(":ings=");
        if (taco.getIngredients() != null) {
          List<String> ingIds = taco.getIngredients().stream()
              .filter(i -> i != null && i.getId() != null)
              .map(Ingredient::getId)
              .sorted()
              .collect(Collectors.toList());
          tb.append(String.join(",", ingIds));
        }
        tacoStrings.add(tb.toString());
      }
      Collections.sort(tacoStrings);
      canonical.append("tacos:").append(String.join("|", tacoStrings));
    }

    return sha256(canonical.toString());
  }

  /**
   * Intenta adquirir la clave para procesamiento exclusivo o reanudar una respuesta ya cacheada.
   */
  public Mono<IdempotencyResolution> tryAcquireOrReplay(String rawKey, String userId, String currentHash) {
    validateKey(rawKey);
    final String key = rawKey.trim();

    String effectiveUser = (userId != null && !userId.trim().isEmpty()) ? userId.trim() : "anonymous";

    // 1. Buscar en memoria
    OrderIdempotencyRecord existing = store.get(key);

    // Si no está en memoria pero repo está activo, intentar cargar
    Mono<OrderIdempotencyRecord> recordMono = existing != null
        ? Mono.just(existing)
        : (repo != null ? repo.findByKey(key).doOnNext(r -> store.put(r.getKey(), r)) : Mono.empty());

    return recordMono.map(record -> {
      // Validar si expiró
      if (record.isExpired()) {
        store.remove(key);
        if (repo != null) {
          repo.deleteByKey(key).subscribe();
        }
        return createAndAcquireNewRecord(key, effectiveUser, currentHash);
      }

      // Validar aislamiento de usuario
      if (record.getUserId() != null && !record.getUserId().equalsIgnoreCase("anonymous")
          && !effectiveUser.equalsIgnoreCase("anonymous")
          && !record.getUserId().equalsIgnoreCase(effectiveUser)) {
        if (metricsService != null) metricsService.recordIdempotencyConflict(key);
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Acceso denegado: La clave de idempotencia '" + key + "' fue registrada por otro usuario.");
      }

      // Si está en progreso (petición en vuelo concurrente)
      if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
        if (metricsService != null) metricsService.recordIdempotencyConflict(key);
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Idempotency-Key in progress: Existe una solicitud concurrente procesando actualmente la clave: " + key);
      }

      // Si ya está completada: Cache HIT o Mismatch
      if (record.getStatus() == IdempotencyStatus.COMPLETED) {
        if (!record.getRequestHash().equals(currentHash)) {
          if (metricsService != null) metricsService.recordIdempotencyConflict(key);
          throw new ResponseStatusException(HttpStatus.CONFLICT,
              "Idempotency-Key conflict: El payload de la orden no coincide con la solicitud original registrada para la clave: " + key);
        }

        // Cache HIT: Retornar orden existente
        if (metricsService != null) metricsService.recordIdempotencyHit(key);
        log.info("// Ejercicio 34: [IDEMPOTENCY] Cache HIT: Devolviendo orden previa id={} para clave={}",
            record.getOrderId(), key);
        return new IdempotencyResolution(true, record.getSavedOrder());
      }

      // Si estaba en FAILED, permitir reintento
      return createAndAcquireNewRecord(key, effectiveUser, currentHash);

    }).switchIfEmpty(Mono.defer(() -> Mono.just(createAndAcquireNewRecord(key, effectiveUser, currentHash))));
  }

  private IdempotencyResolution createAndAcquireNewRecord(String key, String userId, String hash) {
    OrderIdempotencyRecord newRecord = OrderIdempotencyRecord.builder()
        .key(key)
        .userId(userId)
        .requestHash(hash)
        .status(IdempotencyStatus.IN_PROGRESS)
        .createdAt(new Date())
        .expiresAt(new Date(System.currentTimeMillis() + DEFAULT_TTL_MILLIS))
        .build();

    OrderIdempotencyRecord prior = store.putIfAbsent(key, newRecord);
    if (prior != null && !prior.isExpired()) {
      // Condición de carrera concurrente en inserción
      if (prior.getStatus() == IdempotencyStatus.IN_PROGRESS) {
        if (metricsService != null) metricsService.recordIdempotencyConflict(key);
        throw new ResponseStatusException(HttpStatus.CONFLICT,
            "Idempotency-Key in progress: Existe una solicitud concurrente procesando actualmente la clave: " + key);
      }
      if (prior.getStatus() == IdempotencyStatus.COMPLETED && prior.getRequestHash().equals(hash)) {
        if (metricsService != null) metricsService.recordIdempotencyHit(key);
        return new IdempotencyResolution(true, prior.getSavedOrder());
      }
      if (metricsService != null) metricsService.recordIdempotencyConflict(key);
      throw new ResponseStatusException(HttpStatus.CONFLICT,
          "Idempotency-Key conflict: Clave en conflicto para: " + key);
    }

    if (repo != null) {
      repo.save(newRecord).subscribe();
    }

    if (metricsService != null) metricsService.recordIdempotencyMiss(key);
    log.debug("// Ejercicio 34: [IDEMPOTENCY] Cache MISS: Iniciando procesamiento para clave={}", key);
    return new IdempotencyResolution(false, null);
  }

  /**
   * Completa exitosamente el registro de idempotencia guardando la orden resultante.
   */
  public Mono<Void> completeIdempotency(String key, TacoOrder savedOrder) {
    if (key == null || savedOrder == null) {
      return Mono.empty();
    }

    OrderIdempotencyRecord record = store.computeIfPresent(key, (k, existing) -> {
      existing.setStatus(IdempotencyStatus.COMPLETED);
      existing.setOrderId(savedOrder.getId());
      existing.setSavedOrder(savedOrder);
      existing.setCompletedAt(new Date());
      return existing;
    });

    if (record != null && repo != null) {
      return repo.save(record).then();
    }
    return Mono.empty();
  }

  /**
   * Libera la clave de idempotencia en caso de que ocurra un error durante el procesamiento de la orden.
   */
  public Mono<Void> releaseOnError(String key) {
    if (key == null) {
      return Mono.empty();
    }
    store.remove(key);
    if (repo != null) {
      return repo.deleteByKey(key);
    }
    return Mono.empty();
  }

  public void clear() {
    store.clear();
  }

  private String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder();
      for (byte b : bytes) {
        sb.append(String.format("%02x", b));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Error calculando SHA-256", e);
    }
  }

  /**
   * Resultado de la resolución de idempotencia.
   */
  public static class IdempotencyResolution {
    private final boolean replay;
    private final TacoOrder cachedOrder;

    public IdempotencyResolution(boolean replay, TacoOrder cachedOrder) {
      this.replay = replay;
      this.cachedOrder = cachedOrder;
    }

    public boolean isReplay() {
      return replay;
    }

    public TacoOrder getCachedOrder() {
      return cachedOrder;
    }
  }
}
