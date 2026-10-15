import { Injectable, signal } from '@angular/core';

export interface AuditRecord {
  auditId: number;
  orderRef: string;
  actor: string;
  clientName: string;
  clientEmail: string;
  clientSegment: string;
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
  recordedAt: string;
  recordedAtIso: string;
}

export interface AuditTradeRecordInput {
  orderId: number | null;
  accountId: number | null;
  instrument: string;
  side: string;
  quantity: number;
  indicativePrice: number;
  status: string;
  reason?: string;
  actorName?: string;
  actorEmail?: string;
  clientSegment?: string;
}

@Injectable({ providedIn: 'root' })
export class AdminAuditService {
  private readonly storageKey = 'adminAuditRecords';
  readonly records = signal<AuditRecord[]>([]);
  readonly loading = signal(false);
  readonly error = signal('');

  constructor() {
    this.restore();
  }

  refresh(): void {
    this.restore();
  }

  clear(): void {
    this.records.set([]);
    sessionStorage.removeItem(this.storageKey);
  }

  recordTrade(input: AuditTradeRecordInput): void {
    const name = input.actorName || sessionStorage.getItem('currentUserName') || 'Local User';
    const email = input.actorEmail || sessionStorage.getItem('currentUserEmail') || 'local@example.com';
    const segment = input.clientSegment || sessionStorage.getItem('currentUserSegment') || 'RETAIL';
    const nowIso = new Date().toISOString();
    const accountValue = input.accountId ?? 0;
    const record: AuditRecord = {
      auditId: Date.now(),
      orderRef: input.orderId != null ? `ORD-${input.orderId}` : 'ORD-PENDING',
      actor: `${name} <${email}> (${segment})`,
      clientName: name,
      clientEmail: email,
      clientSegment: segment,
      account: accountValue,
      accountLabel: accountValue > 0 ? `Account ${accountValue}` : 'Unknown account',
      instrument: input.instrument,
      instrumentKey: input.instrument.toUpperCase(),
      side: input.side,
      quantityValue: input.quantity,
      quantity: input.quantity.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 8 }),
      indicativePriceValue: input.indicativePrice,
      indicativePrice: '$' + input.indicativePrice.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 8 }),
      status: input.status,
      reason: input.reason?.trim() || '-',
      recordedAt: new Date(nowIso).toLocaleString('en-GB', { timeZone: 'UTC' }) + ' UTC',
      recordedAtIso: nowIso,
    };

    const next = [record, ...this.records()].slice(0, 500);
    this.records.set(next);
    sessionStorage.setItem(this.storageKey, JSON.stringify(next));
  }

  private restore(): void {
    this.loading.set(true);
    this.error.set('');
    try {
      const raw = sessionStorage.getItem(this.storageKey);
      if (!raw) {
        this.records.set([]);
        return;
      }
      const parsed = JSON.parse(raw) as Partial<AuditRecord>[];
      if (!Array.isArray(parsed)) {
        this.records.set([]);
        return;
      }

      const normalized = parsed.map((item) => {
        const iso = item.recordedAtIso ?? new Date().toISOString();
        const clientName = item.clientName ?? 'Local User';
        const clientEmail = item.clientEmail ?? 'local@example.com';
        const clientSegment = item.clientSegment ?? 'RETAIL';
        const account = typeof item.account === 'number' ? item.account : 0;
        const instrument = item.instrument ?? 'UNKNOWN';
        const quantityValue = typeof item.quantityValue === 'number'
          ? item.quantityValue
          : Number(String(item.quantity ?? '0').replace(/,/g, ''));
        const indicativePriceValue = typeof item.indicativePriceValue === 'number'
          ? item.indicativePriceValue
          : Number(String(item.indicativePrice ?? '$0').replace(/[$,]/g, ''));

        return {
          auditId: item.auditId ?? Date.now(),
          orderRef: item.orderRef ?? 'ORD-PENDING',
          actor: item.actor ?? `${clientName} <${clientEmail}> (${clientSegment})`,
          clientName,
          clientEmail,
          clientSegment,
          account,
          accountLabel: item.accountLabel ?? (account > 0 ? `Account ${account}` : 'Unknown account'),
          instrument,
          instrumentKey: item.instrumentKey ?? instrument.toUpperCase(),
          side: item.side ?? 'UNKNOWN',
          quantity: item.quantity ?? quantityValue.toLocaleString('en-US', { minimumFractionDigits: 0, maximumFractionDigits: 8 }),
          quantityValue,
          indicativePrice: item.indicativePrice ?? ('$' + indicativePriceValue.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 8 })),
          indicativePriceValue,
          status: item.status ?? 'UNKNOWN',
          reason: item.reason ?? '-',
          recordedAt: item.recordedAt ?? new Date(iso).toLocaleString('en-GB', { timeZone: 'UTC' }) + ' UTC',
          recordedAtIso: iso,
        } satisfies AuditRecord;
      });
      this.records.set(normalized);
    } catch {
      this.records.set([]);
      this.error.set('Could not read locally stored audit records.');
      sessionStorage.removeItem(this.storageKey);
    } finally {
      this.loading.set(false);
    }
  }
}
