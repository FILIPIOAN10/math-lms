package ro.mathlms.invite;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.content.SchoolClass;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InviteLinkTest {

    private final Instant now = Instant.parse("2026-10-11T10:00:00Z");
    private final SchoolClass clasa = new SchoolClass("Clasa a 8-a", null);

    @Test
    void aClassLinkWorksForSevenDaysAndHasItsCap() {
        InviteLink link = InviteLink.forClass("code", clasa, 30, now);

        assertThat(link.getKind()).isEqualTo(InviteLinkKind.CLASS);
        assertThat(link.getKind().role()).isEqualTo(Role.STUDENT);
        assertThat(link.getExpiresAt()).isEqualTo(now.plus(InviteLink.TTL));
        assertThat(link.unavailableReason(now)).isEmpty();
        assertThat(link.unavailableReason(link.getExpiresAt())).hasValueSatisfying(r -> assertThat(r).contains("expirat"));
    }

    @Test
    void aParentLinkIsSingleUse() {
        InviteLink link = InviteLink.forParentOf("code", new User("ana@scoala.ro", "Ana Pop", Role.STUDENT), now);

        assertThat(link.getMaxUses()).isEqualTo(1);
        assertThat(link.getKind().role()).isEqualTo(Role.PARENT);
        ReflectionTestUtils.setField(link, "uses", 1);
        assertThat(link.unavailableReason(now)).hasValueSatisfying(r -> assertThat(r).contains("numărul maxim"));
    }

    @Test
    void revokingWinsOverEveryOtherReasonAndKeepsTheFirstMoment() {
        InviteLink link = InviteLink.forClass("code", clasa, 1, now);
        ReflectionTestUtils.setField(link, "uses", 1);

        link.revoke(now);
        link.revoke(now.plusSeconds(60));

        assertThat(link.getRevokedAt()).isEqualTo(now);
        assertThat(link.unavailableReason(now.plus(InviteLink.TTL))).hasValueSatisfying(r -> assertThat(r).contains("oprit"));
    }

    @Test
    void theCapStaysWithinBounds() {
        assertThatThrownBy(() -> InviteLink.forClass("code", clasa, 0, now)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InviteLink.forClass("code", clasa, InviteLink.MAX_USES + 1, now))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void storedCodesAreToldApartFromRoleInvitesByShape() {
        assertThat(InviteLinkService.isStoredCode("a".repeat(30) + "-_")).isTrue();
        assertThat(InviteLinkService.isStoredCode("header.payload.signature")).isFalse();
        assertThat(InviteLinkService.isStoredCode("scurt")).isFalse();
        assertThat(InviteLinkService.isStoredCode(null)).isFalse();
    }
}
