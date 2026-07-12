package com.labwatch.asset.api;

import com.labwatch.asset.api.MonitoringPolicyDtos.CreatePolicyRequest;
import com.labwatch.asset.api.MonitoringPolicyDtos.PolicyResponse;
import com.labwatch.asset.api.MonitoringPolicyDtos.UpdatePolicyRequest;
import com.labwatch.asset.application.MonitoringPolicyService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices/{deviceId}/monitoring-policies")
public class MonitoringPolicyController {

    private final MonitoringPolicyService policyService;

    public MonitoringPolicyController(MonitoringPolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> create(@PathVariable String deviceId,
                                                 @Valid @RequestBody CreatePolicyRequest request) {
        PolicyResponse response = PolicyResponse.from(policyService.create(deviceId, request.metric(),
                request.minimum(), request.maximum(), request.violationDurationSeconds(), request.severity()));
        return ResponseEntity
                .created(URI.create("/api/devices/%s/monitoring-policies/%s".formatted(deviceId, response.id())))
                .body(response);
    }

    @GetMapping
    public List<PolicyResponse> findByDevice(@PathVariable String deviceId) {
        return policyService.findByDevice(deviceId).stream().map(PolicyResponse::from).toList();
    }

    @PutMapping("/{policyId}")
    public PolicyResponse update(@PathVariable String deviceId, @PathVariable UUID policyId,
                                 @Valid @RequestBody UpdatePolicyRequest request) {
        return PolicyResponse.from(policyService.update(deviceId, policyId, request.minimum(),
                request.maximum(), request.violationDurationSeconds(), request.severity()));
    }

    @DeleteMapping("/{policyId}")
    public ResponseEntity<Void> delete(@PathVariable String deviceId, @PathVariable UUID policyId) {
        policyService.delete(deviceId, policyId);
        return ResponseEntity.noContent().build();
    }
}
