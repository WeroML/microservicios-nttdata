package tacos.web.api;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import tacos.Taco;
import tacos.data.TacoRepository;
import tacos.web.api.dto.TacoOfTheDayResponse;
import tacos.web.api.dto.TacoResponse;

// Ejercicio 20: Taco del día determinista y comprobable
@Service
public class TacoOfTheDayService {

  public static final BigDecimal DEFAULT_DISCOUNT_PERCENTAGE = new BigDecimal("20.00");

  private final TacoRepository tacoRepo;
  private final Clock clock;

  public TacoOfTheDayService(TacoRepository tacoRepo) {
    this(tacoRepo, Clock.systemDefaultZone());
  }

  @Autowired
  public TacoOfTheDayService(TacoRepository tacoRepo, @Autowired(required = false) Clock clock) {
    this.tacoRepo = tacoRepo;
    this.clock = (clock != null) ? clock : Clock.systemDefaultZone();
  }

  public Clock getClock() {
    return clock;
  }

  public Mono<TacoOfTheDayResponse> getTacoOfTheDay() {
    return getTacoOfTheDay(LocalDate.now(clock), DEFAULT_DISCOUNT_PERCENTAGE);
  }

  public Mono<TacoOfTheDayResponse> getTacoOfTheDay(LocalDate date) {
    return getTacoOfTheDay(date, DEFAULT_DISCOUNT_PERCENTAGE);
  }

  public Mono<TacoOfTheDayResponse> getTacoOfTheDay(LocalDate date, BigDecimal discountPercentage) {
    LocalDate targetDate = (date != null) ? date : LocalDate.now(clock);
    BigDecimal discountPct = (discountPercentage != null && discountPercentage.compareTo(BigDecimal.ZERO) >= 0)
        ? discountPercentage
        : DEFAULT_DISCOUNT_PERCENTAGE;

    return tacoRepo.findAll()
        .collectList()
        .flatMap(allTacos -> {
          if (allTacos == null || allTacos.isEmpty()) {
            return Mono.empty();
          }

          // Priorizar tacos disponibles en inventario
          List<Taco> candidates = allTacos.stream()
              .filter(t -> t.isAvailable() && t.isInStock())
              .collect(Collectors.toList());

          // Si ninguno tiene inventario, usar disponibles
          if (candidates.isEmpty()) {
            candidates = allTacos.stream()
                .filter(Taco::isAvailable)
                .collect(Collectors.toList());
          }

          // Si ninguno está marcado disponible, usar todos los tacos existentes
          if (candidates.isEmpty()) {
            candidates = allTacos;
          }

          // Ordenamiento determinista para garantizar que el índice sea idéntico
          List<Taco> sortedCandidates = new ArrayList<>(candidates);
          sortedCandidates.sort(Comparator.comparing(
              t -> t.getId() != null ? t.getId() : (t.getName() != null ? t.getName() : "")
          ));

          // Algoritmo de rotación determinista por día del calendario
          long epochDay = targetDate.toEpochDay();
          int index = (int) Math.floorMod(epochDay, sortedCandidates.size());
          Taco selected = sortedCandidates.get(index);

          // Cálculo del precio original y del descuento del día
          BigDecimal originalPrice = selected.getPrice();
          if (originalPrice == null) {
            originalPrice = selected.calculatePriceFromIngredients();
          }
          if (originalPrice == null) {
            originalPrice = BigDecimal.ZERO;
          }
          originalPrice = originalPrice.setScale(2, RoundingMode.HALF_UP);

          BigDecimal discountMultiplier = discountPct.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
          BigDecimal savings = originalPrice.multiply(discountMultiplier).setScale(2, RoundingMode.HALF_UP);
          BigDecimal specialPrice = originalPrice.subtract(savings).setScale(2, RoundingMode.HALF_UP);
          if (specialPrice.compareTo(BigDecimal.ZERO) < 0) {
            specialPrice = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
          }

          String headline = String.format("¡Taco del Día! %s con %s%% de descuento",
              selected.getName(), discountPct.stripTrailingZeros().toPlainString());

          TacoResponse tacoResponse = TacoResponse.fromEntity(selected);

          TacoOfTheDayResponse response = TacoOfTheDayResponse.builder()
              .date(targetDate)
              .taco(tacoResponse)
              .originalPrice(originalPrice)
              .specialPrice(specialPrice)
              .discountPercentage(discountPct.setScale(2, RoundingMode.HALF_UP))
              .savings(savings)
              .promotionHeadline(headline)
              .build();

          return Mono.just(response);
        });
  }
}
