package tacos.web.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KitchenQueueResponse {

  private int totalInQueue;
  private int totalWaiting;
  private int totalPreparing;
  private int estimatedQueueWaitMinutes;
  private List<KitchenQueueItem> orders;
}
