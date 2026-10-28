// Read-only smoke check against a running app. Requires Playwright and a base URL.
const assert = require('node:assert/strict');
const { chromium } = require('playwright');

(async () => {
    assert.ok(process.env.MENSFORM_BASE_URL, 'Set MENSFORM_BASE_URL to the deployed context root');
    const root = new URL(process.env.MENSFORM_BASE_URL.replace(/\/?$/, '/'));
    const browser = await chromium.launch({
        headless: true,
        ...(process.env.MENSFORM_BROWSER_EXECUTABLE ? { executablePath: process.env.MENSFORM_BROWSER_EXECUTABLE } : {})
    });
    try {
        const page = await browser.newPage();
        page.setDefaultTimeout(15000);
        page.setDefaultNavigationTimeout(15000);
        // Keep this navigation test independent of external font/image hosts.
        await page.route('**/*', (route) => new URL(route.request().url()).origin === root.origin
            ? route.continue() : route.abort());
        const errors = [];
        page.on('pageerror', (error) => errors.push(error.message));
        const contextPath = root.pathname.replace(/\/$/, '');
        const frame = () => page.frames().find((item) => item.parentFrame() === page.mainFrame());
        await page.goto(root.href, { waitUntil: 'domcontentloaded' });
        await page.frameLocator('#app-page').locator('body').waitFor();
        assert.equal(page.url(), root.href);
        console.log('Root shell loaded.');
        await frame().locator(`a[href="${contextPath}/products"]`).first().click();
        await frame().waitForURL('**/products', { waitUntil: 'domcontentloaded' });
        assert.equal(page.url(), root.href);
        console.log('Product listing loaded inside frame.');
        await frame().locator('a[href*="/product-detail?productId="]').first().click();
        await frame().waitForURL('**/product-detail?**', { waitUntil: 'domcontentloaded' });
        assert.equal(page.url(), root.href);
        console.log('Product detail loaded inside frame.');
        await page.goBack({ waitUntil: 'domcontentloaded' });
        await frame().waitForURL('**/products', { waitUntil: 'domcontentloaded' });
        assert.equal(page.url(), root.href);
        await frame().locator('a[href*="/product-detail?productId="]').first().click();
        await frame().waitForURL('**/product-detail?**', { waitUntil: 'domcontentloaded' });
        const selectedProduct = frame().url();
        await page.waitForFunction((url) => history.state?.mensformPage === url, selectedProduct, { timeout: 15000 });
        await page.reload({ waitUntil: 'domcontentloaded' });
        await page.frameLocator('#app-page').locator('body').waitFor();
        await frame().waitForURL(selectedProduct, { waitUntil: 'domcontentloaded' });
        assert.equal(page.url(), root.href);
        console.log('Back and Refresh preserve navigation.');
        await page.goto(new URL('staff/warehouse/orders', root).href, { waitUntil: 'domcontentloaded' });
        await page.frameLocator('#app-page').locator('body').waitFor();
        await frame().waitForURL(/\/login(?:\.jsp)?$/, { waitUntil: 'domcontentloaded' });
        assert.equal(page.url(), root.href);
        await page.goto(new URL('checkout?addressId=5&promotionCode=TEST10', root).href, { waitUntil: 'domcontentloaded' });
        await page.frameLocator('#app-page').locator('body').waitFor();
        await frame().waitForURL(/\/login(?:\.jsp)?$/, { waitUntil: 'domcontentloaded' });
        assert.equal(page.url(), root.href);
        console.log('Protected staff and checkout pages enforce login.');
        await frame().locator(`a[href="${contextPath}/index.jsp"]`).first().click();
        await frame().waitForURL('**/index.jsp', { waitUntil: 'domcontentloaded' });
        await page.frameLocator('#app-page').locator('#chatbot-toggle').click();
        await frame().locator('#chatbot-input').fill('Tìm bộ sưu tập đi làm');
        await frame().locator('#chatbot-form button').click();
        await frame().locator('.chatbot-collection').first().waitFor();
        assert.equal(page.url(), root.href);
        assert.deepEqual(errors, []);
        console.log('Browser navigation passed: products, detail, Back, Refresh, staff/checkout authorization and chatbot.');
    } finally {
        await browser.close();
    }
})().catch((error) => { console.error(error); process.exitCode = 1; });
