package com.opencqrs.framework.esdb;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opencqrs.esdb.client.EsdbClient;
import com.opencqrs.esdb.client.Event;
import com.opencqrs.esdb.client.EventCandidate;
import com.opencqrs.esdb.client.Precondition;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EsdbTest
public class EsdbClientTest {

    private static final String TEST_SOURCE = "tag://test-execution";

    @Autowired
    private EsdbClient client;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void singleEventPublishedForPristineSubject() {
        String subject = randomSubject();

        Map<String, Object> data = objectMapper.convertValue(new BookAddedEvent("JRR Tolkien", "LOTR"), Map.class);

        List<Event> published = client.write(
                List.of(new EventCandidate(TEST_SOURCE, subject, "com.opencqrs.books-added.v1", data)),
                List.of(new Precondition.SubjectIsPristine(subject)));

        assertThat(published).singleElement().satisfies(e -> {
            assertThat(e.source()).isEqualTo(TEST_SOURCE);
            assertThat(e.subject()).isEqualTo(subject);
            assertThat(e.type()).isEqualTo("com.opencqrs.books-added.v1");
            assertThat(e.specVersion()).isEqualTo("1.0");
            assertThat(e.dataContentType()).isEqualTo("application/json");
            assertThat(e.id()).isNotBlank();
            assertThat(e.time()).isBeforeOrEqualTo(Instant.now());
            assertThat(e.hash()).isNotBlank();
            assertThat(e.predecessorHash()).isNotBlank();
            assertThat(e.data()).isEqualTo(data);
        });
    }

    private String randomSubject() {
        return "/books/" + UUID.randomUUID();
    }

    public record BookAddedEvent(String author, String title) {}
}
