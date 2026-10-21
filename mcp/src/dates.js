const DAY = 86_400_000;

const periods = new Map([
  ['1d', ['1D', 1]], ['today', ['1D', 1]], ['past day', ['1D', 1]], ['last day', ['1D', 1]],
  ['1w', ['1W', 7]], ['past week', ['1W', 7]], ['last week', ['1W', 7]],
  ['1m', ['1M', 30]], ['past month', ['1M', 30]], ['last month', ['1M', 30]],
  ['all', ['ALL', null]], ['all time', ['ALL', null]],
]);

function dateBoundary(value, end) {
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    throw new Error('Use YYYY-MM-DD dates.');
  }
  const time = Date.parse(value + 'T00:00:00.000Z');
  if (!Number.isFinite(time) || new Date(time).toISOString().slice(0, 10) !== value) {
    throw new Error('Use valid calendar dates.');
  }
  return time + (end ? DAY - 1 : 0);
}

/** Matches PortfolioHistoryService: a month is the preceding 30 days, in UTC. */
export function resolveDates({ filter, startDate, endDate } = {}, now = new Date()) {
  const until = now.getTime();
  if (!Number.isFinite(until)) throw new Error('The current date is invalid.');
  let since;
  let range;
  if (startDate !== undefined || endDate !== undefined) {
    if (filter !== undefined) throw new Error('Use either filter or explicit dates, not both.');
    if (!startDate || !endDate) throw new Error('Provide both startDate and endDate.');
    since = dateBoundary(startDate, false);
    const end = dateBoundary(endDate, true);
    if (since > end) throw new Error('startDate must be on or before endDate.');
    if (endDate > now.toISOString().slice(0, 10)) throw new Error('Dates cannot be in the future.');
    return { range: 'ALL', start: new Date(since).toISOString(), end: new Date(end).toISOString() };
  }
  const normalized = (filter ?? 'past month').trim().toLowerCase()
    .replace(/^filter\s+by\s+/, '').replace(/\s+/g, ' ');
  const period = periods.get(normalized);
  if (!period) throw new Error('Choose past day, past week, past month, all time, or an explicit date range.');
  [range, since] = period;
  since = since === null ? 0 : until - since * DAY;
  return { range, start: new Date(since).toISOString(), end: now.toISOString() };
}

export function inDateRange(timestamp, dates) {
  const time = Date.parse(timestamp);
  return Number.isFinite(time) && time >= Date.parse(dates.start) && time <= Date.parse(dates.end);
}
