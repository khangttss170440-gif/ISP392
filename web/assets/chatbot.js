(() => {
    const root = document.querySelector('.chatbot');
    if (!root) return;

    const endpoint = root.dataset.endpoint;
    const panel = document.getElementById('chatbot-panel');
    const toggle = document.getElementById('chatbot-toggle');
    const close = document.getElementById('chatbot-close');
    const reset = document.getElementById('chatbot-reset');
    const messages = document.getElementById('chatbot-messages');
    const form = document.getElementById('chatbot-form');
    const input = document.getElementById('chatbot-input');
    const sendButton = form.querySelector('button');
    const runtimeSettings = document.querySelector('[data-runtime-settings]')?.dataset || {};
    const storeName = root.dataset.storeName || runtimeSettings.storeName || '';
    const greeting = `Xin chào! Mình là trợ lý ${storeName}. Mình có thể giúp bạn tìm sản phẩm, bộ sưu tập, chọn size và phối đồ. Hôm nay bạn cần tư vấn gì?`;
    let busy = false;

    const setBusy = (value) => {
        busy = value;
        sendButton.disabled = value;
        input.disabled = value;
        reset.disabled = value;
        root.querySelectorAll('[data-message]').forEach((button) => { button.disabled = value; });
        form.setAttribute('aria-busy', String(value));
    };

    const addMessage = (text, role, error = false) => {
        const item = document.createElement('div');
        item.className = `chatbot-message ${role}${error ? ' error' : ''}`;
        item.textContent = text;
        messages.appendChild(item);
        messages.scrollTop = messages.scrollHeight;
        return item;
    };

    const addProducts = (products) => {
        if (!Array.isArray(products) || products.length === 0) return;
        const list = document.createElement('div');
        list.className = 'chatbot-products';
        products.forEach((product) => {
            const card = document.createElement('a');
            card.className = 'chatbot-product';
            const url = new URL(product.url, window.location.href);
            if (url.origin !== window.location.origin || !['http:', 'https:'].includes(url.protocol)) return;
            card.href = url.href;
            if (product.imageUrl) {
                const imageUrl = new URL(product.imageUrl, window.location.href);
                if (imageUrl.origin === window.location.origin && ['http:', 'https:'].includes(imageUrl.protocol)) {
                    const image = document.createElement('img');
                    image.src = imageUrl.href;
                    image.alt = product.name || 'Ảnh sản phẩm';
                    image.loading = 'lazy';
                    image.className = 'chatbot-product-image';
                    card.classList.add('chatbot-product-with-image');
                    card.appendChild(image);
                }
            }
            const name = document.createElement('strong');
            name.textContent = product.name || 'Sản phẩm';
            const details = document.createElement('span');
            details.textContent = [product.brand, product.color, product.size ? `Size ${product.size}` : ''].filter(Boolean).join(' · ');
            const price = document.createElement('b');
            price.textContent = product.price || '';
            const stock = document.createElement('small');
            stock.textContent = `${product.stock || ''} · Xem chi tiết ↗`;
            card.append(name, details, price, stock);
            list.appendChild(card);
        });
        messages.appendChild(list);
        messages.scrollTop = messages.scrollHeight;
    };

    const addCollections = (collections) => {
        if (!Array.isArray(collections) || collections.length === 0) return;
        const list = document.createElement('div');
        list.className = 'chatbot-collections';
        collections.forEach((collection) => {
            const url = new URL(collection.url, window.location.href);
            if (url.origin !== window.location.origin || !['http:', 'https:'].includes(url.protocol)) return;
            const card = document.createElement('a');
            card.className = 'chatbot-collection';
            card.href = url.href;
            const gallery = document.createElement('div');
            gallery.className = 'chatbot-collection-images';
            const pieces = Array.isArray(collection.items) ? collection.items : [];
            pieces.forEach((piece) => {
                const imageUrl = new URL(piece.imageUrl, window.location.href);
                if (imageUrl.origin !== window.location.origin || !['http:', 'https:'].includes(imageUrl.protocol)) return;
                const image = document.createElement('img');
                image.src = imageUrl.href;
                image.alt = piece.name || 'Sản phẩm trong bộ';
                image.loading = 'lazy';
                gallery.appendChild(image);
            });
            const title = document.createElement('strong'); title.textContent = collection.name || 'Bộ sưu tập';
            const meta = document.createElement('small'); meta.textContent = `${collection.style || ''} · ${pieces.length} món`;
            const description = document.createElement('p'); description.textContent = collection.description || '';
            const names = document.createElement('ul');
            pieces.forEach((piece) => {
                const item = document.createElement('li');
                item.textContent = [piece.name, piece.color, piece.size ? `Size ${piece.size}` : '', piece.price].filter(Boolean).join(' · ');
                names.appendChild(item);
            });
            const total = document.createElement('b'); total.textContent = collection.price || '';
            const action = document.createElement('span'); action.textContent = 'Xem bộ sưu tập ↗';
            card.append(gallery, title, meta, description, names, total, action);
            list.appendChild(card);
        });
        messages.appendChild(list);
    };

    addMessage(greeting, 'assistant');

    const setOpen = (open) => {
        panel.hidden = !open;
        toggle.setAttribute('aria-expanded', String(open));
        if (open && !busy) input.focus();
        if (!open) toggle.focus();
    };

    toggle.addEventListener('click', () => setOpen(panel.hidden));
    close.addEventListener('click', () => setOpen(false));
    root.addEventListener('keydown', (event) => {
        if (event.key === 'Escape' && !panel.hidden) {
            event.preventDefault();
            setOpen(false);
        }
    });
    root.addEventListener('click', (event) => {
        const button = event.target.closest('[data-message]');
        if (!button || !root.contains(button) || busy) return;
        input.value = button.dataset.message;
        form.requestSubmit();
    });

    const addSearchInfo = (data) => {
        if (data.searchSummary) {
            const summary = document.createElement('p');
            summary.className = 'chatbot-search-summary';
            summary.textContent = 'Đang tìm: ' + data.searchSummary;
            messages.appendChild(summary);
        }
        addProducts(data.products);
        addCollections(data.collections);
        if (Array.isArray(data.suggestions) && data.suggestions.length) {
            const actions = document.createElement('div');
            actions.className = 'chatbot-search-actions';
            data.suggestions.forEach((message) => {
                if (typeof message !== 'string') return;
                const button = document.createElement('button');
                button.type = 'button';
                button.dataset.message = message;
                button.textContent = message;
                actions.appendChild(button);
            });
            messages.appendChild(actions);
        }
        messages.scrollTop = messages.scrollHeight;
    };

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const message = input.value.trim();
        if (!message || busy) return;

        addMessage(message, 'user');
        input.value = '';
        setBusy(true);
        const pending = addMessage('Đang tìm sản phẩm và trả lời…', 'assistant');
        pending.classList.add('pending');

        const controller = new AbortController();
        // Older rendered pages may not have the new setting yet. Never turn a
        // missing/invalid value into an immediate abort via setTimeout(NaN).
        const requestTimeout = [root.dataset.requestTimeout, runtimeSettings.chatTimeout]
            .map(Number).find((value) => Number.isFinite(value) && value > 0);
        const timeout = requestTimeout === undefined ? null
            : setTimeout(() => controller.abort(), requestTimeout);
        try {
            const body = new URLSearchParams({ message });
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                credentials: 'same-origin',
                body, signal: controller.signal
            });
            const data = await response.json();
            pending.remove();
            if (!response.ok) {
                addMessage(data.error || 'Chưa gửi được tin nhắn. Bạn thử lại nhé.', 'assistant', true);
            } else {
                if (typeof data.reply !== 'string' || !data.reply.trim()) throw new Error('Empty reply');
                addMessage(data.reply, 'assistant');
                addSearchInfo(data);
            }
        } catch (error) {
            pending.remove();
            addMessage(error.name === 'AbortError' ? 'Yêu cầu mất nhiều thời gian. Bạn thử lại với tên sản phẩm, màu, size hoặc mức giá cụ thể nhé.' : 'Không kết nối được. Bạn kiểm tra mạng rồi thử lại nhé.', 'assistant', true);
        } finally {
            if (timeout !== null) clearTimeout(timeout);
            setBusy(false);
            if (!panel.hidden) input.focus();
        }
    });

    reset.addEventListener('click', async () => {
        if (busy) return;
        setBusy(true);
        try {
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                credentials: 'same-origin',
                body: new URLSearchParams({ reset: '1' })
            });
            if (!response.ok) throw new Error('Reset failed');
            messages.replaceChildren();
            addMessage(greeting, 'assistant');
            input.value = '';
        } catch (error) {
            addMessage('Chưa thể bắt đầu cuộc trò chuyện mới. Bạn thử lại nhé.', 'assistant', true);
        } finally {
            setBusy(false);
            if (!panel.hidden) input.focus();
        }
    });
})();



