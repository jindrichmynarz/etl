package com.linkedpipes.etl.test.suite;

import com.linkedpipes.etl.executor.api.v1.LpException;

public class TestFailed extends LpException {

    public TestFailed(String messages, Object... args) {
        super(messages, args);
    }

}
