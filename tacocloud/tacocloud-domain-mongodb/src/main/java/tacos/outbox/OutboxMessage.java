package tacos.outbox;

import java.io.Serializable;
import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.events.OrderEvent;
import tacos.events.OrderEventType;

// Ejercicio 29: Outbox transaccional para no perder órdenes
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "outbox_messages")
public class OutboxMessage implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  private String id;

  /**
   * Identificador único canónico del evento (UUID), garantiza idempotencia y deduplicación.
   */
  private String eventId;

  /**
   * Identificador de la orden asociada.
   */
  private String orderId;

  /**
   * Tipo de evento de orden según el contrato canónico.
   */
  private OrderEventType eventType;

  /**
   * Estado del mensaje en el outbox: PENDING, PUBLISHED, FAILED, DEAD_LETTER.
   */
  @Builder.Default
  private OutboxStatus status = OutboxStatus.PENDING;

  /**
   * Evento canónico completo deserializado.
   */
  private OrderEvent event;

  /**
   * Respaldo serializado en formato JSON.
   */
  private String payloadJson;

  /**
   * Marca de tiempo de registro en el outbox.
   */
  @Builder.Default
  private Date createdAt = new Date();

  /**
   * Marca de tiempo en la que el mensaje fue publicado exitosamente al broker.
   */
  private Date publishedAt;

  /**
   * Marca de tiempo del último intento de despacho.
   */
  private Date lastAttemptAt;

  /**
   * Contador de intentos de despacho realizados.
   */
  @Builder.Default
  private int retryCount = 0;

  /**
   * Número máximo de reintentos permitidos antes de transicionar a DEAD_LETTER.
   */
  @Builder.Default
  private int maxRetries = 5;

  /**
   * Detalle o mensaje de error del último intento fallido.
   */
  private String lastError;

  /**
   * Broker de destino activo al momento del despacho (noop, jms, rabbitmq, kafka).
   */
  private String targetBroker;

  /**
   * Origen del evento (e.g. tacocloud-api, kitchen-service).
   */
  private String source;

}
