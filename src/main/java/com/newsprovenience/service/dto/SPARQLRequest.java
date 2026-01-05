package com.newsprovenience.service.dto;

import lombok.Data;

@Data
public class SPARQLRequest {
    private String query;
    private String format; // json, xml, csv
}
