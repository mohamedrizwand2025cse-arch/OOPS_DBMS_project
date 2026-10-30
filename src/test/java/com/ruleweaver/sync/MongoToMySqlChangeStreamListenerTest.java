package com.ruleweaver.sync;

import org.bson.BsonDocument;
import org.bson.BsonObjectId;
import org.bson.BsonString;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoToMySqlChangeStreamListenerTest {

    @Test
    void ignoresMongoEventsWrittenByTheMySqlWorker() {
        assertTrue(MongoToMySqlChangeStreamListener.isMySqlOriginated(
                "replace", "mysql", null));
        assertTrue(MongoToMySqlChangeStreamListener.isMySqlOriginated(
                "update", "mysql",
                new BsonDocument("_sync_origin", new BsonString("mysql"))));
    }

    @Test
    void doesNotIgnoreLaterMongoUpdatesJustBecauseOriginMarkerIsPresent() {
        assertFalse(MongoToMySqlChangeStreamListener.isMySqlOriginated(
                "update", "mysql",
                new BsonDocument("name", new BsonString("Updated from MongoDB"))));
    }

    @Test
    void doesNotIgnoreMongoOriginatedChangesWithoutWorkerMarker() {
        assertFalse(MongoToMySqlChangeStreamListener.isMySqlOriginated(
                "insert", null, null));
        assertFalse(MongoToMySqlChangeStreamListener.isMySqlOriginated(
                "update", "mongo",
                new BsonDocument("_sync_origin", new BsonString("mongo"))));
    }

    @Test
    void extractsPlainStringAndObjectIdValuesFromDeleteEvents() {
        assertEquals("MYSQL_TO_MONGO_TEST_001",
                MongoToMySqlChangeStreamListener.mongoIdFromBson(
                        new BsonString("MYSQL_TO_MONGO_TEST_001")));
        assertEquals("507f1f77bcf86cd799439011",
                MongoToMySqlChangeStreamListener.mongoIdFromBson(
                        new BsonObjectId(new ObjectId("507f1f77bcf86cd799439011"))));
    }
}
