package tacos.web.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;
import tacos.messaging.DynamicOrderMessagingRouter;
import tacos.web.api.dto.BrokerSelectionResponse;
import tacos.web.api.dto.SelectBrokerRequest;

// Ejercicio 28: Elegir broker en runtime, no editando el POM
@RestController
@RequestMapping(path = "/api/messaging/broker", produces = "application/json")
@CrossOrigin(origins = "*")
@Slf4j
public class BrokerManagementController {

  private final DynamicOrderMessagingRouter router;

  @Autowired
  public BrokerManagementController(DynamicOrderMessagingRouter router) {
    this.router = router;
  }

  @GetMapping
  public ResponseEntity<BrokerSelectionResponse> getActiveBroker() {
    return ResponseEntity.ok(new BrokerSelectionResponse(
        router.getActiveBroker(),
        router.getAvailableBrokers(),
        "Active messaging broker selected in runtime without POM editing"
    ));
  }

  @PutMapping
  public ResponseEntity<BrokerSelectionResponse> switchBroker(
      @RequestBody(required = false) SelectBrokerRequest request,
      @RequestParam(required = false) String type) {

    String targetBroker = (request != null && request.getBroker() != null && !request.getBroker().trim().isEmpty())
        ? request.getBroker()
        : type;

    if (targetBroker == null || targetBroker.trim().isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter 'broker' or query param 'type' is required");
    }

    try {
      String previous = router.getActiveBroker();
      String active = router.setActiveBroker(targetBroker);
      return ResponseEntity.ok(new BrokerSelectionResponse(
          active,
          router.getAvailableBrokers(),
          "Successfully switched messaging broker from '" + previous + "' to '" + active + "' in runtime without POM editing"
      ));
    } catch (IllegalArgumentException ex) {
      log.warn("// Ejercicio 28: Failed to switch broker to '{}': {}", targetBroker, ex.getMessage());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
  }

}
