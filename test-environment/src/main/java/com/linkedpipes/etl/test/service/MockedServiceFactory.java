package com.linkedpipes.etl.test.service;

import com.linkedpipes.etl.executor.api.v1.service.ExceptionFactory;
import com.linkedpipes.etl.executor.api.v1.service.ProgressReport;

public class MockedServiceFactory {

    private MockedServiceFactory() {

    }

    public static ExceptionFactory createExceptionFactory() {
        return new MockedExceptionFactory();
    }

    public static ProgressReport createProgressReport() {
        return new MockedProgressReport();
    }

}
