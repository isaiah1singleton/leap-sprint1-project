import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ClientThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly stylesheetId = 'client-refactored-styles';

  enable(): void {
    if (this.document.getElementById(this.stylesheetId)) {
      return;
    }

    const stylesheet = this.document.createElement('link');
    stylesheet.id = this.stylesheetId;
    stylesheet.rel = 'stylesheet';
    stylesheet.href = new URL('styles-refactor.css', this.document.baseURI).href;
    this.document.head.appendChild(stylesheet);
  }

  disable(): void {
    this.document.getElementById(this.stylesheetId)?.remove();
  }
}