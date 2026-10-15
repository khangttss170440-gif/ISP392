const {chromium}=require('playwright');
const assert=require('node:assert/strict');
const fs=require('node:fs');
(async()=>{
 const browser=await chromium.launch({channel:process.env.BROWSER_CHANNEL||'msedge',headless:true});
 try {
  const page=await browser.newPage({viewport:{width:390,height:844}});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  let reply={fee:0,verified:true,distanceKm:3.2,message:'Miễn phí trong 5 km.',address:'Test destination'};
  let submitted;
  await page.route('https://maps.google.com/**',r=>r.fulfill({contentType:'text/html',body:'<p>Map preview</p>'}));
  await page.route('http://shipping.test/customer/shipping-quote',r=>{submitted=new URLSearchParams(r.request().postData());return r.fulfill({json:reply});});
  const widget=`<section data-shipping-widget data-endpoint="http://shipping.test/customer/shipping-quote" data-map-key="" data-shop-address="138 Quang Trung, Hóc Môn" data-initial-message="Chưa xác minh khoảng cách"><button type="button" data-shipping-search>Tính phí</button><a data-address-map>Mở Google Maps</a><p data-shipping-status role="status"></p><div class="shipping-map" data-shipping-google-map hidden></div><iframe class="shipping-map" data-shipping-frame></iframe></section>`;
  const html=(saved)=>`<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><style>${fs.readFileSync('web/assets/shipping-map.css','utf8')}</style><form>${saved?'<select name="addressId"><option value="7" data-location="138 Quang Trung, Hóc Môn">Saved address</option><option value="new">New address</option></select>':'<input type="hidden" name="addressId" value="7">'}<input name="addressLine" value="Address detail"><input name="ward" value="Ward"><input name="district" value="District"><input name="province" value="TP.HCM"><input type="hidden" name="shippingFeePreview" value="45000">${widget}</form><span id="shippingFeeLabel">45.000₫</span><span id="checkoutTotalLabel" data-subtotal="100000" data-discount="10000">135.000₫</span>`;
  await page.setContent(html(false));await page.addScriptTag({path:'web/assets/shipping-map.js'});
  await page.locator('[data-shipping-search]').click();
  await page.waitForFunction(()=>document.querySelector('[name="shippingFeePreview"]').value==='0');
  assert.equal(submitted.has('addressId'),false,'Editing address uses current field contents, not stale saved address');
  assert.equal(submitted.get('addressLine'),'Address detail');
  assert.equal(await page.locator('#checkoutTotalLabel').textContent(),'90.000₫');
  assert.equal(await page.locator('#shippingFeeLabel').textContent(),'Miễn phí');
  await page.locator('[name="addressLine"]').fill('Different address');await page.locator('[name="province"]').focus();
  assert.equal(await page.locator('[name="shippingFeePreview"]').inputValue(),'45000');
  assert.match(await page.locator('[data-address-map]').getAttribute('href'),/Different%20address/);
  reply={fee:45000,verified:false,distanceKm:null,message:'Chưa xác minh khoảng cách.',address:'Different address'};
  await page.locator('[data-shipping-search]').click();
  await page.waitForFunction(()=>!document.querySelector('[data-shipping-search]').disabled);
  assert.equal(await page.locator('#checkoutTotalLabel').textContent(),'135.000₫');
  assert.match(await page.locator('[data-shipping-status]').textContent(),/Chưa xác minh/);
  assert.equal(await page.evaluate(()=>document.documentElement.scrollWidth>innerWidth),false);
  await page.setContent(html(true));await page.addScriptTag({path:'web/assets/shipping-map.js'});
  await page.locator('[data-shipping-search]').click();await page.waitForFunction(()=>!document.querySelector('[data-shipping-search]').disabled);
  assert.equal(submitted.get('addressId'),'7');assert.equal(submitted.has('addressLine'),false);
  assert.deepEqual(errors,[]);
  console.log('PASS shipping UI: address search, edit/save address distinction, free/45000 totals, invalidation after changing address, missing-verification notice, Maps URL and mobile layout.');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});
