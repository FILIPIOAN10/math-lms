package ro.mathlms.parent;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import ro.mathlms.parent.ParentDtos.ChildDto;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ParentControllerTest {

    private final ParentService service = mock(ParentService.class);
    private final ParentController controller = new ParentController(service);
    private final Authentication auth = new UsernamePasswordAuthenticationToken("maria@scoala.ro", null);

    @Test
    void childrenAreMappedToDtosForThePrincipal() {
        User child = new User("ana@scoala.ro", "Ana Pop", Role.STUDENT);
        ReflectionTestUtils.setField(child, "id", 2L);
        when(service.myChildren("maria@scoala.ro")).thenReturn(List.of(child));

        List<ChildDto> result = controller.children(auth);

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.id()).isEqualTo(2L);
            assertThat(dto.fullName()).isEqualTo("Ana Pop");
        });
    }

    @Test
    void attemptsAndResultAlwaysCarryThePrincipalsEmail() {
        controller.attempts(2L, auth);
        controller.result(2L, 50L, auth);
        controller.progress(2L, auth);

        verify(service).childAttempts("maria@scoala.ro", 2L);
        verify(service).childResult("maria@scoala.ro", 2L, 50L);
        verify(service).childProgress("maria@scoala.ro", 2L);
    }
}
