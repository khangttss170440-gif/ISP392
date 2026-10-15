// Run with Playwright available on NODE_PATH; defaults to the installed Edge browser.
const { chromium } = require('playwright');
const fs = require('node:fs');
const assert = require('node:assert/strict');
(async () => {
    const browser = await chromium.launch({ headless: true, channel: process.env.BROWSER_CHANNEL || 'msedge' });
    try {
        const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
        const errors = [];
        page.on('pageerror', (error) => errors.push(error.message));
        let cartBody;
        await page.route('http://shop.test/**', (route) => {
            if (route.request().url().endsWith('/cart')) {
                cartBody = new URLSearchParams(route.request().postData());
                return route.fulfill({ contentType: 'text/html', body: '<p>Cart received</p>' });
            }
            return route.fulfill({ contentType: 'image/svg+xml', body: '<svg xmlns="http://www.w3.org/2000/svg" width="600" height="750"><rect width="600" height="750" fill="#c6b49a"/></svg>' });
        });
        const rows = [
            [101, 'Đen', 'M', 450000, 5], [102, 'Đen', 'L', 450000, 0],
            [201, 'Trắng', 'M', 500000, 2], [202, 'Trắng', 'L', 500000, 3],
            [301, 'Be', 'L', 600000, 4], [401, 'Đỏ', 'M', 550000, 0]
        ];
        const colors = ['Đen', 'Trắng', 'Be', 'Đỏ'];
        const html = `<!doctype html><html lang="vi"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>${fs.readFileSync('web/assets/store.css', 'utf8')}</style><style>${fs.readFileSync('web/assets/product-images.css', 'utf8')}</style><body><main class="shell page"><div class="detail-layout"><div class="product-gallery"><div class="detail-art product-photo"><img id="mainProductImage" src="http://shop.test/images/cover.svg" alt="Áo sơ mi"></div><div class="product-image-thumbs">${[['Đen', 'black'], ['Trắng', 'white'], ['', 'shared']].map(([color, name]) => `<button type="button" data-product-image="http://shop.test/images/${name}.svg" data-image-color="${color}" aria-pressed="false"><img alt="${name}" src="http://shop.test/images/${name}.svg"></button>`).join('')}</div><p id="colorImageNote" hidden></p></div><div class="detail-copy"><h1>Áo sơ mi</h1><div id="selected-price"></div><form id="productVariantForm" action="http://shop.test/cart" method="post"><input type="hidden" name="action" value="add"><div id="variantPicker" hidden><fieldset class="variant-color-fieldset"><legend>Màu sắc <span id="selectedColorLabel"></span></legend><div class="variant-color-options">${colors.map(color => `<button type="button" data-variant-color="${color}" class="variant-color-button" aria-pressed="false">${color}</button>`).join('')}</div></fieldset><label for="variantSize">Kích cỡ</label><select id="variantSize" class="select" disabled><option value="">Chọn màu trước</option></select></div><div id="legacyVariantPicker"><select id="variantId" name="variantId" required><option value="">Chọn phiên bản</option>${rows.map(([id,color,size,price,stock]) => `<option value="${id}" data-color="${color}" data-size="${size}" data-amount="${price}" data-price="${new Intl.NumberFormat('vi-VN').format(price)}" data-stock="${stock}" ${stock <= 0 ? 'disabled' : ''}>${color} ${size}</option>`).join('')}</select></div><p id="variantStock"></p><input id="quantity" name="quantity" type="number" min="1" max="99" value="1"><button id="addVariantToCart" type="submit">Thêm vào giỏ</button></form></div></div></main></body></html>`;
        await page.setContent(html);
        await page.locator('[data-image-color="Đen"]').evaluate((button) => { button.dataset.imageColor = ' đEN '; });
        await page.addScriptTag({ path: 'web/assets/product-images.js' });
        await page.addScriptTag({ path: 'web/assets/product-variants.js' });
        assert.equal(await page.locator('#addVariantToCart').isDisabled(), true);
        await page.locator('[data-variant-color="Đen"]').click();
        assert.match(await page.locator('#mainProductImage').getAttribute('src'), /black/);
        assert.equal(await page.locator('[data-image-color="Trắng"]').isVisible(), false);
        assert.equal(await page.locator('#variantSize option[value="102"]').isDisabled(), true);
        await page.locator('#variantSize').selectOption('101');
        assert.equal(await page.locator('#variantId').inputValue(), '101');
        assert.equal(await page.locator('#quantity').getAttribute('max'), '5');
        await page.locator('#quantity').fill('4');
        await page.locator('[data-variant-color="Trắng"]').click();
        assert.match(await page.locator('#mainProductImage').getAttribute('src'), /white/);
        assert.equal(await page.locator('#variantId').inputValue(), '201');
        assert.equal(await page.locator('#quantity').inputValue(), '2');
        assert.match(await page.locator('#selected-price').textContent(), /500/);
        await page.locator('#variantSize').selectOption('202');
        await page.locator('[data-variant-color="Be"]').click();
        assert.equal(await page.locator('#variantId').inputValue(), '301');
        assert.match(await page.locator('#mainProductImage').getAttribute('src'), /shared/);
        assert.equal(await page.locator('#colorImageNote').isVisible(), true);
        await page.locator('[data-variant-color="Đỏ"]').click();
        assert.equal(await page.locator('#addVariantToCart').isDisabled(), true);
        assert.equal(await page.locator('#variantSize').isDisabled(), true);
        assert.equal(await page.locator('#variantId').inputValue(), '');
        await page.locator('[data-variant-color="Đen"]').click();
        await page.locator('#variantSize').selectOption('101');
        await page.locator('[data-image-color=""]').click();
        assert.match(await page.locator('#mainProductImage').getAttribute('src'), /shared/);
        await page.setViewportSize({ width: 390, height: 844 });
        assert.equal(await page.evaluate(() => document.documentElement.scrollWidth > innerWidth), false);
        await page.locator('#quantity').fill('1');
        await Promise.all([page.waitForURL('http://shop.test/cart'), page.locator('#addVariantToCart').click()]);
        assert.equal(cartBody.get('variantId'), '101');
        assert.equal(cartBody.get('quantity'), '1');
        assert.equal(cartBody.get('action'), 'add');
        assert.deepEqual(errors, []);
        console.log('PASS: color photos, filtered gallery, shared-photo fallback, valid sizes, sold-out choices, price/stock, quantity limits, preserving size, mobile layout and correct cart variant submission.');
    } finally { await browser.close(); }
})().catch((error) => { console.error(error); process.exit(1); });
