(() => {
    const widget = document.querySelector('[data-shipping-widget]');
    if (!widget) return;
    const form = widget.closest('form');
    const addressSelect = form.querySelector('select[name="addressId"]');
    const button = widget.querySelector('[data-shipping-search]');
    const status = widget.querySelector('[data-shipping-status]');
    const checkoutSubmit = form.querySelector('#checkoutSubmit');
    const money = new Intl.NumberFormat('vi-VN');
    let sequence = 0;
    let abort;

    const input = (name) => form.querySelector(`[name="${name}"]`);
    const isSaved = () => addressSelect && addressSelect.value !== 'new';
    const qualifiesByAmount = () => Number(document.getElementById('checkoutTotalLabel')?.dataset.subtotal || 0) >= 799000;
    const showFee = (fee, priced, freeByOrderValue) => {
        const feeLabel = document.getElementById('shippingFeeLabel');
        const total = document.getElementById('checkoutTotalLabel');
        const preview = form.querySelector('[name="shippingFeePreview"]');
        const canCheckout = priced || freeByOrderValue;
        if (feeLabel) feeLabel.textContent = !canCheckout ? 'Chưa tính' : fee === 0 ? 'Miễn phí' : `${money.format(fee)}₫`;
        if (preview) preview.value = canCheckout ? String(fee) : '';
        if (total) total.textContent = `${money.format(Number(total.dataset.subtotal) - Number(total.dataset.discount) + fee)}₫`;
        if (checkoutSubmit) checkoutSubmit.disabled = !canCheckout;
    };
    const invalidate = () => {
        sequence++;
        abort?.abort();
        button.disabled = false;
        status.textContent = 'Địa chỉ đã thay đổi. Bấm tính phí theo khu vực để cập nhật.';
        showFee(0, false, qualifiesByAmount());
        const promotionAddress = document.getElementById('promotionAddressId');
        if (promotionAddress && addressSelect) promotionAddress.value = addressSelect.value;
    };
    const search = async () => {
        const current = ++sequence;
        abort?.abort();
        abort = new AbortController();
        const params = new URLSearchParams();
        if (isSaved()) params.set('addressId', addressSelect.value);
        else ['addressLine', 'ward', 'district', 'province'].forEach((name) => params.set(name, input(name)?.value.trim() || ''));
        button.disabled = true;
        status.textContent = 'Đang tính phí giao hàng theo khu vực…';
        try {
            const response = await fetch(widget.dataset.endpoint, {
                method: 'POST', credentials: 'same-origin', body: params, signal: abort.signal
            });
            if (response.redirected) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
            const data = await response.json();
            if (!response.ok) throw new Error(data.error || 'Chưa tính được phí giao hàng.');
            if (current !== sequence) return;
            showFee(data.fee, data.priced, data.freeByOrderValue);
            status.textContent = data.message;
        } catch (error) {
            if (current === sequence && error.name !== 'AbortError') {
                showFee(0, false, qualifiesByAmount());
                status.textContent = `${error.message} ${qualifiesByAmount() ? 'Đơn từ 799.000₫ vẫn được miễn phí giao hàng.' : ''}`;
            }
        } finally {
            if (current === sequence) button.disabled = false;
        }
    };

    button.addEventListener('click', search);
    ['addressLine', 'ward', 'district', 'province'].forEach((name) => input(name)?.addEventListener('change', invalidate));
    addressSelect?.addEventListener('change', () => {
        invalidate();
        if (isSaved()) void search();
    });
    showFee(Number(form.querySelector('[name="shippingFeePreview"]')?.value || 0),
        widget.dataset.initialAllowed === 'true', widget.dataset.initialFreeByValue === 'true');
    status.textContent = widget.dataset.initialMessage;
})();
