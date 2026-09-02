package ro.mathlms.quiz;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import ro.mathlms.quiz.QuizDtos.ItemDto;
import ro.mathlms.quiz.QuizDtos.QuizSummaryDto;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QuizAdminControllerTest {

    private final QuizAdminService service = mock(QuizAdminService.class);
    private final QuizAdminController controller = new QuizAdminController(service);

    @Test
    void listMapsToSummaries() {
        when(service.listQuizzes()).thenReturn(List.of(new Quiz("Simulare EN", "d")));

        List<QuizSummaryDto> result = controller.list();

        assertThat(result).singleElement().satisfies(dto -> {
            assertThat(dto.title()).isEqualTo("Simulare EN");
            assertThat(dto.status()).isEqualTo(QuizStatus.DRAFT);
        });
    }

    @Test
    void createReturns201() {
        when(service.createQuiz("Simulare EN", "d")).thenReturn(new Quiz("Simulare EN", "d"));

        ResponseEntity<QuizSummaryDto> response =
                controller.create(new QuizRequest("Simulare EN", "d"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().title()).isEqualTo("Simulare EN");
    }

    @Test
    void addItemReturns201AndDelegates() {
        ItemRequest request = new ItemRequest(QuizItemType.OPEN, 1, "x", 10, null, null);
        ItemDto dto = new ItemDto(1L, 1, QuizItemType.OPEN, "x", 10, null, List.of());
        when(service.addItem(3L, request)).thenReturn(dto);

        ResponseEntity<ItemDto> response = controller.addItem(3L, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(dto);
    }

    @Test
    void deleteItemReturns204() {
        ResponseEntity<Void> response = controller.deleteItem(7L);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(service).deleteItem(7L);
    }
}
