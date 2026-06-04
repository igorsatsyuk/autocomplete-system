package lt.satsyuk.search.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchStatTest {

    @Test
    void defaultConstructorAndSettersPopulateFields() {
        SearchStat stat = new SearchStat();

        stat.setQuery("java");
        stat.setFrequency(5L);

        assertThat(stat.getQuery()).isEqualTo("java");
        assertThat(stat.getFrequency()).isEqualTo(5L);
    }

    @Test
    void allArgsConstructorInitializesFields() {
        SearchStat stat = new SearchStat("spring", 3L);

        assertThat(stat.getQuery()).isEqualTo("spring");
        assertThat(stat.getFrequency()).isEqualTo(3L);
    }
}

