package com.linkedpipes.plugin.extractor.httpgetfiles;

import com.linkedpipes.etl.dataunit.sesame.api.rdf.SingleGraphDataUnit;
import com.linkedpipes.etl.dataunit.system.api.SystemDataUnitException;
import com.linkedpipes.etl.dataunit.system.api.files.WritableFilesDataUnit;
import com.linkedpipes.etl.dpu.api.service.ProgressReport;
import com.linkedpipes.etl.executor.api.v1.exception.NonRecoverableException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.linkedpipes.etl.dpu.api.executable.SimpleExecution;
import com.linkedpipes.etl.dpu.api.Component;

/**
 *
 * @author Škoda Petr
 */
public final class HttpGetFiles implements SimpleExecution {

    private static final Logger LOG
            = LoggerFactory.getLogger(HttpGetFiles.class);

    @Component.ContainsConfiguration
    @Component.InputPort(id = "Configuration")
    public SingleGraphDataUnit configurationRdf;

    @Component.OutputPort(id = "FilesOutput")
    public WritableFilesDataUnit output;

    @Component.Configuration
    public HttpGetFilesConfiguration configuration;

    @Component.Inject
    public ProgressReport progressReport;

    @Override
    public void execute(Component.Context context)
            throws NonRecoverableException {
        // TODO Do not use this, but be selective about certs we trust.
        try {
            LOG.warn("'Trust all certs' policy used -> security risk!");
            setTrustAllCerts();
        } catch (Exception ex) {
            throw new Component.ExecutionFailed(
                    "Can't set trust all certificates.", ex);
        }
        progressReport.start(configuration.getReferences().size());
        for (HttpGetFilesConfiguration.Reference reference
                : configuration.getReferences()) {
            try{
                download(reference);
            } catch (Throwable t) {
                if (configuration.isSkipOnError()) {
                    LOG.warn("Skipping file: {} -> {}", reference.getFileName(),
                            reference.getUri(), t);
                } else {
                    throw t;
                }
            }
            progressReport.entryProcessed();
            // Check for cancel.
            if (context.canceled()) {
                throw new Component.ExecutionCancelled();
            }
            // TODO Add wait time.
        }
        progressReport.done();
    }

    /**
     * Download and store referenced resource.
     *
     * @param reference
     * @throws com.linkedpipes.etl.dpu.api.Component.ExecutionFailed
     * @throws SystemDataUnitException
     */
    private void download(HttpGetFilesConfiguration.Reference reference)
            throws ExecutionFailed, SystemDataUnitException {
        LOG.info("Downloading: {} -> {}", reference.getUri(),
                reference.getFileName());
        // Prepare source URL.
        final URL source;
        try {
            source = new URL(reference.getUri());
        } catch (MalformedURLException ex) {
            throw new Component.ExecutionFailed("Invalid URI: {}.",
                    reference.getUri(), ex);
        }
        // Prepare target destination.
        final File destination = output.createFile(
                reference.getFileName()).toFile();
        // Download file.
        HttpURLConnection connection;
        try {
            connection = (HttpURLConnection) source.openConnection();
        } catch (IOException ex) {
            throw new ExecutionFailed("Can't open connection.", ex);
        }
        if (configuration.isForceFollowRedirect()) {
            // Check for redirect. We can hawe multiple redirects
            // so follow untill there is no one.
            HttpURLConnection oldConnection;
            try {
                do {
                    oldConnection = connection;
                    connection = followRedirect(oldConnection);
                } while (connection != oldConnection);
            } catch (IOException ex) {
                throw new ExecutionFailed("Can't resolve redirect.", ex);
            }
        }
        // Copy content.
        try (InputStream inputStream = connection.getInputStream()) {
            FileUtils.copyInputStreamToFile(inputStream, destination);
        } catch (IOException ex) {
            throw new ExecutionFailed("Can't copy file.", ex);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * Add trust to all certificates.
     *
     * @throws Exception
     */
    private static void setTrustAllCerts() throws Exception {
        final TrustManager[] trustAllCerts = new TrustManager[]{
            new X509TrustManager() {
                @Override
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(
                        java.security.cert.X509Certificate[] certs,
                        String authType) {
                }

                @Override
                public void checkServerTrusted(
                        java.security.cert.X509Certificate[] certs,
                        String authType) {
                }
            }
        };
        // Install the all-trusting trust manager.
        try {
            final SSLContext sc = SSLContext.getInstance("SSL");
            sc.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(
                    sc.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(
                    (String urlHostName, SSLSession session) -> true);
        } catch (KeyManagementException | NoSuchAlgorithmException ex) {
            throw ex;
        }
    }

    /**
     * Open connection and check for redirect. If there is redirect then
     * close given connection and return connection to the new location.
     *
     * @param connection
     * @return
     * @throws IOException
     * @throws com.linkedpipes.etl.dpu.api.DataProcessingUnit.ExecutionFailed
     */
    private static HttpURLConnection followRedirect(
            HttpURLConnection connection) throws IOException, ExecutionFailed {
        connection.connect();
        if (connection.getResponseCode()
                == HttpURLConnection.HTTP_SEE_OTHER) {
            final String location = connection.getHeaderField("Location");
            if (location == null) {
                throw new ExecutionFailed("Missing Location for redirect.");
            } else {
                // Update based on the redirect.
                connection.disconnect();
                final URL source = new URL(location);
                LOG.debug("Follow redirect: {}", location);
                final HttpURLConnection newConnection
                        = (HttpURLConnection) source.openConnection();
                return newConnection;
            }
        } else {
            return connection;
        }
    }

}
