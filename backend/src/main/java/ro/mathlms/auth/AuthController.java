package ro.mathlms.auth;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

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

    public AuthController(UserRepository userRepository,
                          RegistrationService registrationService,
                          EmailService emailService,
                          VerificationTokenService verificationTokenService,
                          InviteTokenService inviteTokenService,
                          LoginService loginService,
                          JwtCookieFactory jwtCookieFactory,
                          PasswordResetService passwordResetService) {
        this.userRepository = userRepository;
        this.registrationService = registrationService;
        this.emailService = emailService;
        this.verificationTokenService = verificationTokenService;
        this.inviteTokenService = inviteTokenService;
        this.loginService = loginService;
        this.jwtCookieFactory = jwtCookieFactory;
        this.passwordResetService = passwordResetService;
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
        response.addCookie(jwtCookieFactory.create(user));
        return ResponseEntity.ok(UserDto.from(user));
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
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        Cookie cookie = new Cookie(JwtCookieSuccessHandler.COOKIE_NAME, "");
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }
}
