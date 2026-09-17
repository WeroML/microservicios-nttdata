package tacos.web.api;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import tacos.Ingredient;
import tacos.Taco;
import tacos.data.TacoRepository;
import tacos.web.api.dto.PagedResponse;
import tacos.web.api.dto.TacoResponse;
import tacos.web.api.dto.TacoSearchCriteria;

// Ejercicio 19: Buscar, filtrar, ordenar y paginar tacos
@Service
public class TacoQueryService {

  private final TacoRepository tacoRepo;

  @Autowired
  public TacoQueryService(TacoRepository tacoRepo) {
    this.tacoRepo = tacoRepo;
  }

  public Mono<PagedResponse<TacoResponse>> searchTacos(TacoSearchCriteria criteria) {
    TacoSearchCriteria safeCriteria = (criteria != null) ? criteria : new TacoSearchCriteria();

    return tacoRepo.findAll()
        .doOnNext(Taco::updateDietaryAndAllergenInfo)
        .filter(taco -> matchesCriteria(taco, safeCriteria))
        .collectList()
        .map(filteredTacos -> {
          Comparator<Taco> comparator = getComparator(safeCriteria);
          filteredTacos.sort(comparator);

          int totalElements = filteredTacos.size();
          int page = Math.max(0, safeCriteria.getPage());
          int size = safeCriteria.getSize() <= 0 ? 10 : safeCriteria.getSize();
          int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

          List<TacoResponse> content;
          if (totalElements == 0 || page >= totalPages) {
            content = Collections.emptyList();
          } else {
            int fromIndex = page * size;
            int toIndex = Math.min(fromIndex + size, totalElements);
            content = filteredTacos.subList(fromIndex, toIndex).stream()
                .map(TacoResponse::fromEntity)
                .collect(Collectors.toList());
          }

          boolean first = page == 0;
          boolean last = totalPages == 0 || page >= totalPages - 1;
          boolean hasNext = page < totalPages - 1;
          boolean hasPrevious = page > 0 && totalPages > 0;

          return new PagedResponse<>(
              content,
              page,
              size,
              totalElements,
              totalPages,
              first,
              last,
              hasNext,
              hasPrevious
          );
        });
  }

  private boolean matchesCriteria(Taco taco, TacoSearchCriteria criteria) {
    // 1. Text search: matches name or any ingredient name/id
    if (criteria.getSearch() != null && !criteria.getSearch().trim().isEmpty()) {
      String search = criteria.getSearch().trim().toLowerCase();
      boolean nameMatches = taco.getName() != null && taco.getName().toLowerCase().contains(search);
      boolean ingredientMatches = false;
      if (taco.getIngredients() != null) {
        for (Ingredient ing : taco.getIngredients()) {
          if (ing == null) continue;
          if (ing.getId() != null && ing.getId().toLowerCase().contains(search)) {
            ingredientMatches = true;
            break;
          }
          if (ing.getName() != null && ing.getName().toLowerCase().contains(search)) {
            ingredientMatches = true;
            break;
          }
        }
      }
      if (!nameMatches && !ingredientMatches) {
        return false;
      }
    }

    // 2. Specific ingredient filter
    if (criteria.getIngredient() != null && !criteria.getIngredient().trim().isEmpty()) {
      String ingFilter = criteria.getIngredient().trim().toLowerCase();
      boolean found = false;
      if (taco.getIngredients() != null) {
        for (Ingredient ing : taco.getIngredients()) {
          if (ing == null) continue;
          if (ing.getId() != null && ing.getId().equalsIgnoreCase(ingFilter)) {
            found = true;
            break;
          }
          if (ing.getName() != null && ing.getName().toLowerCase().contains(ingFilter)) {
            found = true;
            break;
          }
        }
      }
      if (!found) {
        return false;
      }
    }

    // 3. Price range
    BigDecimal price = taco.getPrice();
    if (price == null) {
      price = taco.calculatePriceFromIngredients();
    }
    if (criteria.getMinPrice() != null) {
      if (price == null || price.compareTo(criteria.getMinPrice()) < 0) {
        return false;
      }
    }
    if (criteria.getMaxPrice() != null) {
      if (price == null || price.compareTo(criteria.getMaxPrice()) > 0) {
        return false;
      }
    }

    // 4. Availability & Stock
    if (criteria.getAvailable() != null) {
      if (criteria.getAvailable() && !taco.isAvailable()) {
        return false;
      }
      if (!criteria.getAvailable() && taco.isAvailable()) {
        return false;
      }
    }
    if (criteria.getInStock() != null) {
      if (criteria.getInStock() && !taco.isInStock()) {
        return false;
      }
      if (!criteria.getInStock() && taco.isInStock()) {
        return false;
      }
    }

    // 5. Dietary Label
    if (criteria.getDietary() != null && !taco.hasDietaryLabel(criteria.getDietary())) {
      return false;
    }

    // 6. Exclude Allergen
    if (criteria.getExcludeAllergen() != null && taco.hasAllergen(criteria.getExcludeAllergen())) {
      return false;
    }

    // 7. Max Spice Level
    if (criteria.getMaxSpice() != null && taco.computeSpiceLevel().getLevel() > criteria.getMaxSpice().getLevel()) {
      return false;
    }

    return true;
  }

  private Comparator<Taco> getComparator(TacoSearchCriteria criteria) {
    String sortBy = criteria.getSortBy() != null ? criteria.getSortBy().toLowerCase() : "name";
    Comparator<Taco> comparator;

    switch (sortBy) {
      case "price":
        comparator = Comparator.comparing(
            t -> t.getPrice() != null ? t.getPrice() : (t.calculatePriceFromIngredients() != null ? t.calculatePriceFromIngredients() : BigDecimal.ZERO)
        );
        break;
      case "createdat":
      case "date":
        comparator = Comparator.comparing(
            t -> t.getCreatedAt() != null ? t.getCreatedAt() : new Date(0)
        );
        break;
      case "spicelevel":
      case "spice":
        comparator = Comparator.comparingInt(
            t -> t.computeSpiceLevel() != null ? t.computeSpiceLevel().getLevel() : 0
        );
        break;
      case "stock":
        comparator = Comparator.comparingInt(
            t -> t.getStock() != null ? t.getStock() : 0
        );
        break;
      case "name":
      default:
        comparator = Comparator.comparing(
            t -> t.getName() != null ? t.getName().toLowerCase() : "",
            String.CASE_INSENSITIVE_ORDER
        );
        break;
    }

    if ("desc".equalsIgnoreCase(criteria.getSortDir())) {
      comparator = comparator.reversed();
    }

    // Tie-breaker by id for stable pagination
    return comparator.thenComparing(t -> t.getId() != null ? t.getId() : "");
  }
}
