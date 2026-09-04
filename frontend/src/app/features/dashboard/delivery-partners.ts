import { Component, signal } from '@angular/core';

import { VndCurrencyPipe } from '../../core/currency/vnd-currency.pipe';
import { INTEGRATED_CARRIERS } from './delivery-partner.models';

type MainTab = 'integration' | 'other';
type IntegrationSubTab = 'info' | 'shipping-history' | 'reconciliation-history';

/** "Đối tác giao hàng" page, laid out like KiotViet's own: an Integration tab (carrier
 * overview + shipping/reconciliation history) and an "Other" tab for manually-added
 * partners. Nothing here has a backend counterpart yet, so every control that would
 * need one stays disabled with a "Sắp ra mắt" tooltip - only the tab switching is real. */
@Component({
  selector: 'app-delivery-partners',
  standalone: true,
  imports: [VndCurrencyPipe],
  templateUrl: './delivery-partners.html',
})
export class DeliveryPartners {
  readonly carriers = INTEGRATED_CARRIERS;

  readonly mainTab = signal<MainTab>('integration');
  readonly subTab = signal<IntegrationSubTab>('info');

  setMainTab(tab: MainTab): void {
    this.mainTab.set(tab);
  }

  setSubTab(tab: IntegrationSubTab): void {
    this.subTab.set(tab);
  }
}
