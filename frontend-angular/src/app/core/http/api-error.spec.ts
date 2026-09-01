import { HttpErrorResponse } from '@angular/common/http';

import { extractErrorMessage } from './api-error';

function errorResponse(body: unknown): HttpErrorResponse {
  return new HttpErrorResponse({ error: body, status: 400 });
}

describe('extractErrorMessage', () => {
  it('joins field messages when validationErrors is present', () => {
    const msg = extractErrorMessage(
      errorResponse({
        message: 'Invalid input data',
        validationErrors: { storeSlug: 'Slug must be lowercase', email: 'Email should be valid' },
      }),
    );
    expect(msg).toContain('Slug must be lowercase');
    expect(msg).toContain('Email should be valid');
  });

  it('falls back to message when there are no validationErrors', () => {
    expect(extractErrorMessage(errorResponse({ message: 'Unauthorized' }))).toBe('Unauthorized');
  });

  it('falls back to the plain {error} shape used by several controllers', () => {
    expect(extractErrorMessage(errorResponse({ error: 'Invalid username or password' }))).toBe(
      'Invalid username or password',
    );
  });

  it('falls back to a generic message for a non-object body (network failure)', () => {
    expect(extractErrorMessage(errorResponse(null))).toBe('Đã xảy ra lỗi. Vui lòng thử lại.');
  });

  it('prefers validationErrors over message when both are present', () => {
    const msg = extractErrorMessage(
      errorResponse({ message: 'Invalid input data', validationErrors: { username: 'Required' } }),
    );
    expect(msg).toBe('Required');
  });
});
