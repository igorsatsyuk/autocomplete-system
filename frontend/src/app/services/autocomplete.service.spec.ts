import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { AutocompleteService, AutocompleteEntry } from './autocomplete.service';

describe('AutocompleteService', () => {
  let service: AutocompleteService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [AutocompleteService]
    });
    service = TestBed.inject(AutocompleteService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  describe('fetchSuggestions()', () => {
    it('returns empty array for empty query', (done) => {
      service.fetchSuggestions('').subscribe(result => {
        expect(result).toEqual([]);
        done();
      });
      http.expectNone('/api/complete');
    });

    it('returns empty array for whitespace-only query', (done) => {
      service.fetchSuggestions('   ').subscribe(result => {
        expect(result).toEqual([]);
        done();
      });
      http.expectNone('/api/complete');
    });

    it('calls GET /api/complete with q and limit params', () => {
      const mockEntries: AutocompleteEntry[] = [
        { query: 'java', score: 5 },
        { query: 'javascript', score: 3 }
      ];

      service.fetchSuggestions('ja').subscribe(entries => {
        expect(entries).toEqual(mockEntries);
      });

      const req = http.expectOne(r =>
        r.url === '/api/complete' &&
        r.params.get('q') === 'ja' &&
        r.params.get('limit') === '10'
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockEntries);
    });

    it('propagates HTTP errors', (done) => {
      service.fetchSuggestions('err').subscribe({
        error: (e) => {
          expect(e.status).toBe(500);
          done();
        }
      });

      const req = http.expectOne(r => r.url === '/api/complete');
      req.flush('Server error', { status: 500, statusText: 'Internal Server Error' });
    });
  });

  describe('sendSearch()', () => {
    it('calls GET /api/search with query param', () => {
      service.sendSearch('java').subscribe();

      const req = http.expectOne(r =>
        r.url === '/api/search' &&
        r.params.get('q') === 'java'
      );
      expect(req.request.method).toBe('GET');
      req.flush(null);
    });
  });

  describe('searchStream()', () => {
    it('emits suggestions after debounce', fakeAsync(() => {
      const mockEntries: AutocompleteEntry[] = [{ query: 'java', score: 10 }];
      const emitted: AutocompleteEntry[][] = [];

      service.searchStream().subscribe(entries => emitted.push(entries));

      service.nextQuery('j');
      service.nextQuery('ja');
      tick(150);

      const req = http.expectOne(r => r.url === '/api/complete' && r.params.get('q') === 'ja');
      req.flush(mockEntries);

      expect(emitted.length).toBe(1);
      expect(emitted[0]).toEqual(mockEntries);
    }));

    it('deduplicates identical consecutive queries', fakeAsync(() => {
      const mockEntries: AutocompleteEntry[] = [{ query: 'java', score: 10 }];
      const emitted: AutocompleteEntry[][] = [];

      service.searchStream().subscribe(entries => emitted.push(entries));

      service.nextQuery('java');
      tick(150);
      const req1 = http.expectOne(r => r.url === '/api/complete');
      req1.flush(mockEntries);

      service.nextQuery('java');
      tick(150);
      http.expectNone('/api/complete');

      expect(emitted.length).toBe(1);
    }));

    it('cancels previous request on new query (switchMap)', fakeAsync(() => {
      const results: AutocompleteEntry[][] = [];

      service.searchStream().subscribe(entries => results.push(entries));

      // Send both queries within the debounce window so only 'jav' fires
      service.nextQuery('ja');
      service.nextQuery('jav');
      tick(150);

      // Only one request for 'jav' should reach the HTTP layer
      const req = http.expectOne(r => r.params.get('q') === 'jav');
      req.flush([{ query: 'javascript', score: 8 }]);

      expect(results.length).toBe(1);
      expect(results[0][0].query).toBe('javascript');
    }));
  });
});

