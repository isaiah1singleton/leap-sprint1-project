export interface PortfolioHistoryPoint {
  observedAt: string;
  cashBalance: number;
  investedValue: number | null;
  portfolioValue: number | null;
  lifetimeProfitLoss: number | null;
  oldestQuoteAt: string | null;
  valueUnavailableReason: string | null;
  profitLossUnavailableReason: string | null;
}

export interface PortfolioHistory {
  accountId: number;
  currency: string;
  recordingStartedAt: string | null;
  range: string;
  points: PortfolioHistoryPoint[];
}
