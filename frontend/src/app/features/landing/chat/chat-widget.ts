import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';

import { ChatWidgetService } from './chat-widget.service';
import { ChatMessage } from './chat.models';

const SESSION_STORAGE_KEY = 'chat:session:platform';

/**
 * Floating chat bubble/panel on the landing page - a pre-sales consultant for
 * Tryum itself (plans, features, how to sign up), not a shop assistant: the
 * homepage has no store in scope. Talks to POST /assistant/chat, where the
 * backend does the real work (Gemini with a Groq fallback); this component
 * just holds the on-screen transcript and a sessionStorage-persisted session
 * id (matches the backend's ~30min Redis TTL).
 */
@Component({
  selector: 'app-chat-widget',
  standalone: true,
  templateUrl: './chat-widget.html',
})
export class ChatWidget {
  private readonly chatService = inject(ChatWidgetService);

  /** Starter chips, shown only on an empty transcript - the bot answers all of these from its system prompt. */
  readonly suggestions = [
    'Tryum có những tính năng gì?',
    'Giá bao nhiêu một tháng?',
    'Dùng thử miễn phí thế nào?',
  ];

  readonly open = signal(false);
  readonly messages = signal<ChatMessage[]>([]);
  readonly draft = signal('');
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);

  private sessionId = this.readSessionId();

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

  /** Fills the composer from a suggested-question chip and sends it straight away. */
  ask(question: string): void {
    this.draft.set(question);
    this.send();
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

    this.chatService.sendMessage(this.sessionId, text).subscribe({
      next: (res) => {
        this.sessionId = res.sessionId;
        this.writeSessionId(res.sessionId);
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

  private readSessionId(): string | null {
    try {
      return sessionStorage.getItem(SESSION_STORAGE_KEY);
    } catch {
      return null;
    }
  }

  private writeSessionId(sessionId: string): void {
    try {
      sessionStorage.setItem(SESSION_STORAGE_KEY, sessionId);
    } catch {
      // Private browsing / storage disabled - the conversation just won't survive a page reload.
    }
  }
}
