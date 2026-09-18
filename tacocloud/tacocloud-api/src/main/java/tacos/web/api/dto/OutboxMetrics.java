package tacos.web.api.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.outbox.OutboxStatus;

// Ejercicio 29: Outbox transaccional para no perder órdenes
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxMetrics {

  private long totalCount;
  private long pendingCount;
  private long publishedCount;
  private long failedCount;
  private long deadLetterCount;
  private Map<OutboxStatus, Long> countsByStatus;
  private String activeBroker;

}
