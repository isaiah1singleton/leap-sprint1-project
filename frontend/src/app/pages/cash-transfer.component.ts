import { Component, computed, effect, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { AccountResponse } from '../core/account.service';
import { AccountSelectionService } from '../core/account-selection.service';
import { Receipt, TradingService, TransferMode } from '../core/trading.service';

@Component({
  selector: 'app-cash-transfer',
  imports: [FormsModule, RouterLink],
  templateUrl: './cash-transfer.component.html',
  styleUrl: './cash-transfer.component.css',
})
export class CashTransferComponent {
  readonly trading = inject(TradingService);
  readonly selectedAccount = inject(AccountSelectionService).selectedAccount;
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');
  readonly transferAccount = signal<AccountResponse | null>(null);
  readonly submitting = signal(false);
  readonly receipt = signal<Receipt | null>(null);
  readonly error = signal('');
  mode: TransferMode = 'deposit';
  amount = '';

  readonly unavailableReason = computed(() => {
    const account = this.selectedAccount();
    if (!account) return 'Select a trading account to move cash.';
    if (account.accountStatus !== 'ACTIVE') return 'Cash transfers are unavailable for an inactive account.';
    if (this.trading.loading()) return 'Loading your cash balance and order status…';
    if (this.trading.error()) return 'Cash transfers are unavailable until account data can be loaded. Retry below.';
    if (this.trading.pendingOrderCount() > 0) return 'An accepted order is awaiting execution. Deposits and withdrawals will be available once it finishes. Refresh account data to check its status.';
    return '';
  });

  constructor() {
    effect(() => {
      const account = this.transferAccount();
      if (account && account.accountId !== this.selectedAccount()?.accountId) {
        this.dialog().nativeElement.close();
        this.transferAccount.set(null);
      }
    });
  }

  open(mode: TransferMode): void {
    if (this.unavailableReason() || this.submitting()) return;
    this.mode = mode;
    this.amount = '';
    this.error.set('');
    this.receipt.set(null);
    this.transferAccount.set(this.selectedAccount());
    this.dialog().nativeElement.showModal();
  }

  close(event?: Event): void {
    event?.preventDefault();
    if (this.submitting()) return;
    this.dialog().nativeElement.close();
    this.transferAccount.set(null);
  }

  async submit(): Promise<void> {
    const account = this.transferAccount();
    if (!account || account.accountId !== this.selectedAccount()?.accountId ||
        this.submitting() || this.receipt() || this.unavailableReason()) return;
    this.submitting.set(true);
    this.error.set('');
    try {
      const result = await this.trading.transfer(this.mode, this.amount);
      if (this.transferAccount() !== account || this.selectedAccount()?.accountId !== account.accountId) return;
      if ('message' in result) {
        this.error.set(result.message);
      } else {
        this.receipt.set(result);
        this.amount = '';
      }
    } catch {
      this.error.set('The transfer result could not be confirmed. Check cash activity before trying again.');
    } finally {
      this.submitting.set(false);
    }
  }
}
