package tacos.web.api.dto;

import java.util.Date;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 29: Outbox transaccional para no perder órdenes
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxRelaySummary {

  private int processedCount;
  private int successCount;
  private int failedCount;
  private String activeBroker;
  private Date executedAt;
  private String message;

}
