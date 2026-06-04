package lt.satsyuk.common.kafka;

public final class KafkaTopics {

    public static final String SEARCH_EVENTS = "search-events";
    public static final String SEARCH_STATS = "search-stats";
    public static final String DB_CHANGES_PREFIX = "db-changes";
    public static final String DB_CHANGES_SEARCH_STATS = "db-changes.public.search_stats";
    public static final String DB_CHANGES_SEARCH_STATS_PATTERN = "db-changes\\.public\\.search_stats";

    private KafkaTopics() {}
}