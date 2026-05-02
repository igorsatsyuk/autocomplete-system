package lt.satsyuk.search.controller;

import lt.satsyuk.search.kafka.SearchEventProducer;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SearchController {

    private final SearchEventProducer producer;

    public SearchController(SearchEventProducer producer) {
        this.producer = producer;
    }

    @GetMapping("/search")
    public void search(@RequestParam("q") String q) {
        producer.sendSearchEvent(q);
    }
}