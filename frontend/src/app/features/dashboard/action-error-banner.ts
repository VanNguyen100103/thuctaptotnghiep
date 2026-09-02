import { Component, input } from '@angular/core';

import { ActionError } from './subscription-error.util';

@Component({
  selector: 'app-action-error-banner',
  standalone: true,
  templateUrl: './action-error-banner.html',
})
export class ActionErrorBanner {
  readonly error = input.required<ActionError | null>();
}
