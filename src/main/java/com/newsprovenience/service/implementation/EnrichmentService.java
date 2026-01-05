package com.newsprovenience.service.implementation;

import com.newsprovenience.domain.Article;
import lombok.RequiredArgsConstructor;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EnrichmentService {

    private final RDFService rdfService;

    @Value("${dbpedia.sparql-endpoint}")
    private String dbpediaEndpoint;

    @Value("${wikidata.sparql-endpoint}")
    private String wikidataEndpoint;

    @Value("${rdf.namespaces.schema}")
    private String schemaNamespace;

    /**
     * MVP: caută câteva URI-uri DBpedia/Wikidata după un keyphrase și scrie schema:about în graful articolului.
     */
    public void enrichArticle(Article article, String graphUri) {
        String phrase = extractKeyPhrase(article.getTitle());
        if (phrase.isBlank()) return;

        List<String> dbpediaUris = queryDbpediaResources(phrase);
        List<String> wikidataUris = queryWikidataItems(phrase);

        if (dbpediaUris.isEmpty() && wikidataUris.isEmpty()) return;

        Model additions = ModelFactory.createDefaultModel();
        Resource a = additions.createResource(article.getUri());
        Property about = additions.createProperty(schemaNamespace + "about");

        for (String uri : dbpediaUris) a.addProperty(about, additions.createResource(uri));
        for (String uri : wikidataUris) a.addProperty(about, additions.createResource(uri));

        rdfService.addToNamedGraph(graphUri, additions);
    }

    private List<String> queryDbpediaResources(String phrase) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT ?resource
            WHERE {
              ?resource rdfs:label ?label .
              FILTER(LANG(?label) = "en")
              FILTER(CONTAINS(LCASE(STR(?label)), LCASE(?PHRASE)))
            }
            LIMIT 5
        """);
        pss.setLiteral("PHRASE", phrase);

        return execRemoteSelectUris(dbpediaEndpoint, pss.asQuery(), "resource");
    }

    private List<String> queryWikidataItems(String phrase) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            PREFIX wikibase: <http://wikiba.se/ontology#>
            PREFIX bd: <http://www.bigdata.com/rdf#>

            SELECT ?item
            WHERE {
              ?item rdfs:label ?label .
              FILTER(LANG(?label) = "en")
              FILTER(CONTAINS(LCASE(STR(?label)), LCASE(?PHRASE)))
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en". }
            }
            LIMIT 5
        """);
        pss.setLiteral("PHRASE", phrase);

        return execRemoteSelectUris(wikidataEndpoint, pss.asQuery(), "item");
    }

    private List<String> execRemoteSelectUris(String endpoint, Query query, String var) {
        List<String> uris = new ArrayList<>();
        try (QueryExecution qexec = QueryExecutionHTTP.service(endpoint).query(query).build()) {
            ResultSet rs = qexec.execSelect();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                if (sol.contains(var) && sol.get(var).isResource()) {
                    uris.add(sol.getResource(var).getURI());
                }
            }
        } catch (Exception ignored) {
        }
        return uris;
    }

    private String extractKeyPhrase(String text) {
        if (text == null) return "";
        String[] words = text.trim().split("\\s+");
        return words.length > 0 ? words[0] : "";
    }
}
