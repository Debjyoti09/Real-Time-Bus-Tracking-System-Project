package com.bustracking.service;
import com.bustracking.dto.StopRequestDTO;
import com.bustracking.dto.StopResponseDTO;
import com.bustracking.entity.Stop;
import java.util.List;

public interface StopService {

    StopResponseDTO createStop(StopRequestDTO request);

    List<StopResponseDTO> getAllStops();

    StopResponseDTO getStopById(Long id);

    StopResponseDTO updateStop(Long id, StopRequestDTO request);

    void deleteStop(Long id);

    ///  -------those method are available for before doing StopDto----------
//    Stop createStop(Stop stop);
//
//    List<Stop> getAllStops();
//
//    Stop getStopById(Long id);
//
//    void deleteStop(Long id);
}
