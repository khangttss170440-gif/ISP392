(() => {
    const root = document.querySelector('[data-order-tracking]');
    if (!root) return;
    const settings = document.querySelector('[data-runtime-settings]').dataset;
    const pollMillis = Number(settings.trackingPoll);
    const rows = new Map([...document.querySelectorAll('[data-tracking-row]')]
        .filter(row => row.closest('[data-order-tracking]') === root)
        .map(row => [Number(row.dataset.orderId), row]));
    if (!rows.size) return;
    const notice = root.querySelector('[data-tracking-notice]');
    const clock = new Intl.DateTimeFormat(settings.locale, { dateStyle: 'short', timeStyle: 'medium', timeZone: settings.timezone });
    let timer, controller, running = false, stopped = false;
    const text = (row, selector, value) => row.querySelectorAll(selector).forEach(el => { el.textContent = value || ''; });
    const update = item => {
        const row = rows.get(Number(item.orderId));
        if (!row) return;
        const wasDelivered = row.dataset.orderStatus === 'Delivered';
        row.dataset.orderStatus = item.orderStatus;
        text(row, '[data-order-status]', item.orderStatusLabel);
        text(row, '[data-shipping-status]', item.shippingStatusLabel);
        text(row, '[data-payment-status]', item.paymentStatusLabel);
        text(row, '[data-shipping-notice]', item.shippingNotice);
        row.querySelectorAll('[data-shipping-notice]').forEach(el => { el.hidden = !item.shippingNotice; });
        text(row, '[data-tracking-number]', item.trackingNumber || 'Chưa có mã vận đơn');
        text(row, '[data-tracking-carrier]', item.carrier || 'Shop đang chuẩn bị giao hàng');
        text(row, '[data-carrier-last-sync]', item.lastSyncAt ? `Đồng bộ vận chuyển: ${clock.format(new Date(item.lastSyncAt))}` : 'Chưa có lần đồng bộ từ hãng vận chuyển.');
        row.querySelectorAll('[data-order-request]').forEach(el => {
            el.hidden = !item.requestAllowed;
            el.textContent = item.orderStatus === 'Delivered' ? 'Gửi yêu cầu' : 'Hủy đơn';
        });
        if (!wasDelivered && item.orderStatus === 'Delivered') {
            const review = row.querySelector('[data-review-refresh]');
            if (review) review.hidden = false;
            text(row, '[data-review-notice]', 'Đơn hàng đã giao thành công. Bạn có thể tải lại trang để đánh giá sản phẩm.');
        }
    };
    const schedule = () => {
        clearTimeout(timer);
        if (!stopped && !document.hidden) timer = setTimeout(poll, pollMillis);
    };
    async function poll() {
        if (stopped || running || document.hidden) return;
        clearTimeout(timer); running = true;
        try {
            const ids = [...rows.keys()];
            for (let i = 0; i < ids.length; i += 100) {
                controller = new AbortController();
                const timeout = setTimeout(() => controller.abort(), Number(settings.trackingTimeout));
                try {
                    const url = new URL(root.dataset.trackingEndpoint, location.origin);
                    url.searchParams.set('orderIds', ids.slice(i, i + 100).join(','));
                    const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store', signal: controller.signal });
                    if (response.redirected || response.status === 401 || response.status === 403) {
                        stopped = true;
                        if (notice) notice.textContent = 'Phiên đăng nhập đã hết hạn. Đăng nhập lại để theo dõi đơn hàng.';
                        return;
                    }
                    const data = await response.json();
                    if (!response.ok) throw new Error(data.error || 'Chưa tải được cập nhật.');
                    data.orders.forEach(update);
                } finally { clearTimeout(timeout); }
            }
            if (notice) notice.textContent = `Trạng thái được tự động kiểm tra mỗi ${pollMillis / 1000} giây.`;
        } catch (error) {
            if (!stopped && !document.hidden && notice) notice.textContent = `Chưa nhận được cập nhật mới. Hệ thống sẽ thử lại sau ${pollMillis / 1000} giây.`;
        } finally { running = false; controller = null; schedule(); }
    }
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) { clearTimeout(timer); controller?.abort(); }
        else if (!running) void poll();
    });
    window.addEventListener('pagehide', () => { stopped = true; clearTimeout(timer); controller?.abort(); });
    window.addEventListener('pageshow', event => { if (event.persisted) { stopped = false; void poll(); } });
    void poll();
})();
