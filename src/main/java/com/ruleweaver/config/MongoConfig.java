package com.ruleweaver.config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/**
 * Loads MongoDB configuration without embedding credentials in the application.
 */
public class MongoConfig {
    private static final String URI_PROPERTY = "RULEWEAVER_MONGO_URI";
    private static final String DATABASE_PROPERTY = "RULEWEAVER_DB_NAME";
    private static final String DEFAULT_DATABASE_NAME = "ruleweaver";
    private static volatile MongoClient mongoClient;

    private MongoConfig() {
    }

    public static synchronized MongoDatabase getDatabase() {
        if (mongoClient == null) {
            String uri = getConfiguredUri().orElseThrow(
                    () -> new IllegalStateException("MongoDB is not configured."));
            mongoClient = MongoClients.create(uri);
        }
        return mongoClient.getDatabase(getDatabaseName());
    }

    public static Optional<String> getConfiguredUri() {
        String envUri = System.getenv(URI_PROPERTY);
        if (envUri != null && !envUri.isBlank()) {
            return Optional.of(envUri.trim());
        }
        return Optional.ofNullable(loadProperties().getProperty(URI_PROPERTY))
                .map(String::trim)
                .filter(uri -> !uri.isBlank());
    }

    public static String getDatabaseName() {
        String envName = System.getenv(DATABASE_PROPERTY);
        if (envName != null && !envName.isBlank()) {
            return envName.trim();
        }
        return Optional.ofNullable(loadProperties().getProperty(DATABASE_PROPERTY))
                .map(String::trim)
                .filter(name -> !name.isBlank())
                .orElse(DEFAULT_DATABASE_NAME);
    }

    public static void verifyAndSave(String uri, String databaseName) {
        if (uri == null || uri.isBlank()) {
            throw new IllegalArgumentException("Enter a MongoDB connection URI.");
        }
        String normalizedDatabaseName = databaseName == null || databaseName.isBlank()
                ? DEFAULT_DATABASE_NAME
                : databaseName.trim();

        try (MongoClient testClient = MongoClients.create(uri.trim())) {
            testClient.getDatabase(normalizedDatabaseName).runCommand(new Document("ping", 1));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Could not connect to MongoDB: " + sanitize(e.getMessage()), e);
        }

        Properties properties = loadProperties();
        properties.setProperty(URI_PROPERTY, uri.trim());
        properties.setProperty(DATABASE_PROPERTY, normalizedDatabaseName);
        saveProperties(properties);
        close();
    }

    public static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll(
                        "(?i)mongodb(?:\\+srv)?://\\S+", "[MongoDB URI]")
                .replaceAll("(?i)jdbc:mysql://\\S+", "[MySQL URL]");
    }

    private static Properties loadProperties() {
        Properties properties = new Properties();
        List<Path> paths = configPaths();
        for (int i = paths.size() - 1; i >= 0; i--) {
            Path path = paths.get(i);
            if (!Files.isRegularFile(path)) {
                continue;
            }
            try (InputStream input = Files.newInputStream(path)) {
                properties.load(input);
            } catch (IOException e) {
                throw new IllegalStateException("Could not read MongoDB config file " + path, e);
            }
        }
        return properties;
    }

    private static void saveProperties(Properties properties) {
        IOException lastError = null;
        List<Path> paths = configPaths();
        for (Path path : paths) {
            try {
                Path parent = path.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (OutputStream output = Files.newOutputStream(path)) {
                    properties.store(output, "RuleWeaver X local configuration");
                }
                return;
            } catch (IOException e) {
                lastError = e;
            }
        }
        throw new IllegalStateException("Could not save MongoDB configuration.", lastError);
    }

    private static List<Path> configPaths() {
        List<Path> paths = new ArrayList<>();
        paths.add(applicationDirectory().resolve("config.local.properties"));
        paths.add(Paths.get(System.getProperty("user.home"), ".ruleweaver", "config.local.properties"));
        return paths;
    }

    private static Path applicationDirectory() {
        try {
            Path codeLocation = Paths.get(MongoConfig.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(codeLocation)) {
                return codeLocation.getParent();
            }
        } catch (URISyntaxException | NullPointerException ignored) {
            // Fall back to the process working directory when code location is unavailable.
        }
        // In development classes are loaded from target/classes, whereas the
        // application-local configuration belongs beside the launched app (the
        // working directory). A packaged JAR is handled by the branch above.
        return Paths.get(System.getProperty("user.dir"));
    }

    public static synchronized void close() {
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
        }
    }
}
