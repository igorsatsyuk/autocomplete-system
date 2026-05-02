import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { debounceTime, distinctUntilChanged, switchMap } from 'rxjs/operators';
import { Observable, Subject } from 'rxjs';

export interface AutocompleteEntry {
  query: string;
  score: number;
}

@Injectable({
  providedIn: 'root'
})
export class AutocompleteService {

  private input$ = new Subject<string>();

  constructor(private http: HttpClient) {}

  searchStream(): Observable<AutocompleteEntry[]> {
    return this.input$.pipe(
      debounceTime(150),
      distinctUntilChanged(),
      switchMap(q => this.fetchSuggestions(q))
    );
  }

  nextQuery(q: string) {
    this.input$.next(q);
  }

  fetchSuggestions(q: string): Observable<AutocompleteEntry[]> {
    if (!q || !q.trim()) {
      return new Observable<AutocompleteEntry[]>(observer => {
        observer.next([]);
        observer.complete();
      });
    }
    return this.http.get<AutocompleteEntry[]>(`/api/complete`, {
      params: { q, limit: 10 }
    });
  }

  sendSearch(q: string): Observable<void> {
    return this.http.get<void>(`/api/search`, {
      params: { q }
    });
  }
}