package com.linkedpipes.plugin.transformer.textHolder;

import com.linkedpipes.etl.test.suite.TestExecution;
import org.junit.Test;

public class ExecutionTest {

    @Test
    public void writeSimpleFile() throws Exception {
        TestExecution test = TestExecution.create(TextHolder.class);
        test.setConfigurationFile(
                "execution/writeSimpleFile/configuration.ttl");
        test.setExpectedOutput("FilesOutput",
                "execution/writeSimpleFile/output");
        test.executeAndEvaluate();
    }

}
