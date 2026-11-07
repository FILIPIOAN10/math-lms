package ro.mathlms.publisher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import ro.mathlms.TestcontainersConfiguration;
import ro.mathlms.user.Role;
import ro.mathlms.user.User;
import ro.mathlms.user.UserRepository;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Editură → Carte → Teste: a publisher has books, a book holds tests. A test in a book belongs to the book's publisher;
 * deleting a book or a publisher never deletes a test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PublisherBookIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private UserRepository userRepository;

    private User admin;
    private String stamp;

    @BeforeEach
    void setUp() {
        stamp = String.valueOf(System.nanoTime());
        admin = userRepository.save(new User("admin-" + stamp + "@scoala.ro", "Admin", Role.ADMIN));
    }

    private RequestPostProcessor asAdmin() {
        return user(admin.getEmail()).roles("ADMIN");
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private ResultActions send(MockHttpServletRequestBuilder request, Object body, RequestPostProcessor who) throws Exception {
        return mockMvc.perform(request.with(who).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(body)));
    }

    private long publisher(String name) throws Exception {
        return json(send(post("/api/admin/publishers"), Map.of("name", name + " " + stamp), asAdmin())
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private long book(long publisherId, String title) throws Exception {
        return json(send(post("/api/admin/publishers/" + publisherId + "/books"),
                Map.of("title", title, "description", "Teste de antrenament"), asAdmin())
                .andExpect(status().isCreated())).get("id").asLong();
    }

    private JsonNode publisherJson(long id) throws Exception {
        return json(mockMvc.perform(get("/api/admin/publishers/" + id).with(asAdmin())).andExpect(status().isOk()));
    }

    private Map<String, Object> quiz(String title, Long publisherId, Long bookId) {
        Map<String, Object> body = new HashMap<>();
        body.put("title", title + " " + stamp);
        body.put("publisherId", publisherId);
        body.put("bookId", bookId);
        return body;
    }

    private JsonNode quizRow(long quizId) throws Exception {
        for (JsonNode q : json(mockMvc.perform(get("/api/admin/quizzes").with(asAdmin())).andExpect(status().isOk()))) {
            if (q.get("id").asLong() == quizId) {
                return q;
            }
        }
        return null;
    }

    @Test
    void aPublisherHasBooksEachWithItsTests() throws Exception {
        long artKlett = publisher("Art Klett");
        long culegere = book(artKlett, "Culegere EN 2026");
        book(artKlett, "Teste de antrenament");

        JsonNode detail = publisherJson(artKlett);
        assertThat(detail.get("name").asString()).isEqualTo("Art Klett " + stamp);
        JsonNode books = detail.get("books");
        assertThat(books).hasSize(2);
        assertThat(books.get(0).get("title").asString()).isEqualTo("Culegere EN 2026"); // alphabetical
        assertThat(books.get(0).get("description").asString()).isEqualTo("Teste de antrenament");
        assertThat(books.get(0).get("quizCount").asLong()).isZero();

        // A test made in the book: its publisher follows from the book.
        JsonNode test = json(send(post("/api/admin/quizzes"), quiz("Testul 1", null, culegere), asAdmin())
                .andExpect(status().isCreated()));
        assertThat(test.get("bookId").asLong()).isEqualTo(culegere);
        assertThat(test.get("bookTitle").asString()).isEqualTo("Culegere EN 2026");
        assertThat(test.get("publisherId").asLong()).isEqualTo(artKlett);
        assertThat(publisherJson(artKlett).get("books").get(0).get("quizCount").asLong()).isEqualTo(1);
        assertThat(quizRow(test.get("id").asLong()).get("bookTitle").asString()).isEqualTo("Culegere EN 2026");

        // The list for the test dialog carries each publisher's books.
        JsonNode listed = null;
        for (JsonNode p : json(mockMvc.perform(get("/api/admin/publishers").with(asAdmin())).andExpect(status().isOk()))) {
            if (p.get("id").asLong() == artKlett) {
                listed = p;
            }
        }
        assertThat(listed).isNotNull();
        assertThat(listed.get("books")).hasSize(2);
    }

    @Test
    void aBookTitleIsRequiredAndUniqueWithinItsPublisher() throws Exception {
        long one = publisher("Intuitext");
        long other = publisher("Litera");
        book(one, "Culegere");
        String message = send(post("/api/admin/publishers/" + one + "/books"), Map.of("title", "CULEGERE"), asAdmin())
                .andExpect(status().isConflict()).andReturn().getResponse().getContentAsString();
        assertThat(message).contains("Există deja");
        book(other, "Culegere"); // another publisher may have a book of the same name
        send(post("/api/admin/publishers/" + one + "/books"), Map.of("title", " "), asAdmin()).andExpect(status().isBadRequest());

        long renamed = book(one, "Auxiliar");
        send(put("/api/admin/publisher-books/" + renamed), Map.of("title", "Auxiliar EN"), asAdmin()).andExpect(status().isOk());
        assertThat(publisherJson(one).get("books").get(0).get("title").asString()).isEqualTo("Auxiliar EN");
    }

    @Test
    void aBookOfAnotherPublisherIsRefused() throws Exception {
        long one = publisher("CD Press");
        long other = publisher("Paralela 45");
        long otherBook = book(other, "Culegere");
        send(post("/api/admin/quizzes"), quiz("T", one, otherBook), asAdmin()).andExpect(status().isBadRequest());
        send(post("/api/admin/quizzes"), quiz("T", null, 999_999_999L), asAdmin()).andExpect(status().isNotFound());
    }

    @Test
    void deletingABookOrAPublisherKeepsItsTests() throws Exception {
        long artKlett = publisher("Art Klett");
        long culegere = book(artKlett, "Culegere");
        long first = json(send(post("/api/admin/quizzes"), quiz("Testul 1", null, culegere), asAdmin())
                .andExpect(status().isCreated())).get("id").asLong();

        mockMvc.perform(delete("/api/admin/publisher-books/" + culegere).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        JsonNode afterBook = quizRow(first);
        assertThat(afterBook).isNotNull();
        assertThat(afterBook.get("bookId").isNull()).isTrue();
        assertThat(afterBook.get("publisherId").asLong()).as("still that publisher's test").isEqualTo(artKlett);

        long again = book(artKlett, "Altă culegere");
        long second = json(send(post("/api/admin/quizzes"), quiz("Testul 2", null, again), asAdmin())
                .andExpect(status().isCreated())).get("id").asLong();
        mockMvc.perform(delete("/api/admin/publishers/" + artKlett).with(asAdmin()).with(csrf()))
                .andExpect(status().isNoContent());
        JsonNode afterPublisher = quizRow(second);
        assertThat(afterPublisher).isNotNull();
        assertThat(afterPublisher.get("bookId").isNull()).isTrue();
        assertThat(afterPublisher.get("publisherId").isNull()).isTrue();
    }

    @Test
    void teachersReadButDoNotManageBooks() throws Exception {
        long id = publisher("Booklet");
        RequestPostProcessor teacher = user("prof@scoala.ro").roles("TEACHER");
        mockMvc.perform(get("/api/admin/publishers/" + id).with(teacher)).andExpect(status().isOk());
        send(post("/api/admin/publishers/" + id + "/books"), Map.of("title", "Culegere"), teacher)
                .andExpect(status().isForbidden());
    }
}
