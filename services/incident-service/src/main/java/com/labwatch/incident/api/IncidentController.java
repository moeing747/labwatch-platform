package com.labwatch.incident.api;

import com.labwatch.contracts.incident.IncidentStatus;
import com.labwatch.incident.api.IncidentDtos.HistoryResponse;
import com.labwatch.incident.api.IncidentDtos.IncidentResponse;
import com.labwatch.incident.api.IncidentDtos.NoteRequest;
import com.labwatch.incident.application.IncidentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {

    private final IncidentService incidentService;

    public IncidentController(IncidentService incidentService) {
        this.incidentService = incidentService;
    }

    @GetMapping
    public List<IncidentResponse> findAll(@RequestParam(required = false) IncidentStatus status) {
        return incidentService.findAll(status).stream().map(IncidentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@PathVariable UUID id) {
        return IncidentResponse.from(incidentService.getById(id));
    }

    @GetMapping("/{id}/history")
    public List<HistoryResponse> history(@PathVariable UUID id) {
        return incidentService.getHistory(id).stream().map(HistoryResponse::from).toList();
    }

    @PostMapping("/{id}/acknowledge")
    public IncidentResponse acknowledge(@PathVariable UUID id) {
        return IncidentResponse.from(incidentService.acknowledge(id));
    }

    @PostMapping("/{id}/investigate")
    public IncidentResponse investigate(@PathVariable UUID id) {
        return IncidentResponse.from(incidentService.startInvestigation(id));
    }

    @PostMapping("/{id}/resolve")
    public IncidentResponse resolve(@PathVariable UUID id) {
        return IncidentResponse.from(incidentService.resolve(id));
    }

    @PostMapping("/{id}/notes")
    public ResponseEntity<HistoryResponse> addNote(@PathVariable UUID id, @Valid @RequestBody NoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(HistoryResponse.from(incidentService.addNote(id, request.text())));
    }
}
