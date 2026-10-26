import { test as base, expect } from '@playwright/test';

export const test = base.extend<{
  api: { writes: object[]; rejectOrders: boolean; failBalance: boolean };
}>({
  api: [
    async ({ page }, use) => {
      const state = { writes: [] as object[], rejectOrders: false, failBalance: false };
      const unexpected: string[] = [];
      const pageErrors: string[] = [];
      page.on('pageerror', (error) => pageErrors.push(error.message));
      const timestamp = '2026-10-20T14:00:00Z';
      const accounts = [42, 43].map((id, i) => ({
        accountId: id,
        accountName: i ? 'Savings' : 'Trading',
        accountStatus: 'ACTIVE',
        openedAt: timestamp,
      }));
      const cash: Record<number, number> = { 42: 1000, 43: 250 };
      const holdings: Record<number, number> = { 42: 0, 43: 0 };
      const orders: any[] = [];
      const movements: any[] = [];
      const quote = {
        symbol: 'AAPL',
        quote: {
          symbol: 'AAPL',
          price: 100,
          bid: 99,
          ask: 100,
          spreadBps: 100,
          currency: 'USD',
          asOf: timestamp,
          marketState: 'OPEN',
          change: 0,
          changePercent: 0,
          previousClose: 100,
        },
        source: 'test',
        stale: false,
        error: null,
      };
      await page.route('**/*', async (route) => {
        const request = route.request();
        const url = new URL(request.url());
        const path = url.pathname;
        if (
          url.origin === 'http://127.0.0.1:4300' &&
          !path.startsWith('/api/') &&
          !path.startsWith('/auth/')
        )
          return route.continue();
        const headers = {
          'Access-Control-Allow-Origin': '*',
          'Access-Control-Allow-Headers': 'Authorization,Content-Type',
          'Access-Control-Allow-Methods': 'GET,POST,OPTIONS',
        };
        const send = (body: unknown, status = 200) =>
          route.fulfill({ status, json: body, headers });
        if (request.method() === 'OPTIONS') return send({}, 200);
        if (path === '/auth/login') {
          const body = request.postDataJSON();
          if (body.password !== 'password123')
            return send({ message: 'invalid username or password' }, 401);
          const claims = {
            name: 'Alice Trader',
            sub: body.email,
            roles: body.email.startsWith('admin') ? ['admin'] : [],
          };
          const token = `header.${Buffer.from(JSON.stringify(claims)).toString('base64url')}.test`;
          return send({ accessToken: token, refreshToken: { uuid: 'test', body: 'test' } });
        }
        if (path === '/auth/register')
          return send({ email: request.postDataJSON().email, registered: true }, 201);
        if (!request.headers()['authorization']?.startsWith('Bearer '))
          return send({ detail: 'Unauthorized' }, 401);
        if (path === '/api/accounts') return send(accounts);
        if (path === '/api/market/symbols')
          return send([
            {
              instrumentId: 7,
              symbol: 'AAPL',
              name: 'Apple Inc.',
              type: 'equity',
              exchange: 'US',
              currency: 'USD',
            },
          ]);
        if (path === '/api/market/quotes/AAPL') return send(quote);
        if (path === '/api/market/quotes')
          return send({ quotes: [quote], disclaimer: 'Test quotes' });
        if (path === '/api/admin/audit') return send([]);
        const match = path.match(/^\/api\/accounts\/(42|43)\/(.+)$/);
        if (match) {
          const id = Number(match[1]);
          const resource = match[2];
          if (resource === 'balance')
            return state.failBalance
              ? send({ detail: 'Storage unavailable' }, 503)
              : send({ accountId: id, totalBalance: cash[id], currency: 'USD' });
          if (resource === 'holdings')
            return send(
              holdings[id]
                ? [
                    {
                      instrumentId: 7,
                      symbol: 'AAPL',
                      name: 'Apple Inc.',
                      totalQuantity: holdings[id],
                      reservedQuantity: 0,
                      availableQuantity: holdings[id],
                    },
                  ]
                : [],
            );
          if (resource === 'cash-movements')
            return send(movements.filter((item) => item.accountId === id));
          if (resource === 'portfolio-summary')
            return send({
              accountId: id,
              currency: 'USD',
              cashBalance: cash[id],
              estimatedPortfolioValue: cash[id] + holdings[id] * 100,
              buyingPower: cash[id],
              investedAssetsValue: holdings[id] * 100,
              lifetimeProfitLoss: 0,
              buyingPowerUnavailableReason: null,
              profitLossUnavailableReason: null,
              accountAsOf: timestamp,
              oldestQuoteAt: null,
              valuationWarnings: [],
              holdings: [],
            });
          if (resource === 'portfolio-history')
            return send({
              accountId: id,
              currency: 'USD',
              recordingStartedAt: null,
              range: 'ALL',
              points: [],
            });
          if (resource === 'transfers') {
            const body = request.postDataJSON();
            state.writes.push(body);
            const amount = body.type === 'WITHDRAW' ? -body.amount : body.amount;
            if (cash[id] + amount < 0) return send({ detail: 'Insufficient cash' }, 409);
            cash[id] += amount;
            const movement = {
              cashMovementId: movements.length + 1,
              accountId: id,
              movementType: body.type,
              amount: { amount, currency: 'USD' },
              occurredAt: timestamp,
              reason: null,
            };
            movements.unshift(movement);
            return send({ movement, balance: { totalBalance: cash[id], currency: 'USD' } });
          }
        }
        if (path === '/api/orders' && request.method() === 'GET')
          return send(
            orders.filter((order) => order.accountId === Number(url.searchParams.get('accountId'))),
          );
        if (path === '/api/orders' && request.method() === 'POST') {
          const body = request.postDataJSON();
          state.writes.push(body);
          const order = {
            ...body,
            orderId: orders.length + 1,
            symbol: 'AAPL',
            status: state.rejectOrders ? 'REJECTED' : 'FILLED',
            reason: state.rejectOrders ? 'Insufficient cash' : null,
            submittedAt: timestamp,
            executionPrice: state.rejectOrders ? null : body.indicativePrice,
            currency: 'USD',
          };
          if (!state.rejectOrders) {
            cash[body.accountId] +=
              (body.side === 'BUY' ? -1 : 1) * body.quantity * body.indicativePrice;
            holdings[body.accountId] += (body.side === 'BUY' ? 1 : -1) * body.quantity;
          }
          orders.unshift(order);
          return send(order, 201);
        }
        const orderMatch = path.match(/^\/api\/orders\/(\d+)(\/fill)?$/);
        if (orderMatch) {
          const order = orders.find((item) => item.orderId === Number(orderMatch[1]));
          return orderMatch[2]
            ? send({ executionPrice: { amount: order?.executionPrice, currency: 'USD' } })
            : send(order);
        }
        unexpected.push(`${request.method()} ${path}`);
        return send({ detail: 'Unmocked request' }, 501);
      });
      await use(state);
      expect(unexpected, 'all API requests must have explicit contract mocks').toEqual([]);
      expect(pageErrors, 'browser runtime errors').toEqual([]);
    },
    { auto: true },
  ],
});
export { expect } from '@playwright/test';
