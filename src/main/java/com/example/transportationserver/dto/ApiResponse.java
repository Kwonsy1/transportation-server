package com.example.transportationserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class ApiResponse<T> {
    
    @JsonProperty("response")
    private Response<T> response;
    
    public Response<T> getResponse() {
        return response;
    }
    
    public void setResponse(Response<T> response) {
        this.response = response;
    }
    
    public static class Response<T> {
        @JsonProperty("header")
        private Header header;
        
        @JsonProperty("body")
        private Body<T> body;
        
        public Header getHeader() {
            return header;
        }
        
        public void setHeader(Header header) {
            this.header = header;
        }
        
        public Body<T> getBody() {
            return body;
        }
        
        public void setBody(Body<T> body) {
            this.body = body;
        }
    }
    
    public static class Header {
        @JsonProperty("resultCode")
        private String resultCode;
        
        @JsonProperty("resultMsg")
        private String resultMsg;
        
        public String getResultCode() {
            return resultCode;
        }
        
        public void setResultCode(String resultCode) {
            this.resultCode = resultCode;
        }
        
        public String getResultMsg() {
            return resultMsg;
        }
        
        public void setResultMsg(String resultMsg) {
            this.resultMsg = resultMsg;
        }
    }
    
    public static class Body<T> {
        @JsonProperty("items")
        private Items<T> items;
        
        @JsonProperty("totalCount")
        private Integer totalCount;
        
        @JsonProperty("pageNo")
        private Integer pageNo;
        
        public Items<T> getItems() {
            return items;
        }
        
        public void setItems(Items<T> items) {
            this.items = items;
        }
        
        public Integer getTotalCount() {
            return totalCount;
        }
        
        public void setTotalCount(Integer totalCount) {
            this.totalCount = totalCount;
        }
        
        public Integer getPageNo() {
            return pageNo;
        }
        
        public void setPageNo(Integer pageNo) {
            this.pageNo = pageNo;
        }
    }
    
    public static class Items<T> {
        @JsonProperty("item")
        private List<T> item;
        
        public List<T> getItem() {
            return item;
        }
        
        public void setItem(List<T> item) {
            this.item = item;
        }
    }
}