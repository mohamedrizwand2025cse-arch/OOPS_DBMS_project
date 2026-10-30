CREATE DATABASE IF NOT EXISTS ruleweaver_sql
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE ruleweaver_sql;

CREATE TABLE IF NOT EXISTS synced_applicants (
    mongo_id VARCHAR(255) NOT NULL,
    program_id VARCHAR(255) NULL,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(320) NOT NULL,
    gpa DECIMAL(5, 2) NOT NULL,
    interview_score INT NOT NULL,
    skills JSON NOT NULL,
    custom_attributes JSON NOT NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (mongo_id)
) ENGINE=InnoDB;

CREATE TABLE IF NOT EXISTS sync_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT,
    mongo_id VARCHAR(255) NOT NULL,
    operation ENUM('UPSERT', 'DELETE') NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed BOOLEAN NOT NULL DEFAULT FALSE,
    processed_at TIMESTAMP NULL DEFAULT NULL,
    PRIMARY KEY (id),
    INDEX idx_sync_outbox_pending (processed, id),
    INDEX idx_sync_outbox_mongo_id (mongo_id, id)
) ENGINE=InnoDB;

DROP TRIGGER IF EXISTS trg_applicants_after_insert;
DROP TRIGGER IF EXISTS trg_applicants_after_update;
DROP TRIGGER IF EXISTS trg_applicants_after_delete;

DELIMITER $$

CREATE TRIGGER trg_applicants_after_insert
AFTER INSERT ON synced_applicants
FOR EACH ROW
BEGIN
    IF COALESCE(@ruleweaver_sync_origin, '') <> 'mongo' THEN
        INSERT INTO sync_outbox (mongo_id, operation)
        VALUES (NEW.mongo_id, 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_applicants_after_update
AFTER UPDATE ON synced_applicants
FOR EACH ROW
BEGIN
    IF COALESCE(@ruleweaver_sync_origin, '') <> 'mongo' THEN
        INSERT INTO sync_outbox (mongo_id, operation)
        VALUES (NEW.mongo_id, 'UPSERT');
    END IF;
END$$

CREATE TRIGGER trg_applicants_after_delete
AFTER DELETE ON synced_applicants
FOR EACH ROW
BEGIN
    IF COALESCE(@ruleweaver_sync_origin, '') <> 'mongo' THEN
        INSERT INTO sync_outbox (mongo_id, operation)
        VALUES (OLD.mongo_id, 'DELETE');
    END IF;
END$$

DELIMITER ;
