package br.com.example.activation.api;
import br.com.example.activation.domain.ActivationStatus;
import br.com.example.activation.service.ActivationRequestService;
import jakarta.validation.Valid;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/requests") @SecurityRequirement(name="bearerAuth")
public class ActivationRequestController {
 private final ActivationRequestService service;
 public ActivationRequestController(ActivationRequestService s){service=s;}
 @PostMapping @PreAuthorize("hasAuthority('SCOPE_requests:write')")
 public ResponseEntity<ActivationRequestResponse> create(@Valid @RequestBody CreateActivationRequest input){
  var r=service.create(input);return ResponseEntity.created(URI.create("/api/v1/requests/"+r.getId())).body(ActivationRequestResponse.from(r));
 }
 @GetMapping("/{id}") @PreAuthorize("hasAuthority('SCOPE_requests:read')")
 public ActivationRequestResponse get(@PathVariable UUID id){return ActivationRequestResponse.from(service.get(id));}
 @GetMapping @PreAuthorize("hasAuthority('SCOPE_requests:read')")
 public Page<ActivationRequestResponse> list(@RequestParam(required=false) ActivationStatus status,@RequestParam(required=false) String customerId,@PageableDefault(size=20,sort="createdAt") Pageable pageable){
  return service.list(status,customerId,pageable).map(ActivationRequestResponse::from);
 }
}
