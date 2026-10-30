package com.ruleweaver.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoConfigTest {

    @Test
    void sanitizesMongoAndMySqlConnectionStrings() {
        String message = "Failed for mongodb+srv://user:secret@cluster/db "
                + "and jdbc:mysql://user:secret@localhost:3306/db";

        assertEquals("Failed for [MongoDB URI] and [MySQL URL]",
                MongoConfig.sanitize(message));
    }
}
