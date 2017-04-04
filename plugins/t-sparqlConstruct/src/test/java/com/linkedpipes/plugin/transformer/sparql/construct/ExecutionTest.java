package com.linkedpipes.plugin.transformer.sparql.construct;

import com.linkedpipes.etl.test.suite.TestExecution;
import org.junit.Test;

public class ExecutionTest {

    @Test
    public void writeSimpleFile() throws Exception {
        TestExecution test = TestExecution.create(SparqlConstruct.class);
        test.setConfigurationFile(
                "execution/01/configuration.ttl");
        test.setInput("InputRdf",
                "execution/01/input.ttl");
        test.setExpectedOutput("OutputRdf",
                "execution/01/output.ttl");
        test.executeAndEvaluate();
    }

}
