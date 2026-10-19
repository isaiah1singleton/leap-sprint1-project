import { Component, computed, inject, OnDestroy, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AdminAuditService, AuditRecord } from '../core/admin-audit.service';

@Component({
  selector: 'app-admin-audit',
  imports: [FormsModule],
  templateUrl: './admin-audit.component.html',
  styleUrl: './admin-audit.component.css',
})
export class AdminAuditComponent implements OnDestroy {
  readonly audit = inject(AdminAuditService);

  readonly clientFilter = signal('');
  readonly instrumentFilter = signal('ALL');
  readonly accountFilter = signal('ALL');
  readonly statusFilter = signal('ALL');
  readonly sideFilter = signal('ALL');
  readonly dateFrom = signal('');
  readonly dateTo = signal('');

  readonly clientOptions = computed(() => this.distinct(this.audit.records().map((record) => record.clientEmail)));
  readonly instrumentOptions = computed(() => this.distinct(this.audit.records().map((record) => record.instrumentKey)));
  readonly accountOptions = computed(() => this.distinct(this.audit.records().map((record) => String(record.account))));
  readonly statusOptions = computed(() => this.distinct(this.audit.records().map((record) => record.status)));
  readonly sideOptions = computed(() => this.distinct(this.audit.records().map((record) => record.side)));

  readonly filteredRecords = computed(() => {
    const from = this.dateFrom();
    const to = this.dateTo();
    const client = this.clientFilter().toLowerCase().trim();
    const instrument = this.instrumentFilter();
    const account = this.accountFilter();
    const status = this.statusFilter();
    const side = this.sideFilter();

    return this.audit.records().filter((record) => {
      if (client && !record.clientEmail.toLowerCase().includes(client) && !record.clientName.toLowerCase().includes(client)) {
        return false;
      }

      if (instrument !== 'ALL' && record.instrumentKey !== instrument) {
        return false;
      }

      if (account !== 'ALL' && String(record.account) !== account) {
        return false;
      }

      if (status !== 'ALL' && record.status !== status) {
        return false;
      }

      if (side !== 'ALL' && record.side !== side) {
        return false;
      }

      const day = record.recordedAtIso.slice(0, 10);
      if (from && day < from) {
        return false;
      }

      if (to && day > to) {
        return false;
      }

      return true;
    });
  });

  readonly summary = computed(() => {
    const rows = this.filteredRecords();
    const uniqueClients = new Set(rows.map((row) => row.clientEmail)).size;
    const uniqueInstruments = new Set(rows.map((row) => row.instrumentKey)).size;
    const rejectedCount = rows.filter((row) => row.status.includes('REJECT') || row.status.includes('FAILED')).length;
    const cancelledCount = rows.filter((row) => row.status.includes('CANCEL')).length;
    return {
      total: rows.length,
      uniqueClients,
      uniqueInstruments,
      rejectedCount,
      cancelledCount,
    };
  });

  readonly statusBreakdown = computed(() => this.breakdown(this.filteredRecords(), (row) => row.status));
  readonly clientBreakdown = computed(() => this.breakdown(this.filteredRecords(), (row) => row.clientEmail).slice(0, 8));
  readonly instrumentBreakdown = computed(() => this.breakdown(this.filteredRecords(), (row) => row.instrumentKey).slice(0, 8));
  private readonly refreshTimer = window.setInterval(() => void this.audit.refresh(true), 3000);

  constructor() {
    void this.audit.refresh();
  }

  ngOnDestroy(): void {
    window.clearInterval(this.refreshTimer);
  }

  resetFilters(): void {
    this.clientFilter.set('');
    this.instrumentFilter.set('ALL');
    this.accountFilter.set('ALL');
    this.statusFilter.set('ALL');
    this.sideFilter.set('ALL');
    this.dateFrom.set('');
    this.dateTo.set('');
  }

  setClientFilter(value: string): void { this.clientFilter.set(value); }
  setInstrumentFilter(value: string): void { this.instrumentFilter.set(value); }
  setAccountFilter(value: string): void { this.accountFilter.set(value); }
  setStatusFilter(value: string): void { this.statusFilter.set(value); }
  setSideFilter(value: string): void { this.sideFilter.set(value); }
  setDateFrom(value: string): void { this.dateFrom.set(value); }
  setDateTo(value: string): void { this.dateTo.set(value); }

  exportSpreadsheet(): void {
    const headers = ['Audit', 'Order', 'Actor', 'Account', 'Instrument', 'Side', 'Quantity', 'Indicative', 'Status', 'Recorded at', 'Reason'];
    const rows = this.filteredRecords().map((record) => [
      `#${record.auditId}`,
      record.orderRef,
      record.actor,
      record.account,
      record.instrument,
      record.side,
      record.quantity,
      record.indicativePrice,
      record.status,
      record.recordedAt,
      record.reason,
    ]);
    const csv = [headers, ...rows].map((row) => row.map((value) => this.csvCell(value)).join(',')).join('\r\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `trade-audit-${this.fileDate()}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  savePdf(): void {
    document.body.classList.add('printing-audit');
    window.addEventListener('afterprint', () => document.body.classList.remove('printing-audit'), { once: true });
    window.print();
  }

  private breakdown(records: AuditRecord[], selector: (row: AuditRecord) => string): Array<{ key: string; count: number }> {
    const counts = new Map<string, number>();
    for (const row of records) {
      const key = selector(row);
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }
    return [...counts.entries()]
      .map(([key, count]) => ({ key, count }))
      .sort((a, b) => b.count - a.count || a.key.localeCompare(b.key));
  }

  private distinct(values: string[]): string[] {
    return [...new Set(values)].sort((a, b) => a.localeCompare(b));
  }

  private csvCell(value: unknown): string {
    return `"${String(value).replaceAll('"', '""')}"`;
  }

  private fileDate(): string {
    return new Date().toISOString().slice(0, 10);
  }
}
