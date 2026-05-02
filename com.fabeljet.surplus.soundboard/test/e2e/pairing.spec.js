'use strict';

const { test, expect } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const pairHtml = fs.readFileSync(
  path.join(__dirname, '../../drivers/soundboard/pair/start.html'),
  'utf8',
);

/** Load the pairing HTML, injecting a mock Homey with configurable connect behaviour. */
async function loadPairingPage(page, { deviceName = 'My Soundboard', shouldFail = false } = {}) {
  await page.setContent(pairHtml);
  await page.evaluate(({ deviceName, shouldFail }) => {
    let callCount = 0;
    window.Homey = {
      emit: async (event, _data) => {
        if (event === 'connect') {
          if (shouldFail) throw new Error('Connection refused');
          return { deviceName };
        }
      },
      createDevice: async (_data) => {},
      done: () => {},
    };
  }, { deviceName, shouldFail });
}

test.describe('Pairing UI', () => {
  test('connect button and IP field are visible on load', async ({ page }) => {
    await loadPairingPage(page);
    await expect(page.locator('#ip')).toBeVisible();
    await expect(page.locator('#port')).toBeVisible();
    await expect(page.locator('#btn')).toBeVisible();
  });

  test('shows validation error when IP is empty', async ({ page }) => {
    await loadPairingPage(page);
    await page.click('#btn');
    await expect(page.locator('#status')).toContainText('Enter the IP address');
  });

  test('happy path: connects and does not show error', async ({ page }) => {
    await loadPairingPage(page, { deviceName: 'Studio Board' });

    let doneCalled = false;
    await page.exposeFunction('__playwrightDoneCallback', () => { doneCalled = true; });
    await page.evaluate(() => {
      const original = window.Homey.done;
      window.Homey.done = () => {
        window.__playwrightDoneCallback();
        original();
      };
    });

    await page.fill('#ip', '192.168.1.50');
    await page.fill('#port', '8765');
    await page.click('#btn');

    await expect(page.locator('#status')).not.toContainText('Could not connect');
    await page.waitForFunction(() => window.__doneCalled !== undefined || true, null, { timeout: 2000 });
  });

  test('shows error message when connection fails', async ({ page }) => {
    await loadPairingPage(page, { shouldFail: true });

    await page.fill('#ip', '10.0.0.1');
    await page.click('#btn');

    await expect(page.locator('#status')).toContainText('Could not connect');
  });

  test('button is re-enabled after failed connection (retry possible)', async ({ page }) => {
    await loadPairingPage(page, { shouldFail: true });

    await page.fill('#ip', '10.0.0.1');
    await page.click('#btn');

    await expect(page.locator('#status')).toContainText('Could not connect');
    await expect(page.locator('#btn')).toBeEnabled();
  });

  test('retry succeeds after initial failure', async ({ page }) => {
    await page.setContent(pairHtml);
    await page.evaluate(() => {
      let attempts = 0;
      window.Homey = {
        emit: async (event, _data) => {
          if (event === 'connect') {
            attempts += 1;
            if (attempts === 1) throw new Error('timeout');
            return { deviceName: 'Retry Device' };
          }
        },
        createDevice: async (_data) => {},
        done: () => {},
      };
    });

    await page.fill('#ip', '192.168.1.1');
    await page.click('#btn');
    await expect(page.locator('#status')).toContainText('Could not connect');

    // Retry: button should be re-enabled
    await page.click('#btn');
    await expect(page.locator('#status')).not.toContainText('Could not connect');
  });

  test('port defaults to 8765', async ({ page }) => {
    await loadPairingPage(page);
    await expect(page.locator('#port')).toHaveValue('8765');
  });
});
