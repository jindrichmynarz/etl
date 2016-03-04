package com.linkedpipes.plugin.loader.sparql.endpoint;

import com.linkedpipes.etl.dataunit.sesame.api.rdf.SingleGraphDataUnit;
import com.linkedpipes.etl.dpu.api.DataProcessingUnit;
import com.linkedpipes.etl.dpu.api.executable.SequentialExecution;
import com.linkedpipes.etl.dpu.api.extensions.AfterExecution;
import com.linkedpipes.etl.dpu.api.extensions.FaultTolerance;
import com.linkedpipes.etl.executor.api.v1.exception.NonRecoverableException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.openrdf.model.IRI;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.SimpleValueFactory;
import org.openrdf.query.QueryLanguage;
import org.openrdf.query.Update;
import org.openrdf.repository.Repository;
import org.openrdf.repository.RepositoryConnection;
import org.openrdf.repository.RepositoryResult;
import org.openrdf.repository.sparql.SPARQLRepository;

/**
 *
 * @author Petr Škoda
 */
public class SparqlEndpointLoader implements SequentialExecution {

    @DataProcessingUnit.InputPort(id = "InputRdf")
    public SingleGraphDataUnit outputRdf;

    @DataProcessingUnit.Configuration
    public SparqlEndpointLoaderConfiguration configuration;

    @DataProcessingUnit.Extension
    public FaultTolerance faultTolerance;

    @DataProcessingUnit.Extension
    public AfterExecution afterExecution;

    @Override
    public void execute(Context context) throws NonRecoverableException {
        // Create repository.
        final SPARQLRepository sparqlRepository = new SPARQLRepository(
                configuration.getEndpoint());
        // No action here.
        sparqlRepository.initialize();
        //
        afterExecution.addAction(() -> {
            sparqlRepository.shutDown();
        });
        //
        faultTolerance.call(() -> {
            try (final CloseableHttpClient httpclient = getHttpClient()) {
                sparqlRepository.setHttpClient(httpclient);
                if (configuration.isClearDestinationGraph()) {
                    clearGraph(sparqlRepository,
                            configuration.getTargetGraphName());
                }
            }
        });
        faultTolerance.call(() -> {
            try (final CloseableHttpClient httpclient = getHttpClient()) {
                sparqlRepository.setHttpClient(httpclient);
                loadData(sparqlRepository);
            }
        });
    }

    private void loadData(Repository repository) {
        final IRI remoteGraph = SimpleValueFactory.getInstance().createIRI(
                configuration.getTargetGraphName());
        try (final RepositoryConnection remote = repository.getConnection();
                final RepositoryConnection local
                = outputRdf.getRepository().getConnection()) {
            final RepositoryResult<Statement> result = local.getStatements(
                    null, null, null, outputRdf.getGraph());
            final List<Statement> toAdd
                    = new ArrayList<>(configuration.getCommitSize());
            while (result.hasNext()) {
                toAdd.add(result.next());
                if (toAdd.size() >= configuration.getCommitSize()) {
                    remote.add(toAdd, remoteGraph);
                    toAdd.clear();
                }
            }
            // Add the rest of the elements.
            remote.add(toAdd, remoteGraph);
        }
    }

    private static void clearGraph(Repository repository, String graph) {
        try (RepositoryConnection connection = repository.getConnection()) {
            final Update update = connection.prepareUpdate(QueryLanguage.SPARQL,
                    "CLEAR GRAPH <" + graph + ">");
            update.execute();
        }
    }

    private CloseableHttpClient getHttpClient() {
        final CredentialsProvider credsProvider = new BasicCredentialsProvider();
        if (configuration.isUseAuthentification()) {
            credsProvider.setCredentials(
                    new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT),
                    new UsernamePasswordCredentials(
                            configuration.getUserName(),
                            configuration.getPassword()));
        }
        return HttpClients.custom()
                .setDefaultCredentialsProvider(credsProvider).build();
    }

}
