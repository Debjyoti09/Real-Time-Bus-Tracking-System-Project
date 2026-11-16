package com.bustracking.controller;
import com.bustracking.dto.StopRequestDTO;
import com.bustracking.dto.StopResponseDTO;
import com.bustracking.entity.Stop;
import com.bustracking.service.StopService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/stops")
public class StopController {

    private final StopService stopService;

    public StopController(StopService stopService) {
        this.stopService = stopService;
    }

    @PostMapping
    public StopResponseDTO createStop(@Valid @RequestBody StopRequestDTO request) {
        return stopService.createStop(request);
    }

    @GetMapping
    public List<StopResponseDTO> getAllStops() {
        return stopService.getAllStops();
    }

    @GetMapping("/{id}")
    public StopResponseDTO getStopById(@PathVariable Long id) {
        return stopService.getStopById(id);
    }

    @PutMapping("/{id}")
    public StopResponseDTO updateStop(@PathVariable Long id, @Valid @RequestBody StopRequestDTO request) {
        return stopService.updateStop(id, request);
    }

    @DeleteMapping("/{id}")
    public String deleteStop(@PathVariable Long id) {
        stopService.deleteStop(id);
        return "Stop deleted successfully";
    }
}
