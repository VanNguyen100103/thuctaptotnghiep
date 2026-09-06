import { HttpErrorResponse } from '@angular/common/http';
import { Component, effect, inject, input, signal } from '@angular/core';

import { ChatWidgetService } from './chat-widget.service';
import { ChatMessage } from './chat.models';

const SESSION_STORAGE_PREFIX = 'chat:session:';

/**
 * Floating chat bubble/panel, mounted once by StorefrontLayout so it appears
 * across the whole storefront. Talks to POST /stores/{slug}/chat - the
 * backend does the real work (live product/policy lookups, Gemini with a
 * Groq fallback); this component just holds the on-screen transcript and a
 * sessionStorage-persisted session id (matches the backend's ~30min Redis TTL).
 */
@Component({
  selector: 'app-chat-widget',
  standalone: true,
  templateUrl: './chat-widget.html',
})
export class ChatWidget {
  private readonly chatService = inject(ChatWidgetService);

  readonly storeSlug = input.required<string>();

  readonly open = signal(false);
  readonly messages = signal<ChatMessage[]>([]);
  readonly draft = signal('');
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);

  private sessionId: string | null = null;

  constructor() {
    // Reset the on-screen transcript (and pick up any saved session) whenever
    // the widget is mounted for a different store.
    effect(() => {
      const slug = this.storeSlug();
      this.sessionId = this.readSessionId(slug);
      this.messages.set([]);
      this.error.set(null);
    });
  }

  toggle(): void {
    this.open.update((o) => !o);
  }

  onDraftInput(event: Event): void {
    this.draft.set((event.target as HTMLTextAreaElement).value);
  }

  onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      this.send();
    }
  }

  send(): void {
    const text = this.draft().trim();
    if (!text || this.sending()) {
      return;
    }

    this.messages.update((m) => [...m, { role: 'user', text }]);
    this.draft.set('');
    this.sending.set(true);
    this.error.set(null);

    this.chatService.sendMessage(this.storeSlug(), this.sessionId, text).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.writeSessionId(this.storeSlug(), res.sessionId);
        this.messages.update((m) => [...m, { role: 'assistant', text: res.reply }]);
        this.sending.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.sending.set(false);
        this.error.set(
          err.status === 429
            ? 'Bạn đang hỏi hơi nhanh, vui lòng thử lại sau ít phút.'
            : 'Trợ lý hiện chưa thể trả lời, vui lòng thử lại sau.',
        );
      },
    });
  }

  private readSessionId(storeSlug: string): string | null {
    try {
      return sessionStorage.getItem(SESSION_STORAGE_PREFIX + storeSlug);
    } catch {
      return null;
    }
  }

  private writeSessionId(storeSlug: string, sessionId: string): void {
    try {
      sessionStorage.setItem(SESSION_STORAGE_PREFIX + storeSlug, sessionId);
    } catch {
      // Private browsing / storage disabled - the conversation just won't survive a page reload.
    }
  }
}
