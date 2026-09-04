import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, effect, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { switchMap, take, takeWhile, timer } from 'rxjs';

import { AuthService } from '../../../core/auth/auth.service';
import { CartService } from '../../../core/cart/cart.service';
import { extractErrorMessage } from '../../../core/http/api-error';
import { CouponValidation, isNoPaymentYet, OrderDetail, PaymentMethodCode } from '../checkout.models';
import { clearPendingOrder, savePendingOrder } from '../pending-order.storage';
import { StorefrontPaymentService } from '../storefront-payment.service';
import { VndCurrencyPipe } from '../../../core/currency/vnd-currency.pipe';

/** Parsed back out of the VietQR image URL SePayPaymentProvider builds - see CreatePaymentResponse.redirectUrl. */
interface BankTransferQr {
  qrUrl: string;
  bank: string;
  account: string;
  content: string;
  amount: number;
}

@Component({
  selector: 'app-storefront-checkout',
  standalone: true,
  imports: [ReactiveFormsModule, VndCurrencyPipe],
  templateUrl: './storefront-checkout.html',
})
export class StorefrontCheckout {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly cartService = inject(CartService);
  private readonly paymentService = inject(StorefrontPaymentService);

  private readonly paramMap = toSignal(this.route.paramMap, { requireSync: true });
  readonly storeSlug = computed(() => this.paramMap()!.get('storeSlug')!);

  readonly cart = this.cartService.cart;
  readonly step = signal<'address' | 'payment'>('address');
  readonly order = signal<OrderDetail | null>(null);

  readonly form = this.fb.nonNullable.group({
    addressLine1: ['', Validators.required],
    addressLine2: [''],
    city: ['', Validators.required],
    stateProvince: ['', Validators.required],
    postalCode: ['', Validators.required],
    country: ['Vietnam', Validators.required],
    phoneNumber: [''],
    email: [this.authService.currentUser()?.email ?? '', [Validators.required, Validators.email]],
    couponCode: [''],
  });

  readonly appliedCoupon = signal<CouponValidation | null>(null);
  readonly validatingCoupon = signal(false);
  readonly couponError = signal<string | null>(null);
  readonly submitting = signal(false);
  readonly formError = signal<string | null>(null);
  readonly selectedMethod = signal<PaymentMethodCode | null>(null);
  readonly paymentError = signal<string | null>(null);
  readonly confirmingPayment = signal(false);

  // BANK_TRANSFER (SePay) never leaves the SPA - createPayment() returns a QR
  // to render right here instead of a redirect, so it needs its own polling
  // state instead of going through pending-order.storage + /payment/success
  // like PayPal/MoMo/COD do.
  readonly bankTransferQr = signal<BankTransferQr | null>(null);
  readonly bankTransferPolling = signal(false);
  readonly bankTransferConfirmed = signal(false);

  readonly paymentMethods: { code: PaymentMethodCode; label: string }[] = [
    { code: 'PAYPAL', label: 'PayPal' },
    { code: 'MOMO', label: 'Ví MoMo' },
    { code: 'BANK_TRANSFER', label: 'Chuyển khoản QR (SePay)' },
    { code: 'CASH_ON_DELIVERY', label: 'Thanh toán khi nhận hàng (COD)' },
  ];

  constructor() {
    effect(() => {
      const slug = this.storeSlug();
      this.cartService.enterStore(slug);
      this.cartService.loadCart(slug).subscribe();
    });

    this.form.controls.couponCode.valueChanges.subscribe(() => {
      this.appliedCoupon.set(null);
      this.couponError.set(null);
    });
  }

  applyCoupon(): void {
    const code = this.form.controls.couponCode.value.trim();
    if (!code) {
      return;
    }
    const subtotal = this.cart()?.totalPrice ?? 0;
    this.validatingCoupon.set(true);
    this.couponError.set(null);

    this.paymentService.validateCoupon(code, subtotal).subscribe({
      next: (res) => {
        this.validatingCoupon.set(false);
        if (res.valid) {
          this.appliedCoupon.set(res);
        } else {
          this.couponError.set(res.message ?? 'Mã giảm giá không hợp lệ.');
        }
      },
      error: (err: HttpErrorResponse) => {
        this.validatingCoupon.set(false);
        this.couponError.set(extractErrorMessage(err));
      },
    });
  }

  submitAddress(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.formError.set(null);

    const value = this.form.getRawValue();
    this.paymentService
      .checkout({
        shippingAddress: {
          addressLine1: value.addressLine1,
          addressLine2: value.addressLine2 || undefined,
          city: value.city,
          stateProvince: value.stateProvince,
          postalCode: value.postalCode,
          country: value.country,
          phoneNumber: value.phoneNumber || undefined,
        },
        email: value.email,
        couponCode: this.appliedCoupon() ? value.couponCode : undefined,
        storeSlug: this.storeSlug(),
      })
      .subscribe({
        next: (res) => {
          this.submitting.set(false);
          this.order.set(res.order);
          this.cartService.clearLocal();
          this.step.set('payment');
        },
        error: (err: HttpErrorResponse) => {
          this.submitting.set(false);
          this.formError.set(extractErrorMessage(err));
        },
      });
  }

  selectMethod(method: PaymentMethodCode): void {
    this.selectedMethod.set(method);
    this.paymentError.set(null);
  }

  confirmPayment(): void {
    const method = this.selectedMethod();
    const order = this.order();
    if (!method || !order) {
      return;
    }

    this.confirmingPayment.set(true);
    this.paymentError.set(null);
    savePendingOrder({ orderId: order.id, storeSlug: this.storeSlug() });

    this.paymentService.createPayment({ orderId: order.id, paymentMethod: method }).subscribe({
      next: (res) => {
        if (res.paymentMethod === 'CASH_ON_DELIVERY') {
          // Order already confirmed server-side - internal nav, no external hop.
          this.router.navigateByUrl('/payment/success');
        } else if (res.paymentMethod === 'BANK_TRANSFER') {
          this.confirmingPayment.set(false);
          this.startBankTransferPolling(res.redirectUrl, order.id);
        } else {
          window.location.href = res.redirectUrl;
        }
      },
      error: (err: HttpErrorResponse) => {
        this.confirmingPayment.set(false);
        this.paymentError.set(extractErrorMessage(err));
      },
    });
  }

  /** qrUrl is SePayPaymentProvider's vietqr.app image URL - account/bank/amount/content ride along as its own query params. */
  private startBankTransferPolling(qrUrl: string, orderId: number): void {
    const params = new URL(qrUrl).searchParams;
    this.bankTransferQr.set({
      qrUrl,
      bank: params.get('bank') ?? '',
      account: params.get('acc') ?? '',
      content: params.get('des') ?? '',
      amount: Number(params.get('amount') ?? 0),
    });
    this.bankTransferPolling.set(true);

    // Bank transfers take longer than a gateway redirect to settle - poll
    // every 3s for up to 5 minutes rather than payment-success's 5-try burst.
    timer(0, 3000)
      .pipe(
        take(100),
        switchMap(() => this.paymentService.getPaymentByOrder(orderId)),
        takeWhile((payment) => isNoPaymentYet(payment) || payment.status === 'PENDING', true),
      )
      .subscribe({
        next: (payment) => {
          if (!isNoPaymentYet(payment) && payment.status === 'COMPLETED') {
            this.bankTransferConfirmed.set(true);
            this.bankTransferPolling.set(false);
            clearPendingOrder();
          }
        },
        error: (err: HttpErrorResponse) => {
          this.bankTransferPolling.set(false);
          this.paymentError.set(extractErrorMessage(err));
        },
        complete: () => this.bankTransferPolling.set(false),
      });
  }

  goToStore(): void {
    this.router.navigate(['/store', this.storeSlug()]);
  }
}
