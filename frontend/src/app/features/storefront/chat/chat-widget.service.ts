import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { ChatRequest, ChatResponse } from './chat.models';

@Injectable({ providedIn: 'root' })
export class ChatWidgetService {
  constructor(private readonly http: HttpClient) {}

  sendMessage(storeSlug: string, sessionId: string | null, message: string): Observable<ChatResponse> {
    const request: ChatRequest = { sessionId, message };
    return this.http.post<ChatResponse>(`${environment.apiUrl}/stores/${storeSlug}/chat`, request);
  }
}
