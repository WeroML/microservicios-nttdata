package tacos.kitchen.consumer;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
// Ejercicio 31: Correlation ID de HTTP a evento y logs
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedMessageRecord {

  private String messageKey;
  private String correlationId;
  private String broker;
  private ProcessStatus status;
  private Date firstReceivedAt;
  private Date processedAt;
  private int attempts;
  private String lastError;

}
