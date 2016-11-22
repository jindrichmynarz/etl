package com.linkedpipes.plugin.transformer.sparql.construct;

import com.linkedpipes.etl.component.api.Component;
import com.linkedpipes.etl.component.api.service.ExceptionFactory;
import com.linkedpipes.etl.component.api.service.ProgressReport;
import com.linkedpipes.etl.dataunit.sesame.api.rdf.ChunkedStatements;
import com.linkedpipes.etl.dataunit.sesame.api.rdf.SingleGraphDataUnit;
import com.linkedpipes.etl.dataunit.sesame.api.rdf.WritableChunkedStatements;
import com.linkedpipes.etl.executor.api.v1.exception.LpException;
import org.openrdf.model.Statement;
import org.openrdf.query.GraphQueryResult;
import org.openrdf.repository.Repository;
import org.openrdf.repository.sail.SailRepository;
import org.openrdf.repository.util.Repositories;
import org.openrdf.sail.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Chunked version of SPARQL construct. Perform the construct operation
 * on chunks of RDF data.
 *
 * Use the same vocabulary as SPARQL construct.
 */
public final class SparqlConstructChunked implements Component.Sequential {

    private static final Logger LOG =
            LoggerFactory.getLogger(SparqlConstructChunked.class);

    @Component.InputPort(id = "InputRdf")
    public ChunkedStatements inputRdf;

    @Component.ContainsConfiguration
    @Component.InputPort(id = "Configuration")
    public SingleGraphDataUnit configurationRdf;

    @Component.InputPort(id = "OutputRdf")
    public WritableChunkedStatements outputRdf;

    @Component.Configuration
    public SparqlConstructConfiguration configuration;

    @Component.Inject
    public ExceptionFactory exceptionFactory;

    @Component.Inject
    public ProgressReport progressReport;

    @Override
    public void execute() throws LpException {
        if (configuration.getQuery() == null
                || configuration.getQuery().isEmpty()) {
            throw exceptionFactory.failure("Missing property: {}",
                    SparqlConstructVocabulary.HAS_QUERY);
        }

        Date time;
        long inGlobal = 0;
        long outGlobal = 0;
        long loadingTimeGlobal = 0;
        long repositoryTimeGlobal = 0;
        long queryTimeGlobal = 0;
        long writeTimeGlobal = 0;

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
            // Execute query and store result.
            Repositories.consume(repository, (connection) -> {
                final GraphQueryResult result = connection.prepareGraphQuery(
                        configuration.getQuery()).evaluate();
                while (result.hasNext()) {
                    outputBuffer.add(result.next());
                }
            });
            long queryTime = (new Date()).getTime() - time.getTime();
            time = new Date();
            outputRdf.submit(outputBuffer);
            long writeTime = (new Date()).getTime() - time.getTime();

            LOG.info("CHUNK:{},{},{},{},{},{}", statements.size(),
                    outputBuffer.size(), loadingTime,
                    repositoryTime, queryTime, writeTime);

            inGlobal += statements.size();
            outGlobal = outputBuffer.size();
            loadingTimeGlobal += loadingTime;
            repositoryTimeGlobal += repositoryTime;
            queryTimeGlobal += queryTime;
            writeTimeGlobal += writeTime;

            // Cleanup.
            outputBuffer.clear();
            repository.shutDown();
            progressReport.entryProcessed();

        }
        progressReport.done();
        LOG.info("GLOBAL:{},{},{},{},{},{}", inGlobal, outGlobal,
                loadingTimeGlobal, repositoryTimeGlobal,
                queryTimeGlobal, writeTimeGlobal);
    }

}
