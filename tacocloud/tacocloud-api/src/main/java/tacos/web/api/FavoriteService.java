package tacos.web.api;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.User;
import tacos.UserFavorite;
import tacos.data.TacoRepository;
import tacos.data.UserFavoriteRepository;
import tacos.data.UserRepository;
import tacos.web.api.dto.FavoriteRequest;
import tacos.web.api.dto.FavoriteResponse;
import tacos.web.api.dto.TacoResponse;

// Ejercicio 21: Favoritos por usuario sin confiar en userId del cliente
@Service
public class FavoriteService {

  private final UserFavoriteRepository favoriteRepo;
  private final TacoRepository tacoRepo;
  private final UserRepository userRepo;

  @Autowired
  public FavoriteService(UserFavoriteRepository favoriteRepo,
                         TacoRepository tacoRepo,
                         UserRepository userRepo) {
    this.favoriteRepo = favoriteRepo;
    this.tacoRepo = tacoRepo;
    this.userRepo = userRepo;
  }

  /**
   * Resuelve autoritativamente la identidad del usuario desde el Principal autenticado,
   * sin depender de ningún parámetro o cabecera que envíe el cliente.
   */
  public Mono<User> resolveAuthenticatedUser(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    if (principal instanceof Authentication) {
      Object p = ((Authentication) principal).getPrincipal();
      if (p instanceof User) {
        return Mono.just((User) p);
      }
    }

    return userRepo.findByUsername(principal.getName().trim())
        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Usuario autenticado no encontrado en el sistema: " + principal.getName())));
  }

  /**
   * Obtiene los favoritos exclusivos del usuario autenticado.
   */
  public Flux<FavoriteResponse> getUserFavorites(Principal principal) {
    return resolveAuthenticatedUser(principal)
        .flatMapMany(user -> favoriteRepo.findByUserId(user.getId())
            .flatMap(fav -> tacoRepo.findById(fav.getTacoId())
                .map(taco -> FavoriteResponse.builder()
                    .favoriteId(fav.getId())
                    .userId(user.getId())
                    .username(user.getUsername())
                    .taco(TacoResponse.fromEntity(taco))
                    .addedAt(fav.getAddedAt())
                    .build())
            )
        );
  }

  /**
   * Agrega un taco a los favoritos del usuario autenticado.
   * Si el cliente intenta inyectar un userId ajeno en la petición, se rechaza con 403 Forbidden.
   */
  public Mono<FavoriteResponse> addFavorite(Principal principal, FavoriteRequest request) {
    if (request == null || request.getTacoId() == null || request.getTacoId().trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del taco es obligatorio"));
    }

    return resolveAuthenticatedUser(principal)
        .flatMap(user -> {
          // Control de seguridad: Si el cliente envía un userId que no coincide con el usuario autenticado
          if (request.getUserId() != null && !request.getUserId().trim().isEmpty()
              && !request.getUserId().trim().equals(user.getId())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Acceso denegado: No está autorizado para manipular favoritos en nombre de otro usuario."));
          }

          String tacoId = request.getTacoId().trim();

          // Validar existencia del taco en el catálogo
          return tacoRepo.findById(tacoId)
              .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Taco no encontrado: " + tacoId)))
              .flatMap(taco -> favoriteRepo.findByUserIdAndTacoId(user.getId(), tacoId)
                  .switchIfEmpty(Mono.defer(() -> {
                    UserFavorite newFav = new UserFavorite(user.getId(), user.getUsername(), tacoId);
                    return favoriteRepo.save(newFav);
                  }))
                  .map(fav -> FavoriteResponse.builder()
                      .favoriteId(fav.getId())
                      .userId(user.getId())
                      .username(user.getUsername())
                      .taco(TacoResponse.fromEntity(taco))
                      .addedAt(fav.getAddedAt())
                      .build())
              );
        });
  }

  /**
   * Elimina un taco de los favoritos del usuario autenticado.
   * Si el cliente envía un userId ajeno como parámetro, se rechaza con 403 Forbidden.
   */
  public Mono<Void> removeFavorite(Principal principal, String tacoId, String untrustedUserId) {
    if (tacoId == null || tacoId.trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El ID del taco es obligatorio"));
    }

    return resolveAuthenticatedUser(principal)
        .flatMap(user -> {
          // Control de seguridad: Si el cliente envía un userId ajeno
          if (untrustedUserId != null && !untrustedUserId.trim().isEmpty()
              && !untrustedUserId.trim().equals(user.getId())) {
            return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Acceso denegado: No está autorizado para eliminar favoritos de otro usuario."));
          }

          // Eliminar únicamente el registro que pertenezca al usuario autenticado
          return favoriteRepo.deleteByUserIdAndTacoId(user.getId(), tacoId.trim());
        });
  }

  /**
   * Comprueba si un taco específico es favorito del usuario autenticado.
   */
  public Mono<Boolean> isFavorite(Principal principal, String tacoId) {
    if (tacoId == null || tacoId.trim().isEmpty()) {
      return Mono.just(false);
    }
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> favoriteRepo.existsByUserIdAndTacoId(user.getId(), tacoId.trim()));
  }
}
