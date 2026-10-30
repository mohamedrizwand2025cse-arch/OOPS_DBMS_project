
package com.ruleweaver.sync;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.ruleweaver.config.MySqlConnectionFactory;
import com.ruleweaver.config.MongoConfig;

import org.bson.Document;
import org.bson.types.ObjectId;

import java.sql.SQLException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class MySqlToMongoSyncWorker {

    private static final Logger LOGGER =
            Logger.getLogger(MySqlToMongoSyncWorker.class.getName());
    private static final ObjectMapper JSON = new ObjectMapper();

    private final MongoCollection<Document> applicants;

    public MySqlToMongoSyncWorker(MongoDatabase database) {
        this.applicants = database.getCollection("applicants");
    }

    public int runOnce() throws SQLException {
        List<QueueItem> pending = readPendingEvents();
        int processed = 0;

        for (QueueItem item : pending) {
            LOGGER.info(() -> "Applying MySQL sync event id=" + item.id()
                    + ", operation=" + item.operation()
                    + ", mongo_id=" + item.mongoId());
            try {
                if ("UPSERT".equalsIgnoreCase(item.operation())) {
                    syncApplicant(item.mongoId());
                } else if ("DELETE".equalsIgnoreCase(item.operation())) {
                    markDeleted(item.mongoId());
                } else {
                    throw new SQLException(
                            "Unknown operation: " + item.operation());
                }

                markProcessed(item.id());
                processed++;

            } catch (Exception e) {
                LOGGER.log(Level.SEVERE,
                        "MySQL sync event id=" + item.id()
                                + ", operation=" + item.operation()
                                + ", mongo_id=" + item.mongoId()
                                + " failed; it remains pending. "
                                + describeFailure(e));
            }
        }

        return processed;
    }

    private List<QueueItem> readPendingEvents() throws SQLException {
        List<QueueItem> items = new ArrayList<>();

        String sql = """
                SELECT id, mongo_id, operation
                FROM sync_outbox
                WHERE processed = FALSE
                ORDER BY id
                LIMIT 50
                """;

        try (Connection connection = MySqlConnectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql);
                ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                items.add(new QueueItem(
                        rs.getLong("id"),
                        rs.getString("mongo_id"),
                        rs.getString("operation")));
            }
        }

        return items;
    }

    private void syncApplicant(String mongoId) throws SQLException {
        String sql = """
                SELECT mongo_id, program_id, name, email, gpa,
                       interview_score, skills, custom_attributes, is_deleted
                FROM synced_applicants
                WHERE mongo_id = ?
                """;

        try (Connection connection = MySqlConnectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setString(1, mongoId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || rs.getBoolean("is_deleted")) {
                    markDeleted(mongoId);
                    return;
                }

                List<String> skills = readJson(
                        rs.getString("skills"),
                        new TypeReference<List<String>>() {
                        });

                Map<String, Object> attributes = readJson(
                        rs.getString("custom_attributes"),
                        new TypeReference<Map<String, Object>>() {
                        });

                Document document = new Document("_id", toMongoId(mongoId))
                        .append("programId", rs.getString("program_id"))
                        .append("name", rs.getString("name"))
                        .append("email", rs.getString("email"))
                        .append("gpa", rs.getDouble("gpa"))
                        .append("interviewScore", rs.getInt("interview_score"))
                        .append("skills", skills != null ? skills : List.of())
                        .append("customAttributes",
                                attributes != null ? attributes : new HashMap<>())
                        .append("status", "ACTIVE")
                        .append("_sync_deleted", false)
                        .append("_sync_origin", "mysql");

                applicants.replaceOne(
                        Filters.eq("_id", toMongoId(mongoId)),
                        document,
                        new ReplaceOptions().upsert(true));
            }
        }
    }

    private void markDeleted(String mongoId) {
        applicants.updateOne(
                Filters.eq("_id", toMongoId(mongoId)),
                new Document("$set", new Document("_sync_deleted", true)
                        .append("_sync_origin", "mysql")));
    }

    private void markProcessed(long id) throws SQLException {
        String sql = """
                UPDATE sync_outbox
                SET processed = TRUE,
                    processed_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """;

        try (Connection connection = MySqlConnectionFactory.getConnection();
                PreparedStatement ps = connection.prepareStatement(sql)) {

            ps.setLong(1, id);
            ps.executeUpdate();
        }
    }

    private static Object toMongoId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Applicant ID is empty.");
        }

        try {
            return new ObjectId(id);
        } catch (IllegalArgumentException ignored) {
            return id;
        }
    }

    private static <T> T readJson(
            String value, TypeReference<T> type) throws SQLException {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return JSON.readValue(value, type);
        } catch (Exception e) {
            throw new SQLException("Invalid JSON in applicant data.", e);
        }
    }

    private static String describeFailure(Exception failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) {
            message = "No additional error message.";
        }
        message = MongoConfig.sanitize(message);
        if (failure instanceof SQLException sqlException) {
            return failure.getClass().getSimpleName() + ": " + message
                    + " (SQLState=" + sqlException.getSQLState()
                    + ", vendorCode=" + sqlException.getErrorCode() + ")";
        }
        return failure.getClass().getSimpleName() + ": " + message;
    }

    private record QueueItem(
            long id, String mongoId, String operation) {
    }
}
