import { Component, ElementRef, OnInit, output, signal, viewChild } from '@angular/core';

import { environment } from '../../../environments/environment';

/**
 * Minimal shape of the part of Google Identity Services this uses. Declared
 * locally rather than pulling in @types/google.accounts: two methods is less
 * than the type package costs to keep in step.
 */
interface GoogleIdentityServices {
  accounts: {
    id: {
      initialize(config: { client_id: string; callback: (response: { credential: string }) => void }): void;
      renderButton(parent: HTMLElement, options: Record<string, unknown>): void;
    };
  };
}

declare global {
  interface Window {
    google?: GoogleIdentityServices;
  }
}

const GIS_SRC = 'https://accounts.google.com/gsi/client';

/**
 * Google's own sign-in button.
 *
 * It has to be Google's rather than one of ours: the account picker is their
 * popup, and rendering it is the only supported way to start the flow. What
 * comes back is an ID token, emitted here for a caller to send to the API -
 * this component never talks to our backend itself.
 *
 * The script is fetched on demand rather than sitting in index.html, so a
 * deployment with no client id configured never loads third-party code, and
 * neither does anyone who only ever visits the storefront.
 */
@Component({
  selector: 'app-google-sign-in-button',
  standalone: true,
  template: `
    <div #target class="flex justify-center"></div>
    @if (loadError()) {
      <p class="mt-2 text-center text-xs text-gray-400">{{ loadError() }}</p>
    }
  `,
})
export class GoogleSignInButton implements OnInit {
  /** The verified-by-Google ID token. Worthless on its own - the API decides what it means. */
  readonly credential = output<string>();

  readonly loadError = signal<string | null>(null);

  private readonly target = viewChild.required<ElementRef<HTMLDivElement>>('target');

  ngOnInit(): void {
    if (!environment.googleClientId) {
      return;
    }
    this.loadScript()
      .then(() => this.render())
      .catch(() => this.loadError.set('Không tải được đăng nhập Google'));
  }

  /** Resolves once window.google is usable. Safe to call more than once - the tag is only added the first time. */
  private loadScript(): Promise<void> {
    if (window.google) {
      return Promise.resolve();
    }
    const existing = document.querySelector<HTMLScriptElement>(`script[src="${GIS_SRC}"]`);
    if (existing) {
      return new Promise((resolve, reject) => {
        existing.addEventListener('load', () => resolve());
        existing.addEventListener('error', () => reject(new Error('gsi failed')));
      });
    }
    return new Promise((resolve, reject) => {
      const script = document.createElement('script');
      script.src = GIS_SRC;
      script.async = true;
      script.defer = true;
      script.onload = () => resolve();
      script.onerror = () => reject(new Error('gsi failed'));
      document.head.appendChild(script);
    });
  }

  private render(): void {
    const google = window.google;
    if (!google) {
      this.loadError.set('Không tải được đăng nhập Google');
      return;
    }
    google.accounts.id.initialize({
      client_id: environment.googleClientId,
      callback: (response) => this.credential.emit(response.credential),
    });
    google.accounts.id.renderButton(this.target().nativeElement, {
      theme: 'outline',
      size: 'large',
      width: 320,
      text: 'signin_with',
      locale: 'vi',
    });
  }
}
