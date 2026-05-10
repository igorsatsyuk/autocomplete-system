const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:4200';
const POLL_TIMEOUT_MS = 180000;
const POLL_INTERVAL_MS = 2000;
const RETRIABLE_STATUS_CODES = new Set([502, 503, 504]);

function buildUrl(path) {
  return new URL(path, FRONTEND_URL).toString();
}

async function sendSearch(query) {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  let lastStatus = 'unknown';

  while (Date.now() < deadline) {
    try {
      const response = await fetch(buildUrl(`/api/search?q=${encodeURIComponent(query)}`));
      if (response.ok) {
        return;
      }
      lastStatus = response.status;
      if (!RETRIABLE_STATUS_CODES.has(response.status)) {
        throw new Error(`Search request failed for '${query}': HTTP ${response.status}`);
      }
    } catch (error) {
      if (Date.now() >= deadline) {
        throw error;
      }
    }

    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  throw new Error(`Search request timed out for '${query}'. Last status: ${lastStatus}`);
}

async function fetchSuggestions(prefix) {
  try {
    const response = await fetch(buildUrl(`/api/complete?q=${encodeURIComponent(prefix)}&limit=10`));
    if (!response.ok) {
      if (RETRIABLE_STATUS_CODES.has(response.status)) {
        return null;
      }
      throw new Error(`Autocomplete request failed for '${prefix}': HTTP ${response.status}`);
    }
    return response.json();
  } catch {
    return null;
  }
}

async function waitForSuggestions(prefix, requiredQueries) {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  let lastEntries = [];

  while (Date.now() < deadline) {
    lastEntries = await fetchSuggestions(prefix);
    if (!Array.isArray(lastEntries)) {
      await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
      continue;
    }
    const values = new Set(lastEntries.map((entry) => entry.query));
    const allPresent = requiredQueries.every((query) => values.has(query));
    if (allPresent) {
      return lastEntries;
    }
    await new Promise((resolve) => setTimeout(resolve, POLL_INTERVAL_MS));
  }

  throw new Error(
    `Timed out waiting for suggestions. Prefix='${prefix}', expected=${requiredQueries.join(', ')}, actual=${JSON.stringify(lastEntries)}`
  );
}

async function run() {
  const runId = Date.now().toString(36);
  const topQuery = `e2esmoke${runId}java`;
  const secondQuery = `e2esmoke${runId}javascript`;
  const prefix = `e2esmoke${runId}ja`;

  await sendSearch(topQuery);
  await sendSearch(topQuery);
  await sendSearch(topQuery);
  await sendSearch(secondQuery);

  const apiEntries = await waitForSuggestions(prefix, [topQuery, secondQuery]);

  const browser = await chromium.launch({ headless: true });

  try {
    const page = await browser.newPage();
    await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' });

    const input = page.locator('[data-testid="search-input"]');
    await input.click();
    await page.keyboard.type(prefix, { delay: 30 });

    await page.waitForFunction(
      () => document.querySelectorAll('[data-testid="suggestion-item"]').length > 0,
      null,
      { timeout: 10000 }
    );

    const renderedSuggestions = await page.$$eval('[data-testid="suggestion-item"]', (nodes) =>
      nodes.map((node) => node.textContent.trim())
    );

    assert.equal(renderedSuggestions[0], topQuery, `Expected top suggestion '${topQuery}', got '${renderedSuggestions[0]}'`);
    assert.ok(renderedSuggestions.includes(secondQuery), `Expected suggestions to include '${secondQuery}'`);

    await page.locator('[data-testid="suggestion-item"]').first().click();
    const selectedValue = await input.inputValue();
    assert.equal(selectedValue, topQuery, 'Clicking first suggestion should copy it to input');

    console.log('E2E smoke passed');
    console.log(
      JSON.stringify(
        {
          frontendUrl: FRONTEND_URL,
          prefix,
          apiEntries,
          renderedSuggestions,
          selectedValue
        },
        null,
        2
      )
    );
  } finally {
    await browser.close();
  }
}

run().catch((error) => {
  console.error('E2E smoke failed');
  console.error(error);
  process.exit(1);
});

