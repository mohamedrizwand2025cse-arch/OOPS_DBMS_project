
package com.ruleweaver.sync;

import com.mongodb.client.MongoDatabase;
import com.ruleweaver.config.MongoConfig;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SyncWorkerRunner implements AutoCloseable {

    private static final Logger LOGGER =
            Logger.getLogger(SyncWorkerRunner.class.getName());
    private final MySqlToMongoSyncWorker worker;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "ruleweaver-mysql-sync");
        thread.setDaemon(true);
        return thread;
    });

    private final AtomicBoolean started = new AtomicBoolean(false);

    public SyncWorkerRunner(MongoDatabase database) {
        this.worker = new MySqlToMongoSyncWorker(database);
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        LOGGER.info("MySQL-to-MongoDB sync worker started; polling every 5 seconds.");
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                int processed = worker.runOnce();

                if (processed > 0) {
                    LOGGER.info("Processed " + processed
                            + " MySQL sync event(s).");
                }
            } catch (Exception e) {
                String message = e.getMessage();
                if (message == null || message.isBlank()) {
                    message = "No additional error message.";
                }
                message = MongoConfig.sanitize(message);
                String detail = e.getClass().getSimpleName() + ": " + message;
                if (e instanceof java.sql.SQLException sqlException) {
                    detail += " (SQLState=" + sqlException.getSQLState()
                            + ", vendorCode=" + sqlException.getErrorCode()
                            + ")";
                }
                LOGGER.log(Level.SEVERE,
                        "Could not read pending MySQL sync events: " + detail);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        scheduler.shutdownNow();
    }
}
