package tacos.web.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 22: Calificaciones y ranking de tacos
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TacoRankingItem {

  private int rank;
  private TacoResponse taco;
  private double averageRating;
  private int totalRatings;
  private String formattedRating;
}
