import { HttpErrorResponse } from '@angular/common/http';

import { extractErrorMessage } from '../../core/http/api-error';

export interface ActionError {
  message: string;
  isUpgradeRequired: boolean;
}

/** AdminProductController's write endpoints 402 with a plain {error} body
 * when the store has no active subscription or hit its product-count limit -
 * the message is already a human-readable upgrade prompt. */
export function toActionError(err: HttpErrorResponse): ActionError {
  return { message: extractErrorMessage(err), isUpgradeRequired: err.status === 402 };
}
