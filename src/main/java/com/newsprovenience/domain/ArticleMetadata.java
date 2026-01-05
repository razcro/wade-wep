package com.newsprovenience.domain;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "article_metadata")
@Data
public class ArticleMetadata {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @Column(nullable = false, length = 100)
    private String metadataKey;

    @Column(columnDefinition = "TEXT")
    private String metadataValue;

    @Column(length = 50)
    private String standard; // DCMI, IPTC, Schema.org
}
