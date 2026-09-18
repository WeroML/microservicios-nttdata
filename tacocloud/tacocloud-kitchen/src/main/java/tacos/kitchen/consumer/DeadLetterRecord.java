package tacos.kitchen.consumer;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeadLetterRecord {

  private String dlqId;
  private String messageKey;
  private String broker;
  private Object payload;
  private String payloadType;
  private int attempts;
  private String errorMessage;
  private String exceptionClass;
  private String stackTrace;
  private Date failedAt;
  private DeadLetterStatus status;
  private Date replayedAt;

}
