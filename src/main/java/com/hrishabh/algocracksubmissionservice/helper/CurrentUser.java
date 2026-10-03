package com.hrishabh.algocracksubmissionservice.helper;

import com.hrishabh.algocracksubmissionservice.exception.ValidationException;

/**
 * Caller identity from {@code X-User-Id} set by the API gateway from a validated JWT.
 */
public final class CurrentUser {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLE_HEADER = "X-User-Role";
    public static final String ADMIN_ROLE = "ADMIN";
    public static final String INTERNAL_CALL_HEADER = "X-Internal-Call";

    private static final int MAX_USER_ID_LENGTH = 255;

    private CurrentUser() {
    }

    public static String require(String headerValue) {
        if (headerValue == null || headerValue.isBlank() || headerValue.length() > MAX_USER_ID_LENGTH) {
            throw new ValidationException("Authentication required");
        }
        return headerValue.trim();
    }

    public static String requireAdmin(String userIdHeader, String roleHeader) {
        String userId = require(userIdHeader);
        if (roleHeader == null || !ADMIN_ROLE.equalsIgnoreCase(roleHeader.trim())) {
            throw new ValidationException("Admin role required");
        }
        return userId;
    }

    public static void requireSelf(String pathUserId, String authenticatedUserId) {
        if (!require(authenticatedUserId).equals(require(pathUserId))) {
            throw new ValidationException("Access denied");
        }
    }

    /** Trusted internal service call (header must not originate from the public gateway client). */
    public static boolean isInternalCall(String internalHeader) {
        return internalHeader != null && "true".equalsIgnoreCase(internalHeader.trim());
    }

    public static void requireSelfOrInternal(
            String pathUserId, String authenticatedUserId, String internalHeader) {
        require(pathUserId);
        if (isInternalCall(internalHeader)) {
            return;
        }
        requireSelf(pathUserId, authenticatedUserId);
    }
}
