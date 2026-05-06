import { Component } from '@angular/core';
import { AutocompleteService, AutocompleteEntry } from './services/autocomplete.service';
import { Observable } from 'rxjs';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {

  query = '';
  suggestions$: Observable<AutocompleteEntry[]>;
  showSuggestions = false;

  constructor(private readonly autocomplete: AutocompleteService) {
    this.suggestions$ = this.autocomplete.searchStream();
  }


  onInputChange(value: string) {
    this.query = value;
    this.showSuggestions = value.trim().length > 0;
    this.autocomplete.nextQuery(value);
  }

  onSelectSuggestion(entry: AutocompleteEntry) {
    this.query = entry.query;
    this.showSuggestions = false;
  }

  onSubmit() {
    const q = this.query.trim();
    if (!q) {
      return;
    }
    this.autocomplete.sendSearch(q).subscribe();
    this.showSuggestions = false;
  }
}