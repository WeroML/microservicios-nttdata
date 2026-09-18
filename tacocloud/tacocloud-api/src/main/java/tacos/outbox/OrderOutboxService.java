package tacos.outbox;

import java.util.Date;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.TacoOrder;
import tacos.data.OutboxRepository;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;
import tacos.messaging.DynamicOrderMessagingRouter;
import tacos.messaging.OrderMessagingService;
import tacos.web.api.dto.OutboxMetrics;
import tacos.web.api.dto.OutboxRelaySummary;

// Ejercicio 29: Outbox transaccional para no perder órdenes
@Service
@Slf4j
public class OrderOutboxService implements TransactionalOutboxService {

  private final OutboxRepository outboxRepo;
  private final OrderMessagingService orderMessages;
  private final ObjectMapper objectMapper;

  @Value("${tacocloud.outbox.immediate-dispatch:true}")
  private boolean immediateDispatch = true;

  @Value("${tacocloud.outbox.max-retries:5}")
  private int defaultMaxRetries = 5;

  @Value("${tacocloud.outbox.scheduled.enabled:false}")
  private boolean scheduledEnabled = false;

  public OrderOutboxService(OutboxRepository outboxRepo, OrderMessagingService orderMessages) {
    this(outboxRepo, orderMessages, new ObjectMapper());
  }

  @Autowired
  public OrderOutboxService(
      OutboxRepository outboxRepo,
      @Autowired(required = false) OrderMessagingService orderMessages,
      @Autowired(required = false) ObjectMapper objectMapper) {
    this.outboxRepo = outboxRepo;
    this.orderMessages = orderMessages;
    this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
  }

  @Override
  public Mono<OutboxMessage> enqueueOrder(TacoOrder order, OrderEventType eventType) {
    OrderEventType effectiveType = eventType != null ? eventType : OrderEventType.ORDER_CREATED;
    OrderEvent event = OrderEvent.fromOrder(order, effectiveType);
    return enqueueEvent(event);
  }

  @Override
  public Mono<OutboxMessage> enqueueEvent(OrderEvent event) {
    if (event == null) {
      return Mono.error(new IllegalArgumentException("OrderEvent no puede ser nulo"));
    }

    String payloadJson = null;
    try {
      payloadJson = objectMapper.writeValueAsString(event);
    } catch (Exception ex) {
      log.warn("// Ejercicio 29: No fue posible serializar payloadJson del evento {}: {}", event.getEventId(), ex.getMessage());
    }

    String activeBroker = resolveActiveBrokerName();

    OutboxMessage message = OutboxMessage.builder()
        .eventId(event.getEventId())
        .orderId(event.getOrderId())
        .eventType(event.getEventType())
        .event(event)
        .payloadJson(payloadJson)
        .status(OutboxStatus.PENDING)
        .createdAt(new Date())
        .retryCount(0)
        .maxRetries(defaultMaxRetries)
        .targetBroker(activeBroker)
        .source(event.getSource())
        .build();

    log.info("// Ejercicio 29: Registrando mensaje en Outbox atómico: [eventId={}, orderId={}, type={}]",
        message.getEventId(), message.getOrderId(), message.getEventType());

    return outboxRepo.save(message)
        .flatMap(saved -> {
          if (immediateDispatch) {
            return tryDispatch(saved);
          }
          return Mono.just(saved);
        });
  }

  /**
   * Intenta despachar de inmediato el mensaje al broker de mensajería.
   * Si el broker falla, el error es capturado, el mensaje permanece en estado PENDING
   * con su detalle de error y reintento incrementado, asegurando que la orden no se pierda.
   */
  private Mono<OutboxMessage> tryDispatch(OutboxMessage message) {
    message.setLastAttemptAt(new Date());
    message.setTargetBroker(resolveActiveBrokerName());

    if (orderMessages == null) {
      message.setLastError("No hay OrderMessagingService configurado en el sistema");
      message.setRetryCount(message.getRetryCount() + 1);
      return outboxRepo.save(message);
    }

    try {
      log.info("// Ejercicio 29: Despachando mensaje de outbox [eventId={}] a broker '{}'",
          message.getEventId(), message.getTargetBroker());

      orderMessages.sendOrderEvent(message.getEvent());

      // Éxito: marcamos como PUBLISHED
      message.setStatus(OutboxStatus.PUBLISHED);
      message.setPublishedAt(new Date());
      message.setLastError(null);
      log.info("// Ejercicio 29: Mensaje de outbox [eventId={}] publicado exitosamente", message.getEventId());
      return outboxRepo.save(message);

    } catch (Exception ex) {
      // Broker no disponible o fallo de red: salvaguardar en outbox como PENDING sin quebrar la petición
      int newRetries = message.getRetryCount() + 1;
      message.setRetryCount(newRetries);
      message.setLastError(ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName());

      if (newRetries >= message.getMaxRetries()) {
        message.setStatus(OutboxStatus.DEAD_LETTER);
        log.error("// Ejercicio 29: Mensaje [eventId={}] superó maxRetries ({}). Transicionando a DEAD_LETTER. Causa: {}",
            message.getEventId(), message.getMaxRetries(), ex.getMessage());
      } else {
        message.setStatus(OutboxStatus.PENDING);
        log.warn("// Ejercicio 29: Fallo temporal al despachar evento [eventId={}]. Queda PENDING en outbox (intento {}/{}). Causa: {}",
            message.getEventId(), newRetries, message.getMaxRetries(), ex.getMessage());
      }

      return outboxRepo.save(message);
    }
  }

  @Override
  public Mono<OutboxRelaySummary> processPendingMessages() {
    Date startTime = new Date();
    String activeBroker = resolveActiveBrokerName();

    log.info("// Ejercicio 29: Iniciando ciclo de relay de outbox para broker '{}'", activeBroker);

    return outboxRepo.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING)
        .concatMap(this::tryDispatch)
        .collectList()
        .map(processedList -> {
          int total = processedList.size();
          int success = 0;
          int failed = 0;
          for (OutboxMessage msg : processedList) {
            if (msg.getStatus() == OutboxStatus.PUBLISHED) {
              success++;
            } else {
              failed++;
            }
          }
          String msgText = String.format("Ciclo de relay completado: %d procesados (%d exitosos, %d pendientes/fallidos)",
              total, success, failed);
          log.info("// Ejercicio 29: " + msgText);

          return OutboxRelaySummary.builder()
              .processedCount(total)
              .successCount(success)
              .failedCount(failed)
              .activeBroker(activeBroker)
              .executedAt(startTime)
              .message(msgText)
              .build();
        });
  }

  @Override
  public Mono<OutboxMessage> retryMessage(String messageId) {
    return outboxRepo.findById(messageId)
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje de outbox no encontrado: " + messageId)))
        .flatMap(msg -> {
          msg.setStatus(OutboxStatus.PENDING);
          return tryDispatch(msg);
        });
  }

  @Override
  public Mono<OutboxMetrics> getMetrics() {
    return Mono.zip(
        outboxRepo.count(),
        outboxRepo.countByStatus(OutboxStatus.PENDING).defaultIfEmpty(0L),
        outboxRepo.countByStatus(OutboxStatus.PUBLISHED).defaultIfEmpty(0L),
        outboxRepo.countByStatus(OutboxStatus.FAILED).defaultIfEmpty(0L),
        outboxRepo.countByStatus(OutboxStatus.DEAD_LETTER).defaultIfEmpty(0L)
    ).map(tuple -> {
      long total = tuple.getT1();
      long pending = tuple.getT2();
      long published = tuple.getT3();
      long failed = tuple.getT4();
      long deadLetter = tuple.getT5();

      Map<OutboxStatus, Long> counts = new EnumMap<>(OutboxStatus.class);
      counts.put(OutboxStatus.PENDING, pending);
      counts.put(OutboxStatus.PUBLISHED, published);
      counts.put(OutboxStatus.FAILED, failed);
      counts.put(OutboxStatus.DEAD_LETTER, deadLetter);

      return OutboxMetrics.builder()
          .totalCount(total)
          .pendingCount(pending)
          .publishedCount(published)
          .failedCount(failed)
          .deadLetterCount(deadLetter)
          .countsByStatus(counts)
          .activeBroker(resolveActiveBrokerName())
          .build();
    });
  }

  @Override
  public Flux<OutboxMessage> getMessages(OutboxStatus status) {
    if (status != null) {
      return outboxRepo.findByStatusOrderByCreatedAtAsc(status);
    }
    return outboxRepo.findAll();
  }

  @Scheduled(fixedDelayString = "${tacocloud.outbox.polling-interval-ms:10000}", initialDelay = 5000)
  public void scheduledRelay() {
    if (scheduledEnabled) {
      processPendingMessages().subscribe(
          summary -> log.debug("// Ejercicio 29: Scheduled relay finalizado: {}", summary.getMessage()),
          err -> log.error("// Ejercicio 29: Error en scheduled relay: {}", err.getMessage())
      );
    }
  }

  private String resolveActiveBrokerName() {
    if (orderMessages instanceof DynamicOrderMessagingRouter) {
      return ((DynamicOrderMessagingRouter) orderMessages).getActiveBroker();
    }
    return "default";
  }

  public void setImmediateDispatch(boolean immediateDispatch) {
    this.immediateDispatch = immediateDispatch;
  }

  public void setDefaultMaxRetries(int defaultMaxRetries) {
    this.defaultMaxRetries = defaultMaxRetries;
  }

  public void setScheduledEnabled(boolean scheduledEnabled) {
    this.scheduledEnabled = scheduledEnabled;
  }

}
