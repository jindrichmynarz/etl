package com.linkedpipes.etl.test.dataunit;

import com.linkedpipes.etl.dataunit.core.rdf.ChunkedTriples;
import com.linkedpipes.etl.dataunit.core.rdf.WritableChunkedTriples;
import com.linkedpipes.etl.executor.api.v1.LpException;
import com.linkedpipes.etl.rdf.utils.RdfUtilsException;
import com.linkedpipes.etl.rdf.utils.rdf4j.Rdf4jTestUtils;
import com.linkedpipes.etl.rdf.utils.rdf4j.Rdf4jUtils;
import org.eclipse.rdf4j.model.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class TestChunkedTriplesDataUnit
        implements ChunkedTriples, WritableChunkedTriples, Checkable {

    private static final Logger LOG =
            LoggerFactory.getLogger(TestChunkedTriplesDataUnit.class);

    private final File directory;

    private int chunkCounter = 0;

    public TestChunkedTriplesDataUnit(File directory) {
        this.directory = directory;
    }

    @Override
    public void submit(Collection<Statement> statements) throws LpException {
        File chunkFile = getNewChunkFile();
        try {
            Rdf4jUtils.save(chunkFile, statements);
        } catch (RdfUtilsException ex) {
            throw new LpException("Can't save chunk.", ex);
        }
    }

    private File getNewChunkFile() {
        return new File(directory, "chunk-" + ++chunkCounter);
    }

    @Override
    public long size() {
        return listFiles(directory).size();
    }

    @Override
    public Collection<File> getSourceDirectories() {
        return Arrays.asList(directory);
    }

    @Override
    public Iterator<Chunk> iterator() {
        final Iterator<File> files = listFiles(directory).iterator();
        return new Iterator<Chunk>() {
            @Override
            public boolean hasNext() {
                return files.hasNext();
            }

            @Override
            public Chunk next() {
                return createChunk(files.next());
            }
        };
    }

    private Chunk createChunk(File file) {
        return new Chunk() {
            @Override
            public Collection<Statement> toCollection() throws LpException {
                try {
                    return Rdf4jUtils.loadAsStatements(file);
                } catch (RdfUtilsException ex) {
                    throw new LpException("Can't load file.");
                }
            }
        };
    }

    private static List<File> listFiles(File rootDirectory) {
        final List<File> files = new LinkedList<>();
        try {
            Files.walkFileTree(rootDirectory.toPath(),
                    new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file,
                                BasicFileAttributes attributes)
                                throws IOException {
                            files.add(file.toFile());
                            return super.visitFile(file, attributes);
                        }
                    });
        } catch (IOException ex) {
            throw new RuntimeException("Can't iterate files.", ex);
        }
        return files;
    }

    @Override
    public boolean checkContent(File expectedContent) {
        boolean match = true;
        List<File> actual = listFiles(directory);
        List<File> expected = listFiles(expectedContent);
        if (actual.size() != expected.size()) {
            match = false;
            LOG.error("File count does not match actual: {} expected: {}",
                    actual.size(), expected.size());
        }
        //
        for (File actualFile : actual) {
            String fileName = directory.toPath().relativize(
                    actualFile.toPath()).toString();
            File expectedFile = new File(expectedContent, fileName);
            if (!expectedFile.exists()) {
                LOG.error("Missing file: {}");
                match = false;
            }
            List<Statement> actualValue;
            List<Statement> expectedValue;
            try {
                actualValue = Rdf4jUtils.loadAsStatements(actualFile);
                expectedValue = Rdf4jUtils.loadAsStatements(expectedFile);
            } catch (RdfUtilsException ex) {
                LOG.error("Can't read files: {} {}", actualFile, expectedFile);
                match = false;
                continue;
            }
            if (!Rdf4jTestUtils.rdfEqual(actualValue, expectedValue)) {
                match = false;
                LOG.error("Content does not match for: {}", fileName);
            }

        }
        return match;
    }

}
