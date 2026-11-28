package ro.mathlms.studyplan;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import ro.mathlms.TestImages;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** V84 on real Postgres: one exam date for everyone, targets on the 1 - 10 scale, one snapshot a day. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class StudyPlanSchemaTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer(TestImages.POSTGRES);

    @Autowired
    private JdbcTemplate jdbc;

    private long newStudent() {
        return jdbc.queryForObject("""
                insert into users (email, full_name, role, status)
                values (?, 'Ana', 'STUDENT', 'ACTIVE') returning id""",
                Long.class, "elev-" + System.nanoTime() + "@scoala.ro");
    }

    @Test
    void thereIsExactlyOneExamDateRowAndItStartsEmpty() {
        assertThat(jdbc.queryForList("select exam_date from en_exam_settings")).hasSize(1)
                .first().satisfies(row -> assertThat(row.get("exam_date")).isNull());
        assertThatThrownBy(() -> jdbc.update("insert into en_exam_settings (id) values (2)"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void aTargetIsAGradeFromOneToTen() {
        long student = newStudent();
        String insert = "insert into student_en_goals (student_id, target_grade, set_on, updated_at) values (?, ?, ?, now())";

        jdbc.update(insert, student, 9.25, LocalDate.now());
        assertThat(jdbc.queryForObject("select target_grade from student_en_goals where student_id = ?",
                java.math.BigDecimal.class, student)).isEqualByComparingTo("9.25");
        // Last: a failed statement aborts the test's transaction in Postgres.
        assertThatThrownBy(() -> jdbc.update(insert, newStudent(), 10.5, LocalDate.now()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void oneSnapshotPerStudentPerDay() {
        long student = newStudent();
        String insert = "insert into en_prediction_snapshots (student_id, day, grade, low, high) values (?, ?, 7.4, 7.1, 7.7)";
        jdbc.update(insert, student, LocalDate.of(2026, 11, 28));

        assertThatThrownBy(() -> jdbc.update(insert, student, LocalDate.of(2026, 11, 28)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingAChapterTakesItsPinsWithIt() {
        long student = newStudent();
        long chapter = jdbc.queryForObject("""
                insert into en_chapters (code, title, area, position) values ('Z9', 'De probă', 'ALGEBRA', 999)
                returning id""", Long.class);
        jdbc.update("insert into study_plan_pins (student_id, en_chapter_id, created_at) values (?, ?, now())",
                student, chapter);

        jdbc.update("delete from en_chapters where id = ?", chapter);

        assertThat(jdbc.queryForObject("select count(*) from study_plan_pins where student_id = ?", Integer.class, student))
                .isZero();
    }
}
