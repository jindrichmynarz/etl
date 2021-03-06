package com.linkedpipes.plugin.extractor.sparql.endpoint;

import com.linkedpipes.etl.executor.api.v1.rdf.RdfToPojo;

import java.util.ArrayList;
import java.util.List;

@RdfToPojo.Type(iri = SparqlEndpointChunkedVocabulary.CONFIG)
public class SparqlEndpointChunkedConfiguration {

    /**
     * Must contains ${VALUES} place holder.
     */
    @RdfToPojo.Property(iri = SparqlEndpointChunkedVocabulary.HAS_QUERY)
    private String query;

    @RdfToPojo.Property(iri = SparqlEndpointChunkedVocabulary.HAS_ENDPOINT)
    private String endpoint;

    /**
     * Default graphs can be specified only via the runtime configuration.
     */
    @RdfToPojo.Property(iri = SparqlEndpointChunkedVocabulary.HAS_DEFAULT_GRAPH)
    private List<String> defaultGraphs = new ArrayList<>();

    /**
     * Used as a Accept value in header.
     */
    @RdfToPojo.Property(iri = SparqlEndpointChunkedVocabulary.HAS_HEADER_ACCEPT)
    private String transferMimeType;

    @RdfToPojo.Property(iri = SparqlEndpointChunkedVocabulary.HAS_CHUNK_SIZE)
    private Integer chunkSize;

    public SparqlEndpointChunkedConfiguration() {
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public List<String> getDefaultGraphs() {
        return defaultGraphs;
    }

    public void setDefaultGraphs(List<String> defaultGraphs) {
        this.defaultGraphs = defaultGraphs;
    }

    public String getTransferMimeType() {
        return transferMimeType;
    }

    public void setTransferMimeType(String transferMimeType) {
        this.transferMimeType = transferMimeType;
    }

    public Integer getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(Integer chunkSize) {
        this.chunkSize = chunkSize;
    }
}
