package com.linkedpipes.plugin.transformer.sparql.update;

import com.linkedpipes.etl.component.api.Component;
import com.linkedpipes.etl.component.api.service.ExceptionFactory;
import com.linkedpipes.etl.component.api.service.ProgressReport;
import com.linkedpipes.etl.dataunit.sesame.api.rdf.ChunkedStatements;
import com.linkedpipes.etl.dataunit.sesame.api.rdf.SingleGraphDataUnit;
import com.linkedpipes.etl.dataunit.sesame.api.rdf.WritableChunkedStatements;
import com.linkedpipes.etl.executor.api.v1.exception.LpException;
import org.openrdf.model.Statement;
import org.openrdf.repository.Repository;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.util.Repositories;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.helpers.AbstractRDFHandler;
import org.openrdf.sail.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Chunked version of SparqlUpdate.
 */
public final class SparqlUpdateChunked implements Component.Sequential {

    private static final Logger LOG =
            LoggerFactory.getLogger(SparqlUpdateChunked.class);

    @Component.InputPort(id = "InputRdf")
    public ChunkedStatements inputRdf;

    @Component.ContainsConfiguration
    @Component.InputPort(id = "Configuration")
    public SingleGraphDataUnit configurationRdf;

    @Component.OutputPort(id = "OutputRdf")
    public WritableChunkedStatements outputRdf;

    @Component.Configuration
    public SparqlUpdateConfiguration configuration;

    @Component.Inject
    public ExceptionFactory exceptionFactory;

    @Component.Inject
    public ProgressReport progressReport;

    @Override
    public void execute() throws LpException {
        if (configuration.getQuery() == null
                || configuration.getQuery().isEmpty()) {
            throw exceptionFactory.failure("Missing property: {}",
                    SparqlUpdateVocabulary.CONFIG_SPARQL);
        }


        Date time;
        long loadingTimeGlobal = 0;
        long repositoryTimeGlobal = 0;
        long queryTimeGlobal = 0;
        long writeTimeGlobal = 0;
        long getTimeGlobal = 0;

        // We always perform inserts.
        progressReport.start(inputRdf.size());
        List<Statement> outputBuffer = new ArrayList<>(10000);
        for (ChunkedStatements.Chunk chunk : inputRdf) {
            // Prepare repository and load data.
            final Repository repository = new SailRepository(new MemoryStore());
            repository.initialize();
            time = new Date();
            final Collection<Statement> statements = chunk.toStatements();
            long loadingTime = (new Date()).getTime() - time.getTime();
            time = new Date();
            Repositories.consume(repository, (connection) -> {
                connection.add(statements);
            });
            long repositoryTime = (new Date()).getTime() - time.getTime();
            time = new Date();
            // Execute query.
            Repositories.consume(repository, (connection) -> {
                connection.prepareUpdate(
                        configuration.getQuery()).execute();
            });
            long queryTime = (new Date()).getTime() - time.getTime();
            time = new Date();
            // Store data.
            Repositories.consume(repository, (connection) -> {
                connection.export(new AbstractRDFHandler() {
                    @Override
                    public void handleStatement(Statement st)
                            throws RDFHandlerException {
                        outputBuffer.add(st);
                    }
                });
            });
            long getTime = (new Date()).getTime() - time.getTime();
            time = new Date();
            outputRdf.submit(outputBuffer);
            long writeTime = (new Date()).getTime() - time.getTime();

            LOG.info("CHUNK:{},{},{},{},{},{}", statements.size(),
                    outputBuffer.size(), loadingTime,
                    repositoryTime, queryTime, writeTime);

            loadingTimeGlobal += loadingTime;
            repositoryTimeGlobal += repositoryTime;
            queryTimeGlobal += queryTime;
            writeTimeGlobal += writeTime;
            getTimeGlobal += getTime;

            // Cleanup.
            outputBuffer.clear();
            repository.shutDown();
            progressReport.entryProcessed();
        }
        progressReport.done();
        LOG.info("GLOBAL:{},{},{},{},{}", loadingTimeGlobal,
                repositoryTimeGlobal, queryTimeGlobal, writeTimeGlobal,
                getTimeGlobal);
    }

}
