const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { test } = require('node:test');

const script = fs.readFileSync(path.join(__dirname, '../../web/assets/chatbot.js'), 'utf8');

class Element {
    constructor() {
        this.children = [];
        this.handlers = {};
        this.dataset = {};
        this.hidden = true;
        this.value = '';
        this.classList = { add() {} };
    }
    appendChild(child) { this.children.push(child); child.parent = this; return child; }
    append(...children) { children.forEach((child) => this.appendChild(child)); }
    remove() { this.parent.children = this.parent.children.filter((child) => child !== this); }
    setAttribute() {}
    addEventListener(event, handler) { this.handlers[event] = handler; }
    querySelector() { return this.button; }
    querySelectorAll() { return []; }
    focus() {}
}

function widget({ rootTimeout, sharedTimeout, noSharedSettings = false, expire = false, failure = false } = {}) {
    const root = new Element();
    root.dataset = { endpoint: '/MenWearStore/chatbot', storeName: 'Test store' };
    if (rootTimeout !== undefined) root.dataset.requestTimeout = rootTimeout;
    const elements = Object.fromEntries(['panel', 'toggle', 'close', 'reset', 'messages', 'form', 'input']
        .map((id) => [`chatbot-${id}`, new Element()]));
    elements['chatbot-form'].button = new Element();
    const timers = [];
    const cleared = [];
    const calls = [];
    const context = {
        document: {
            querySelector: (selector) => selector === '.chatbot' ? root
                : noSharedSettings ? null : { dataset: { storeName: 'Test store', chatTimeout: sharedTimeout } },
            getElementById: (id) => elements[id],
            createElement: () => new Element()
        },
        window: { location: new URL('http://localhost/MenWearStore/') },
        URL, URLSearchParams, AbortController,
        setTimeout: (callback, delay) => {
            timers.push(delay);
            // A browser treats NaN/zero as an immediate timeout. Reproduce it
            // so a missing deployment setting would fail this regression test.
            if (!Number.isFinite(delay) || delay <= 0 || expire) callback();
            return timers.length;
        },
        clearTimeout: (id) => cleared.push(id),
        fetch: async (endpoint, options) => {
            calls.push({ endpoint, options });
            await new Promise((resolve) => setImmediate(resolve));
            if (options.signal.aborted) throw Object.assign(new Error('Aborted'), { name: 'AbortError' });
            if (failure) throw new TypeError('Network failed');
            return {
                ok: true,
                json: async () => ({
                    reply: 'Found matching catalog results',
                    products: [{ name: 'Shirt', url: '/product-detail?productId=1', stock: 2 }],
                    collections: [{ name: 'Office set', url: '/collections?collectionId=3', items: [] }]
                })
            };
        }
    };
    vm.runInNewContext(script, context);
    return {
        timers, cleared, calls, elements,
        submit: async () => {
            elements['chatbot-input'].value = 'Tìm bộ sưu tập đi làm';
            await elements['chatbot-form'].handlers.submit({ preventDefault() {} });
        }
    };
}

function assertSuccess(client) {
    const messages = client.elements['chatbot-messages'].children;
    assert.ok(messages.some((item) => item.textContent === 'Found matching catalog results'));
    assert.ok(messages.some((item) => item.className === 'chatbot-products'));
    assert.ok(messages.some((item) => item.className === 'chatbot-collections'));
    assert.ok(!messages.some((item) => item.className.includes('error')));
    assert.equal(client.elements['chatbot-form'].button.disabled, false);
    assert.equal(client.calls[0].options.credentials, 'same-origin');
}

test('widget uses its own configured timeout when shared deployed settings are older', async () => {
    const client = widget({ rootTimeout: '70000' });
    await client.submit();
    assert.deepEqual(client.timers, [70000]);
    assert.deepEqual(client.cleared, [1]);
    assertSuccess(client);
});

test('older widgets can use a valid shared timeout', async () => {
    const client = widget({ sharedTimeout: '45000' });
    await client.submit();
    assert.deepEqual(client.timers, [45000]);
    assertSuccess(client);
});

for (const value of [undefined, '', 'invalid', '0', '-1', 'Infinity']) {
    test(`missing or invalid timeout (${String(value)}) does not abort a fast response`, async () => {
        const client = widget({ rootTimeout: value, sharedTimeout: value });
        await client.submit();
        assert.deepEqual(client.timers, []);
        assertSuccess(client);
    });
}

test('widget works without the shared settings element', async () => {
    const client = widget({ rootTimeout: '45000', noSharedSettings: true });
    await client.submit();
    assertSuccess(client);
});

test('an actual timeout reports the error and re-enables sending', async () => {
    const client = widget({ rootTimeout: '45000', expire: true });
    await client.submit();
    const error = client.elements['chatbot-messages'].children.find((item) => item.className.includes('error'));
    assert.match(error.textContent, /Yêu cầu mất nhiều thời gian/);
    assert.equal(client.elements['chatbot-form'].button.disabled, false);
    assert.deepEqual(client.cleared, [1]);
});

test('network errors remain distinct from timeouts', async () => {
    const client = widget({ rootTimeout: '45000', failure: true });
    await client.submit();
    const error = client.elements['chatbot-messages'].children.find((item) => item.className.includes('error'));
    assert.match(error.textContent, /Không kết nối được/);
    assert.equal(client.elements['chatbot-form'].button.disabled, false);
});
