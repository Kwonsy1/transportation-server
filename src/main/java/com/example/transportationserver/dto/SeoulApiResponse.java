package com.example.transportationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class SeoulApiResponse {
    
    @JsonProperty("SearchInfoBySubwayNameService")
    private SearchInfoBySubwayNameService searchInfoBySubwayNameService;
    
    public SearchInfoBySubwayNameService getSearchInfoBySubwayNameService() {
        return searchInfoBySubwayNameService;
    }
    
    public void setSearchInfoBySubwayNameService(SearchInfoBySubwayNameService searchInfoBySubwayNameService) {
        this.searchInfoBySubwayNameService = searchInfoBySubwayNameService;
    }
    
    public static class SearchInfoBySubwayNameService {
        @JsonProperty("list_total_count")
        private Integer listTotalCount;
        
        @JsonProperty("RESULT")
        private Result result;
        
        @JsonProperty("row")
        private List<SubwayStationApiDto> row;
        
        public Integer getListTotalCount() {
            return listTotalCount;
        }
        
        public void setListTotalCount(Integer listTotalCount) {
            this.listTotalCount = listTotalCount;
        }
        
        public Result getResult() {
            return result;
        }
        
        public void setResult(Result result) {
            this.result = result;
        }
        
        public List<SubwayStationApiDto> getRow() {
            return row;
        }
        
        public void setRow(List<SubwayStationApiDto> row) {
            this.row = row;
        }
    }
    
    public static class Result {
        @JsonProperty("CODE")
        private String code;
        
        @JsonProperty("MESSAGE")
        private String message;
        
        public String getCode() {
            return code;
        }
        
        public void setCode(String code) {
            this.code = code;
        }
        
        public String getMessage() {
            return message;
        }
        
        public void setMessage(String message) {
            this.message = message;
        }
    }
}