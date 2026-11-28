package ro.mathlms.auth;

/** The answer to the SPA's activity ping: how long a session may go unused before it closes itself. */
public record SessionActivityResponse(long idleTimeoutMinutes) {
}
