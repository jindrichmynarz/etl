package com.linkedpipes.etl.executor.logging;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.OutputStreamAppender;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedFNATP;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 *
 */
public class LoggerFacade {

    public static final String EXECUTION_MDC = "execution";

    public static final String WEB_MDC = "web";

    private Appender debugAppender = null;

    private Appender infoAppender = null;

    public void prepareAppendersForExecution(
            File debugLogFile, File infoLogFile) {
        destroyExecutionAppenders();
        debugAppender = createExecutionAppender(debugLogFile, "DEBUG");
        infoAppender = createExecutionAppender(infoLogFile, "INFO");
    }

    public void destroyExecutionAppenders() {
        if (debugAppender != null) {
            destroyAppender(debugAppender);
            debugAppender = null;
        }
        if (infoAppender != null) {
            destroyAppender(infoAppender);
            infoAppender = null;
        }
    }

    public static Appender<ILoggingEvent> createRollingFileAppender(
            File logDirectory, String logFileName, LoggerContext loggerContext,
            String levelFilter) {
        logDirectory.mkdirs();
        final RollingFileAppender appender = new RollingFileAppender();
        appender.setContext(loggerContext);
        appender.setFile(logDirectory.getPath() + File.separator
                + logFileName + ".log");
        addRollingPolicy(appender, loggerContext, logDirectory, logFileName);
        addEncoder(appender, loggerContext,
                "%d [%thread] %-5level %logger{50} - %msg%n");
        addThresholdFilter(appender, levelFilter);
        appender.start();
        return appender;
    }

    private static void destroyAppender(Appender appender) {
        final LoggerContext loggerContext
                = (LoggerContext) LoggerFactory.getILoggerFactory();
        final ch.qos.logback.classic.Logger logbackLogger
                = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        logbackLogger.detachAppender(appender);
        appender.stop();
    }

    private static FileAppender createExecutionAppender(
            File logFile, String level) {
        final LoggerContext loggerContext
                = (LoggerContext) LoggerFactory.getILoggerFactory();

        final FileAppender appender = new FileAppender();
        appender.setContext(loggerContext);
        appender.setFile(logFile.getPath());
        addEncoder(appender, loggerContext,
                "%d [%thread] %-5level %logger{25} - %msg%n");
        addMdcFilter(appender, EXECUTION_MDC);
        addThresholdFilter(appender, level);
        appender.start();

        final ch.qos.logback.classic.Logger logbackLogger
                = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        logbackLogger.addAppender(appender);

        return appender;
    }

    private static void addEncoder(OutputStreamAppender appender,
            LoggerContext loggerContext, String patter) {
        final PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(loggerContext);
        encoder.setPattern(patter);
        appender.setEncoder(encoder);
        encoder.start();
    }

    private static void addMdcFilter(
            UnsynchronizedAppenderBase appender, String mdc) {
        final MdcKeyFilter mdcFilter = new MdcKeyFilter(mdc);
        appender.addFilter(mdcFilter);
        mdcFilter.start();

    }

    private static void addThresholdFilter(
            UnsynchronizedAppenderBase appender, String level) {
        final ThresholdFilter thresholdFilter = new ThresholdFilter();
        thresholdFilter.setLevel(level);
        appender.addFilter(thresholdFilter);
        thresholdFilter.start();
    }

    private static void addRollingPolicy(RollingFileAppender appender,
            LoggerContext loggerContext, File logDirectory, String fileName) {
        final TimeBasedRollingPolicy rollingPolicy =
                new TimeBasedRollingPolicy();
        rollingPolicy.setContext(loggerContext);
        rollingPolicy.setParent(appender);
        rollingPolicy.setFileNamePattern(logDirectory.getPath()
                + File.separator + fileName + ".%d{yyyy-MM-dd}.%i.log");
        rollingPolicy.setMaxHistory(7);
        appender.setRollingPolicy(rollingPolicy);
        // File split policy.
        final SizeAndTimeBasedFNATP triggeringPolicy;
        triggeringPolicy = new SizeAndTimeBasedFNATP();
        triggeringPolicy.setMaxFileSize("1024MB");
        triggeringPolicy.setTimeBasedRollingPolicy(rollingPolicy);
        appender.setTriggeringPolicy(triggeringPolicy);
        rollingPolicy.setTimeBasedFileNamingAndTriggeringPolicy(
                triggeringPolicy);
        //
        rollingPolicy.start();
        // We need TimeBasedRollingPolicy to have the FileNamePattern
        // pattern initialized which is done in rollingPolicy.start();
        triggeringPolicy.start();
    }


}
