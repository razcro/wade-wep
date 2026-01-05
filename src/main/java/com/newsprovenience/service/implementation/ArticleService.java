package com.newsprovenience.service.implementation;

import com.newsprovenience.domain.Article;
import com.newsprovenience.repository.ArticleRepository;
import com.newsprovenience.service.dto.ArticleDTO;
import com.newsprovenience.service.dto.ArticleSearchRequest;
import com.newsprovenience.service.mapper.ArticleMapper;
import lombok.RequiredArgsConstructor;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final ArticleMapper articleMapper;
    private final RDFService rdfService;
    private final EnrichmentService enrichmentService;

    @Transactional
    public Article createArticle(ArticleDTO dto) {
        Article article = articleMapper.toEntity(dto);

        // Generate URI if not provided
        if (article.getUri() == null) {
            article.setUri(generateUri(article));
        }

        // Save to relational database
        Article saved = articleRepository.save(article);

        // Convert to RDF
        Model rdfModel = rdfService.articleToRDF(saved);

        // Store in Fuseki as a named graph (v1)
        String graphUri = rdfService.graphUriForV1(saved.getUri());
        rdfService.putNamedGraph(graphUri, rdfModel);

        // Enrich (adds triples into the same named graph)
        enrichmentService.enrichArticle(saved, graphUri);

        return saved;
    }

    @Transactional(readOnly = true)
    public List<Article> searchArticles(ArticleSearchRequest request) {
        if (request.getQuery() != null && !request.getQuery().isEmpty()) {
            return articleRepository.searchArticles(request.getQuery());
        }

        return articleRepository.findWithFilters(
                request.getLanguage(),
                request.getMediaType(),
                request.getMaxWords(),
                request.getDateFrom(),
                request.getDateTo()
        );
    }

    public String exportArticleAsJsonLd(Long id) {
        Optional<Article> article = findById(id);
        if (article.isEmpty()) return null;

        String graphUri = rdfService.graphUriForV1(article.get().getUri());
        Model model = rdfService.getNamedGraph(graphUri);

        // fallback: dacă graful e gol din orice motiv
        if (model == null || model.isEmpty()) {
            model = rdfService.articleToRDF(article.get());
        }

        return rdfService.modelToJsonLd(model);
    }

    public String exportArticleAsRdfXml(Long id) {
        Optional<Article> article = findById(id);
        if (article.isEmpty()) return null;

        String graphUri = rdfService.graphUriForV1(article.get().getUri());
        Model model = rdfService.getNamedGraph(graphUri);

        if (model == null || model.isEmpty()) {
            model = rdfService.articleToRDF(article.get());
        }

        return rdfService.modelToRdfXml(model);
    }

    @Transactional(readOnly = true)
    public Optional<Article> findById(Long id) {
        return articleRepository.findById(id);
    }

    private String generateUri(Article article) {
        String title = (article.getTitle() == null || article.getTitle().isBlank())
                ? "article"
                : article.getTitle();

        String slug = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-");

        // FIX BUG: substring pe slug.length(), nu pe title.length()
        slug = slug.substring(0, Math.min(50, slug.length()));

        return "http://example.org/news/article/" + slug + "-" + System.currentTimeMillis();
    }
}
