import { Page } from '@playwright/test';
import { test, expect } from './fixtures';

async function signIn(page: Page, email = 'alice@example.test') {
  await page.goto('/login');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Password', { exact: true }).fill('password123');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  await expect(page).toHaveURL(/\/app\/(overview|admin-audit)$/);
  if (!email.startsWith('admin'))
    await page.getByLabel('Account', { exact: true }).selectOption('42');
}
async function navigate(page: Page, name: string) {
  await page
    .getByRole('navigation', { name: 'Main navigation' })
    .getByRole('link', { name, exact: true })
    .click();
}
async function reviewBuy(page: Page) {
  await navigate(page, 'Transact');
  await page.getByRole('button', { name: 'AAPL', exact: true }).click();
  await page.getByLabel('Quantity', { exact: true }).fill('2');
  await page.getByRole('button', { name: 'Review buy', exact: true }).click();
  await expect(page.getByRole('dialog')).toBeVisible();
}

test('reviews without writing, confirms exactly once, then displays the authoritative fill', async ({
  page,
  api,
}) => {
  await signIn(page);
  await reviewBuy(page);
  expect(api.writes).toHaveLength(0);
  await expect(page.getByRole('dialog')).toContainText('$200.00');
  await page.getByRole('button', { name: 'Confirm buy', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Order filled');
  expect(api.writes).toHaveLength(1);
  expect(api.writes[0]).toMatchObject({
    accountId: 42,
    instrumentId: 7,
    side: 'BUY',
    quantity: 2,
    indicativePrice: 100,
  });
  await page.getByRole('button', { name: 'Done', exact: true }).click();
  await navigate(page, 'Order history');
  await expect(page.locator('tbody tr').first()).toContainText('ORD-1');
  await expect(page.locator('tbody tr').first()).toContainText('FILLED');
  await navigate(page, 'Dashboard');
  await expect(page.getByRole('region', { name: 'Account cash' })).toContainText('$800.00');
});

test('canceling a review sends no trade', async ({ page, api }) => {
  await signIn(page);
  await reviewBuy(page);
  await page.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  expect(api.writes).toHaveLength(0);
});

test('a rejected order leaves the cash balance unchanged', async ({ page, api }) => {
  api.rejectOrders = true;
  await signIn(page);
  await reviewBuy(page);
  await page.getByRole('button', { name: 'Confirm buy', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Insufficient cash');
  await page.getByRole('button', { name: 'Done', exact: true }).click();
  await navigate(page, 'Dashboard');
  await expect(page.getByRole('region', { name: 'Account cash' })).toContainText('$1,000.00');
});

test('deposits, prevents overdrafts, and displays committed cash activity', async ({ page }) => {
  await signIn(page);
  await page.getByRole('button', { name: 'Deposit', exact: true }).click();
  await page.getByLabel('Amount (USD)', { exact: true }).fill('100.25');
  await page.getByRole('button', { name: 'Confirm deposit', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Deposit completed');
  await page.getByRole('button', { name: 'Done', exact: true }).click();
  await page.getByRole('button', { name: 'Withdraw', exact: true }).click();
  await page.getByLabel('Amount (USD)', { exact: true }).fill('5000');
  await page.getByRole('button', { name: 'Confirm withdrawal', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Insufficient cash');
  await page.getByLabel('Amount (USD)', { exact: true }).fill('50.25');
  await page.getByRole('button', { name: 'Confirm withdrawal', exact: true }).click();
  await expect(page.getByRole('dialog')).toContainText('Withdrawal completed');
  await page.getByRole('button', { name: 'Done', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Account cash' })).toContainText('$1,050.00');
  await navigate(page, 'Cash activity');
  await expect(page.locator('main')).toContainText('+$100.25');
  await expect(page.locator('main')).toContainText('−$50.25');
});

test('switching accounts replaces balances and sign-out clears account and auth state', async ({
  page,
}) => {
  await signIn(page);
  await expect(page.getByRole('region', { name: 'Account cash' })).toContainText('$1,000.00');
  await page.getByLabel('Account', { exact: true }).selectOption('43');
  await expect(page.getByRole('region', { name: 'Account cash' })).toContainText('$250.00');
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await expect(page).toHaveURL(/\/login$/);
  expect(await page.evaluate(() => sessionStorage.getItem('accessToken'))).toBeNull();
  expect(await page.evaluate(() => sessionStorage.getItem('selectedAccount'))).toBeNull();
  await page.goBack();
  await expect(page).toHaveURL(/\/login$/);
});

test('clients cannot open admin routes, while admins see the audit workspace', async ({ page }) => {
  await signIn(page);
  await page.goto('/app/admin-audit');
  await expect(page).toHaveURL(/\/app\/overview$/);
  await page.getByRole('button', { name: 'Sign out', exact: true }).click();
  await signIn(page, 'admin@example.test');
  await expect(page.getByRole('link', { name: 'Audit ledger', exact: true })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Transact', exact: true })).toHaveCount(0);
});
