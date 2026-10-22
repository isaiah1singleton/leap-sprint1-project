import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export type TaxDocument = '1099-B' | '1099-DA';
export interface TaxLot {
  acquired: string;
  holdingPeriod: string;
  quantity: string;
  proceeds: string;
  basis: string;
  gain: string;
}
export interface TaxSale {
  fillId: number;
  symbol: string;
  description: string;
  assetClass: string;
  currency: string;
  sold: string;
  quantity: string;
  proceeds: string;
  basis: string | null;
  gain: string | null;
  lots: TaxLot[];
}
export interface TaxReport {
  year: number;
  document: TaxDocument;
  clientReference: string;
  currency: string;
  sales: TaxSale[];
  gains: string | null;
  losses: string | null;
  net: string | null;
  unavailableBasisCount: number;
}

@Injectable({ providedIn: 'root' })
export class TaxService {
  private readonly http = inject(HttpClient);

  report(year: number, document: TaxDocument): Promise<TaxReport> {
    return firstValueFrom(this.http.get<TaxReport>('/api/tax/report', { params: { year, document } }));
  }

  download(year: number, document: TaxDocument): Promise<Blob> {
    return firstValueFrom(this.http.get(`/api/tax/documents/${document}`, {
      params: { year }, responseType: 'blob',
    }));
  }
}
