package com.bustracking.service.impl;
import com.bustracking.entity.Stop;
import com.bustracking.dto.*;
import com.bustracking.exception.ResourceNotFoundException;
import com.bustracking.repository.StopRepository;
import com.bustracking.service.StopService;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StopServiceImpl implements StopService {

    private final StopRepository stopRepository;

    StopServiceImpl(StopRepository stopRepository){
        this.stopRepository = stopRepository;
    }

    @Override
    public StopResponseDTO createStop(StopRequestDTO request){

        Stop existingStop = stopRepository.findByName(request.getName()).orElse(null);

        if(existingStop != null){

            return convertToResponse(existingStop);
        }

        Stop stop = new Stop();

        stop.setName(request.getName());

        stop.setLatitude(request.getLatitude());

        stop.setLongitude(request.getLongitude());

        Stop savedStop = stopRepository.save(stop);

        return convertToResponse(savedStop);
    }

    @Override
    public List<StopResponseDTO> getAllStops(){

        List<Stop> stops = stopRepository.findAll();

        List<StopResponseDTO> responseList = new ArrayList<>();

        for(Stop stop : stops){
            responseList.add(convertToResponse(stop));
        }

        return responseList;
    }

    @Override
    public StopResponseDTO getStopById(Long id){

        Stop stop = stopRepository.findById(id).orElse(null);

        if(stop == null){
            throw new ResourceNotFoundException("Stop not found with id "+ id);
        }

        return convertToResponse(stop);
    }

    @Override
    public StopResponseDTO updateStop(Long id, StopRequestDTO request) {
        Stop stop = stopRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Stop not found with id: " + id));
        stop.setName(request.getName());
        stop.setLatitude(request.getLatitude());
        stop.setLongitude(request.getLongitude());
        return convertToResponse(stopRepository.save(stop));
    }

    @Override
    public void deleteStop(Long id){

        Stop stop = stopRepository.findById(id).orElse(null);

        if(stop == null){
            throw new ResourceNotFoundException("Route not found with id "+id);
        }

        stopRepository.delete(stop);
    }

    private StopResponseDTO convertToResponse(Stop stop){

        StopResponseDTO response = new StopResponseDTO();

        response.setId(stop.getId());
        response.setName(stop.getName());
        response.setLatitude(stop.getLatitude());
        response.setLongitude(stop.getLongitude());

        return response;
    }

}
