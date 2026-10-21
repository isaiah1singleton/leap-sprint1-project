import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { LeapApi } from './api.js';
import { createStocksServer } from './server.js';

try {
  const api = new LeapApi({
    baseUrl: process.env.LEAP_API_BASE_URL,
    accessToken: process.env.LEAP_ACCESS_TOKEN,
  });
  const defaultAccountId = process.env.LEAP_ACCOUNT_ID
    ? Number(process.env.LEAP_ACCOUNT_ID) : undefined;
  const server = createStocksServer({ api, defaultAccountId });
  await server.connect(new StdioServerTransport());
} catch (error) {
  // stdout belongs exclusively to the MCP protocol.
  console.error('LEAP MCP could not start: ' + error.message);
  process.exitCode = 1;
}
