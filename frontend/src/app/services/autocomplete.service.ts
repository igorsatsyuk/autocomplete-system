import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { Observable, Subject, of } from 'rxjs';

export interface AutocompleteEntry {
  query: string;
  score: number;
}

@Injectable({
  providedIn: 'root'
})
export class AutocompleteService {

  private readonly input$ = new Subject<string>();

  constructor(private readonly http: HttpClient) {}

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
    const normalized = q?.trim().toLowerCase();
    if (!normalized) {
      return of([]);
    }
    return this.http.get<AutocompleteEntry[]>(`/api/complete`, {
      params: { q: normalized, limit: 10 }
    }).pipe(
      map(entries => [...entries].sort((a, b) => b.score - a.score))
    );
  }

  sendSearch(q: string): Observable<void> {
    return this.http.get<void>(`/api/search`, {
      params: { q }
    });
  }
}