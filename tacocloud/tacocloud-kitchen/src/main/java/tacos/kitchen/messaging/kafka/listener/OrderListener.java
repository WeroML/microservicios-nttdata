package tacos.kitchen.messaging.kafka.listener;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import tacos.TacoOrder;
import tacos.kitchen.KitchenUI;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Profile("kafka-listener")
@Component
@Slf4j
public class OrderListener {
  
  private final KitchenUI ui;
  private final tacos.kitchen.consumer.IdempotentOrderConsumerEngine consumerEngine;

  @Autowired
  public OrderListener(
      KitchenUI ui,
      @Autowired(required = false) tacos.kitchen.consumer.IdempotentOrderConsumerEngine consumerEngine) {
    this.ui = ui;
    this.consumerEngine = consumerEngine;
  }

  public OrderListener(KitchenUI ui) {
    this(ui, null);
  }

  @KafkaListener(topics="tacocloud.orders.topic")
  public void handle(TacoOrder order, ConsumerRecord<String, TacoOrder> record) {
    if (record != null) {
      log.info("// Ejercicio 30: Received from partition {} with timestamp {}",
          record.partition(), record.timestamp());
    }
    
    String key = record != null && record.key() != null ? record.key() : (order != null ? order.getId() : null);
    if (consumerEngine != null) {
      consumerEngine.consumeOrder(order, "KAFKA", key);
    } else if (ui != null) {
      ui.displayOrder(order);
    }
  }
  
}
