package com.linkedpipes.etl.test.dataunit;

import com.linkedpipes.etl.dataunit.core.rdf.SingleGraphDataUnit;
import com.linkedpipes.etl.dataunit.core.rdf.WritableSingleGraphDataUnit;
import com.linkedpipes.etl.executor.api.v1.LpException;
import com.linkedpipes.etl.rdf.utils.RdfUtilsException;
import com.linkedpipes.etl.rdf.utils.rdf4j.Rdf4jTestUtils;
import com.linkedpipes.etl.rdf.utils.rdf4j.Rdf4jUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.util.Repositories;
import org.eclipse.rdf4j.rio.RDFHandlerException;
import org.eclipse.rdf4j.rio.helpers.AbstractRDFHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class TestSingleGraphDataUnit
        implements SingleGraphDataUnit, WritableSingleGraphDataUnit, Checkable {

    private static final Logger LOG =
            LoggerFactory.getLogger(TestSingleGraphDataUnit.class);

    private final IRI graph;

    private final Repository repository;

    public TestSingleGraphDataUnit(String graph,
            Repository repository) {
        this.graph = SimpleValueFactory.getInstance().createIRI(graph);
        this.repository = repository;
    }

    @Override
    public IRI getWriteGraph() {
        return graph;
    }

    @Override
    public IRI getReadGraph() {
        return graph;
    }

    @Override
    public void execute(RepositoryProcedure action)
            throws LpException {
        try (RepositoryConnection connection = repository.getConnection()) {
            action.accept(connection);
        }
    }

    @Override
    public <T> T execute(RepositoryFunction<T> action)
            throws LpException {
        try (RepositoryConnection connection = repository.getConnection()) {
            return action.accept(connection);
        }
    }

    @Override
    public void execute(Procedure action) throws LpException {
        action.accept();
    }

    @Override
    public Repository getRepository() {
        return repository;
    }

    @Override
    public boolean checkContent(File expectedContent) {
        List<Statement> actual = new LinkedList<>();
        ValueFactory valueFactory = SimpleValueFactory.getInstance();
        Repositories.consume(repository, (connection) -> {
            connection.export(
                    new AbstractRDFHandler() {
                        @Override
                        public void handleStatement(Statement st)
                                throws RDFHandlerException {
                            // Remove graph.
                            actual.add(valueFactory.createStatement(
                                    st.getSubject(),
                                    st.getPredicate(),
                                    st.getObject()
                            ));
                        }
                    }, graph);
        });
        List<Statement> expected;
        try {
            expected = Rdf4jUtils.loadAsStatements(expectedContent);
        } catch (RdfUtilsException ex) {
            LOG.error("Can't read expected values.", ex);
            return false;
        }
        return Rdf4jTestUtils.rdfEqual(expected, actual);
    }

}
