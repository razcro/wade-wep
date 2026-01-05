package com.newsprovenience.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "articles")
@Data
public class Article {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(nullable = false)
    private String uri; // RDF URI

    @NotBlank
    @Column(nullable = false, length = 1000)
    private String title;

    @Column(length = 5000)
    private String description;

    @Column(columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private Author author;

    @Column(length = 10)
    private String language;

    private Integer wordCount;

    @Column(length = 50)
    private String mediaType; // Article, Documentary, Podcast, Investigation

    private LocalDateTime publishedDate;

    @ManyToMany
    @JoinTable(
            name = "article_topics",
            joinColumns = @JoinColumn(name = "article_id"),
            inverseJoinColumns = @JoinColumn(name = "topic_id")
    )
    private Set<Topic> topics = new HashSet<>();

    @OneToMany(mappedBy = "article", cascade = CascadeType.ALL)
    private Set<ArticleMetadata> metadata = new HashSet<>();

    @ElementCollection
    @CollectionTable(name = "article_sources")
    private Set<String> sources = new HashSet<>();

    @Column(length = 500)
    private String thumbnailUrl;

    @Column(length = 500)
    private String originalUrl;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
