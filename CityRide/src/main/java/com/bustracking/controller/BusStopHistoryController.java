package com.bustracking.controller;

import com.bustracking.dto.BusStopHistoryResponseDTO;
import com.bustracking.service.BusStopHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
@RestController
@RequestMapping("/api/bus-stop-history")
public class BusStopHistoryController {

    private final BusStopHistoryService busStopHistoryService;

    public BusStopHistoryController(BusStopHistoryService busStopHistoryService) {

        this.busStopHistoryService = busStopHistoryService;
    }

    @GetMapping
    public List<BusStopHistoryResponseDTO> getAllHistory() {

        return busStopHistoryService.getAllHistory();
    }

    @GetMapping("/{id}")
    public BusStopHistoryResponseDTO getHistoryById(@PathVariable Long id) {

        return busStopHistoryService.getHistoryById(id);
    }

    @DeleteMapping("/{id}")
    public String deleteHistory(@PathVariable Long id) {

        busStopHistoryService.deleteHistory(id);

        return "History deleted successfully";
    }
}
