package com.newsprovenience.web;

import com.newsprovenience.domain.Article;
import com.newsprovenience.service.dto.ArticleDTO;
import com.newsprovenience.service.dto.ArticleSearchRequest;
import com.newsprovenience.service.implementation.ArticleService;
import com.newsprovenience.service.implementation.SPARQLService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ArticleController {

    private final ArticleService articleService;
    private final SPARQLService sparqlService;

    @PostMapping
    public ResponseEntity<Article> createArticle(@RequestBody ArticleDTO dto) {
        Article created = articleService.createArticle(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Article> getArticle(@PathVariable Long id) {
        return articleService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/search")
    public ResponseEntity<List<Article>> searchArticles(
            @RequestBody ArticleSearchRequest request) {
        List<Article> results = articleService.searchArticles(request);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/search/sparql")
    public ResponseEntity<String> searchSemantic(@RequestBody ArticleSearchRequest request) {
        String sparql = sparqlService.buildSearchQuery(request);
        String json = sparqlService.executeQuery(sparql, "json");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/{id}/export/jsonld")
    public ResponseEntity<String> exportAsJsonLd(@PathVariable Long id) {
        String jsonLd = articleService.exportArticleAsJsonLd(id);
        if (jsonLd != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.valueOf("application/ld+json"))
                    .body(jsonLd);
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/{id}/export/rdf")
    public ResponseEntity<String> exportAsRdf(@PathVariable Long id) {
        String rdf = articleService.exportArticleAsRdfXml(id);
        if (rdf != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_XML)
                    .body(rdf);
        }
        return ResponseEntity.notFound().build();
    }
}
