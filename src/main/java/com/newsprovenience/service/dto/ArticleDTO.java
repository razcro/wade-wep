package com.newsprovenience.service.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Set;

@Data
public class ArticleDTO {
    private String title;
    private String description;
    private String content;
    private String language;
    private Integer wordCount;
    private String mediaType;
    private LocalDateTime publishedDate;
    private String originalUrl;
    private String thumbnailUrl;
    private Long authorId;
    private Set<Long> topicIds;
    private Set<String> sources;
}
