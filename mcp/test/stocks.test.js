import assert from 'node:assert/strict';
import { createServer } from 'node:http';
import { once } from 'node:events';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';
import { Client } from '@modelcontextprotocol/sdk/client/index.js';
import { StdioClientTransport } from '@modelcontextprotocol/sdk/client/stdio.js';
import { InMemoryTransport } from '@modelcontextprotocol/sdk/inMemory.js';
import { ElicitRequestSchema } from '@modelcontextprotocol/sdk/types.js';
import { LeapApi } from '../src/api.js';
import { resolveDates, inDateRange } from '../src/dates.js';
import { createStocksServer } from '../src/server.js';

const NOW = new Date('2026-10-21T12:00:00.000Z');
const TOKEN = 'test-session-never-expose';
const ACCOUNT = { accountId: 7, accountName: 'Investment account', accountStatus: 'ACTIVE' };
const INSTRUMENT = { instrumentId: 12, symbol: 'AAPL', market: 'US', assetClass: 'EQUITY', isTradable: true, quoteCurrency: 'USD' };
const QUOTE = { symbol: 'AAPL', price: 214.62, currency: 'USD', asOf: '2026-10-21T11:59:30Z' };
const TRADE = { symbol: 'aapl', side: 'BUY', quantity: 2 };
const timestamps = ['2026-09-01T12:00:00Z', '2026-10-01T12:00:00Z', '2026-10-20T12:00:00Z'];

async function backend(t, options = {}) {
  const requests = [];
  const http = createServer(async (req, res) => {
    let text = '';
    for await (const chunk of req) text += chunk;
    const body = text ? JSON.parse(text) : undefined;
    requests.push({ method: req.method, path: req.url, body, authorization: req.headers.authorization });
    res.setHeader('Content-Type', 'application/json');
    const send = (value, status = 200) => { res.statusCode = status; res.end(JSON.stringify(value)); };
    if (req.headers.authorization !== 'Bearer ' + TOKEN) return send({ detail: 'Unauthorized' }, 401);
    if (options.failureStatus) return send({ detail: TOKEN }, options.failureStatus);
    if (req.url === '/api/accounts') return send(options.accounts ?? [ACCOUNT]);
    if (req.url === '/api/accounts/7/holdings') {
      return send([{ instrumentId: 12, symbol: 'AAPL', name: 'Apple', totalQuantity: 10, availableQuantity: 10, reservedQuantity: 0 }]);
    }
    if (req.url.startsWith('/api/accounts/7/portfolio-history?range=')) {
      return send({ accountId: 7, currency: 'USD', range: new URL(req.url, 'http://localhost').searchParams.get('range'),
        recordingStartedAt: timestamps[0], points: options.historyPoints ?? timestamps.map((observedAt) => ({ observedAt, portfolioValue: 1000 })) });
    }
    if (req.url === '/api/orders?accountId=7') {
      return send(timestamps.map((submittedAt, i) => ({
        orderId: i + 1, accountId: 7, submittedAt, symbol: 'AAPL', side: 'BUY', status: 'FILLED',
      })));
    }
    if (req.url === '/api/instruments') return send(options.instruments ?? [INSTRUMENT]);
    if (req.url === '/api/market/quotes/AAPL') {
      return send(options.quoteResult ?? { symbol: 'AAPL', quote: QUOTE, source: 'fauxnance', stale: false, error: null });
    }
    if (req.url === '/api/orders' && req.method === 'POST') {
      if (options.disconnectOnSubmit) { req.socket.destroy(); return; }
      if (options.postStatus) return send({ detail: TOKEN }, options.postStatus);
      if (options.invalidOrderResponse) return send({ accepted: true }, 201);
      return send({ orderId: 55, symbol: 'AAPL', ...body, status: options.orderStatus ?? 'ACCEPTED',
        reason: options.orderStatus === 'REJECTED' ? 'INSUFFICIENT_CASH: Cash balance does not cover the order.' : null }, 201);
    }
    if (req.url === '/api/orders/55') return send({ orderId: 55, accountId: 7, status: 'FILLED' });
    return send({ detail: 'Not found' }, 404);
  });
  http.listen(0, '127.0.0.1');
  await once(http, 'listening');
  t.after(() => { http.closeAllConnections(); return new Promise((resolve) => http.close(resolve)); });
  return { requests, baseUrl: 'http://127.0.0.1:' + http.address().port };
}

async function harness(t, options = {}) {
  const app = await backend(t, options);
  const api = new LeapApi({ baseUrl: app.baseUrl, accessToken: TOKEN });
  const server = createStocksServer({ api, now: options.now ?? (() => NOW),
    defaultAccountId: options.defaultAccountId, confirmationTimeoutMs: options.confirmationTimeoutMs ?? 120_000 });
  const client = new Client({ name: 'test-host', version: '1.0.0' }, {
    capabilities: options.noElicitation ? {} : { elicitation: options.elicitation ?? { form: {} } },
  });
  if (!options.noElicitation) client.setRequestHandler(ElicitRequestSchema, async (request) => {
    if (options.confirm) return options.confirm(request, app);
    return { action: 'accept', content: { confirm: true } };
  });
  const [clientTransport, serverTransport] = InMemoryTransport.createLinkedPair();
  await server.connect(serverTransport);
  await client.connect(clientTransport);
  t.after(async () => { await client.close(); await server.close(); });
  return { ...app, client, server };
}
const call = (client, name, args) => client.callTool({ name, ...(args === undefined ? {} : { arguments: args }) });
const posts = (app) => app.requests.filter((req) => req.method === 'POST');

test('UTC date defaults, natural language, strict calendar validation and inclusive boundaries', () => {
  assert.deepEqual(resolveDates({}, NOW), { range: '1M', start: '2026-09-21T12:00:00.000Z', end: NOW.toISOString() });
  for (const filter of ['past week', 'filter by past week', '  FILTER BY Past Week  ', '1W', 'last week']) {
    assert.equal(resolveDates({ filter }, NOW).start, '2026-10-14T12:00:00.000Z');
  }
  assert.equal(resolveDates({ filter: 'past day' }, NOW).range, '1D');
  assert.equal(resolveDates({ filter: 'all time' }, NOW).start, '1970-01-01T00:00:00.000Z');
  const range = resolveDates({ startDate: '2026-10-01', endDate: '2026-10-02' }, NOW);
  assert.equal(range.range, 'ALL');
  assert(inDateRange('2026-10-01T00:00:00Z', range));
  assert(inDateRange('2026-10-02T23:59:59.999Z', range));
  assert(!inDateRange('2026-10-03T00:00:00Z', range));
  assert(!inDateRange('invalid', range));
  for (const input of [
    { startDate: '2026-02-30', endDate: '2026-03-01' },
    { startDate: '2026-10-02', endDate: '2026-10-01' },
    { startDate: '2026-10-01' }, { endDate: '2026-10-01' },
    { startDate: '2026-10-01', endDate: '2026-10-22' },
    { filter: 'past week', startDate: '2026-10-01', endDate: '2026-10-02' }, { filter: 'nonsense' },
  ]) assert.throws(() => resolveDates(input, NOW));
});

test('all four capabilities are discoverable, and no-argument stock view defaults to 30 days', async (t) => {
  const { client, requests } = await harness(t);
  const tools = (await client.listTools()).tools;
  assert.deepEqual(tools.map((tool) => tool.name), ['list_accounts', 'view_stocks', 'get_order', 'place_trade']);
  assert.match(client.getInstructions(), /confirmation UI/);
  const write = tools.find((tool) => tool.name === 'place_trade');
  assert.equal(write.annotations.destructiveHint, true);
  assert.equal(write.annotations.idempotentHint, false);
  assert(!('confirm' in write.inputSchema.properties));
  assert.equal((await client.listPrompts()).prompts.length, 2);
  assert.equal((await client.listResources()).resources.length, 2);
  assert.equal((await client.listResourceTemplates()).resourceTemplates.length, 3);
  const view = (await call(client, 'view_stocks')).structuredContent;
  assert.equal(view.dateRange.range, '1M');
  assert.equal(view.holdings[0].totalQuantity, 10);
  assert.equal(view.history.points.length, 2);
  assert.equal(view.orders.length, 2);
  assert(requests.some((req) => req.path.endsWith('portfolio-history?range=1M')));
  assert(requests.every((req) => req.authorization === 'Bearer ' + TOKEN));
  assert.equal(posts({ requests }).length, 0);
});

test('past week and custom dates filter observations and activity without hiding current holdings', async (t) => {
  const { client, requests } = await harness(t);
  const weekly = (await call(client, 'view_stocks', { filter: 'filter by past week' })).structuredContent;
  assert.equal(weekly.history.points.length, 1);
  assert.equal(weekly.orders.length, 1);
  assert.equal(weekly.holdings.length, 1);
  const custom = (await call(client, 'view_stocks', { startDate: '2026-10-01', endDate: '2026-10-01' })).structuredContent;
  assert.equal(custom.orders.length, 1);
  assert.equal(custom.orders[0].orderId, 2);
  assert.equal(custom.history.points.length, 1);
  assert(requests.some((req) => req.path.endsWith('portfolio-history?range=ALL')));
  const unmatched = (await call(client, 'view_stocks', { symbol: 'MSFT' })).structuredContent;
  assert.equal(unmatched.holdings.length, 0);
  assert.equal(unmatched.orders.length, 0);
  assert.equal(unmatched.history.points.length, 2);
});

test('resources use the same ownership and date filters, and prompts do not place orders', async (t) => {
  const app = await harness(t);
  const accounts = await app.client.readResource({ uri: 'leap://accounts' });
  assert.equal(JSON.parse(accounts.contents[0].text).accounts[0].accountId, 7);
  const defaultStocks = await app.client.readResource({ uri: 'leap://stocks' });
  assert.equal(JSON.parse(defaultStocks.contents[0].text).dateRange.range, '1M');
  const resource = await app.client.readResource({ uri: 'leap://accounts/7/stocks/past%20week' });
  assert.equal(JSON.parse(resource.contents[0].text).orders.length, 1);
  await assert.rejects(app.client.readResource({ uri: 'leap://accounts/999/stocks' }), /Choose one of your accounts/);
  const review = await app.client.getPrompt({ name: 'review_stocks', arguments: {} });
  assert.match(review.messages[0].content.text, /past month/);
  const trade = await app.client.getPrompt({ name: 'trade_stock', arguments: { symbol: 'AAPL', side: 'BUY', quantity: '2' } });
  assert.match(trade.messages[0].content.text, /Never answer that confirmation/);
  assert.equal(posts(app).length, 0);
});

for (const side of ['BUY', 'SELL']) {
  test(side + ' submits exactly the previewed payload only after client confirmation', async (t) => {
    let asked = 0;
    const app = await harness(t, { confirm: async (request, state) => {
      asked++;
      assert.equal(posts(state).length, 0);
      assert.equal(request.params.requestedSchema.properties.confirm.default, false);
      assert.match(request.params.message, new RegExp(side + ' 2 shares of AAPL'));
      assert.match(request.params.message, /#7/);
      assert.match(request.params.message, /429.24 USD/);
      assert.match(request.params.message, /2026-10-21T11:59:30Z/);
      return { action: 'accept', content: { confirm: true, quantity: 999 } };
    } });
    const response = await call(app.client, 'place_trade', { ...TRADE, side });
    assert.equal(response.isError, undefined);
    assert.equal(response.structuredContent.status, 'ACCEPTED');
    assert.equal(asked, 1);
    assert.deepEqual(posts(app)[0].body, {
      accountId: 7, instrumentId: 12, side, quantity: 2,
      indicativePrice: 214.62, quoteAsOf: QUOTE.asOf, quoteCurrency: 'USD',
    });
    assert.equal(posts(app).length, 1);
    const status = await call(app.client, 'get_order', { orderId: 55 });
    assert.equal(status.structuredContent.order.status, 'FILLED');
  });
}

for (const answer of [
  { action: 'decline' }, { action: 'cancel' }, { action: 'accept', content: { confirm: false } },
]) {
  test('confirmation ' + JSON.stringify(answer) + ' never submits', async (t) => {
    const app = await harness(t, { confirm: async () => answer });
    const response = await call(app.client, 'place_trade', TRADE);
    assert.equal(response.structuredContent.status, 'cancelled');
    assert.equal(posts(app).length, 0);
  });
}

for (const capability of ['none', 'url-only']) {
  test(capability + ' confirmation support fails closed', async (t) => {
    const app = await harness(t, { noElicitation: capability === 'none', elicitation: { url: {} } });
    const response = await call(app.client, 'place_trade', TRADE);
    assert.equal(response.isError, true);
    assert.match(response.content[0].text, /must support form elicitation/);
    assert.equal(app.requests.length, 0);
  });
}

test('legacy form capability still requires user confirmation', async (t) => {
  let asked = false;
  const app = await harness(t, { elicitation: {}, confirm: async () => {
    asked = true;
    return { action: 'decline' };
  } });
  assert.equal((await call(app.client, 'place_trade', TRADE)).structuredContent.status, 'cancelled');
  assert(asked);
  assert.equal(posts(app).length, 0);
});

test('model cannot supply confirmation or alter quote, quantity must be positive whole shares', async (t) => {
  const app = await harness(t);
  for (const args of [
    { ...TRADE, confirm: true }, { ...TRADE, indicativePrice: 1 }, { ...TRADE, quantity: 0 },
    { ...TRADE, quantity: -1 }, { ...TRADE, quantity: 1.5 }, { ...TRADE, side: 'CANCEL' },
  ]) assert.equal((await call(app.client, 'place_trade', args)).isError, true);
  assert.equal(posts(app).length, 0);
  assert.equal(app.requests.length, 0);
});

test('ambiguous accounts and non-owned accounts fail before reading portfolio or quoting orders', async (t) => {
  const app = await harness(t, { accounts: [ACCOUNT, { ...ACCOUNT, accountId: 8 }] });
  assert.equal((await call(app.client, 'view_stocks')).isError, true);
  assert.equal((await call(app.client, 'place_trade', { ...TRADE, accountId: 999 })).isError, true);
  assert(app.requests.every((req) => req.path === '/api/accounts'));
  const explicit = await call(app.client, 'view_stocks', { accountId: 7 });
  assert.equal(explicit.structuredContent.account.accountId, 7);
});

test('configured account is still checked for ownership', async (t) => {
  const app = await harness(t, { defaultAccountId: 999 });
  assert.equal((await call(app.client, 'view_stocks')).isError, true);
  assert(app.requests.every((req) => req.path === '/api/accounts'));
});

for (const quoteResult of [
  { quote: QUOTE, stale: true },
  { quote: null, error: { code: 'QUOTE_UNAVAILABLE' } },
  { quote: { ...QUOTE, currency: 'GBP' }, stale: false },
  { quote: { ...QUOTE, price: 0 }, stale: false },
  { quote: { ...QUOTE, asOf: '2026-10-22T12:00:00Z' }, stale: false },
]) {
  test('unusable quote is never submitted: ' + JSON.stringify(quoteResult), async (t) => {
    const app = await harness(t, { quoteResult, confirm: async () => assert.fail('No confirmation with unusable quote') });
    assert.equal((await call(app.client, 'place_trade', TRADE)).isError, true);
    assert.equal(posts(app).length, 0);
  });
}

test('ambiguous instruments require market selection', async (t) => {
  const app = await harness(t, { instruments: [INSTRUMENT, { ...INSTRUMENT, market: 'OTHER' }] });
  assert.equal((await call(app.client, 'place_trade', TRADE)).isError, true);
  assert.equal((await call(app.client, 'place_trade', { ...TRADE, market: 'US' })).structuredContent.status, 'ACCEPTED');
  assert.equal(posts(app).length, 1);
});

test('expired or failed confirmation never writes', async (t) => {
  let current = NOW;
  const app = await harness(t, { now: () => current, confirm: async () => {
    current = new Date(NOW.getTime() + 120_000);
    return { action: 'accept', content: { confirm: true } };
  } });
  assert.match((await call(app.client, 'place_trade', TRADE)).content[0].text, /Confirmation expired/);
  assert.equal(posts(app).length, 0);
});

test('confirmation transport failure and invalid boolean fail closed', async (t) => {
  for (const confirm of [
    async () => { throw new Error('Confirmation UI closed'); },
    async () => ({ action: 'accept', content: { confirm: 'true' } }),
  ]) {
    const app = await harness(t, { confirm });
    assert.equal((await call(app.client, 'place_trade', TRADE)).isError, true);
    assert.equal(posts(app).length, 0);
  }
});

test('confirmation timeout never submits', async (t) => {
  const app = await harness(t, { confirmationTimeoutMs: 25, confirm: async () => new Promise(() => {}) });
  assert.equal((await call(app.client, 'place_trade', TRADE)).isError, true);
  assert.equal(posts(app).length, 0);
});

test('concurrent trade cannot bypass the outstanding confirmation', async (t) => {
  let release;
  let asked;
  const ready = new Promise((resolve) => { asked = resolve; });
  const app = await harness(t, { confirm: async () => {
    asked();
    return new Promise((resolve) => { release = resolve; });
  } });
  const first = call(app.client, 'place_trade', TRADE);
  await ready;
  const second = await call(app.client, 'place_trade', { ...TRADE, side: 'SELL' });
  assert.equal(second.isError, true);
  assert.match(second.content[0].text, /Another trade/);
  assert.equal(posts(app).length, 0);
  release({ action: 'decline' });
  await first;
});

test('backend rejection is returned accurately, and lost responses never trigger automatic retry', async (t) => {
  const rejected = await harness(t, { orderStatus: 'REJECTED' });
  const response = await call(rejected.client, 'place_trade', TRADE);
  assert.equal(response.structuredContent.status, 'REJECTED');
  assert.match(response.structuredContent.order.reason, /INSUFFICIENT_CASH/);
  const lost = await harness(t, { disconnectOnSubmit: true });
  const unknown = await call(lost.client, 'place_trade', TRADE);
  assert.equal(unknown.structuredContent.status, 'submission_unknown');
  assert.match(unknown.structuredContent.message, /Do not resubmit/);
  assert.equal(posts(lost).length, 1);
});

test('authorization errors are clear and raw secrets are not returned', async (t) => {
  const app = await harness(t, { failureStatus: 401 });
  const response = await call(app.client, 'view_stocks');
  assert.equal(response.isError, true);
  assert.match(response.content[0].text, /session expired or is invalid/);
  assert(!JSON.stringify(response).includes(TOKEN));
  assert.throws(() => new LeapApi({ accessToken: '' }), /LEAP_ACCESS_TOKEN/);
});

test('real stdio process initializes and reads the existing REST API', async (t) => {
  const app = await backend(t);
  const client = new Client({ name: 'stdio-test', version: '1.0.0' });
  const transport = new StdioClientTransport({
    command: process.execPath,
    args: [fileURLToPath(new URL('../src/index.js', import.meta.url))],
    env: { LEAP_API_BASE_URL: app.baseUrl, LEAP_ACCESS_TOKEN: TOKEN },
    stderr: 'pipe',
  });
  t.after(() => client.close());
  await client.connect(transport);
  const response = await call(client, 'list_accounts');
  assert.equal(response.structuredContent.accounts[0].accountId, 7);
  assert.equal((await client.listTools()).tools.length, 4);
});

test('relative views include the observation recorded while the history API is read', async (t) => {
  let clockReads = 0;
  const app = await harness(t, {
    now: () => new Date(NOW.getTime() + clockReads++ * 1000),
    historyPoints: [{ observedAt: '2026-10-21T12:00:00.500Z', portfolioValue: 1234 }],
  });
  const view = (await call(app.client, 'view_stocks')).structuredContent;
  assert.equal(view.history.points.length, 1);
  assert.equal(view.history.points[0].portfolioValue, 1234);
});

test('account resource templates support default and explicit date views', async (t) => {
  const app = await harness(t);
  const defaultView = await app.client.readResource({ uri: 'leap://accounts/7/stocks' });
  assert.equal(JSON.parse(defaultView.contents[0].text).dateRange.range, '1M');
  const dated = await app.client.readResource({ uri: 'leap://accounts/7/stocks/from/2026-10-01/to/2026-10-01' });
  assert.equal(JSON.parse(dated.contents[0].text).history.points.length, 1);
  await assert.rejects(app.client.readResource({ uri: 'leap://accounts/7/stocks/from/2026-02-30/to/2026-03-01' }), /valid calendar dates/);
  assert.equal(posts(app).length, 0);
});

test('malformed and server-error submission responses preserve uncertainty without retry', async (t) => {
  for (const options of [{ invalidOrderResponse: true }, { postStatus: 500 }]) {
    const app = await harness(t, options);
    const response = await call(app.client, 'place_trade', TRADE);
    assert.equal(response.structuredContent.status, 'submission_unknown');
    assert.equal(posts(app).length, 1);
    assert(!JSON.stringify(response).includes(TOKEN));
  }
});

test('cancelling the tool while confirmation is open never submits, even if approval arrives late', async (t) => {
  let answer;
  let notifyReady;
  const ready = new Promise((resolve) => { notifyReady = resolve; });
  const app = await harness(t, { confirm: async () => {
    notifyReady();
    return new Promise((resolve) => { answer = resolve; });
  } });
  const cancellation = new AbortController();
  const pending = app.client.callTool({ name: 'place_trade', arguments: TRADE }, undefined, { signal: cancellation.signal });
  const rejected = assert.rejects(pending);
  await ready;
  cancellation.abort();
  await rejected;
  answer({ action: 'accept', content: { confirm: true } });
  // A round trip gives the server time to handle the cancellation and late reply.
  await call(app.client, 'list_accounts');
  assert.equal(posts(app).length, 0);
});
