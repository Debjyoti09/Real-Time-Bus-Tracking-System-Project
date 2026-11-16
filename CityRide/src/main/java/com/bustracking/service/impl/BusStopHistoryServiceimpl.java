package com.bustracking.service.impl;

import com.bustracking.dto.BusStopHistoryResponseDTO;
import com.bustracking.entity.BusStopHistory;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.BusStopHistoryRepository;
import com.bustracking.service.BusStopHistoryService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BusStopHistoryServiceimpl implements BusStopHistoryService {

    private final BusStopHistoryRepository busStopHistoryRepository;

    BusStopHistoryServiceimpl(BusStopHistoryRepository busStopHistoryRepository){

        this.busStopHistoryRepository = busStopHistoryRepository;
    }

    @Override
    public List<BusStopHistoryResponseDTO> getAllHistory(){

        List<BusStopHistory> histories = busStopHistoryRepository.findAll();

        List<BusStopHistoryResponseDTO> responseList = new ArrayList<>();

        for(BusStopHistory busStopHistory : histories){

            BusStopHistoryResponseDTO response = convertToresponse(busStopHistory);

            responseList.add(response);
        }

        return responseList;
    }

    @Override
    public BusStopHistoryResponseDTO getHistoryById(Long id){

        BusStopHistory history = busStopHistoryRepository.findById(id).orElse(null);

        if(history == null){

            throw new ResourceNotFoundException("History not found with id: "+id);
        }

        return convertToresponse(history);
    }

    @Override
    public void deleteHistory(Long id){

        BusStopHistory history = busStopHistoryRepository.findById(id).orElse(null);

        if(history == null){

            throw new ResourceNotFoundException("History not found with id: "+id);
        }

        busStopHistoryRepository.delete(history);
    }


    public void recordDepartture(Long busId, long stopId, Long tripId){

        BusStopHistory history = busStopHistoryRepository.findByBusIdAndStopIdAndTripId(busId, stopId, tripId).orElse(null);

        if(history == null){

            throw new ResourceNotFoundException("History not found");
        }

        if(history.getDepartureTime() == null){

            history.setDepartureTime(java.time.LocalDateTime.now());

            busStopHistoryRepository.save(history);
        }

    }

    private BusStopHistoryResponseDTO convertToresponse(BusStopHistory history){

        BusStopHistoryResponseDTO response = new BusStopHistoryResponseDTO();

        response.setId(history.getId());

        response.setBusId(history.getBus().getId());

        response.setStopId(history.getStop().getId());

        response.setStopName(history.getStop().getName());

        if (history.getTrip() != null) {
            response.setTripId(history.getTrip().getId());
        }

        response.setArrivalTime(history.getArrivalTime());

        response.setDepartureTime(history.getDepartureTime());

        return response;
    }
}
