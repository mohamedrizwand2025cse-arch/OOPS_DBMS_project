package com.ruleweaver.ui.auth;

import com.ruleweaver.service.AuthService;
import com.ruleweaver.service.AuthService.AuthUser;
import org.bson.Document;

import java.util.Optional;

/**
 * Remembers who is signed in while the app is open.
 * Any screen can call Session.saveData(...) to store data in that person's account.
 */
public final class Session {

    private static AuthUser current;
    private static AuthService service;

    private Session() {
    }

    public static synchronized void start(AuthUser user, AuthService authService) {
        current = user;
        service = authService;
    }

    public static synchronized Optional<AuthUser> current() {
        return Optional.ofNullable(current);
    }

    /** Saves values into the signed-in account (call from a background thread, not the UI thread). */
    public static synchronized void saveData(Document values) {
        if (current != null && service != null) {
            service.saveAccountData(current, values);
        }
    }

    /** Reads the signed-in account's saved data (call from a background thread). */
    public static synchronized Document loadData() {
        return current != null && service != null ? service.loadAccountData(current) : new Document();
    }

    public static synchronized void end() {
        current = null;
        service = null;
    }
}
