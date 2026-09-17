package tacos.web.api;

import java.security.Principal;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;
import tacos.User;
import tacos.data.UserRepository;
import tacos.web.api.dto.ClaimOrderRequest;
import tacos.web.api.dto.KitchenQueueItem;
import tacos.web.api.dto.KitchenQueueResponse;
import tacos.web.api.dto.OrderEtaResponse;

// Ejercicio 26: Cola de cocina, claim atómico y tiempo estimado
@RestController
@RequestMapping(path = "/api/kitchen", produces = "application/json")
@CrossOrigin(origins = "http://localhost:8080")
public class KitchenApiController {

  private final KitchenService kitchenService;
  private final UserRepository userRepo;

  @Autowired
  public KitchenApiController(KitchenService kitchenService,
                              @Autowired(required = false) UserRepository userRepo) {
    this.kitchenService = kitchenService;
    this.userRepo = userRepo;
  }

  @GetMapping("/queue")
  public Mono<KitchenQueueResponse> getQueue(Principal principal) {
    if (principal == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
    if (!isUserAdmin(principal)) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Acceso denegado: Solo personal de cocina o administradores (ROLE_ADMIN) pueden consultar la cola de cocina."));
    }
    return kitchenService.getKitchenQueue();
  }

  @PostMapping("/orders/{orderId}/claim")
  public Mono<KitchenQueueItem> claimOrder(@PathVariable("orderId") String orderId,
                                           @RequestBody(required = false) ClaimOrderRequest request,
                                           Principal principal) {
    return claimOrderInternal(orderId, request, principal);
  }

  @PostMapping("/{orderId}/claim")
  public Mono<KitchenQueueItem> claimOrderDirect(@PathVariable("orderId") String orderId,
                                                 @RequestBody(required = false) ClaimOrderRequest request,
                                                 Principal principal) {
    return claimOrderInternal(orderId, request, principal);
  }

  @PostMapping("/orders/{orderId}/unclaim")
  public Mono<KitchenQueueItem> unclaimOrder(@PathVariable("orderId") String orderId,
                                             Principal principal) {
    return unclaimOrderInternal(orderId, principal);
  }

  @PostMapping("/{orderId}/unclaim")
  public Mono<KitchenQueueItem> unclaimOrderDirect(@PathVariable("orderId") String orderId,
                                                   Principal principal) {
    return unclaimOrderInternal(orderId, principal);
  }

  @GetMapping("/orders/{orderId}/eta")
  public Mono<OrderEtaResponse> getOrderEta(@PathVariable("orderId") String orderId,
                                            Principal principal) {
    return getOrderEtaInternal(orderId, principal);
  }

  @GetMapping("/{orderId}/eta")
  public Mono<OrderEtaResponse> getOrderEtaDirect(@PathVariable("orderId") String orderId,
                                                  Principal principal) {
    return getOrderEtaInternal(orderId, principal);
  }

  private Mono<KitchenQueueItem> claimOrderInternal(String orderId, ClaimOrderRequest request, Principal principal) {
    if (principal == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
    if (!isUserAdmin(principal)) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Acceso denegado: Solo personal de cocina o administradores (ROLE_ADMIN) pueden reclamar órdenes."));
    }
    String chefId = principal.getName();
    return kitchenService.claimOrder(orderId, chefId, request);
  }

  private Mono<KitchenQueueItem> unclaimOrderInternal(String orderId, Principal principal) {
    if (principal == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
    if (!isUserAdmin(principal)) {
      return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN,
          "Acceso denegado: Solo personal de cocina o administradores (ROLE_ADMIN) pueden liberar órdenes."));
    }
    String chefId = principal.getName();
    boolean isAdmin = isUserAdmin(principal);
    return kitchenService.unclaimOrder(orderId, chefId, isAdmin);
  }

  private Mono<OrderEtaResponse> getOrderEtaInternal(String orderId, Principal principal) {
    if (principal == null) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }
    return resolveAuthenticatedUser(principal)
        .flatMap(user -> {
          boolean isAdmin = isUserAdmin(principal);
          return kitchenService.getOrderEta(orderId, user, isAdmin);
        });
  }

  private Mono<User> resolveAuthenticatedUser(Principal principal) {
    if (principal == null || principal.getName() == null || principal.getName().trim().isEmpty()) {
      return Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario no autenticado"));
    }

    if (principal instanceof Authentication) {
      Object p = ((Authentication) principal).getPrincipal();
      if (p instanceof User) {
        return Mono.just((User) p);
      }
    }

    if (userRepo != null) {
      return userRepo.findByUsername(principal.getName().trim())
          .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.UNAUTHORIZED,
              "Usuario autenticado no encontrado: " + principal.getName())));
    }

    User fallbackUser = new User(principal.getName(), "PROTECTED", principal.getName(), null, null, null, null, null, null);
    fallbackUser.setId(principal.getName());
    return Mono.just(fallbackUser);
  }

  private boolean isUserAdmin(Principal principal) {
    if (principal == null) {
      return false;
    }
    if (principal instanceof Authentication) {
      Authentication auth = (Authentication) principal;
      if (auth.getAuthorities() != null) {
        for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
          if ("ROLE_ADMIN".equalsIgnoreCase(ga.getAuthority()) || "ADMIN".equalsIgnoreCase(ga.getAuthority())) {
            return true;
          }
        }
      }
    }
    return "admin".equalsIgnoreCase(principal.getName().trim());
  }
}
