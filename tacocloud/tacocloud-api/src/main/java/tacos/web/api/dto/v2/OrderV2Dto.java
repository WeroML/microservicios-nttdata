package tacos.web.api.dto.v2;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 35: Versionar la API y publicar contrato OpenAPI
public class OrderV2Dto {

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class AddressDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private String street;
    private String city;
    private String state;
    private String zip;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderItemV2Request implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private Integer quantity;
    private List<String> ingredientIds;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderItemV2Response implements Serializable {
    private static final long serialVersionUID = 1L;
    private String name;
    private Integer quantity;
    private List<String> ingredientIds;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class PricingBreakdownDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private BigDecimal subTotal;
    private BigDecimal discount;
    private BigDecimal tax;
    private BigDecimal total;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderTrackingDto implements Serializable {
    private static final long serialVersionUID = 1L;
    private Integer estimatedMinutes;
    private Integer queuePosition;
    private String statusUrl;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderV2Request implements Serializable {
    private static final long serialVersionUID = 1L;
    private String deliveryName;
    private AddressDto deliveryAddress;
    private List<OrderItemV2Request> items;
    private String couponCode;
    private String paymentToken;
    private String idempotencyKey;
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class OrderV2Response implements Serializable {
    private static final long serialVersionUID = 1L;
    private String id;
    @Builder.Default
    private String version = "2.0";
    private String status;
    private Date placedAt;
    private String correlationId;
    private String idempotencyKey;
    private String customerName;
    private AddressDto deliveryAddress;
    private PricingBreakdownDto pricingBreakdown;
    private List<OrderItemV2Response> items;
    private OrderTrackingDto tracking;
    private Map<String, String> links;
  }
}
