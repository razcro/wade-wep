package com.newsprovenience.config;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JenaFusekiConfig {
    @Bean(destroyMethod = "close")
    public RDFConnection rdfConnection(
            @Value("${fuseki.sparql-query-url}") String queryUrl,
            @Value("${fuseki.sparql-update-url}") String updateUrl,
            @Value("${fuseki.graph-store-url}") String gspUrl
    ) {
        return RDFConnectionRemote.create()
                .queryEndpoint(queryUrl)
                .updateEndpoint(updateUrl)
                .gspEndpoint(gspUrl)
                .build();
    }
}
