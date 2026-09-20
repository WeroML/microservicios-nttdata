package tacos.web.api;

import java.security.Principal;

import javax.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import tacos.announcements.OperationalAnnouncementsService;
import tacos.web.api.dto.AnnouncementResponse;
import tacos.web.api.dto.CreateAnnouncementRequest;

// Ejercicio 33: Reemplazar Notes por anuncios operativos seguros
@RestController
@RequestMapping(path = "/api/announcements", produces = MediaType.APPLICATION_JSON_VALUE)
@CrossOrigin(origins = "*")
public class OperationalAnnouncementsController {

  private static final Logger log = LoggerFactory.getLogger(OperationalAnnouncementsController.class);

  private final OperationalAnnouncementsService announcementsService;

  public OperationalAnnouncementsController(OperationalAnnouncementsService announcementsService) {
    this.announcementsService = announcementsService;
  }

  /**
   * Consulta los anuncios operativos activos y vigentes.
   * Acceso público y operativo para cocina, reparto y clientes.
   */
  @GetMapping
  public Flux<AnnouncementResponse> getActiveAnnouncements(
      @RequestParam(required = false) String scope,
      @RequestParam(required = false) String level) {
    log.debug("// Ejercicio 33: Consultando anuncios operativos activos (scope={}, level={})", scope, level);
    return announcementsService.getActiveAnnouncements(scope, level)
        .map(AnnouncementResponse::fromEntity);
  }

  /**
   * Obtiene un anuncio por su identificador único.
   */
  @GetMapping("/{id}")
  public Mono<ResponseEntity<AnnouncementResponse>> getAnnouncementById(@PathVariable("id") String id) {
    return announcementsService.getAnnouncementById(id)
        .map(entity -> ResponseEntity.ok(AnnouncementResponse.fromEntity(entity)));
  }

  /**
   * Consulta el histórico total de anuncios (activos e inactivos) para auditoría.
   */
  @GetMapping("/all")
  public Flux<AnnouncementResponse> getAllAnnouncements() {
    log.debug("// Ejercicio 33: Consultando histórico completo de anuncios operativos");
    return announcementsService.getAllAnnouncements()
        .map(AnnouncementResponse::fromEntity);
  }

  /**
   * Publica un nuevo anuncio operativo seguro.
   * Requiere autorización (ROLE_ADMIN o ROLE_OPERATOR).
   */
  @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
  @ResponseStatus(HttpStatus.CREATED)
  public Mono<ResponseEntity<AnnouncementResponse>> createAnnouncement(
      @Valid @RequestBody CreateAnnouncementRequest request,
      Principal principal) {
    if (!isAuthorizedOperator(principal)) {
      return Mono.error(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
          "Acceso denegado: Solo personal autorizado (ROLE_ADMIN o ROLE_OPERATOR) puede crear anuncios operativos."));
    }
    String username = principal != null ? principal.getName() : "admin";
    log.info("// Ejercicio 33: Solicitud de creación de anuncio recibida de '{}'", username);

    return announcementsService.createAnnouncement(request, username)
        .map(created -> ResponseEntity.status(HttpStatus.CREATED).body(AnnouncementResponse.fromEntity(created)));
  }

  /**
   * Desactiva un anuncio operativo (soft delete con auditoría).
   * Requiere autorización (ROLE_ADMIN o ROLE_OPERATOR).
   */
  @DeleteMapping("/{id}")
  public Mono<ResponseEntity<AnnouncementResponse>> deactivateAnnouncement(
      @PathVariable("id") String id,
      Principal principal) {
    if (!isAuthorizedOperator(principal)) {
      return Mono.error(new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
          "Acceso denegado: Solo personal autorizado (ROLE_ADMIN o ROLE_OPERATOR) puede desactivar anuncios operativos."));
    }
    String username = principal != null ? principal.getName() : "operator";
    log.info("// Ejercicio 33: Solicitud de baja de anuncio '{}' recibida de '{}'", id, username);

    return announcementsService.deactivateAnnouncement(id, username)
        .map(updated -> ResponseEntity.ok(AnnouncementResponse.fromEntity(updated)));
  }

  private boolean isAuthorizedOperator(Principal principal) {
    if (principal == null) {
      return false;
    }
    if (principal instanceof org.springframework.security.core.Authentication) {
      org.springframework.security.core.Authentication auth = (org.springframework.security.core.Authentication) principal;
      if (auth.getAuthorities() != null) {
        for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
          String authStr = ga.getAuthority();
          if ("ROLE_ADMIN".equalsIgnoreCase(authStr) || "ADMIN".equalsIgnoreCase(authStr)
              || "ROLE_OPERATOR".equalsIgnoreCase(authStr) || "OPERATOR".equalsIgnoreCase(authStr)) {
            return true;
          }
        }
      }
    }
    String name = principal.getName() != null ? principal.getName().trim() : "";
    return "admin".equalsIgnoreCase(name) || "operator".equalsIgnoreCase(name);
  }
}
