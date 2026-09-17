package tacos;

import java.io.Serializable;
import java.util.Date;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 22: Calificaciones y ranking de tacos
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document
@CompoundIndex(name = "taco_user_rating_idx", def = "{'tacoId': 1, 'userId': 1}", unique = true)
public class TacoRating implements Serializable {

  private static final long serialVersionUID = 1L;

  @Id
  private String id;

  @NotNull
  private String tacoId;

  @NotNull
  private String userId;

  private String username;

  @Min(value = 1, message = "La calificación mínima es 1 estrella")
  @Max(value = 5, message = "La calificación máxima es 5 estrellas")
  private int rating;

  @Size(max = 500, message = "El comentario no puede exceder los 500 caracteres")
  private String comment;

  private Date createdAt = new Date();
  private Date updatedAt = new Date();

  public TacoRating(String tacoId, String userId, String username, int rating, String comment) {
    this.tacoId = tacoId;
    this.userId = userId;
    this.username = username;
    this.rating = rating;
    this.comment = comment;
    this.createdAt = new Date();
    this.updatedAt = new Date();
  }
}
