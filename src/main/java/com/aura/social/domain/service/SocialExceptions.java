package com.aura.social.domain.service;

/** The social service's domain errors; GlobalExceptionHandler maps each to a status and code. */
public final class SocialExceptions {

    private SocialExceptions() {
    }

    /** Also thrown for a user who blocked the caller — whether they exist is none of the caller's business. */
    public static class UserNotFound extends RuntimeException {
        public UserNotFound() {
            super("No such user");
        }
    }

    /** The action makes no sense in the current state (e.g. befriending someone you blocked). */
    public static class Conflict extends RuntimeException {
        private final String code;

        public Conflict(String code, String message) {
            super(message);
            this.code = code;
        }

        public String code() {
            return code;
        }
    }

    public static class LimitExceeded extends RuntimeException {
        public LimitExceeded(String message) {
            super(message);
        }
    }
}
