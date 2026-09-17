package tacos.events;

import java.io.Serializable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import tacos.User;

// Ejercicio 27: Contrato único de eventos de orden
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderEventCustomer implements Serializable {

  private static final long serialVersionUID = 1L;

  private String customerId;
  private String username;
  private String fullname;
  private String email;
  private String phoneNumber;

  public static OrderEventCustomer fromUser(User user) {
    if (user == null) {
      return null;
    }
    return OrderEventCustomer.builder()
        .customerId(user.getId())
        .username(user.getUsername())
        .fullname(user.getFullname())
        .email(user.getEmail())
        .phoneNumber(user.getPhoneNumber())
        .build();
  }
}
