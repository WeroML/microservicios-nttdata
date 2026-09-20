package tacos.web.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.reactive.server.WebTestClient;

import reactor.core.publisher.Mono;
import tacos.actuator.NotesEndpoint;
import tacos.actuator.NotesEndpoint.SafeNote;
import tacos.actuator.OperationalAnnouncementsEndpoint;
import tacos.announcements.AnnouncementLevel;
import tacos.announcements.AnnouncementScope;
import tacos.announcements.OperationalAnnouncement;
import tacos.announcements.OperationalAnnouncementsService;
import tacos.web.api.dto.CreateAnnouncementRequest;
import tacos.web.api.errors.ProblemDetailsExceptionHandler;

// Ejercicio 33: Reemplazar Notes por anuncios operativos seguros
public class OperationalAnnouncementsSecurityAndEndpointTest {

  private OperationalAnnouncementsService announcementsService;
  private OperationalAnnouncementsController controller;
  private OperationalAnnouncementsEndpoint actuatorEndpoint;
  private WebTestClient testClient;

  @BeforeEach
  void setUp() {
    announcementsService = new OperationalAnnouncementsService();
    announcementsService.clear();

    controller = new OperationalAnnouncementsController(announcementsService);
    actuatorEndpoint = new OperationalAnnouncementsEndpoint(announcementsService);

    testClient = WebTestClient.bindToController(controller)
        .controllerAdvice(new ProblemDetailsExceptionHandler())
        .webFilter((exchange, chain) -> {
          String testUser = exchange.getRequest().getHeaders().getFirst("X-Test-User");
          String testRole = exchange.getRequest().getHeaders().getFirst("X-Test-Role");
          if (testUser != null && !testUser.trim().isEmpty()) {
            List<GrantedAuthority> authorities = new ArrayList<>();
            if (testRole != null && !testRole.trim().isEmpty()) {
              authorities.add(new SimpleGrantedAuthority(testRole.trim()));
            } else {
              authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
            }
            Authentication auth = new UsernamePasswordAuthenticationToken(testUser.trim(), "password", authorities);
            return chain.filter(exchange.mutate().principal(Mono.just(auth)).build());
          }
          return chain.filter(exchange);
        })
        .build();
  }

  // ==========================================
  // CREACIÓN SEGURA Y VALIDACIÓN DE ANUNCIOS
  // ==========================================

  @Test
  @DisplayName("1. Creación Válida: Genera UUID inmutable, timestamp y auditoría de creador")
  void testCreateAnnouncement_ValidPayload_ReturnsCreatedWithUuidAndAudit() {
    CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
        .title("Mantenimiento Horno 1")
        .message("El horno principal estará en limpieza durante 45 minutos.")
        .level(AnnouncementLevel.WARNING)
        .scope(AnnouncementScope.KITCHEN)
        .durationMinutes(60L)
        .build();

    testClient.post()
        .uri("/api/announcements")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.id").isNotEmpty()
        .jsonPath("$.title").isEqualTo("Mantenimiento Horno 1")
        .jsonPath("$.message").isEqualTo("El horno principal estará en limpieza durante 45 minutos.")
        .jsonPath("$.level").isEqualTo("WARNING")
        .jsonPath("$.scope").isEqualTo("KITCHEN")
        .jsonPath("$.active").isEqualTo(true)
        .jsonPath("$.createdBy").isEqualTo("admin")
        .jsonPath("$.createdAt").exists()
        .jsonPath("$.expiresAt").exists();
  }

  @Test
  @DisplayName("2. Validación Problem Details (RFC 7807): Título y mensaje vacíos retornan HTTP 400")
  void testCreateAnnouncement_InvalidPayload_ReturnsProblemDetails400() {
    CreateAnnouncementRequest invalidRequest = CreateAnnouncementRequest.builder()
        .title("") // Vacío: viola @NotBlank y @Size
        .message("   ") // Solo espacios
        .build();

    testClient.post()
        .uri("/api/announcements")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(invalidRequest)
        .exchange()
        .expectStatus().isBadRequest()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(400)
        .jsonPath("$.title").isEqualTo("Validation Failed")
        .jsonPath("$.invalidParams").isArray();
  }

  // ==========================================
  // SEGURIDAD RBAC (ADMIN / OPERATOR VS USER / ANÓNIMO)
  // ==========================================

  @Test
  @DisplayName("3. Seguridad RBAC: Deniega creación a usuarios sin rol de operador (ROLE_USER)")
  void testCreateAnnouncement_UnauthorizedUser_ReturnsForbidden403() {
    CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
        .title("Intento de anuncio no autorizado")
        .message("Usuario común intentando publicar anuncios")
        .build();

    testClient.post()
        .uri("/api/announcements")
        .header("X-Test-User", "bob")
        .header("X-Test-Role", "ROLE_USER")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isForbidden()
        .expectBody()
        .jsonPath("$.status").isEqualTo(403);
  }

  @Test
  @DisplayName("4. Seguridad RBAC: Deniega creación a clientes anónimos (sin autenticación)")
  void testCreateAnnouncement_Anonymous_ReturnsForbidden403() {
    CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
        .title("Intento anónimo")
        .message("Sin credenciales de sesión")
        .build();

    testClient.post()
        .uri("/api/announcements")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isForbidden();
  }

  @Test
  @DisplayName("5. Seguridad RBAC: Permite creación a personal con rol OPERATOR")
  void testCreateAnnouncement_OperatorRole_ReturnsCreated201() {
    CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
        .title("Retraso de Repartidores")
        .message("Lluvia intensa en sector centro provocando demoras de 15 min")
        .level(AnnouncementLevel.INFO)
        .scope(AnnouncementScope.DELIVERY)
        .build();

    testClient.post()
        .uri("/api/announcements")
        .header("X-Test-User", "operator_carlos")
        .header("X-Test-Role", "ROLE_OPERATOR")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus().isCreated()
        .expectBody()
        .jsonPath("$.createdBy").isEqualTo("operator_carlos");
  }

  // ==========================================
  // BORRADO SEGURO POR ID VS VULNERABILIDAD DE ÍNDICE
  // ==========================================

  @Test
  @DisplayName("6. Borrado Seguro por ID: Desactiva por ID con sello de auditoría (no por índice)")
  void testDeactivateAnnouncement_OperatorOrAdmin_ReturnsOkAndDeactivated() {
    // 1. Crear anuncio
    CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
        .title("Alerta Temporal")
        .message("Mensaje a ser desactivado por el supervisor")
        .build();

    OperationalAnnouncement created = announcementsService.createAnnouncement(request, "admin").block();
    assertNotNull(created);
    String id = created.getId();

    // 2. Desactivar por ID
    testClient.delete()
        .uri("/api/announcements/" + id)
        .header("X-Test-User", "supervisor_ana")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.id").isEqualTo(id)
        .jsonPath("$.active").isEqualTo(false)
        .jsonPath("$.deactivatedBy").isEqualTo("supervisor_ana")
        .jsonPath("$.deactivatedAt").exists();

    // 3. Ya no debe figurar en activos
    testClient.get()
        .uri("/api/announcements")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[?(@.id == '" + id + "')]").doesNotExist();

    // 4. Sí debe figurar en histórico total para auditoría
    testClient.get()
        .uri("/api/announcements/all")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$[?(@.id == '" + id + "')].active").isEqualTo(false);
  }

  @Test
  @DisplayName("7. Seguridad RBAC: Deniega desactivación a usuarios sin privilegios (ROLE_USER)")
  void testDeactivateAnnouncement_UnauthorizedUser_ReturnsForbidden403() {
    CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
        .title("Alerta Importante")
        .message("No debe ser borrada por usuarios regulares")
        .build();

    OperationalAnnouncement created = announcementsService.createAnnouncement(request, "admin").block();
    assertNotNull(created);

    testClient.delete()
        .uri("/api/announcements/" + created.getId())
        .header("X-Test-User", "user_pedro")
        .header("X-Test-Role", "ROLE_USER")
        .exchange()
        .expectStatus().isForbidden();
  }

  @Test
  @DisplayName("8. Desactivación con ID Inexistente: Retorna HTTP 404 Problem Details")
  void testDeactivateAnnouncement_NotFound_Returns404ProblemDetails() {
    testClient.delete()
        .uri("/api/announcements/NON-EXISTENT-UUID")
        .header("X-Test-User", "admin")
        .header("X-Test-Role", "ROLE_ADMIN")
        .exchange()
        .expectStatus().isNotFound()
        .expectHeader().contentType(ProblemDetailsExceptionHandler.PROBLEM_JSON_MEDIA_TYPE)
        .expectBody()
        .jsonPath("$.status").isEqualTo(404);
  }

  // ==========================================
  // FILTRADO OPERATIVO Y EXPIRACIÓN
  // ==========================================

  @Test
  @DisplayName("9. Filtrado Operativo: Filtra activas por scope (KITCHEN, DELIVERY) y nivel")
  void testFilterAnnouncements_ByScopeAndLevel() {
    // Anuncio Cocina
    CreateAnnouncementRequest reqKitchen = CreateAnnouncementRequest.builder()
        .title("Pedido Grande")
        .message("Mesa 5 solicitó 30 tacos")
        .level(AnnouncementLevel.WARNING)
        .scope(AnnouncementScope.KITCHEN)
        .build();
    announcementsService.createAnnouncement(reqKitchen, "chef").block();

    // Anuncio Delivery
    CreateAnnouncementRequest reqDelivery = CreateAnnouncementRequest.builder()
        .title("Calle Cerrada")
        .message("Avenida Reforma bloqueada por maratón")
        .level(AnnouncementLevel.CRITICAL)
        .scope(AnnouncementScope.DELIVERY)
        .build();
    announcementsService.createAnnouncement(reqDelivery, "dispatcher").block();

    // Anuncio General
    CreateAnnouncementRequest reqGeneral = CreateAnnouncementRequest.builder()
        .title("Bienvenida de Turno")
        .message("Excelente jornada a todo el equipo")
        .level(AnnouncementLevel.INFO)
        .scope(AnnouncementScope.ALL)
        .build();
    announcementsService.createAnnouncement(reqGeneral, "manager").block();

    // Filtro por Scope = KITCHEN (debe incluir KITCHEN y ALL)
    testClient.get()
        .uri("/api/announcements?scope=KITCHEN")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(2)
        .jsonPath("$[?(@.scope == 'DELIVERY')]").doesNotExist();

    // Filtro por Level = CRITICAL
    testClient.get()
        .uri("/api/announcements?level=CRITICAL")
        .exchange()
        .expectStatus().isOk()
        .expectBody()
        .jsonPath("$.length()").isEqualTo(1)
        .jsonPath("$[0].level").isEqualTo("CRITICAL")
        .jsonPath("$[0].title").isEqualTo("Calle Cerrada");
  }

  @Test
  @DisplayName("10. Expiración Temporal: Anuncio expirado no aparece en listado activo")
  void testExpiration_ExpiredAnnouncementNotReturnedInActive() {
    CreateAnnouncementRequest req = CreateAnnouncementRequest.builder()
        .title("Aviso Express")
        .message("Aviso con vigencia expirada de prueba")
        .build();

    OperationalAnnouncement ann = announcementsService.createAnnouncement(req, "admin").block();
    assertNotNull(ann);
    // Simular expiración fijando expiresAt en el pasado
    ann.setExpiresAt(new Date(System.currentTimeMillis() - 10_000L));

    assertTrue(ann.isExpired());
    assertFalse(ann.isCurrentlyActive());

    List<OperationalAnnouncement> activeList = announcementsService.getActiveAnnouncements(null, null).collectList().block();
    assertNotNull(activeList);
    assertTrue(activeList.stream().noneMatch(a -> a.getId().equals(ann.getId())));
  }

  // ==========================================
  // CONCURRENCIA SEGURA (PREVENCIÓN DE RACE CONDITIONS)
  // ==========================================

  @Test
  @DisplayName("11. Concurrencia Segura: Inserción y consulta simultánea desde múltiples hilos sin fallos")
  void testConcurrency_ThreadSafetyNoRaceConditions() throws InterruptedException {
    int threadCount = 30;
    ExecutorService executor = Executors.newFixedThreadPool(10);
    CountDownLatch latch = new CountDownLatch(threadCount);
    AtomicInteger successCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      final int index = i;
      executor.submit(() -> {
        try {
          CreateAnnouncementRequest req = CreateAnnouncementRequest.builder()
              .title("Anuncio Concurrente " + index)
              .message("Prueba de estrés de hilos concurrentes " + index)
              .level(AnnouncementLevel.INFO)
              .scope(AnnouncementScope.ALL)
              .build();
          OperationalAnnouncement created = announcementsService.createAnnouncement(req, "thread-" + index).block();
          if (created != null && created.getId() != null) {
            successCount.incrementAndGet();
          }
          // Lectura concurrente simultánea
          announcementsService.getActiveAnnouncements(null, null).collectList().block();
        } finally {
          latch.countDown();
        }
      });
    }

    boolean finished = latch.await(10, TimeUnit.SECONDS);
    executor.shutdown();

    assertTrue(finished, "Todos los hilos concurrentes debieron terminar a tiempo");
    assertEquals(threadCount, successCount.get());
    assertEquals(threadCount, announcementsService.getAllAnnouncements().collectList().block().size());
  }

  // ==========================================
  // ACTUATOR ENDPOINT (OperationalAnnouncementsEndpoint)
  // ==========================================

  @Test
  @DisplayName("12. Actuator Endpoint: Operaciones @ReadOperation, @WriteOperation y @DeleteOperation")
  void testActuatorEndpoint_ReadWriteDeleteOperations() {
    // 1. @WriteOperation
    OperationalAnnouncement created = actuatorEndpoint.createAnnouncement(
        "Alerta Actuator",
        "Generada vía Endpoint Actuator de forma segura",
        "CRITICAL",
        "KITCHEN",
        30L,
        "actuator-admin");
    assertNotNull(created);
    assertNotNull(created.getId());
    assertEquals(AnnouncementLevel.CRITICAL, created.getLevel());

    // 2. @ReadOperation (List)
    List<OperationalAnnouncement> list = actuatorEndpoint.announcements(null, null);
    assertNotNull(list);
    assertTrue(list.stream().anyMatch(a -> a.getId().equals(created.getId())));

    // 3. @ReadOperation (@Selector ID)
    OperationalAnnouncement fetched = actuatorEndpoint.announcement(created.getId());
    assertNotNull(fetched);
    assertEquals("Alerta Actuator", fetched.getTitle());

    // 4. @DeleteOperation (@Selector ID)
    Map<String, Object> delResult = actuatorEndpoint.deleteAnnouncement(created.getId(), "actuator-admin");
    assertNotNull(delResult);
    assertEquals("DEACTIVATED", delResult.get("status"));
    assertEquals(created.getId(), delResult.get("id"));

    // Comprobar que quedó inactivo
    OperationalAnnouncement updated = announcementsService.getAnnouncementById(created.getId()).block();
    assertNotNull(updated);
    assertFalse(updated.isActive());
  }

  // ==========================================
  // REFACTORIZACIÓN SEGURA DE NOTES ENDPOINT
  // ==========================================

  @Test
  @DisplayName("13. NotesEndpoint Refactorizado: Thread-safe, UUIDs y borrado protegido de índices")
  void testNotesEndpoint_RefactoredSafeImplementation() {
    NotesEndpoint notesEndpoint = new NotesEndpoint();

    // 1. Rechazo de texto vacío
    List<SafeNote> afterEmpty = notesEndpoint.addNote("");
    assertEquals(0, afterEmpty.size());

    // 2. Creación con UUID y sanitización
    List<SafeNote> notes = notesEndpoint.addNote("<script>alert('hack')</script>Nota de prueba segura");
    assertEquals(1, notes.size());
    SafeNote note = notes.get(0);
    assertNotNull(note.getId());
    assertEquals("Nota de prueba segura", note.getText());
    assertNotNull(note.getTime());

    // 3. Consulta por ID
    SafeNote byId = notesEndpoint.getNoteById(note.getId());
    assertNotNull(byId);
    assertEquals(note.getId(), byId.getId());

    // 4. Borrado por ID
    boolean deleted = notesEndpoint.deleteNoteById(note.getId());
    assertTrue(deleted);
    assertEquals(0, notesEndpoint.notes().size());

    // 5. Protección de índice inválido sin lanzar IndexOutOfBoundsException
    List<SafeNote> afterInvalidIndex = notesEndpoint.deleteNote(999);
    assertNotNull(afterInvalidIndex);
    assertEquals(0, afterInvalidIndex.size());
  }
}
