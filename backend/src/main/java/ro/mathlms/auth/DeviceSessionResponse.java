package ro.mathlms.auth;

/**
 * One active login session, as shown to the user for device management. The public handle is an
 * opaque {@code sessionId} — never the refresh token itself — so listing sessions cannot leak a
 * usable credential. {@code current} marks the session making the request.
 */
public record DeviceSessionResponse(
        String sessionId,
        String deviceInfo,
        String ipAddress,
        long createdAt,
        long lastUsedAt,
        boolean current
) {
}
