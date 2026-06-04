package lt.satsyuk.autocomplete.model;

public record AutocompleteEntry(
        String query,
        double score
) {}