import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { AppComponent } from './app.component';
import { AutocompleteService, AutocompleteEntry } from './services/autocomplete.service';
import { of, Subject } from 'rxjs';
import { By } from '@angular/platform-browser';

describe('AppComponent', () => {
  let fixture: ComponentFixture<AppComponent>;
  let component: AppComponent;
  let serviceSpy: jasmine.SpyObj<AutocompleteService>;
  let suggestions$: Subject<AutocompleteEntry[]>;

  beforeEach(async () => {
    suggestions$ = new Subject<AutocompleteEntry[]>();
    serviceSpy = jasmine.createSpyObj<AutocompleteService>(
      'AutocompleteService',
      ['searchStream', 'nextQuery', 'sendSearch', 'fetchSuggestions']
    );
    serviceSpy.searchStream.and.returnValue(suggestions$.asObservable());
    serviceSpy.sendSearch.and.returnValue(of(undefined as any));

    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [{ provide: AutocompleteService, useValue: serviceSpy }]
    }).compileComponents();

    fixture = TestBed.createComponent(AppComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create the component', () => {
    expect(component).toBeTruthy();
  });

  it('renders the title', () => {
    const h1: HTMLElement = fixture.nativeElement.querySelector('h1');
    expect(h1.textContent).toContain('Autocomplete Demo');
  });

  describe('onInputChange()', () => {
    it('updates query and sets showSuggestions=true for non-empty input', () => {
      component.onInputChange('java');
      expect(component.query).toBe('java');
      expect(component.showSuggestions).toBeTrue();
      expect(serviceSpy.nextQuery).toHaveBeenCalledWith('java');
    });

    it('sets showSuggestions=false for empty input', () => {
      component.onInputChange('');
      expect(component.showSuggestions).toBeFalse();
    });

    it('sets showSuggestions=false for whitespace-only input', () => {
      component.onInputChange('   ');
      expect(component.showSuggestions).toBeFalse();
      expect(serviceSpy.nextQuery).toHaveBeenCalledWith('   ');
    });
  });

  describe('onSelectSuggestion()', () => {
    it('sets query from suggestion and hides suggestions', () => {
      component.showSuggestions = true;
      component.onSelectSuggestion({ query: 'javascript', score: 5 });
      expect(component.query).toBe('javascript');
      expect(component.showSuggestions).toBeFalse();
    });
  });

  describe('onSubmit()', () => {
    it('calls sendSearch and hides suggestions', () => {
      component.query = 'angular';
      component.showSuggestions = true;
      component.onSubmit();
      expect(serviceSpy.sendSearch).toHaveBeenCalledWith('angular');
      expect(component.showSuggestions).toBeFalse();
    });

    it('does nothing for blank query', () => {
      component.query = '   ';
      component.onSubmit();
      expect(serviceSpy.sendSearch).not.toHaveBeenCalled();
    });

    it('does nothing for empty query', () => {
      component.query = '';
      component.onSubmit();
      expect(serviceSpy.sendSearch).not.toHaveBeenCalled();
    });
  });

  describe('suggestions list rendering', () => {
    it('shows suggestion items when showSuggestions is true and suggestions$ emits', fakeAsync(() => {
      // Trigger showSuggestions=true and run CD so *ngIf renders and async pipe subscribes
      component.onInputChange('ja');
      fixture.detectChanges();

      // Now emit through the stream
      suggestions$.next([
        { query: 'java', score: 10 },
        { query: 'javascript', score: 8 }
      ]);
      fixture.detectChanges();

      const items = fixture.debugElement.queryAll(By.css('ul.suggestions li'));
      expect(items.length).toBe(2);
      expect(items[0].nativeElement.textContent.trim()).toBe('java');
      expect(items[1].nativeElement.textContent.trim()).toBe('javascript');
    }));

    it('hides suggestion list when showSuggestions is false', () => {
      component.showSuggestions = false;
      fixture.detectChanges();
      const list = fixture.nativeElement.querySelector('ul.suggestions');
      expect(list).toBeNull();
    });

    it('clicking suggestion selects it', fakeAsync(() => {
      component.onInputChange('ja');
      fixture.detectChanges();

      suggestions$.next([{ query: 'java', score: 5 }]);
      fixture.detectChanges();

      const itemButton: HTMLButtonElement = fixture.nativeElement.querySelector('ul.suggestions li button');
      itemButton.click();
      fixture.detectChanges();

      expect(component.query).toBe('java');
      expect(component.showSuggestions).toBeFalse();
    }));
  });
});

