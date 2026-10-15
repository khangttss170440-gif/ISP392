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
    const suggestions = root.querySelectorAll('[data-message]');
    const greeting = 'Xin chào! Mình là trợ lý MENSFORM. Mình có thể giúp bạn tìm sản phẩm, chọn size và phối đồ. Hôm nay bạn cần tư vấn gì?';
    let busy = false;

    const setBusy = (value) => {
        busy = value;
        sendButton.disabled = value;
        input.disabled = value;
        reset.disabled = value;
        suggestions.forEach((button) => { button.disabled = value; });
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
    suggestions.forEach((button) => button.addEventListener('click', () => {
        if (busy) return;
        input.value = button.dataset.message;
        form.requestSubmit();
    }));

    form.addEventListener('submit', async (event) => {
        event.preventDefault();
        const message = input.value.trim();
        if (!message || busy) return;

        addMessage(message, 'user');
        input.value = '';
        setBusy(true);
        const pending = addMessage('Đang trả lời…', 'assistant');
        pending.classList.add('pending');

        try {
            const body = new URLSearchParams({ message });
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' },
                credentials: 'same-origin',
                body
            });
            const data = await response.json();
            pending.remove();
            if (!response.ok) {
                addMessage(data.error || 'Chưa gửi được tin nhắn. Bạn thử lại nhé.', 'assistant', true);
            } else {
                if (typeof data.reply !== 'string' || !data.reply.trim()) throw new Error('Empty reply');
                addMessage(data.reply, 'assistant');
                addProducts(data.products);
            }
        } catch (error) {
            pending.remove();
            addMessage('Không kết nối được. Bạn kiểm tra mạng rồi thử lại nhé.', 'assistant', true);
        } finally {
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



