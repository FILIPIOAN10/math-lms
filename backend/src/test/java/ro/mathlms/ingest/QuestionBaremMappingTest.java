package ro.mathlms.ingest;

import org.junit.jupiter.api.Test;
import ro.mathlms.ingest.BaremDtos.QuestionBaremDto;
import ro.mathlms.ingest.BaremDtos.QuestionPart;
import ro.mathlms.ingest.BaremDtos.QuestionPartBarem;
import ro.mathlms.ingest.BaremDtos.QuestionRequest;
import ro.mathlms.ingest.BaremReader.Entry;
import ro.mathlms.ingest.BaremReader.OutlineItem;
import ro.mathlms.ingest.BaremReader.PartBarem;
import ro.mathlms.quiz.QuizItemType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** One question of the review, told to the reader and read back, without a database. */
class QuestionBaremMappingTest {

    private static final QuestionRequest PROBLEM = new QuestionRequest("Într-o pungă sunt bomboane.", 5,
            List.of(new QuestionPart("Verificați 55.", 2), new QuestionPart("Cel mai mic număr.", 3)));

    @Test
    void theQuestionGoesToTheReaderAsOneOpenItemWithLetteredRequirements() {
        OutlineItem item = BaremService.outlineOf(PROBLEM);

        assertThat(item.label()).isEqualTo("1");
        assertThat(item.type()).isEqualTo(QuizItemType.OPEN);
        assertThat(item.points()).isEqualTo(5);
        assertThat(item.parts()).extracting(BaremReader.OutlinePart::label).containsExactly("a", "b");
        assertThat(BaremReading.outline(List.of(item))).contains("1 | cu rezolvare | 5p", "a) (2p) Verificați 55.");
    }

    @Test
    void whateverNumberThePhotoPrintsTheRequirementsAreMatchedByLetterInOrder() {
        // The barem page numbers the problem "1." of Subiectul III; only one question was asked.
        Entry read = new Entry("III.1", null, null,
                List.of(new PartBarem("B", "x = 27 ... 3p"), new PartBarem("c", "nu există")), List.of("Citit greu."));

        QuestionBaremDto barem = BaremService.questionBarem(PROBLEM, List.of(read));

        assertThat(barem.parts()).containsExactly(new QuestionPartBarem("a", null), new QuestionPartBarem("b", "x = 27 ... 3p"));
        assertThat(barem.warnings()).containsExactly("Citit greu.");
    }

    @Test
    void nothingReadIsAnEmptyBarem() {
        assertThat(BaremService.questionBarem(PROBLEM, List.of()).parts()).isEmpty();
    }
}
