package lt.satsyuk.search.controller;

import lt.satsyuk.search.kafka.SearchEventProducer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

    @Mock
    private SearchEventProducer producer;

    @Test
    void delegatesQueryToProducer() {
        SearchController controller = new SearchController(producer);

        controller.search("java");

        verify(producer).sendSearchEvent("java");
    }
}

