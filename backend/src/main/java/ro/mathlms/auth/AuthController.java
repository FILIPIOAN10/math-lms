package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final RegistrationService registrationService;
    private final EmailService emailService;
    private final VerificationTokenService verificationTokenService;
    private final InviteTokenService inviteTokenService;
    private final LoginService loginService;
    private final JwtCookieFactory jwtCookieFactory;
    private final PasswordResetService passwordResetService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(UserRepository userRepository,
                          RegistrationService registrationService,
                          EmailService emailService,
                          VerificationTokenService verificationTokenService,
                          InviteTokenService inviteTokenService,
                          LoginService loginService,
                          JwtCookieFactory jwtCookieFactory,
                          PasswordResetService passwordResetService,
                          RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.emailService = emailService;
        this.verificationTokenService = verificationTokenService;
        this.inviteTokenService = inviteTokenService;
        this.loginService = loginService;
        this.jwtCookieFactory = jwtCookieFactory;
        this.passwordResetService = passwordResetService;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping("/me")
    public ResponseEntity<UserDto> me(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.status(401).build();
        }
        return userRepository.findByEmail(authentication.getName())
                .map(UserDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(401).build());
    }

    /**
     * Registers a local account and emails a verification link. The role comes from
     * the signed invite token (minted by an admin), never from client input.
     */
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        Role requestedRole = inviteTokenService.verify(request.inviteToken());
        User user = registrationService.register(
                request.email(), request.fullName(), request.password(), requestedRole);
        String token = verificationTokenService.generate(user.getEmail(), TokenPurpose.VERIFY_EMAIL);
        emailService.sendVerificationEmail(user.getEmail(), token);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** Authenticates a local account and issues the JWT cookie. */
    @PostMapping("/login")
    public ResponseEntity<UserDto> login(@Valid @RequestBody LoginRequest request,
                                         HttpServletResponse response) {
        User user = loginService.authenticate(request.email(), request.password());
        issueSession(user, response);
        return ResponseEntity.ok(UserDto.from(user));
    }

    /**
     * Rotates the refresh-token session and issues a fresh access cookie. Uses only the refresh
     * cookie, so it works when the access token has already expired. An erased account is refused.
     */
    @PostMapping("/refresh")
    public ResponseEntity<UserDto> refresh(HttpServletRequest request, HttpServletResponse response) {
        RefreshTokenService.Rotation rotation =
                refreshTokenService.rotate(refreshTokenService.readRefreshCookie(request));
        User user = userRepository.findByEmail(rotation.email())
                .filter(u -> !u.isErased())
                .orElseThrow(() -> new InvalidRefreshTokenException("Account no longer active"));
        response.addCookie(jwtCookieFactory.create(user));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenService.refreshCookie(rotation.token()).toString());
        return ResponseEntity.ok(UserDto.from(user));
    }

    /** Active login sessions of the current user, for device management. */
    @GetMapping("/sessions")
    public List<DeviceSessionResponse> sessions(Authentication authentication, HttpServletRequest request) {
        return refreshTokenService.listSessions(
                authentication.getName(), refreshTokenService.readRefreshCookie(request));
    }

    /** Revokes one other session (remote logout of a device). */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(@PathVariable String sessionId, Authentication authentication) {
        refreshTokenService.revokeSession(authentication.getName(), sessionId);
        return ResponseEntity.noContent().build();
    }

    /** Logs the user out of every device and clears the current cookies. */
    @PostMapping("/logout-all")
    public ResponseEntity<Void> logoutAll(Authentication authentication, HttpServletResponse response) {
        refreshTokenService.revokeAll(authentication.getName());
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    /** Confirms the email carried by the token and moves the account to approval. */
    @GetMapping("/verify-email")
    public ResponseEntity<Void> verifyEmail(@RequestParam String token) {
        String email = verificationTokenService.verify(token, TokenPurpose.VERIFY_EMAIL);
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new JwtException("No account for this token"));
        user.verifyEmail();
        userRepository.save(user);
        return ResponseEntity.ok().build();
    }

    /** Always answers 200 so callers cannot probe which emails have accounts. */
    @PostMapping("/forgot-password")
    public ResponseEntity<Void> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        passwordResetService.requestReset(request.email());
        return ResponseEntity.ok().build();
    }

    /** Sets a new password using a valid reset token. */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        refreshTokenService.revokeByToken(refreshTokenService.readRefreshCookie(request));
        clearCookies(response);
        return ResponseEntity.noContent().build();
    }

    /** Issues the access cookie and opens a rotating refresh-token session. */
    private void issueSession(User user, HttpServletResponse response) {
        response.addCookie(jwtCookieFactory.create(user));
        String refreshToken = refreshTokenService.createSession(user.getEmail());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenService.refreshCookie(refreshToken).toString());
    }

    private void clearCookies(HttpServletResponse response) {
        Cookie access = new Cookie(JwtCookieSuccessHandler.COOKIE_NAME, "");
        access.setHttpOnly(true);
        access.setPath("/");
        access.setMaxAge(0);
        response.addCookie(access);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenService.cleanRefreshCookie().toString());
    }
}
