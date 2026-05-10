const assert = require('node:assert/strict');
const { chromium } = require('playwright');

const FRONTEND_URL = process.env.FRONTEND_URL || 'http://localhost:4200';
const POLL_TIMEOUT_MS = 30000;
const POLL_INTERVAL_MS = 1000;

function buildUrl(path) {
  return new URL(path, FRONTEND_URL).toString();
}

async function sendSearch(query) {
  const response = await fetch(buildUrl(`/api/search?q=${encodeURIComponent(query)}`));
  if (!response.ok) {
    throw new Error(`Search request failed for '${query}': HTTP ${response.status}`);
  }
}

async function fetchSuggestions(prefix) {
  const response = await fetch(buildUrl(`/api/complete?q=${encodeURIComponent(prefix)}&limit=10`));
  if (!response.ok) {
    throw new Error(`Autocomplete request failed for '${prefix}': HTTP ${response.status}`);
  }
  return response.json();
}

async function waitForSuggestions(prefix, requiredQueries) {
  const deadline = Date.now() + POLL_TIMEOUT_MS;
  let lastEntries = [];

  while (Date.now() < deadline) {
    lastEntries = await fetchSuggestions(prefix);
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

  const browser = await chromium.launch({
    headless: true,
    channel: 'chrome'
  });

  try {
    const page = await browser.newPage();
    await page.goto(FRONTEND_URL, { waitUntil: 'domcontentloaded' });

    const input = page.locator('input[placeholder="Type your query..."]');
    await input.click();
    await page.keyboard.type(prefix, { delay: 30 });

    await page.waitForFunction(
      () => document.querySelectorAll('ul.suggestions li button').length > 0,
      null,
      { timeout: 10000 }
    );

    const renderedSuggestions = await page.$$eval('ul.suggestions li button', (nodes) =>
      nodes.map((node) => node.textContent.trim())
    );

    assert.equal(renderedSuggestions[0], topQuery, `Expected top suggestion '${topQuery}', got '${renderedSuggestions[0]}'`);
    assert.ok(renderedSuggestions.includes(secondQuery), `Expected suggestions to include '${secondQuery}'`);

    await page.click('ul.suggestions li button:first-child');
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

