package com.example.transportationserver.repository;

import com.example.transportationserver.model.SubwayStation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SubwayStationMapper {
    
    @Select("SELECT * FROM subway_stations ORDER BY name")
    List<SubwayStation> findAll();
    
    @Select("SELECT * FROM subway_stations WHERE id = #{id}")
    SubwayStation findById(@Param("id") Long id);
    
    @Select("SELECT * FROM subway_stations WHERE name LIKE CONCAT('%', #{name}, '%') ORDER BY name")
    List<SubwayStation> findByName(@Param("name") String name);
    
    @Select("SELECT * FROM subway_stations WHERE line_number = #{lineNumber} ORDER BY name")
    List<SubwayStation> findByLineNumber(@Param("lineNumber") String lineNumber);
    
    @Select("SELECT * FROM subway_stations WHERE station_code = #{stationCode}")
    List<SubwayStation> findByStationCode(@Param("stationCode") String stationCode);
    
    @Select("SELECT *, (6371 * acos(cos(radians(#{latitude})) * cos(radians(latitude)) * cos(radians(longitude) - radians(#{longitude})) + sin(radians(#{latitude})) * sin(radians(latitude)))) AS distance FROM subway_stations WHERE latitude IS NOT NULL AND longitude IS NOT NULL HAVING distance <= #{radius} ORDER BY distance")
    List<SubwayStation> findNearbyStations(@Param("latitude") Double latitude, 
                                          @Param("longitude") Double longitude, 
                                          @Param("radius") Double radius);
    
    @Insert("INSERT INTO subway_stations (name, line_number, station_code, latitude, longitude, address, external_id, region, city, full_name, aliases, data_source, has_coordinates, created_at, updated_at) VALUES (#{name}, #{lineNumber}, #{stationCode}, #{latitude}, #{longitude}, #{address}, #{externalId}, #{region}, #{city}, #{fullName}, #{aliases}, #{dataSource}, #{hasCoordinates}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SubwayStation station);
    
    @Update("UPDATE subway_stations SET name = #{name}, line_number = #{lineNumber}, station_code = #{stationCode}, latitude = #{latitude}, longitude = #{longitude}, address = #{address}, external_id = #{externalId}, region = #{region}, city = #{city}, full_name = #{fullName}, aliases = #{aliases}, data_source = #{dataSource}, has_coordinates = #{hasCoordinates}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(SubwayStation station);
    
    @Delete("DELETE FROM subway_stations WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
    
    @Select("SELECT COUNT(*) > 0 FROM subway_stations WHERE station_code = #{stationCode}")
    boolean existsByStationCode(@Param("stationCode") String stationCode);
    
    @Select("SELECT * FROM subway_stations WHERE external_id = #{externalId}")
    SubwayStation findByExternalId(@Param("externalId") String externalId);
    
    @Select("SELECT * FROM subway_stations WHERE (latitude IS NULL OR longitude IS NULL OR latitude = 0 OR longitude = 0) ORDER BY name, line_number")
    List<SubwayStation> findStationsWithoutCoordinates();
    
    @Update("UPDATE subway_stations SET latitude = #{latitude}, longitude = #{longitude}, has_coordinates = CASE WHEN #{latitude} IS NOT NULL AND #{longitude} IS NOT NULL THEN true ELSE false END, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateCoordinates(@Param("id") Long id, 
                         @Param("latitude") Double latitude, 
                         @Param("longitude") Double longitude);
}