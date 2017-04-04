package com.linkedpipes.etl.rdf.utils.rdf4j;

import com.linkedpipes.etl.rdf.utils.RdfUtilsException;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFParser;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.StatementCollector;

import java.io.*;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class Rdf4jUtils {

    private Rdf4jUtils() {

    }

    public static List<Statement> loadAsStatements(File file)
            throws RdfUtilsException {
        RDFFormat format = getFormat(file);
        List<Statement> result = new LinkedList<>();
        try (InputStream stream  = new FileInputStream(file)) {
            RDFParser parser = Rio.createParser(format);
            parser.setRDFHandler(new StatementCollector(result));
            parser.parse(stream, "http://localhost/base");
        } catch (IOException e) {
            throw new RdfUtilsException("Can't load from file: {}",
                    file.getName());
        }
        return result;
    }

    private static RDFFormat getFormat(File file) throws RdfUtilsException {
        RDFFormat format = Rio.getParserFormatForFileName(file.getName()).get();
        if (format == null) {
            throw new RdfUtilsException("Unknown format: {}", file.getName());
        } else {
            return format;
        }
    }

    public static void save(File file, Collection<Statement> statements)
            throws RdfUtilsException {
        RDFFormat format = getFormat(file);
        try (OutputStream stream = new FileOutputStream(file)) {
            Rio.write(statements, stream, format);
        } catch (IOException e) {
            throw new RdfUtilsException("Can't save to file: {}",
                    file.getName());
        }

    }


}
