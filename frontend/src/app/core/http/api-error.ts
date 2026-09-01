import { HttpErrorResponse } from '@angular/common/http';

/**
 * The backend returns three different error shapes depending on which code
 * path threw: a plain {error} map (controllers' own try/catch), a full
 * ErrorResponse with validationErrors (GlobalExceptionHandler, e.g. @Valid
 * failures), or a JwtAuthenticationEntryPoint shape with just {message}.
 * This extracts a display string without branching on which one it is.
 */
export function extractErrorMessage(err: HttpErrorResponse): string {
  const body = err.error;

  if (body && typeof body === 'object') {
    if (body.validationErrors && typeof body.validationErrors === 'object') {
      const messages = Object.values(body.validationErrors as Record<string, string>);
      if (messages.length > 0) {
        return messages.join(' ');
      }
    }
    if (typeof body.message === 'string' && body.message.length > 0) {
      return body.message;
    }
    if (typeof body.error === 'string' && body.error.length > 0) {
      return body.error;
    }
  }

  return 'Đã xảy ra lỗi. Vui lòng thử lại.';
}
