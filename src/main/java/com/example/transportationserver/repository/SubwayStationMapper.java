package com.example.transportationserver.repository;

import com.example.transportationserver.model.SubwayStation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SubwayStationMapper {
    
    List<SubwayStation> findAll();
    
    SubwayStation findById(@Param("id") Long id);
    
    List<SubwayStation> findByName(@Param("name") String name);
    
    List<SubwayStation> findByLineNumber(@Param("lineNumber") String lineNumber);
    
    List<SubwayStation> findByStationCode(@Param("stationCode") String stationCode);
    
    List<SubwayStation> findNearbyStations(@Param("latitude") Double latitude, 
                                          @Param("longitude") Double longitude, 
                                          @Param("radius") Double radius);
    
    int insert(SubwayStation station);
    
    int update(SubwayStation station);
    
    int deleteById(@Param("id") Long id);
    
    boolean existsByStationCode(@Param("stationCode") String stationCode);
    
    SubwayStation findByExternalId(@Param("externalId") String externalId);
}