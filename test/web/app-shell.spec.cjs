const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');
const script = fs.readFileSync(path.join(__dirname, '../../web/assets/app-shell.js'), 'utf8');

function openShell(initialPage, state = null, pathname = '/store/') {
    const listeners = {};
    const frame = { title: 'Store', addEventListener: (name, handler) => { listeners[name] = handler; } };
    const history = {
        state, url: pathname,
        replaceState(value, _, url) { this.state = value; this.url = url; }
    };
    const document = {
        title: 'Store',
        querySelector: () => ({ dataset: { appRoot: '/store/', initialPage } }),
        getElementById: () => frame
    };
    vm.runInNewContext(script, {
        URL, document,
        window: { history, location: { origin: 'https://example.test', pathname },
            addEventListener: (name, handler) => { listeners[name] = handler; } }
    });
    return { frame, listeners, history, document };
}

test('direct checkout link keeps its parameters inside the frame and a single root address', () => {
    const shell = openShell('/store/checkout?addressId=5&promotionCode=TEST10', null, '/store/checkout');
    assert.equal(shell.history.url, '/store/');
    assert.equal(shell.frame.src, 'https://example.test/store/checkout?addressId=5&promotionCode=TEST10');
});

test('navigation updates title and refresh state without exposing staff or order routes', () => {
    const shell = openShell('/store/index.jsp');
    shell.frame.contentWindow = { location: { href: 'https://example.test/store/staff/warehouse/order-detail?orderId=34' } };
    shell.frame.contentDocument = { title: 'Order 34 | Warehouse' };
    shell.listeners.load();
    assert.equal(shell.history.url, '/store/');
    assert.equal(shell.document.title, 'Order 34 | Warehouse');
    const reloaded = openShell('/store/index.jsp', shell.history.state);
    assert.equal(reloaded.frame.src, shell.frame.contentWindow.location.href);
});

test('a new external return link takes priority over a previous page state', () => {
    const shell = openShell('/store/payment/vnpay-return?vnp_TxnRef=36',
        { mensformPage: 'https://example.test/store/checkout' }, '/store/payment/vnpay-return');
    assert.equal(shell.frame.src, 'https://example.test/store/payment/vnpay-return?vnp_TxnRef=36');
});

test('page readiness updates refresh state before images finish and ignores untrusted messages', () => {
    const shell = openShell('/store/index.jsp');
    shell.frame.contentWindow = {};
    const event = {
        origin: 'https://example.test', source: shell.frame.contentWindow,
        data: { type: 'mensform-page-ready', url: '/store/cart', title: 'Cart' }
    };
    shell.listeners.message({ ...event, origin: 'https://other.test' });
    shell.listeners.message({ ...event, source: {} });
    assert.equal(shell.history.state.mensformPage, 'https://example.test/store/index.jsp');
    shell.listeners.message(event);
    assert.equal(shell.history.state.mensformPage, 'https://example.test/store/cart');
    assert.equal(shell.document.title, 'Cart');
    assert.equal(shell.history.url, '/store/');
});

for (const page of ['https://other.test/store/login', '//other.test/store/login',
    'javascript:alert(1)', 'https://example.test/store-other/login',
    'https://user:pass@example.test/store/login', '/store/../outside']) {
    test(`rejects a restored frame destination outside this app: ${page}`, () => {
        const shell = openShell('/store/index.jsp', { mensformPage: page });
        assert.equal(shell.frame.src, 'https://example.test/store/index.jsp');
    });
}

test('payment handoff navigates the main browser window to the server-generated provider URL', () => {
    const paymentScript = fs.readFileSync(path.join(__dirname, '../../web/assets/external-navigation.js'), 'utf8');
    let destination;
    const href = 'https://payments.example.test/pay?transaction=test&signature=fixture';
    vm.runInNewContext(paymentScript, {
        document: { getElementById: () => ({ href }) },
        window: { top: { location: { assign: (value) => { destination = value; } } } }
    });
    assert.equal(destination, href);
});
