package tacos.web.api.dto;

import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 21: Favoritos por usuario sin confiar en userId del cliente
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteResponse {

  private String favoriteId;
  private String userId;
  private String username;
  private TacoResponse taco;

  @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
  private Date addedAt;
}
