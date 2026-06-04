package lt.satsyuk.autocomplete.controller;

import lt.satsyuk.autocomplete.model.AutocompleteEntry;
import lt.satsyuk.autocomplete.service.AutocompleteQueryService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api")
public class AutocompleteController {

    private final AutocompleteQueryService queryService;

    public AutocompleteController(AutocompleteQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/complete")
    public Flux<AutocompleteEntry> complete(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return queryService.suggest(q, limit);
    }
}