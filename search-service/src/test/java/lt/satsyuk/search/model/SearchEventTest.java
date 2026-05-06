package lt.satsyuk.search.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchEventTest {

    @Test
    void queryAccessorReturnsRecordValue() {
        SearchEvent event = new SearchEvent("java");

        assertThat(event.query()).isEqualTo("java");
    }
}

