package ro.mathlms.grading;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ro.mathlms.grading.CommentSnippetDtos.SnippetDto;
import ro.mathlms.quiz.InvalidQuizException;
import ro.mathlms.quiz.QuizNotFoundException;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;

import java.time.Clock;
import java.util.List;

/**
 * A teacher's saved grading comments: listed most-used first, saved on purpose (never learned from what was typed),
 * counted each time one is used. Each teacher sees and changes only their own.
 */
@Service
public class CommentSnippetService {

    /** Shown as chips: more than this is a list nobody reads. */
    static final int SHOWN = 30;
    /** Stored per teacher. */
    static final int MAX_PER_TEACHER = 100;

    private final CommentSnippetRepository repository;
    private final UserRepository userRepository;
    private final Clock clock;

    public CommentSnippetService(CommentSnippetRepository repository, UserRepository userRepository, Clock clock) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<SnippetDto> list(String email) {
        return repository.findForTeacher(teacherId(email), Limit.of(SHOWN)).stream().map(SnippetDto::from).toList();
    }

    /** Saves a comment; one the teacher already has (whatever the case) is returned as it is - saving twice is harmless. */
    @Transactional
    public SnippetDto create(String email, String text) {
        String cleaned = clean(text);
        if (cleaned == null) {
            throw new InvalidQuizException("Scrie comentariul înainte să-l salvezi.");
        }
        Long teacherId = teacherId(email);
        return repository.findSame(teacherId, cleaned).map(SnippetDto::from).orElseGet(() -> {
            if (repository.countByTeacherId(teacherId) >= MAX_PER_TEACHER) {
                throw new SnippetLimitException("Ai deja " + MAX_PER_TEACHER
                        + " de comentarii salvate. Șterge din cele pe care nu le mai folosești.");
            }
            return SnippetDto.from(repository.save(new CommentSnippet(teacherId, cleaned, clock.instant())));
        });
    }

    @Transactional
    public void delete(String email, Long id) {
        if (repository.deleteOwn(id, teacherId(email)) == 0) {
            throw new QuizNotFoundException("CommentSnippet", id);
        }
    }

    /** The teacher used it once more: it moves up the list. */
    @Transactional
    public void recordUse(String email, Long id) {
        if (repository.incrementUse(id, teacherId(email), clock.instant()) == 0) {
            throw new QuizNotFoundException("CommentSnippet", id);
        }
    }

    /** The same rule as a grading comment: no surrounding spaces, inner runs of spaces kept, blank = nothing. */
    static String clean(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String stripped = text.strip();
        if (stripped.length() > CommentSnippet.MAX_TEXT) {
            throw new InvalidQuizException("Un comentariu salvat are cel mult " + CommentSnippet.MAX_TEXT + " de caractere.");
        }
        return stripped;
    }

    private Long teacherId(String email) {
        return userRepository.findByEmail(email).map(User::getId)
                .orElseThrow(() -> new QuizNotFoundException("User", null));
    }
}
