package com.newsprovenience.web;

import com.newsprovenience.service.dto.SPARQLRequest;
import com.newsprovenience.service.implementation.SPARQLService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sparql")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class SPARQLController {

    private final SPARQLService sparqlService;

    @PostMapping
    public ResponseEntity<String> executeSparqlQuery(
            @RequestBody SPARQLRequest request) {
        try {
            String format = request.getFormat() != null ?
                    request.getFormat() : "json";
            String results = sparqlService.executeQuery(
                    request.getQuery(), format);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body("Error executing query: " + e.getMessage());
        }
    }

    @GetMapping("/examples/fresh-editorials")
    public ResponseEntity<String> getFreshEditorialsQuery(
            @RequestParam(defaultValue = "technology") String topic,
            @RequestParam(defaultValue = "2024-12-01") String dateFrom) {
        String query = sparqlService.getFreshEditorialsQuery(topic, dateFrom);
        return ResponseEntity.ok(query);
    }

    @GetMapping("/examples/articles-by-language")
    public ResponseEntity<String> getArticlesByLanguage(
            @RequestParam(defaultValue = "en") String lang1,
            @RequestParam(defaultValue = "es") String lang2,
            @RequestParam(defaultValue = "4000") int maxWords,
            @RequestParam(defaultValue = "IT contest") String topic) {

        String query = sparqlService.getArticlesByLanguageAndWords(lang1, lang2, maxWords, topic);
        String json = sparqlService.executeQuery(query, "json");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/examples/romanian-investigations")
    public ResponseEntity<String> getRomanianInvestigations() {
        String query = sparqlService.getRomanianInvestigationsQuery();
        String json = sparqlService.executeQuery(query, "json");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

}
