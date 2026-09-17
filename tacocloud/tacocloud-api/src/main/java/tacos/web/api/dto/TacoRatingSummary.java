package tacos.web.api.dto;

import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 22: Calificaciones y ranking de tacos
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TacoRatingSummary {

  private String tacoId;
  private String tacoName;
  private double averageRating;
  private int totalRatings;
  private Map<Integer, Long> starDistribution;
  private String formattedSummary;
}
