package tacos;

import java.io.Serializable;
import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 21: Favoritos por usuario sin confiar en userId del cliente
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document
@CompoundIndex(name = "user_taco_idx", def = "{'userId': 1, 'tacoId': 1}", unique = true)
public class UserFavorite implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  private String id;

  private String userId;
  private String username;
  private String tacoId;
  private Date addedAt = new Date();

  public UserFavorite(String userId, String username, String tacoId) {
    this.userId = userId;
    this.username = username;
    this.tacoId = tacoId;
    this.addedAt = new Date();
  }
}
