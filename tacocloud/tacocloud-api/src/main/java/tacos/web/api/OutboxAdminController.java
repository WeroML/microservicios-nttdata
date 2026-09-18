package tacos.web.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.outbox.OutboxMessage;
import tacos.outbox.OutboxStatus;
import tacos.outbox.TransactionalOutboxService;
import tacos.web.api.dto.OutboxMetrics;
import tacos.web.api.dto.OutboxRelaySummary;

// Ejercicio 29: Outbox transaccional para no perder órdenes
@RestController
@RequestMapping(path = "/api/outbox", produces = "application/json")
@CrossOrigin(origins = "*")
public class OutboxAdminController {

  private final TransactionalOutboxService outboxService;

  @Autowired
  public OutboxAdminController(TransactionalOutboxService outboxService) {
    this.outboxService = outboxService;
  }

  @GetMapping("/metrics")
  public Mono<ResponseEntity<OutboxMetrics>> getMetrics() {
    return outboxService.getMetrics()
        .map(ResponseEntity::ok);
  }

  @GetMapping
  public Flux<OutboxMessage> getMessages(@RequestParam(name = "status", required = false) OutboxStatus status) {
    return outboxService.getMessages(status);
  }

  @PostMapping("/relay")
  public Mono<ResponseEntity<OutboxRelaySummary>> forceRelay() {
    return outboxService.processPendingMessages()
        .map(ResponseEntity::ok);
  }

  @PostMapping("/{id}/retry")
  public Mono<ResponseEntity<OutboxMessage>> retryMessage(@PathVariable("id") String id) {
    return outboxService.retryMessage(id)
        .map(ResponseEntity::ok);
  }

}
