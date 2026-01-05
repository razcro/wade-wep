package com.newsprovenience.service.dto;


import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ArticleSearchRequest {
    private String query;
    private String language;
    private String mediaType;
    private Integer maxWords;
    private String topic;
    private LocalDateTime dateFrom;
    private LocalDateTime dateTo;
}
