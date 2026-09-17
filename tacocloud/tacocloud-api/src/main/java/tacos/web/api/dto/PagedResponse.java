package tacos.web.api.dto;

import java.util.Collections;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// Ejercicio 19: Buscar, filtrar, ordenar y paginar tacos
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagedResponse<T> {

  private List<T> content;
  private int page;
  private int size;
  private long totalElements;
  private int totalPages;
  private boolean first;
  private boolean last;
  private boolean hasNext;
  private boolean hasPrevious;

  public static <T> PagedResponse<T> of(List<T> allItems, int page, int size) {
    if (size <= 0) {
      size = 10;
    }
    if (page < 0) {
      page = 0;
    }

    long totalElements = (allItems != null) ? allItems.size() : 0;
    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

    List<T> content;
    if (allItems == null || allItems.isEmpty() || page >= totalPages) {
      content = Collections.emptyList();
    } else {
      int fromIndex = page * size;
      int toIndex = Math.min(fromIndex + size, allItems.size());
      content = allItems.subList(fromIndex, toIndex);
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
  }
}
