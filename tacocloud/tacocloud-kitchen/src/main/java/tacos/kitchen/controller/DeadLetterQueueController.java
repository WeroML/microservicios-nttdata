package tacos.kitchen.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tacos.kitchen.consumer.ConsumeResult;
import tacos.kitchen.consumer.DeadLetterQueueService;
import tacos.kitchen.consumer.DeadLetterRecord;
import tacos.kitchen.consumer.IdempotentConsumerRegistry;
import tacos.kitchen.consumer.IdempotentOrderConsumerEngine;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@RestController
@RequestMapping("/api/kitchen")
public class DeadLetterQueueController {

  private final DeadLetterQueueService dlqService;
  private final IdempotentConsumerRegistry registry;
  private final IdempotentOrderConsumerEngine consumerEngine;

  @Autowired
  public DeadLetterQueueController(
      DeadLetterQueueService dlqService,
      IdempotentConsumerRegistry registry,
      IdempotentOrderConsumerEngine consumerEngine) {
    this.dlqService = dlqService;
    this.registry = registry;
    this.consumerEngine = consumerEngine;
  }

  /**
   * Obtiene la lista completa de mensajes almacenados en la Dead Letter Queue.
   */
  @GetMapping("/dlq")
  public List<DeadLetterRecord> getDeadLetters() {
    return dlqService.getAllDeadLetters();
  }

  /**
   * Obtiene el diagnóstico y payload de un mensaje específico en la DLQ.
   */
  @GetMapping("/dlq/{id}")
  public ResponseEntity<DeadLetterRecord> getDeadLetterById(@PathVariable("id") String id) {
    Optional<DeadLetterRecord> record = dlqService.getDeadLetter(id);
    return record.map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  /**
   * Ejecuta el reintento manual (replay) de un mensaje venenoso almacenado en la DLQ.
   */
  @PostMapping("/dlq/{id}/replay")
  public ResponseEntity<ConsumeResult> replayDeadLetter(@PathVariable("id") String id) {
    Optional<DeadLetterRecord> record = dlqService.getDeadLetter(id);
    if (!record.isPresent()) {
      return ResponseEntity.notFound().build();
    }
    ConsumeResult result = consumerEngine.replay(id);
    return ResponseEntity.ok(result);
  }

  /**
   * Descarta un mensaje de la DLQ.
   */
  @DeleteMapping("/dlq/{id}")
  public ResponseEntity<Void> discardDeadLetter(@PathVariable("id") String id) {
    boolean discarded = dlqService.discardDeadLetter(id);
    if (discarded) {
      return ResponseEntity.noContent().build();
    }
    return ResponseEntity.notFound().build();
  }

  /**
   * Estadísticas de idempotencia y estado de la DLQ.
   */
  @GetMapping("/idempotency/stats")
  public Map<String, Object> getIdempotencyStats() {
    Map<String, Object> stats = new HashMap<>();
    stats.put("processedCount", registry.getProcessedCount());
    stats.put("duplicateCount", registry.getDuplicateCount());
    stats.put("dlqCount", dlqService.getDlqCount());
    stats.put("records", registry.getAllRecords());
    return stats;
  }

}
