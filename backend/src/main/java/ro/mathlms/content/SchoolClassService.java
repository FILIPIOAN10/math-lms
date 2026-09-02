package ro.mathlms.content;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** CRUD for school classes. Writes are transactional; uniqueness of name is checked up front. */
@Service
public class SchoolClassService {

    private final SchoolClassRepository repository;

    public SchoolClassService(SchoolClassRepository repository) {
        this.repository = repository;
    }

    public List<SchoolClass> list() {
        return repository.findAll(Sort.by("name"));
    }

    public SchoolClass get(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ContentNotFoundException("SchoolClass", id));
    }

    @Transactional
    public SchoolClass create(String name, String description) {
        if (repository.existsByName(name)) {
            throw new DuplicateContentException("A class named '" + name + "' already exists");
        }
        return repository.save(new SchoolClass(name, description));
    }

    @Transactional
    public SchoolClass update(Long id, String name, String description) {
        SchoolClass schoolClass = get(id);
        if (!schoolClass.getName().equals(name) && repository.existsByName(name)) {
            throw new DuplicateContentException("A class named '" + name + "' already exists");
        }
        schoolClass.update(name, description);
        return repository.save(schoolClass);
    }

    @Transactional
    public void delete(Long id) {
        if (!repository.existsById(id)) {
            throw new ContentNotFoundException("SchoolClass", id);
        }
        repository.deleteById(id);
    }
}
