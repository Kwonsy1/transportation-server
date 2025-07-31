package com.example.transportationserver.repository;

import com.example.transportationserver.model.SubwayStation;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface SubwayStationMapper {
    
    @Select("SELECT * FROM subway_stations ORDER BY name")
    List<SubwayStation> findAll();
    
    @Select("SELECT * FROM subway_stations ORDER BY name LIMIT #{limit} OFFSET #{offset}")
    List<SubwayStation> findWithPaging(@Param("offset") int offset, @Param("limit") int limit);
    
    @Select("SELECT COUNT(*) FROM subway_stations")
    int countAll();
    
    @Select("SELECT * FROM subway_stations WHERE id = #{id}")
    SubwayStation findById(@Param("id") Long id);
    
    @Select("SELECT * FROM subway_stations WHERE name LIKE CONCAT('%', #{name}, '%') ORDER BY name")
    List<SubwayStation> findByName(@Param("name") String name);
    
    @Select("SELECT * FROM subway_stations WHERE line_number = #{lineNumber} ORDER BY name")
    List<SubwayStation> findByLineNumber(@Param("lineNumber") String lineNumber);
    
    @Select("SELECT * FROM subway_stations WHERE station_code = #{stationCode}")
    List<SubwayStation> findByStationCode(@Param("stationCode") String stationCode);
    
    @Select("SELECT *, (6371 * acos(cos(radians(#{latitude})) * cos(radians(latitude)) * cos(radians(longitude) - radians(#{longitude})) + sin(radians(#{latitude})) * sin(radians(latitude)))) AS distance FROM subway_stations WHERE latitude IS NOT NULL AND longitude IS NOT NULL AND (6371 * acos(cos(radians(#{latitude})) * cos(radians(latitude)) * cos(radians(longitude) - radians(#{longitude})) + sin(radians(#{latitude})) * sin(radians(latitude)))) <= #{radius} ORDER BY distance")
    List<SubwayStation> findNearbyStations(@Param("latitude") Double latitude, 
                                          @Param("longitude") Double longitude, 
                                          @Param("radius") Double radius);
    
    @Insert("INSERT INTO subway_stations (name, line_number, station_code, latitude, longitude, address, external_id, subway_station_id, region, city, full_name, aliases, data_source, has_coordinates, created_at, updated_at) VALUES (#{name}, #{lineNumber}, #{stationCode}, #{latitude}, #{longitude}, #{address}, #{externalId}, #{subwayStationId}, #{region}, #{city}, #{fullName}, #{aliases}, #{dataSource}, #{hasCoordinates}, #{createdAt}, #{updatedAt})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SubwayStation station);
    
    @Update("UPDATE subway_stations SET name = #{name}, line_number = #{lineNumber}, station_code = #{stationCode}, latitude = #{latitude}, longitude = #{longitude}, address = #{address}, external_id = #{externalId}, subway_station_id = #{subwayStationId}, region = #{region}, city = #{city}, full_name = #{fullName}, aliases = #{aliases}, data_source = #{dataSource}, has_coordinates = #{hasCoordinates}, updated_at = #{updatedAt} WHERE id = #{id}")
    int update(SubwayStation station);
    
    @Delete("DELETE FROM subway_stations WHERE id = #{id}")
    int deleteById(@Param("id") Long id);
    
    @Select("SELECT COUNT(*) > 0 FROM subway_stations WHERE station_code = #{stationCode}")
    boolean existsByStationCode(@Param("stationCode") String stationCode);
    
    @Select("SELECT * FROM subway_stations WHERE external_id = #{externalId}")
    SubwayStation findByExternalId(@Param("externalId") String externalId);
    
    @Select("SELECT * FROM subway_stations WHERE (latitude IS NULL OR longitude IS NULL OR latitude = 0 OR longitude = 0) ORDER BY name, line_number")
    List<SubwayStation> findStationsWithoutCoordinates();
    
    @Select("SELECT * FROM subway_stations WHERE (latitude IS NULL OR longitude IS NULL OR latitude = 0 OR longitude = 0) ORDER BY name, line_number LIMIT #{limit} OFFSET #{offset}")
    List<SubwayStation> findStationsWithoutCoordinatesWithPaging(@Param("offset") int offset, @Param("limit") int limit);
    
    @Select("SELECT COUNT(*) FROM subway_stations WHERE (latitude IS NULL OR longitude IS NULL OR latitude = 0 OR longitude = 0)")
    int countStationsWithoutCoordinates();
    
    @Update("UPDATE subway_stations SET latitude = #{latitude}, longitude = #{longitude}, has_coordinates = CASE WHEN #{latitude} IS NOT NULL AND #{longitude} IS NOT NULL THEN true ELSE false END, updated_at = CURRENT_TIMESTAMP WHERE id = #{id}")
    int updateCoordinates(@Param("id") Long id, 
                         @Param("latitude") Double latitude, 
                         @Param("longitude") Double longitude);
                         
    @Update("UPDATE subway_stations SET subway_station_id = #{subwayStationId}, updated_at = CURRENT_TIMESTAMP WHERE name = #{name} AND (#{lineNumber} IS NULL OR line_number = #{lineNumber}) AND subway_station_id IS NULL LIMIT 1")
    int updateSubwayStationId(@Param("name") String name,
                             @Param("lineNumber") String lineNumber,
                             @Param("subwayStationId") String subwayStationId);
    
    /**
     * 정확한 역명으로 검색 (역 단위로)
     */
    @Select("SELECT * FROM subway_stations WHERE name = #{name} ORDER BY line_number")
    List<SubwayStation> findByExactName(@Param("name") String name);
    
    /**
     * 역명 + "역" 조합으로 검색
     */
    @Select("SELECT * FROM subway_stations WHERE name = CONCAT(#{name}, '역') ORDER BY line_number")
    List<SubwayStation> findByNameWithStation(@Param("name") String name);
    
    /**
     * 스마트 검색: 우선순위 기반으로 결과 반환
     * 1순위: 정확한 매칭 (예: "강남" -> "강남")
     * 2순위: 역명 매칭 (예: "강남" -> "강남역") 
     * 3순위: 시작 매칭 (예: "강남" -> "강남구청역")
     */
    @Select({
        "SELECT *, ",
        "CASE ",
        "  WHEN name = #{searchTerm} THEN 100 ",
        "  WHEN name = CONCAT(#{searchTerm}, '역') THEN 90 ",
        "  WHEN name LIKE CONCAT(#{searchTerm}, '%') THEN 80 ",
        "  WHEN name LIKE CONCAT('%', #{searchTerm}, '%') THEN 60 ",
        "  ELSE 0 ",
        "END as search_score ",
        "FROM subway_stations ",
        "WHERE name = #{searchTerm} ",
        "   OR name = CONCAT(#{searchTerm}, '역') ",
        "   OR name LIKE CONCAT(#{searchTerm}, '%') ",
        "   OR name LIKE CONCAT('%', #{searchTerm}, '%') ",
        "ORDER BY search_score DESC, name, line_number"
    })
    List<SubwayStation> findBySmartSearch(@Param("searchTerm") String searchTerm);
    
    /**
     * 높은 우선순위 결과만 반환 (정확 매칭 + 역 추가 매칭)
     */
    @Select({
        "SELECT *, ",
        "CASE ",
        "  WHEN name = #{searchTerm} THEN 100 ",
        "  WHEN name = CONCAT(#{searchTerm}, '역') THEN 90 ",
        "  ELSE 0 ",
        "END as search_score ",
        "FROM subway_stations ",
        "WHERE name = #{searchTerm} OR name = CONCAT(#{searchTerm}, '역') ",
        "ORDER BY search_score DESC, line_number"
    })
    List<SubwayStation> findByHighPrioritySearch(@Param("searchTerm") String searchTerm);
}