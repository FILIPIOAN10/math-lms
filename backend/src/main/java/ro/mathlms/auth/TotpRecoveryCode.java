package ro.mathlms.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** One single-use recovery code for a lost phone; only its SHA-256 is kept (the code itself is shown once). */
@Entity
@Table(name = "totp_recovery_codes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TotpRecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    public TotpRecoveryCode(Long userId, String codeHash) {
        this.userId = userId;
        this.codeHash = codeHash;
    }

    public void use(Instant now) {
        this.usedAt = now;
    }
}
