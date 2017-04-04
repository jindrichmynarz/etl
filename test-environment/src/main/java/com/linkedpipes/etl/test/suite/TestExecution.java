package com.linkedpipes.etl.test.suite;

import com.linkedpipes.etl.dataunit.core.files.FilesDataUnit;
import com.linkedpipes.etl.dataunit.core.files.WritableFilesDataUnit;
import com.linkedpipes.etl.dataunit.core.rdf.*;
import com.linkedpipes.etl.executor.api.v1.LpException;
import com.linkedpipes.etl.executor.api.v1.component.Component;
import com.linkedpipes.etl.executor.api.v1.component.SequentialExecution;
import com.linkedpipes.etl.executor.api.v1.rdf.AnnotationDescriptionFactory;
import com.linkedpipes.etl.executor.api.v1.service.ExceptionFactory;
import com.linkedpipes.etl.executor.api.v1.service.ProgressReport;
import com.linkedpipes.etl.executor.api.v1.service.WorkingDirectory;
import com.linkedpipes.etl.rdf.utils.RdfSource;
import com.linkedpipes.etl.rdf.utils.RdfUtils;
import com.linkedpipes.etl.rdf.utils.RdfUtilsException;
import com.linkedpipes.etl.rdf.utils.rdf4j.Rdf4jSource;
import com.linkedpipes.etl.rdf.utils.rdf4j.Rdf4jUtils;
import com.linkedpipes.etl.test.dataunit.*;
import com.linkedpipes.etl.test.service.MockedServiceFactory;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.SimpleValueFactory;
import org.eclipse.rdf4j.repository.Repository;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.util.Repositories;
import org.eclipse.rdf4j.sail.memory.MemoryStore;
import org.eclipse.rdf4j.sail.nativerdf.NativeStore;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Execute given component.
 *
 * Known limitations:
 * - Does not support Component.ContainsConfiguration annotation
 */
public class TestExecution {

    private Class<?> componentClass;

    private Map<String, File> inputs = new HashMap<>();

    private Map<String, File> outputs = new HashMap<>();

    private File configurationFile;

    private File testRootDirectory;

    private Map<String, Checkable> dataUnits = new HashMap<>();

    private Repository rdfRepository;

    private int indexCounter = 0;

    public TestExecution(Class<?> componentClass) {
        this.componentClass = componentClass;
    }

    public void setConfigurationFile(String resourcePath) throws TestFailed {
        configurationFile = resourceToFile(resourcePath);
    }

    public void setInput(String binding, String resourcePath)
            throws TestFailed {
        File file = resourceToFile(resourcePath);
        inputs.put(binding, file);
    }

    public void setExpectedOutput(String binding, String resourcePath)
            throws TestFailed {
        File file = resourceToFile(resourcePath);
        outputs.put(binding, file);
    }

    public void executeAndEvaluate() throws TestFailed {
        prepareWorkingEnvironment();
        initializeRepository();
        SequentialExecution instance = createComponent();
        initializeComponent(instance);
        try {
            execute(instance);
            checkOutputs();
        } finally {
            shutdownRepository();
        }
    }

    private SequentialExecution createComponent() throws TestFailed {
        try {
            return (SequentialExecution) componentClass.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new TestFailed("Can't create component instance.", ex);
        }
    }

    private void prepareWorkingEnvironment() throws TestFailed {
        try {
            testRootDirectory =
                    Files.createTempDirectory("lp-plugin-test-").toFile();
        } catch (IOException ex) {
            throw new TestFailed("Can't create working directory.", ex);
        }
    }

    private void initializeRepository() throws TestFailed {
        File repositoryDirectory = new File(testRootDirectory,
                "repository");
        rdfRepository = new SailRepository(
                new NativeStore(repositoryDirectory));
        rdfRepository.initialize();
    }

    private void shutdownRepository() {
        rdfRepository.shutDown();
    }

    private void initializeComponent(Object instance)
            throws TestFailed {
        for (Field field : instance.getClass().getFields()) {
            if (field.getAnnotation(Component.Inject.class) != null) {
                try {
                    bindExtension(instance, field);
                } catch (IllegalAccessException ex) {
                    throw new TestFailed("Can't bind extension.", ex);
                }
            }
            if (field.getAnnotation(Component.Configuration.class) != null) {
                bindConfiguration(instance, field);
            }
            if (field.getAnnotation(Component.InputPort.class) != null) {
                bindPort(instance, field,
                        field.getAnnotation(Component.InputPort.class).iri());
            }
            if (field.getAnnotation(Component.OutputPort.class) != null) {
                bindPort(instance, field,
                        field.getAnnotation(Component.OutputPort.class).iri());
            }
        }
    }

    private void bindExtension(Object instance, Field field)
            throws IllegalAccessException {
        if (field.getType() == ProgressReport.class) {
            field.set(instance, MockedServiceFactory.createProgressReport());
        } else if (field.getType() == ExceptionFactory.class) {
            field.set(instance, MockedServiceFactory.createExceptionFactory());
        } else if (field.getType() == WorkingDirectory.class) {
            field.set(instance, new WorkingDirectory(
                    new File(testRootDirectory, "working").toURI()));
        } else {
            throw new RuntimeException("Can't initialize extension!");
        }
    }

    private void bindConfiguration(Object instance, Field field)
            throws TestFailed {
        if (configurationFile == null) {
            throw new TestFailed("Missing configuration.");
        }
        List<Statement> statements;
        try {
            statements = Rdf4jUtils.loadAsStatements(configurationFile);
        } catch (RdfUtilsException ex) {
            throw new TestFailed("Can't read configuration file.", ex);
        }
        Object configuration = loadConfiguration(statements, field.getType());
        try {
            field.set(instance, configuration);
        } catch (IllegalAccessException ex) {
            throw new TestFailed("Can't bind configuration.");
        }
    }

    private Object loadConfiguration(List<Statement> statements,
            Class<?> configurationType) throws TestFailed {
        // TODO Replace with entity loading from statements
        Object configurationInstance;
        try {
            configurationInstance = configurationType.newInstance();
        } catch (InstantiationException | IllegalAccessException ex) {
            throw new TestFailed("Can't create configuration object.", ex);
        }
        Repository configRepository = new SailRepository(new MemoryStore());
        configRepository.initialize();
        Repositories.consume(configRepository, (connection) -> {
            ValueFactory valueFactory = SimpleValueFactory.getInstance();
            connection.add(statements,
                    valueFactory.createIRI("http://localhost/graph"));
        });
        RdfSource source = Rdf4jSource.createWrap(configRepository);
        try {
            RdfUtils.loadTypedByReflection(source, configurationInstance,
                    "http://localhost/graph",
                    new AnnotationDescriptionFactory());
        } catch (RdfUtilsException ex) {
            throw new TestFailed("Can't load configuration.", ex);
        } finally {
            source.shutdown();
            configRepository.shutDown();
        }
        return configurationInstance;
    }

    private void bindPort(Object instance, Field field, String binding)
            throws TestFailed {
        Checkable dataUnit = createDataUnit(field.getType(), binding);
        dataUnits.put(binding, dataUnit);
        try {
            field.set(instance, dataUnit);
        } catch (IllegalAccessException ex) {
            throw new TestFailed("Can't bind data unit.", ex);
        }
    }

    private Checkable createDataUnit(Class<?> dataUnitType, String binding)
            throws TestFailed {
        if (dataUnitType.equals(FilesDataUnit.class) ||
                dataUnitType.equals(WritableFilesDataUnit.class)) {
            return createFilesDataUnit(binding);
        }
        if (dataUnitType.equals(GraphListDataUnit.class) ||
                dataUnitType.equals(WritableGraphListDataUnit.class)) {
            // TODO
            throw new TestFailed("GraphListDataUnit is not supported");
        }
        if (dataUnitType.equals(SingleGraphDataUnit.class) ||
                dataUnitType.equals(WritableSingleGraphDataUnit.class)) {
            return createSingleGraphDataUnit(binding);
        }
        if (dataUnitType.equals(ChunkedTriples.class) ||
                dataUnitType.equals(WritableChunkedTriples.class)) {
            return createChunkedTriplesDataUnit(binding);
        }
        throw new TestFailed("Invalid type of DataUnit: {}",
                dataUnitType.getName());
    }

    private Checkable createFilesDataUnit(String binding) {
        File dataDirectory;
        if (inputs.containsKey(binding)) {
            dataDirectory = inputs.get(binding);
        } else {
            dataDirectory = getDataUnitDirectory();
        }
        return new TestFilesDataUnit(dataDirectory);
    }

    private Checkable createSingleGraphDataUnit(String binding)
            throws TestFailed {
        String graph = "http://localhost/graphs/" + ++indexCounter;
        if (inputs.containsKey(binding)) {
            File dataFile = inputs.get(binding);
            try (RepositoryConnection connection =
                         rdfRepository.getConnection()) {
                connection.add(Rdf4jUtils.loadAsStatements(dataFile),
                        SimpleValueFactory.getInstance().createIRI(graph));
            } catch (RdfUtilsException ex) {
                throw new TestFailed("Can't read file.", ex);
            }
        }
        return new TestSingleGraphDataUnit(graph, rdfRepository);
    }

    private Checkable createChunkedTriplesDataUnit(String binding) {
        File dataDirectory;
        if (inputs.containsKey(binding)) {
            dataDirectory = inputs.get(binding);
        } else {
            dataDirectory = getDataUnitDirectory();
        }
        return new TestChunkedTriplesDataUnit(dataDirectory);
    }


    private File getDataUnitDirectory() {
        File directory = new File(testRootDirectory, "dataUnit/" +
                Integer.toString(++indexCounter));
        directory.mkdirs();
        return directory;
    }

    private void execute(SequentialExecution instance) throws TestFailed {
        try {
            instance.execute();
        } catch (LpException ex) {
            throw new TestFailed("Execution failed.", ex);
        }
    }

    private void checkOutputs() throws TestFailed {
        for (Map.Entry<String, File> entry : outputs.entrySet()) {
            Object dataUnit = dataUnits.get(entry.getKey());
            if (dataUnit == null) {
                throw new TestFailed("Missing output: {}", entry.getKey());
            }
            if (!checkDataUnit(dataUnit, entry.getValue())) {
                throw new TestFailed("'{}' does not match expected value." +
                        "see logs for more detail.", entry.getKey());
            }
        }
    }

    private boolean checkDataUnit(Object dataUnit, File expected)
            throws TestFailed {
        Class<?> dataUnitType = dataUnit.getClass();
        if (dataUnitType.equals(TestFilesDataUnit.class)) {
            return ((TestFilesDataUnit) dataUnit).checkContent(expected);
        }
        if (dataUnitType.equals(TestGraphListDataUnit.class)) {
            return ((TestGraphListDataUnit) dataUnit).checkContent(expected);
        }
        if (dataUnitType.equals(TestSingleGraphDataUnit.class)) {
            return ((TestSingleGraphDataUnit) dataUnit).checkContent(expected);
        }
        if (dataUnitType.equals(TestChunkedTriplesDataUnit.class)) {
            return ((TestChunkedTriplesDataUnit) dataUnit)
                    .checkContent(expected);
        }
        throw new TestFailed("Unexpected type of data unit.");
    }

    public static <T extends SequentialExecution> TestExecution create(
            Class<T> componentClass) {
        return new TestExecution(componentClass);
    }

    private static File resourceToFile(String resourceName) throws TestFailed {
        URL url = Thread.currentThread().getContextClassLoader()
                .getResource(resourceName);
        if (url == null) {
            throw new TestFailed("Resource '{}' is missing.", resourceName);
        }
        return new File(url.getPath());
    }

}
