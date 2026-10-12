package ro.mathlms.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Linking a parent on real Postgres + Hibernate. Regression (found by the E2E suite, 2026-10-12): since linking runs in
 * one transaction (F1 journal), re-linking the parent a student already has handed User.linkParent the lazy proxy
 * already in the persistence context, whose fields read null - so it was refused with a 409. Mocks cannot see this.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class LinkParentIntegrationTest {

    @Autowired private AdminUserService service;
    @Autowired private UserRepository users;

    @Test
    @WithMockUser(username = "prof@scoala.ro", roles = "ADMIN")
    void theSameParentCanBeLinkedAgainAndAnotherOneCanReplaceIt() {
        String stamp = String.valueOf(System.nanoTime());
        User mother = users.save(new User("mama-" + stamp + "@scoala.ro", "Mama", Role.PARENT));
        User father = users.save(new User("tata-" + stamp + "@scoala.ro", "Tata", Role.PARENT));
        User student = users.save(new User("elev-" + stamp + "@scoala.ro", "Ana", Role.STUDENT));

        service.linkParent(student.getId(), mother.getId());
        service.linkParent(student.getId(), mother.getId()); // was a 409
        assertThat(users.findByIdAndParentId(student.getId(), mother.getId())).isPresent();

        service.linkParent(student.getId(), father.getId());
        assertThat(users.findByIdAndParentId(student.getId(), father.getId())).isPresent();
    }
}
