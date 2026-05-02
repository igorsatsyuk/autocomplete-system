package lt.satsyuk.search.model;

import jakarta.persistence.*;

@Entity
@Table(name = "search_stats")
public class SearchStat {

    @Id
    @Column(nullable = false)
    private String query;

    @Column(name = "frequency", nullable = false)
    private Long frequency;

    public SearchStat() {}

    public SearchStat(String query, Long frequency) {
        this.query = query;
        this.frequency = frequency;
    }

    public String getQuery() {
        return query;
    }

    public Long getFrequency() {
        return frequency;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public void setFrequency(Long frequency) {
        this.frequency = frequency;
    }
}