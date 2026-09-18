package tacos.kitchen.consumer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 30: Consumidor idempotente, retry limitado y DLQ
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConsumeResult {

  private boolean success;
  private String messageKey;
  private ProcessStatus status;
  private int attempts;
  private String errorMessage;
  private String deadLetterId;

  public static ConsumeResult success(String key, int attempts) {
    return ConsumeResult.builder()
        .success(true)
        .messageKey(key)
        .status(ProcessStatus.PROCESSED)
        .attempts(attempts)
        .build();
  }

  public static ConsumeResult duplicateSkipped(String key) {
    return ConsumeResult.builder()
        .success(true)
        .messageKey(key)
        .status(ProcessStatus.DUPLICATE_SKIPPED)
        .attempts(0)
        .build();
  }

  public static ConsumeResult failedDlq(String key, int attempts, String deadLetterId, String errorMessage) {
    return ConsumeResult.builder()
        .success(false)
        .messageKey(key)
        .status(ProcessStatus.FAILED_DLQ)
        .attempts(attempts)
        .deadLetterId(deadLetterId)
        .errorMessage(errorMessage)
        .build();
  }

}
