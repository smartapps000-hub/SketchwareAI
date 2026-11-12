package com.sketchware.ai.github;

/** v1.9.8: a GitHub call that failed, with a message for the user and GitHub's (or our) error code. */
public final class GitHubException extends Exception {
    public final String code;

    public GitHubException(String code, String message) {
        super(message);
        this.code = code;
    }
}
