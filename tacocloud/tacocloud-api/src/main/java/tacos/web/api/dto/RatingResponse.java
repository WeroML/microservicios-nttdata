package tacos.web.api.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.TacoRating;

// Ejercicio 22: Calificaciones y ranking de tacos
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingResponse {

  private String id;
  private String tacoId;
  private String userId;
  private String username;
  private int rating;
  private String comment;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
  private Date createdAt;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
  private Date updatedAt;

  public static RatingResponse fromEntity(TacoRating entity) {
    if (entity == null) {
      return null;
    }
    return RatingResponse.builder()
        .id(entity.getId())
        .tacoId(entity.getTacoId())
        .userId(entity.getUserId())
        .username(entity.getUsername())
        .rating(entity.getRating())
        .comment(entity.getComment())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }
}
