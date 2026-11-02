package com.ruleweaver.repository;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;

/**
 * Stores user accounts in the "users" collection of the same MongoDB database
 * the rest of RuleWeaver X uses. One document per account.
 */
public class UserRepository {
    private static final int DUPLICATE_KEY_CODE = 11000;

    private final MongoCollection<Document> collection;

    public UserRepository(MongoDatabase database) {
        this.collection = database.getCollection("users");
        // One account per email address, enforced by the database itself.
        this.collection.createIndex(new Document("email", 1), new IndexOptions().unique(true));
    }

    public Document findByEmail(String email) {
        return collection.find(Filters.eq("email", email)).first();
    }

    /** Saves a new account. Returns false when the email is already registered. */
    public boolean insertIfAbsent(Document user) {
        try {
            collection.insertOne(user);
            return true;
        } catch (MongoWriteException e) {
            if (e.getError().getCode() == DUPLICATE_KEY_CODE) {
                return false;
            }
            throw e;
        }
    }

    public void recordLogin(ObjectId id) {
        collection.updateOne(
                Filters.eq("_id", id),
                new Document("$set", new Document("lastLogin", new Date()))
                        .append("$inc", new Document("loginCount", 1)));
    }

    /** Reads the free-form data saved inside an account. */
    public Document findData(ObjectId id) {
        Document user = collection.find(Filters.eq("_id", id)).first();
        if (user == null) {
            return new Document();
        }
        Document data = user.get("data", Document.class);
        return data == null ? new Document() : data;
    }

    /** Merges the given values into the account's saved data. */
    public void mergeData(ObjectId id, Document values) {
        Document set = new Document();
        values.forEach((key, value) -> set.append("data." + key, value));
        if (set.isEmpty()) {
            return;
        }
        collection.updateOne(Filters.eq("_id", id), new Document("$set", set));
    }
}
