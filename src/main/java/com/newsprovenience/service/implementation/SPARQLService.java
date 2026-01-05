package com.newsprovenience.service.implementation;

import com.newsprovenience.service.dto.ArticleSearchRequest;
import lombok.RequiredArgsConstructor;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.query.ParameterizedSparqlString;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class SPARQLService {

    private final RDFService rdfService;

    public String executeQuery(String queryString, String format) {
        ResultSet results = rdfService.executeSparqlQuery(queryString);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        String f = (format == null) ? "json" : format.toLowerCase();

        switch (f) {
            case "xml" -> ResultSetFormatter.outputAsXML(out, results);
            case "csv" -> ResultSetFormatter.outputAsCSV(out, results);
            default -> ResultSetFormatter.outputAsJSON(out, results);
        }

        return out.toString(StandardCharsets.UTF_8);
    }

    // FE-friendly: parametric search (title/desc/body/topic/label + filters)
    public String buildSearchQuery(ArticleSearchRequest request) {
        ParameterizedSparqlString pss = new ParameterizedSparqlString();
        pss.setNsPrefix("schema", "http://schema.org/");
        pss.setNsPrefix("dc", "http://purl.org/dc/elements/1.1/");
        pss.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#");
        pss.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");

        pss.append("""
            SELECT ?article ?title ?description ?language ?wordCount ?published ?genre
            WHERE {
              GRAPH ?g {
                ?article a schema:NewsArticle ;
                         schema:headline ?title .
                OPTIONAL { ?article schema:description ?description . }
                OPTIONAL { ?article schema:inLanguage ?language . }
                OPTIONAL { ?article schema:wordCount ?wordCount . }
                OPTIONAL { ?article schema:datePublished ?published . }
                OPTIONAL { ?article schema:genre ?genre . }
                OPTIONAL { ?article dc:subject ?subject . }
                OPTIONAL {
                  ?article schema:about ?c .
                  OPTIONAL { ?c skos:prefLabel ?cLabel . }
                }
              }
        """);

        if (request != null && request.getQuery() != null && !request.getQuery().isBlank()) {
            pss.append("""
              FILTER(
                CONTAINS(LCASE(STR(?title)), LCASE(?Q)) ||
                (BOUND(?description) && CONTAINS(LCASE(STR(?description)), LCASE(?Q))) ||
                (BOUND(?subject) && CONTAINS(LCASE(STR(?subject)), LCASE(?Q))) ||
                (BOUND(?cLabel) && CONTAINS(LCASE(STR(?cLabel)), LCASE(?Q)))
              )
            """);
            pss.setLiteral("Q", request.getQuery());
        }

        if (request != null && request.getLanguage() != null && !request.getLanguage().isBlank()) {
            pss.append(" FILTER(!BOUND(?language) || ?language = ?LANG) ");
            pss.setLiteral("LANG", request.getLanguage());
        }

        if (request != null && request.getMaxWords() != null) {
            pss.append(" FILTER(!BOUND(?wordCount) || ?wordCount <= ?MAXW) ");
            pss.setLiteral("MAXW", request.getMaxWords());
        }

        if (request != null && request.getMediaType() != null && !request.getMediaType().isBlank()) {
            pss.append(" FILTER(!BOUND(?genre) || LCASE(STR(?genre)) = LCASE(?MT)) ");
            pss.setLiteral("MT", request.getMediaType());
        }

        pss.append("""
            }
            ORDER BY DESC(?published)
            LIMIT 100
        """);

        return pss.toString();
    }

    // Examples aligned with named graphs + model (genre/type/topic)
    public String getFreshEditorialsQuery(String topic, String dateFrom) {
        return String.format("""
            PREFIX schema: <http://schema.org/>
            PREFIX dc: <http://purl.org/dc/elements/1.1/>
            PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>

            SELECT ?article ?title ?published ?description
            WHERE {
              GRAPH ?g {
                ?article a schema:NewsArticle ;
                         schema:headline ?title .
                OPTIONAL { ?article schema:description ?description . }
                OPTIONAL { ?article schema:datePublished ?published . }
                OPTIONAL { ?article schema:genre ?genre . }
                OPTIONAL { ?article dc:type ?dcType . }
                OPTIONAL { ?article dc:subject ?subject . }

                FILTER( (BOUND(?genre) && LCASE(STR(?genre))="editorial")
                     || (BOUND(?dcType) && LCASE(STR(?dcType))="editorial") )

                FILTER( !BOUND(?subject) || CONTAINS(LCASE(STR(?subject)), LCASE("%s")) )

                FILTER( !BOUND(?published) || ?published >= "%sT00:00:00"^^xsd:dateTime )
              }
            }
            ORDER BY DESC(?published)
            LIMIT 50
        """, topic, dateFrom);
    }

    public String getArticlesByLanguageAndWords(String lang1, String lang2, int maxWords, String topic) {
        return String.format("""
        PREFIX schema: <http://schema.org/>
        PREFIX dc: <http://purl.org/dc/elements/1.1/>
        PREFIX skos: <http://www.w3.org/2004/02/skos/core#>

        SELECT ?article ?title ?language ?wordCount ?description
        WHERE {
          GRAPH ?g {
            ?article a schema:NewsArticle ;
                     schema:headline ?title ;
                     schema:inLanguage ?language ;
                     schema:wordCount ?wordCount .
            OPTIONAL { ?article schema:description ?description . }
            OPTIONAL { ?article dc:subject ?subject . }
            OPTIONAL {
              ?article schema:about ?c .
              OPTIONAL { ?c skos:prefLabel ?cLabel . }
            }
          }

          FILTER(?language = "%s" || ?language = "%s")
          FILTER(?wordCount < %d)

          FILTER(
            (BOUND(?subject) && CONTAINS(LCASE(STR(?subject)), LCASE("%s"))) ||
            (BOUND(?cLabel) && CONTAINS(LCASE(STR(?cLabel)), LCASE("%s"))) ||
            CONTAINS(LCASE(STR(?title)), LCASE("%s"))
          )
        }
        ORDER BY ?wordCount
        LIMIT 100
        """, lang1, lang2, maxWords, topic, topic, topic);
    }

    public String getRomanianInvestigationsQuery() {
        return """
        PREFIX schema: <http://schema.org/>

        SELECT ?article ?title ?description ?author ?published ?genre
        WHERE {
          GRAPH ?g {
            ?article a schema:NewsArticle ;
                     schema:headline ?title ;
                     schema:author ?authorNode .
            OPTIONAL { ?article schema:description ?description . }
            OPTIONAL { ?article schema:datePublished ?published . }
            OPTIONAL { ?article schema:genre ?genre . }

            ?authorNode schema:name ?author .
            OPTIONAL { ?authorNode schema:nationality ?nat . }
          }

          FILTER(BOUND(?nat) && CONTAINS(LCASE(STR(?nat)), "roman"))
          FILTER(BOUND(?genre) && (LCASE(STR(?genre)) = "investigation" || LCASE(STR(?genre)) = "documentary"))
        }
        ORDER BY DESC(?published)
        LIMIT 100
        """;
    }

}
