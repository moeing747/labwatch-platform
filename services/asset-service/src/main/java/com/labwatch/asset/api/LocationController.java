package com.labwatch.asset.api;

import com.labwatch.asset.api.LocationDtos.CreateLocationRequest;
import com.labwatch.asset.api.LocationDtos.LocationResponse;
import com.labwatch.asset.application.LocationService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/locations")
public class LocationController {

    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @PostMapping
    public ResponseEntity<LocationResponse> create(@Valid @RequestBody CreateLocationRequest request) {
        LocationResponse response = LocationResponse.from(
                locationService.create(request.name(), request.description()));
        return ResponseEntity.created(URI.create("/api/locations/" + response.id())).body(response);
    }

    @GetMapping
    public List<LocationResponse> findAll() {
        return locationService.findAll().stream().map(LocationResponse::from).toList();
    }

    @GetMapping("/{id}")
    public LocationResponse get(@PathVariable UUID id) {
        return LocationResponse.from(locationService.getById(id));
    }
}
