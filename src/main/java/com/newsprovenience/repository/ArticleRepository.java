package com.newsprovenience.repository;

import com.newsprovenience.domain.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ArticleRepository extends JpaRepository<Article, Long> {

    Optional<Article> findByUri(String uri);

    List<Article> findByLanguage(String language);

    List<Article> findByMediaType(String mediaType);

    @Query("SELECT a FROM Article a WHERE " +
            "LOWER(a.title) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(a.description) LIKE LOWER(CONCAT('%', :query, '%'))")
    List<Article> searchArticles(@Param("query") String query);

    @Query("SELECT a FROM Article a WHERE " +
            "(:language IS NULL OR a.language = :language) AND " +
            "(:mediaType IS NULL OR a.mediaType = :mediaType) AND " +
            "(:maxWords IS NULL OR a.wordCount <= :maxWords) AND " +
            "(:dateFrom IS NULL OR a.publishedDate >= :dateFrom) AND " +
            "(:dateTo IS NULL OR a.publishedDate <= :dateTo)")
    List<Article> findWithFilters(
            @Param("language") String language,
            @Param("mediaType") String mediaType,
            @Param("maxWords") Integer maxWords,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo
    );

    @Query("SELECT a FROM Article a JOIN a.topics t WHERE t.name = :topicName")
    List<Article> findByTopicName(@Param("topicName") String topicName);
}
