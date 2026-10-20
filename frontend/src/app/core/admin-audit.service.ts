import { HttpClient } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface AuditRecord {
  auditId: number;
  eventId: string;
  orderId: number;
  orderRef: string;
  actor: string;
  clientName: string;
  clientEmail: string;
  clientSegment?: string;
  account: number;
  accountLabel: string;
  instrument: string;
  instrumentKey: string;
  side: string;
  quantity: string;
  quantityValue: number;
  indicativePrice: string;
  indicativePriceValue: number;
  status: string;
  reason: string;
  suspicious: boolean;
  recordedAt: string;
  recordedAtIso: string;
}

export interface TradeLifecycle {
  orderId: number;
  orderRef: string;
  accountId: number;
  clientId: number;
  clientName: string;
  clientEmail: string;
  instrument: string;
  side: string;
  quantity: number;
  events: Array<{ eventId: string; eventType: string; status: string; reason: string | null; price: number | null; occurredAt: string }>;
  execution: { price: number; quantity: number; executedAt: string } | null;
  settlements: Array<{ movementId: number; amount: number; movementType: string; currency: string; reason: string | null; occurredAt: string }>;
  complete: boolean;
}

interface AuditRecordDto {
  auditId: number;
  eventId: string;
  orderId: number;
  orderRef: string;
  clientName: string;
  clientEmail: string;
  clientSegment: string;
  accountId: number;
  instrument: string;
  side: string;
  quantity: number;
  price: number | null;
  status: string;
  reason: string | null;
  suspicious: boolean;
  occurredAt: string;
}

@Injectable({ providedIn: 'root' })
export class AdminAuditService {
  private readonly http = inject(HttpClient);
  readonly records = signal<AuditRecord[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');
  readonly lifecycle = signal<TradeLifecycle | null>(null);
  readonly lifecycleLoading = signal(false);

  async refresh(silent = false): Promise<void> {
    if (!silent) {
      this.loading.set(true);
    }
    this.error.set('');
    try {
      const rows = await firstValueFrom(
        this.http.get<AuditRecordDto[]>('/api/admin/audit', { params: { limit: 500 } }),
      );
      this.records.set(rows.map((row) => this.normalize(row)));
    } catch {
      this.error.set('Could not load audit records from the backend.');
    } finally {
      if (!silent) {
        this.loading.set(false);
      }
    }
  }

  async setSuspicious(orderId: number, suspicious: boolean): Promise<void> {
    const path = `/api/admin/audit/${orderId}/suspicious`;
    if (suspicious) {
      await firstValueFrom(this.http.post<void>(path, {}));
    } else {
      await firstValueFrom(this.http.delete<void>(path));
    }
    await this.refresh(true);
  }

  async loadLifecycle(orderId: number): Promise<void> {
    this.lifecycleLoading.set(true);
    this.error.set('');
    try {
      const lifecycle = await firstValueFrom(this.http.get<TradeLifecycle>(`/api/admin/audit/${orderId}`));
      this.lifecycle.set(lifecycle);
    } catch {
      this.error.set('Could not load the complete trade lifecycle.');
      this.lifecycle.set(null);
    } finally {
      this.lifecycleLoading.set(false);
    }
  }

  closeLifecycle(): void {
    this.lifecycle.set(null);
  }

  clear(): void {
    this.error.set('Audit records are managed by the backend and cannot be cleared here.');
  }

  private normalize(row: AuditRecordDto): AuditRecord {
    const price = row.price ?? 0;
    const occurred = new Date(row.occurredAt);
    return {
      auditId: row.auditId,
      eventId: row.eventId,
      orderId: row.orderId,
      orderRef: row.orderRef,
      actor: `${row.clientName} <${row.clientEmail}>`,
      clientName: row.clientName,
      clientEmail: row.clientEmail,
      clientSegment: row.clientSegment ?? 'CLIENT',
      account: row.accountId,
      accountLabel: `Account ${row.accountId}`,
      instrument: row.instrument,
      instrumentKey: row.instrument.toUpperCase(),
      side: row.side,
      quantityValue: Number(row.quantity),
      quantity: Number(row.quantity).toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 8 }),
      indicativePriceValue: price,
      indicativePrice: price > 0 ? '$' + price.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 8 }) : '-',
      status: row.status,
      reason: row.reason?.trim() || '-',
      suspicious: row.suspicious,
      recordedAt: occurred.toLocaleString('en-GB', { timeZone: 'UTC' }) + ' UTC',
      recordedAtIso: row.occurredAt,
    };
  }
}
