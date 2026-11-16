package com.bustracking.service;

import com.bustracking.dto.BusStopHistoryResponseDTO;

import java.util.List;

public interface BusStopHistoryService {

    List<BusStopHistoryResponseDTO> getAllHistory();

    BusStopHistoryResponseDTO getHistoryById(Long id);

    void deleteHistory(Long id);
}
