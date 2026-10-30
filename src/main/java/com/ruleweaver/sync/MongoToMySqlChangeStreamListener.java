
package com.ruleweaver.sync;

import com.mongodb.client.ChangeStreamIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.changestream.FullDocument;
import com.mongodb.client.model.changestream.ChangeStreamDocument;
import com.ruleweaver.config.MongoConfig;
import com.ruleweaver.model.Applicant;
import com.ruleweaver.repository.ApplicantMySqlRepository;
import com.ruleweaver.ui.adapter.ApplicantDocumentMapper;

import org.bson.Document;
import org.bson.BsonValue;
import org.bson.BsonDocument;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

public final class MongoToMySqlChangeStreamListener
        implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(MongoToMySqlChangeStreamListener.class.getName());

    private final MongoCollection<Document> applicants;
    private final ApplicantMySqlRepository mysqlRepository;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Thread listenerThread;

    public MongoToMySqlChangeStreamListener(MongoDatabase database) {
        this.applicants = database.getCollection("applicants");
        this.mysqlRepository = new ApplicantMySqlRepository();
    }

    public synchronized void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        listenerThread = new Thread(
                this::listen,
                "ruleweaver-mongo-change-stream");
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    private void listen() {
        while (running.get()) {
            try {
                ChangeStreamIterable<Document> stream = applicants.watch()
                        .fullDocument(FullDocument.UPDATE_LOOKUP);

                for (ChangeStreamDocument<Document> change : stream) {
                    if (!running.get()) {
                        break;
                    }

                    processChange(change);
                }
            } catch (Exception e) {
                if (running.get()) {
                    LOGGER.warning("MongoDB change stream failed: "
                            + MongoConfig.sanitize(e.getClass().getSimpleName()
                                    + ": " + e.getMessage()));

                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void processChange(ChangeStreamDocument<Document> change) {
        String operation = change.getOperationType().getValue();

        if ("delete".equals(operation)) {
            BsonValue idValue = change.getDocumentKey() == null
                    ? null
                    : change.getDocumentKey().get("_id");

            if (idValue != null) {
                String id = mongoIdFromBson(idValue);

                try {
                    mysqlRepository.markDeletedByMongoId(id);
                } catch (SQLException e) {
                    logFailure("Could not sync deleted applicant " + id, e);
                }
            }
            return;
        }

        Document document = change.getFullDocument();

        if (document == null) {
            return;
        }

        BsonDocument updatedFields = change.getUpdateDescription() == null
                ? null
                : change.getUpdateDescription().getUpdatedFields();
        if (isMySqlOriginated(
                operation, document.getString("_sync_origin"), updatedFields)) {
            return;
        }

        if (Boolean.TRUE.equals(document.getBoolean("_sync_deleted"))) {
            String id = document.get("_id").toString();
            try {
                mysqlRepository.markDeletedByMongoId(id);
            } catch (SQLException e) {
                logFailure("Could not sync applicant tombstone " + id, e);
            }
            return;
        }

        Applicant applicant = ApplicantDocumentMapper.toApplicant(document);

        try {
            mysqlRepository.upsert(applicant);
        } catch (SQLException e) {
            logFailure("Could not sync applicant " + applicant.getId(), e);
        }
    }

    static boolean isMySqlOriginated(
            String operation, String origin, BsonDocument updatedFields) {
        if (!"mysql".equals(origin)) {
            return false;
        }
        BsonValue updatedOrigin = updatedFields == null
                ? null
                : updatedFields.get("_sync_origin");
        return "insert".equals(operation)
                || "replace".equals(operation)
                || (updatedOrigin != null && updatedOrigin.isString()
                        && "mysql".equals(updatedOrigin.asString().getValue()));
    }

    static String mongoIdFromBson(BsonValue idValue) {
        if (idValue.isObjectId()) {
            return idValue.asObjectId().getValue().toHexString();
        }
        if (idValue.isString()) {
            return idValue.asString().getValue();
        }
        return idValue.toString();
    }

    private static void logFailure(String action, SQLException failure) {
        LOGGER.warning(action + " (SQLState=" + failure.getSQLState()
                + ", vendorCode=" + failure.getErrorCode() + "): "
                + MongoConfig.sanitize(failure.getClass().getSimpleName()
                        + ": " + failure.getMessage()));
    }

    @Override
    public synchronized void close() {
        running.set(false);

        if (listenerThread != null) {
            listenerThread.interrupt();
            listenerThread = null;
        }
    }
}
