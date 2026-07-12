package com.labwatch.asset.api;

import com.labwatch.asset.api.DeviceDtos.CreateDeviceRequest;
import com.labwatch.asset.api.DeviceDtos.DeviceResponse;
import com.labwatch.asset.api.DeviceDtos.UpdateDeviceRequest;
import com.labwatch.asset.application.DeviceService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(@Valid @RequestBody CreateDeviceRequest request) {
        DeviceResponse response = DeviceResponse.from(
                deviceService.create(request.deviceId(), request.name(), request.locationId()));
        return ResponseEntity.created(URI.create("/api/devices/" + response.deviceId())).body(response);
    }

    @GetMapping
    public List<DeviceResponse> findAll() {
        return deviceService.findAll().stream().map(DeviceResponse::from).toList();
    }

    @GetMapping("/{deviceId}")
    public DeviceResponse get(@PathVariable String deviceId) {
        return DeviceResponse.from(deviceService.getByDeviceId(deviceId));
    }

    @PutMapping("/{deviceId}")
    public DeviceResponse update(@PathVariable String deviceId, @Valid @RequestBody UpdateDeviceRequest request) {
        return DeviceResponse.from(deviceService.update(deviceId, request.name(), request.locationId()));
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> delete(@PathVariable String deviceId) {
        deviceService.delete(deviceId);
        return ResponseEntity.noContent().build();
    }
}
