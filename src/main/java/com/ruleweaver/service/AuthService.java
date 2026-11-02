package com.ruleweaver.service;

import com.ruleweaver.repository.UserRepository;
import org.bson.Document;
import org.bson.types.ObjectId;

import java.util.Date;

/** Creates accounts, checks sign-ins and saves data inside each account. */
public class AuthService {

    /** A problem the person can fix (wrong password, email already used, ...). */
    public static final class AuthException extends RuntimeException {
        public AuthException(String message) {
            super(message);
        }
    }

    /** The signed-in account. Never contains the password. */
    public record AuthUser(String id, String name, String email) {
    }

    private final UserRepository users;

    public AuthService(UserRepository users) {
        this.users = users;
    }

    public AuthUser register(String name, String email, String password) {
        String problem = CredentialUtil.validate(true, name, email, password);
        if (problem != null) {
            throw new AuthException(problem);
        }
        String normalizedEmail = CredentialUtil.normalizeEmail(email);
        Date now = new Date();
        Document user = new Document("name", name.trim())
                .append("email", normalizedEmail)
                .append("passwordHash", CredentialUtil.hashPassword(password))
                .append("createdAt", now)
                .append("lastLogin", now)
                .append("loginCount", 1)
                .append("data", new Document());

        if (!users.insertIfAbsent(user)) {
            throw new AuthException("An account with this email already exists.");
        }
        return toUser(user);
    }

    public AuthUser login(String email, String password) {
        String problem = CredentialUtil.validate(false, null, email, password);
        if (problem != null) {
            throw new AuthException(problem);
        }
        Document user = users.findByEmail(CredentialUtil.normalizeEmail(email));
        if (user == null || !CredentialUtil.verifyPassword(password, user.getString("passwordHash"))) {
            throw new AuthException("Wrong email or password.");
        }
        users.recordLogin(user.getObjectId("_id"));
        return toUser(user);
    }

    /** Data saved in this account (settings, preferences, anything the app wants to keep per user). */
    public Document loadAccountData(AuthUser user) {
        return users.findData(new ObjectId(user.id()));
    }

    public void saveAccountData(AuthUser user, Document values) {
        users.mergeData(new ObjectId(user.id()), values);
    }

    private static AuthUser toUser(Document user) {
        return new AuthUser(user.getObjectId("_id").toHexString(), user.getString("name"), user.getString("email"));
    }
}
