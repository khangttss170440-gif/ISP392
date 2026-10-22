// Run with Playwright available on NODE_PATH.
const { chromium } = require('playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');

(async () => {
    const browser = await chromium.launch({ headless: true, channel: 'msedge' });
    try {
        const page = await browser.newPage({ viewport: { width: 1440, height: 900 }, reducedMotion: 'reduce' });
        const errors = [];
        page.on('pageerror', error => errors.push(error.message));
        const source = fs.readFileSync('web/includes/home-products.jspf', 'utf8');
        const start = source.indexOf('<div class="home-carousel"');
        const end = source.indexOf('<% } else { %>', start);
        const loop = /<% for \(Product homeProduct : homeProducts\) \{ %>([\s\S]*?)<% } %>/;
        let markup = source.slice(start, end).replace(loop, (_, card) =>
            Array.from({ length: 7 }, (_, i) => card
                .replaceAll('<%=request.getContextPath()%>', '')
                .replaceAll('<%=homeProduct.getProductID()%>', String(i + 1))
                .replaceAll('<%=navEscape(homeProduct.getProductName())%>', `Sản phẩm ${i + 1}`)
                .replaceAll('<%=navEscape(homeProduct.getCategoryName())%>', 'Phụ kiện')
                .replace(/src="[^"]+"/, 'src="data:image/svg+xml,%3Csvg xmlns=\'http://www.w3.org/2000/svg\' width=\'600\' height=\'750\'%3E%3Crect width=\'600\' height=\'750\' fill=\'%23c6b49a\'/%3E%3C/svg%3E"'))
                .join(''));
        await page.setContent(`<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>*{box-sizing:border-box}body{margin:0}.shell{width:90%;margin:auto}a{color:inherit;text-decoration:none}${fs.readFileSync('web/assets/home-theme.css', 'utf8')}</style><body class="home-theme"><section class="home-products"><div class="shell">${markup}</div></section></body></html>`);
        await page.addScriptTag({ path: 'web/assets/home-products.js' });
        const previous = page.getByRole('button', { name: 'Xem sản phẩm trước' });
        const next = page.getByRole('button', { name: 'Xem sản phẩm tiếp theo' });
        const waitForFirst = (id) => page.waitForFunction(id => {
            const row = document.querySelector('.home-product-row');
            return row.firstElementChild.getAttribute('href').endsWith(`productId=${id}`)
                && row.getAnimations().length === 0;
        }, id);
        assert.equal(await previous.isEnabled(), true);
        assert.equal(await next.isEnabled(), true);
        const cardLayout = await page.locator('.home-product-row').evaluate(row => {
            const cards = [...row.children];
            const styles = cards.map(card => getComputedStyle(card));
            const width = parseFloat(styles[0].width);
            return {
                visible: Math.round((row.clientWidth + 22) / (width + 22)),
                firstScale: new DOMMatrix(styles[0].transform).a,
                centerScale: new DOMMatrix(styles[2].transform).a,
                secondTransform: styles[1].transform
            };
        });
        assert.equal(cardLayout.visible, 5);
        assert.equal(cardLayout.firstScale, 1);
        assert.equal(cardLayout.centerScale, 1.12);
        assert.equal(cardLayout.secondTransform, 'none');
        await next.click();
        await waitForFirst(2);
        assert.equal(await page.locator('.home-product-card').nth(2).evaluate(el => new DOMMatrix(getComputedStyle(el).transform).a), 1.12);
        await previous.click();
        await waitForFirst(1);
        // Check every step across two full rotations in both directions.
        for (let i = 1; i <= 14; i++) {
            await next.click();
            await waitForFirst(i % 7 + 1);
        }
        for (let i = 1; i <= 14; i++) {
            await previous.click();
            await waitForFirst((7 - i % 7) % 7 + 1);
        }
        await page.locator('#homeProductTrack').focus();
        await page.keyboard.press('ArrowRight');
        await waitForFirst(2);
        await page.keyboard.press('ArrowLeft');
        await waitForFirst(1);
        // Real animations must preserve rapid clicks instead of dropping steps.
        await page.emulateMedia({ reducedMotion: 'no-preference' });
        await next.evaluate(button => { for (let i = 0; i < 9; i++) button.click(); });
        await waitForFirst(3);
        await previous.evaluate(button => { for (let i = 0; i < 9; i++) button.click(); });
        await waitForFirst(1);
        assert.equal(await page.locator('.home-product-card').count(), 7);
        assert.equal(await page.locator('.home-product-card').first().evaluate(el => getComputedStyle(el).borderTopStyle), 'solid');
        assert.equal(source.includes('home-products-more'), false);
        assert.match(await page.locator('.home-product-card').first().getAttribute('href'), /product-detail\?productId=1$/);
        await page.setViewportSize({ width: 390, height: 844 });
        await page.waitForTimeout(100);
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth), true);
        assert.equal(await page.locator('#homeProductTrack').evaluate(el => getComputedStyle(el).scrollbarWidth), 'none');
        assert.equal(await next.isEnabled(), true);
        await previous.click();
        await waitForFirst(7);
        await next.click();
        await waitForFirst(1);
        await page.locator('.home-product-card').evaluateAll(cards => cards.slice(1).forEach(card => card.remove()));
        await page.waitForFunction(() => document.querySelector('[data-carousel-next]').disabled);
        assert.equal(await previous.isDisabled(), true);
        assert.deepEqual(errors, []);
        console.log('PASS: continuous loops both directions, rapid animated clicks, hidden scrollbar, keyboard, mobile and single product.');
    } finally {
        await browser.close();
    }
})().catch(error => { console.error(error); process.exitCode = 1; });
