package lt.satsyuk.search.model;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SearchStatRepository extends JpaRepository<SearchStat, String> {
}