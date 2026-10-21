// Paths mirror backend/src/main/java/com/neueda/leap/ApiRoutes.java.
export const routes = Object.freeze({
  accounts: '/api/accounts',
  orders: '/api/orders',
  instruments: '/api/instruments',
  quote: '/api/market/quotes',
});

export class ApiError extends Error {
  constructor(message, status) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

export class LeapApi {
  constructor({ baseUrl = 'http://127.0.0.1:8082', accessToken, fetchImpl = fetch, timeoutMs = 15_000 }) {
    if (!accessToken?.trim()) throw new Error('Set LEAP_ACCESS_TOKEN to a current user session token.');
    const url = new URL(baseUrl);
    if (!['http:', 'https:'].includes(url.protocol) || url.username || url.password || url.search || url.hash) {
      throw new Error('LEAP_API_BASE_URL must be an HTTP(S) URL without credentials, a query, or a fragment.');
    }
    this.baseUrl = url.href.replace(/\/$/, '');
    this.accessToken = accessToken.trim();
    this.fetchImpl = fetchImpl;
    this.timeoutMs = timeoutMs;
  }

  async request(path, { method = 'GET', body, signal } = {}) {
    const signals = [AbortSignal.timeout(this.timeoutMs)];
    if (signal) signals.push(signal);
    // Never follow a redirect carrying credentials, or retry a possibly submitted order.
    const response = await this.fetchImpl(this.baseUrl + path, {
      method, redirect: 'error',
      headers: {
        Authorization: 'Bearer ' + this.accessToken,
        Accept: 'application/json',
        ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      signal: AbortSignal.any(signals),
    });
    if (!response.ok) {
      const message = response.status === 401
        ? 'Your LEAP session expired or is invalid. Sign in again and update LEAP_ACCESS_TOKEN.'
        : response.status === 403
          ? 'This LEAP session is not allowed to access the requested data.'
          : 'LEAP API returned HTTP ' + response.status + '. Review the account or order in the app.';
      // Raw responses and headers may contain secrets; keep them out of model context.
      throw new ApiError(message, response.status);
    }
    return response.json();
  }

  async account(accountId, signal) {
    const accounts = await this.request(routes.accounts, { signal });
    if (accountId !== undefined) {
      const selected = accounts.find((account) => account.accountId === accountId);
      if (!selected) throw new Error('Choose one of your accounts using list_accounts.');
      return selected;
    }
    const active = accounts.filter((account) => account.accountStatus === 'ACTIVE');
    if (active.length !== 1) {
      throw new Error(active.length
        ? 'You have multiple active accounts. Use list_accounts and choose an accountId.'
        : 'You have no active trading accounts.');
    }
    return active[0];
  }
}
