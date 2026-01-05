package com.newsprovenience.service.implementation;

import com.newsprovenience.domain.Article;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.*;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.StringWriter;

@Service
public class RDFService {

    private final RDFConnection conn;

    public RDFService(RDFConnection conn) {
        this.conn = conn;
    }

    @Value("${rdf.namespaces.base}")
    private String baseNamespace;

    @Value("${rdf.namespaces.schema}")
    private String schemaNamespace;

    @Value("${rdf.namespaces.dc}")
    private String dcNamespace;

    @Value("${rdf.namespaces.prov}")
    private String provNamespace;

    @Value("${rdf.namespaces.skos}")
    private String skosNamespace;

    // -------------------------
    // SPARQL (SELECT/UPDATE)
    // -------------------------
    public ResultSet executeSparqlQuery(String queryString) {
        Query query = QueryFactory.create(queryString);
        try (QueryExecution qexec = conn.query(query)) {
            // Copiem rezultatul ca să nu depindă de QueryExecution închis.
            return ResultSetFactory.copyResults(qexec.execSelect());
        }
    }

    public void executeSparqlUpdate(String updateString) {
        conn.update(updateString);
    }

    // -------------------------
    // Graph Store Protocol (GSP)
    // -------------------------
    public void putNamedGraph(String graphUri, Model model) {
        conn.put(graphUri, model); // PUT = înlocuiește graful
    }

    public void addToNamedGraph(String graphUri, Model model) {
        conn.load(graphUri, model); // LOAD/POST = adaugă în graf
    }

    public Model getNamedGraph(String graphUri) {
        return conn.fetch(graphUri);
    }

    public String graphUriForV1(String articleUri) {
        return articleUri + "/graph/v1";
    }

    // -------------------------
    // RDF mapping (Article -> RDF)
    // -------------------------
    public Model articleToRDF(Article article) {
        Model model = ModelFactory.createDefaultModel();

        model.setNsPrefix("schema", schemaNamespace);
        model.setNsPrefix("dc", dcNamespace);
        model.setNsPrefix("prov", provNamespace);
        model.setNsPrefix("skos", skosNamespace);
        model.setNsPrefix("", baseNamespace);

        Resource articleRes = model.createResource(article.getUri());

        // Types: NewsArticle + CreativeWork (explicit)
        articleRes.addProperty(RDF.type, model.createResource(schemaNamespace + "NewsArticle"));
        articleRes.addProperty(RDF.type, model.createResource(schemaNamespace + "CreativeWork"));

        String lang = (article.getLanguage() == null || article.getLanguage().isBlank())
                ? null
                : article.getLanguage();

        // schema:headline
        Property headline = model.createProperty(schemaNamespace + "headline");
        if (article.getTitle() != null && !article.getTitle().isBlank()) {
            if (lang != null) articleRes.addLiteral(headline, model.createLiteral(article.getTitle(), lang));
            else articleRes.addProperty(headline, article.getTitle());
        }

        // schema:description
        Property description = model.createProperty(schemaNamespace + "description");
        if (article.getDescription() != null && !article.getDescription().isBlank()) {
            if (lang != null) articleRes.addLiteral(description, model.createLiteral(article.getDescription(), lang));
            else articleRes.addProperty(description, article.getDescription());
        }

        // schema:articleBody
        Property body = model.createProperty(schemaNamespace + "articleBody");
        if (article.getContent() != null && !article.getContent().isBlank()) {
            if (lang != null) articleRes.addLiteral(body, model.createLiteral(article.getContent(), lang));
            else articleRes.addProperty(body, article.getContent());
        }

        // schema:inLanguage
        if (article.getLanguage() != null && !article.getLanguage().isBlank()) {
            articleRes.addProperty(model.createProperty(schemaNamespace + "inLanguage"), article.getLanguage());
        }

        // schema:wordCount (typed)
        if (article.getWordCount() != null) {
            articleRes.addLiteral(model.createProperty(schemaNamespace + "wordCount"),
                    model.createTypedLiteral(article.getWordCount()));
        }

        // schema:datePublished (typed xsd:dateTime)
        if (article.getPublishedDate() != null) {
            articleRes.addLiteral(model.createProperty(schemaNamespace + "datePublished"),
                    model.createTypedLiteral(article.getPublishedDate().toString(), XSDDatatype.XSDdateTime));
        }

        // schema:url + dc:source (originalUrl + sources)
        if (article.getOriginalUrl() != null && !article.getOriginalUrl().isBlank()) {
            articleRes.addProperty(model.createProperty(schemaNamespace + "url"), model.createResource(article.getOriginalUrl()));
            articleRes.addProperty(model.createProperty(dcNamespace + "source"), model.createResource(article.getOriginalUrl()));
        }
        if (article.getSources() != null) {
            for (String s : article.getSources()) {
                if (s != null && !s.isBlank()) {
                    articleRes.addProperty(model.createProperty(dcNamespace + "source"), model.createResource(s));
                }
            }
        }

        // mediaType -> schema:genre + dc:type (ca să-ți iasă query-urile de tip editorial/investigation/documentary)
        if (article.getMediaType() != null && !article.getMediaType().isBlank()) {
            articleRes.addLiteral(model.createProperty(schemaNamespace + "genre"), article.getMediaType());
            articleRes.addLiteral(model.createProperty(dcNamespace + "type"), article.getMediaType());
        }

        // -------------------------
        // AUTHOR + PROV Agent
        // -------------------------
        if (article.getAuthor() != null) {
            Resource authorRes = model.createResource(article.getAuthor().getUri());
            authorRes.addProperty(RDF.type, model.createResource(schemaNamespace + "Person"));
            authorRes.addProperty(RDF.type, model.createResource(provNamespace + "Agent"));

            if (article.getAuthor().getName() != null && !article.getAuthor().getName().isBlank()) {
                if (lang != null) authorRes.addLiteral(model.createProperty(schemaNamespace + "name"),
                        model.createLiteral(article.getAuthor().getName(), lang));
                else authorRes.addProperty(model.createProperty(schemaNamespace + "name"), article.getAuthor().getName());
            }

            if (article.getAuthor().getNationality() != null && !article.getAuthor().getNationality().isBlank()) {
                authorRes.addLiteral(model.createProperty(schemaNamespace + "nationality"), article.getAuthor().getNationality());
            }

            // publisher/affiliation ca Organization (opțional, dar util)
            if (article.getAuthor().getAffiliation() != null && !article.getAuthor().getAffiliation().isBlank()) {
                Resource orgRes = model.createResource(baseNamespace + "org/" + article.getAuthor().getAffiliation().replaceAll("\\s+", "-").toLowerCase());
                orgRes.addProperty(RDF.type, model.createResource(schemaNamespace + "Organization"));
                orgRes.addProperty(model.createProperty(schemaNamespace + "name"), article.getAuthor().getAffiliation());
                articleRes.addProperty(model.createProperty(schemaNamespace + "publisher"), orgRes);
            }

            articleRes.addProperty(model.createProperty(schemaNamespace + "author"), authorRes);

            // PROV attribution
            articleRes.addProperty(model.createProperty(provNamespace + "wasAttributedTo"), authorRes);
        }

        // -------------------------
        // THESAURUS (SKOS) + schema:about
        // -------------------------
        Property about = model.createProperty(schemaNamespace + "about");
        Property subj = model.createProperty(dcNamespace + "subject");

        Resource scheme = model.createResource(baseNamespace + "scheme/topics");
        scheme.addProperty(RDF.type, model.createResource(skosNamespace + "ConceptScheme"));
        scheme.addProperty(model.createProperty(skosNamespace + "prefLabel"), model.createLiteral("News Topics", "en"));

        if (article.getTopics() != null) {
            article.getTopics().forEach(t -> {
                if (t == null || t.getUri() == null || t.getUri().isBlank()) return;

                Resource concept = model.createResource(t.getUri());
                concept.addProperty(RDF.type, model.createResource(skosNamespace + "Concept"));
                concept.addProperty(model.createProperty(skosNamespace + "inScheme"), scheme);

                if (t.getName() != null && !t.getName().isBlank()) {
                    if (lang != null) concept.addProperty(model.createProperty(skosNamespace + "prefLabel"), model.createLiteral(t.getName(), lang));
                    else concept.addProperty(model.createProperty(skosNamespace + "prefLabel"), t.getName());

                    // păstrăm și dc:subject pentru compatibilitate cu query-uri simple
                    articleRes.addProperty(subj, t.getName());
                }

                if (t.getDbpediaUri() != null && !t.getDbpediaUri().isBlank()) {
                    concept.addProperty(model.createProperty(skosNamespace + "exactMatch"), model.createResource(t.getDbpediaUri()));
                }

                articleRes.addProperty(about, concept);
            });
        }

        // -------------------------
        // MULTIMEDIA (schema:MediaObject)
        // -------------------------
        Property associatedMedia = model.createProperty(schemaNamespace + "associatedMedia");
        Property contentUrl = model.createProperty(schemaNamespace + "contentUrl");
        Property encodingFormat = model.createProperty(schemaNamespace + "encodingFormat");

        // Thumbnail ca ImageObject (minim, dar bifează explicit MediaObject)
        if (article.getThumbnailUrl() != null && !article.getThumbnailUrl().isBlank()) {
            Resource img = model.createResource(article.getUri() + "/media/thumbnail");
            img.addProperty(RDF.type, model.createResource(schemaNamespace + "ImageObject"));
            img.addProperty(contentUrl, model.createResource(article.getThumbnailUrl()));
            img.addProperty(encodingFormat, guessEncodingFormat(model, article.getThumbnailUrl()));
            articleRes.addProperty(associatedMedia, img);
        }

        // Media din metadata (chei convenționale: audioUrl/videoUrl/presentationUrl/imageUrl)
        if (article.getMetadata() != null) {
            for (var m : article.getMetadata()) {
                if (m == null || m.getMetadataKey() == null || m.getMetadataValue() == null) continue;

                String k = m.getMetadataKey().trim().toLowerCase();
                String v = m.getMetadataValue().trim();
                if (v.isBlank()) continue;

                String mediaTypeUri = null;
                if (k.equals("audiourl") || k.equals("podcasturl")) mediaTypeUri = schemaNamespace + "AudioObject";
                if (k.equals("videourl")) mediaTypeUri = schemaNamespace + "VideoObject";
                if (k.equals("imageurl")) mediaTypeUri = schemaNamespace + "ImageObject";
                if (k.equals("presentationurl") || k.equals("slidesurl")) mediaTypeUri = schemaNamespace + "MediaObject";

                if (mediaTypeUri != null) {
                    Resource media = model.createResource(article.getUri() + "/media/" + k + "/" + (m.getId() != null ? m.getId() : System.currentTimeMillis()));
                    media.addProperty(RDF.type, model.createResource(mediaTypeUri));
                    media.addProperty(contentUrl, model.createResource(v));
                    media.addProperty(encodingFormat, guessEncodingFormat(model, v));
                    articleRes.addProperty(associatedMedia, media);
                }
            }
        }

        // -------------------------
        // METADATA structurat (schema:PropertyValue)
        // -------------------------
        Property additionalProperty = model.createProperty(schemaNamespace + "additionalProperty");
        Property propertyID = model.createProperty(schemaNamespace + "propertyID");
        Property value = model.createProperty(schemaNamespace + "value");

        if (article.getMetadata() != null) {
            for (var m : article.getMetadata()) {
                if (m == null || m.getMetadataKey() == null || m.getMetadataValue() == null) continue;
                if (m.getMetadataValue().isBlank()) continue;

                Resource pv = model.createResource(article.getUri() + "/meta/" + (m.getId() != null ? m.getId() : System.currentTimeMillis()));
                pv.addProperty(RDF.type, model.createResource(schemaNamespace + "PropertyValue"));
                pv.addProperty(propertyID, m.getMetadataKey());
                pv.addProperty(value, m.getMetadataValue());

                if (m.getStandard() != null && !m.getStandard().isBlank()) {
                    pv.addProperty(model.createProperty(schemaNamespace + "description"), "Standard=" + m.getStandard());
                }

                articleRes.addProperty(additionalProperty, pv);
            }
        }

        // -------------------------
        // PROVENANCE (PROV-O) expresiv (ingest)
        // -------------------------
        Property wasGeneratedBy = model.createProperty(provNamespace + "wasGeneratedBy");
        Property wasAssociatedWith = model.createProperty(provNamespace + "wasAssociatedWith");
        Property used = model.createProperty(provNamespace + "used");
        Property endedAtTime = model.createProperty(provNamespace + "endedAtTime");

        Resource activity = model.createResource(article.getUri() + "/prov/activity/ingest/v1");
        activity.addProperty(RDF.type, model.createResource(provNamespace + "Activity"));

        // sistem agent
        Resource systemAgent = model.createResource(baseNamespace + "agent/system");
        systemAgent.addProperty(RDF.type, model.createResource(provNamespace + "Agent"));
        systemAgent.addProperty(model.createProperty(schemaNamespace + "name"), "NewsProvenience Pipeline");

        activity.addProperty(wasAssociatedWith, systemAgent);

        // used: originalUrl + sources
        if (article.getOriginalUrl() != null && !article.getOriginalUrl().isBlank()) {
            activity.addProperty(used, model.createResource(article.getOriginalUrl()));
        }
        if (article.getSources() != null) {
            for (String s : article.getSources()) {
                if (s != null && !s.isBlank()) activity.addProperty(used, model.createResource(s));
            }
        }

        if (article.getCreatedAt() != null) {
            activity.addLiteral(endedAtTime,
                    model.createTypedLiteral(article.getCreatedAt().toString(), XSDDatatype.XSDdateTime));
        }

        articleRes.addProperty(wasGeneratedBy, activity);

        return model;
    }

    private Literal guessEncodingFormat(Model model, String url) {
        String u = url.toLowerCase();
        String mime = "application/octet-stream";
        if (u.endsWith(".mp3")) mime = "audio/mpeg";
        else if (u.endsWith(".wav")) mime = "audio/wav";
        else if (u.endsWith(".mp4")) mime = "video/mp4";
        else if (u.endsWith(".webm")) mime = "video/webm";
        else if (u.endsWith(".jpg") || u.endsWith(".jpeg")) mime = "image/jpeg";
        else if (u.endsWith(".png")) mime = "image/png";
        else if (u.endsWith(".pdf")) mime = "application/pdf";
        else if (u.endsWith(".ppt") || u.endsWith(".pptx")) mime = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
        return model.createLiteral(mime);
    }


    // -------------------------
    // Serializări
    // -------------------------
    public String modelToJsonLd(Model model) {
        StringWriter writer = new StringWriter();
        model.write(writer, "JSON-LD");
        return writer.toString();
    }

    public String modelToRdfXml(Model model) {
        StringWriter writer = new StringWriter();
        model.write(writer, "RDF/XML");
        return writer.toString();
    }
}
