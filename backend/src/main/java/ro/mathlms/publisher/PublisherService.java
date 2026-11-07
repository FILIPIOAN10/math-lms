package ro.mathlms.publisher;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import ro.mathlms.publisher.PublisherDtos.BookDto;
import ro.mathlms.publisher.PublisherDtos.BookRequest;
import ro.mathlms.publisher.PublisherDtos.PublisherDto;
import ro.mathlms.publisher.PublisherDtos.PublisherRequest;
import ro.mathlms.storage.FileService;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * "Edituri": the administrators' own list of publishers (none is built in), each with an optional description and
 * logo. Tests pick from it; deleting a publisher leaves its tests in place, without one.
 */
@Service
public class PublisherService {

    private static final Logger log = LoggerFactory.getLogger(PublisherService.class);

    /** The browser shrinks pictures before upload; this bounds what a direct call may store. */
    static final long MAX_LOGO_BYTES = 2L * 1024 * 1024;
    /** No SVG: it can carry script, and a logo does not need it. */
    private static final Set<String> TYPES = Set.of("image/png", "image/jpeg", "image/webp");

    private final PublisherRepository repository;
    private final PublisherBookRepository bookRepository;
    private final FileService fileService;
    private final Clock clock;
    private final String dir;

    public PublisherService(PublisherRepository repository, PublisherBookRepository bookRepository,
                            FileService fileService, Clock clock,
                            @Value("${app.storage.publisher-logos-dir}") String dir) {
        this.repository = repository;
        this.bookRepository = bookRepository;
        this.fileService = fileService;
        this.clock = clock;
        this.dir = dir;
    }

    /** Every publisher with its books (alphabetical): the Edituri page and the test's dropdowns read it at once. */
    @Transactional(readOnly = true)
    public List<PublisherDto> list() {
        Map<Long, Long> counts = counts();
        Map<Long, List<BookDto>> books = booksByPublisher();
        return repository.findAllByName().stream()
                .map(p -> PublisherDto.from(p, counts.getOrDefault(p.getId(), 0L), books.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    /** One publisher's page: itself and its books. */
    @Transactional(readOnly = true)
    public PublisherDto get(Long id) {
        Publisher publisher = find(id);
        return PublisherDto.from(publisher, counts().getOrDefault(id, 0L), booksByPublisher().getOrDefault(id, List.of()));
    }

    @Transactional
    public BookDto addBook(Long publisherId, BookRequest request) {
        PublisherBook book = new PublisherBook(find(publisherId), request.title(), request.description(), clock.instant());
        requireFreeTitle(publisherId, book.getTitle(), null);
        return BookDto.from(bookRepository.save(book), 0);
    }

    @Transactional
    public BookDto updateBook(Long bookId, BookRequest request) {
        PublisherBook book = findBook(bookId);
        book.rename(request.title(), request.description(), clock.instant());
        requireFreeTitle(book.getPublisher().getId(), book.getTitle(), bookId);
        return BookDto.from(book, bookCounts().getOrDefault(bookId, 0L));
    }

    /** The book goes; its tests stay, without a book but still with the publisher. */
    @Transactional
    public void deleteBook(Long bookId) {
        findBook(bookId);
        bookRepository.detachQuizzes(bookId);
        bookRepository.deleteById(bookId);
    }

    @Transactional
    public PublisherDto create(PublisherRequest request) {
        Publisher publisher = new Publisher(request.name(), request.description(), clock.instant());
        requireFreeName(publisher.getName(), null);
        return PublisherDto.from(repository.save(publisher), 0, List.of());
    }

    @Transactional
    public PublisherDto update(Long id, PublisherRequest request) {
        Publisher publisher = find(id);
        publisher.rename(request.name(), request.description(), clock.instant());
        requireFreeName(publisher.getName(), id);
        return PublisherDto.from(publisher, counts().getOrDefault(id, 0L), booksByPublisher().getOrDefault(id, List.of()));
    }

    /** The publisher goes; the tests that named it stay, without a publisher. Its logo file goes after the commit. */
    @Transactional
    public void delete(Long id) {
        String logo = find(id).getLogoKey();
        repository.detachQuizzes(id);
        repository.deleteById(id);
        deleteAfterCommit(logo);
    }

    @Transactional
    public void setLogo(Long id, MultipartFile file) {
        Publisher publisher = find(id);
        if (file == null || file.isEmpty() || file.getContentType() == null || !TYPES.contains(file.getContentType())) {
            throw new IllegalArgumentException("Sigla trebuie să fie o imagine PNG, JPG sau WEBP.");
        }
        if (file.getSize() > MAX_LOGO_BYTES) {
            throw new IllegalArgumentException("Sigla e prea mare (maxim 2 MB).");
        }
        String key;
        try {
            key = fileService.uploadImage(dir, file);
        } catch (IOException e) {
            throw new UncheckedIOException("Nu am putut salva sigla", e);
        }
        whenRolledBack(key);
        deleteAfterCommit(publisher.changeLogo(key, clock.instant()));
    }

    @Transactional
    public void removeLogo(Long id) {
        deleteAfterCommit(find(id).changeLogo(null, clock.instant()));
    }

    @Transactional(readOnly = true)
    public Resource loadLogo(Long id) {
        Publisher publisher = find(id);
        if (publisher.getLogoKey() == null) {
            throw new PublisherNotFoundException(id);
        }
        try {
            return fileService.loadImage(dir, publisher.getLogoKey());
        } catch (IOException e) {
            throw new PublisherNotFoundException(id);
        }
    }

    // --- helpers ---

    private Publisher find(Long id) {
        return repository.findById(id).orElseThrow(() -> new PublisherNotFoundException(id));
    }

    private PublisherBook findBook(Long id) {
        return bookRepository.findById(id).orElseThrow(() -> new PublisherNotFoundException("Cartea", id));
    }

    private Map<Long, List<BookDto>> booksByPublisher() {
        Map<Long, Long> counts = bookCounts();
        Map<Long, List<BookDto>> books = new HashMap<>();
        for (PublisherBook b : bookRepository.findAllByTitle()) {
            books.computeIfAbsent(b.getPublisher().getId(), k -> new ArrayList<>())
                    .add(BookDto.from(b, counts.getOrDefault(b.getId(), 0L)));
        }
        return books;
    }

    private Map<Long, Long> bookCounts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : bookRepository.quizCounts()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private void requireFreeTitle(Long publisherId, String title, Long exceptId) {
        if (bookRepository.titleTaken(publisherId, title, exceptId)) {
            throw new PublisherConflictException("Există deja cartea „" + title + "” la această editură.");
        }
    }

    private Map<Long, Long> counts() {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : repository.quizCounts()) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }

    private void requireFreeName(String name, Long exceptId) {
        if (repository.nameTaken(name, exceptId)) {
            throw new PublisherConflictException("Există deja editura „" + name + "”.");
        }
    }

    private void deleteAfterCommit(String key) {
        if (key == null) {
            return;
        }
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            delete(key);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                delete(key);
            }
        });
    }

    /** A logo stored for a change that was then rolled back is deleted with it. */
    private void whenRolledBack(String key) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    delete(key);
                }
            }
        });
    }

    private void delete(String key) {
        try {
            fileService.deleteImage(dir, key);
        } catch (IOException e) {
            log.warn("Could not delete publisher logo {}", key, e);
        }
    }
}
