(() => {
    const widget = document.querySelector('[data-shipping-widget]');
    if (!widget) return;
    const form = widget.closest('form');
    const addressSelect = form.querySelector('select[name="addressId"]');
    const button = widget.querySelector('[data-shipping-search]');
    const status = widget.querySelector('[data-shipping-status]');
    const frame = widget.querySelector('[data-shipping-frame]');
    const mapElement = widget.querySelector('[data-shipping-google-map]');
    const addressLink = widget.querySelector('[data-address-map]');
    const money = new Intl.NumberFormat('vi-VN');
    let sequence = 0, abort, mapPromise, map;
    const overlays = [];
    const input = (name) => form.querySelector(`[name="${name}"]`);
    const isSaved = () => addressSelect && addressSelect.value !== 'new';
    const addressText = () => isSaved() ? addressSelect.selectedOptions[0]?.dataset.location || ''
        : ['addressLine', 'ward', 'district', 'province'].map((name) => input(name)?.value.trim()).filter(Boolean).join(', ');
    const updatePreview = () => {
        const address = addressText() || widget.dataset.shopAddress;
        frame.src = `https://maps.google.com/maps?q=${encodeURIComponent(address)}&output=embed`;
        addressLink.href = `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(address)}`;
        return address;
    };
    const showFee = (fee) => {
        const feeLabel = document.getElementById('shippingFeeLabel');
        const total = document.getElementById('checkoutTotalLabel');
        const preview = form.querySelector('[name="shippingFeePreview"]');
        if (feeLabel) feeLabel.textContent = fee === 0 ? 'Miễn phí' : `${money.format(fee)}₫`;
        if (preview) preview.value = String(fee);
        if (total) total.textContent = `${money.format(Number(total.dataset.subtotal) - Number(total.dataset.discount) + fee)}₫`;
    };
    const loadMap = () => {
        if (window.google?.maps?.Map) return Promise.resolve();
        if (!mapPromise) mapPromise = new Promise((resolve, reject) => {
            const script = document.createElement('script');
            window.mensformShippingMapReady = resolve;
            script.src = `https://maps.googleapis.com/maps/api/js?key=${encodeURIComponent(widget.dataset.mapKey)}&callback=mensformShippingMapReady&loading=async&language=vi&region=VN`;
            script.async = true;
            script.onerror = reject;
            document.head.appendChild(script);
        });
        return mapPromise;
    };
    const drawMap = async (quote, current) => {
        if (!widget.dataset.mapKey || !quote.verified) return;
        try {
            await loadMap();
            if (current !== sequence) return;
            const shop = { lat: quote.shopLat, lng: quote.shopLng };
            const destination = { lat: quote.lat, lng: quote.lng };
            mapElement.hidden = false;
            if (!map) map = new google.maps.Map(mapElement, { center: shop, zoom: 12, streetViewControl: false, mapTypeControl: false });
            overlays.forEach((overlay) => overlay.setMap(null)); overlays.length = 0;
            const zone = new google.maps.Circle({ map, center: shop, radius: 5000, fillColor: '#b99a60', fillOpacity: .14, strokeColor: '#7b5a35', strokeWeight: 2 });
            overlays.push(zone, new google.maps.Circle({ map, center: destination, radius: 40, fillColor: '#211b17', fillOpacity: 1, strokeColor: '#fff', strokeWeight: 2 }));
            const bounds = zone.getBounds(); bounds.extend(destination); map.fitBounds(bounds);
            frame.hidden = true;
        } catch { mapElement.hidden = true; frame.hidden = false; }
    };
    const invalidate = () => {
        sequence++; abort?.abort();
        button.disabled = false;
        status.textContent = 'Địa chỉ đã thay đổi. Bấm tìm kiếm để kiểm tra lại phí giao hàng.';
        showFee(45000); mapElement.hidden = true; frame.hidden = false;
        updatePreview();
        const promoAddress = document.getElementById('promotionAddressId');
        if (promoAddress && addressSelect) promoAddress.value = addressSelect.value;
    };
    const search = async () => {
        const current = ++sequence;
        abort?.abort(); abort = new AbortController();
        const params = new URLSearchParams();
        if (isSaved()) params.set('addressId', addressSelect.value);
        else ['addressLine', 'ward', 'district', 'province'].forEach((name) => params.set(name, input(name)?.value.trim() || ''));
        button.disabled = true; status.textContent = 'Đang tìm địa chỉ và kiểm tra phí giao hàng…';
        try {
            const response = await fetch(widget.dataset.endpoint, { method: 'POST', credentials: 'same-origin', body: params, signal: abort.signal });
            if (response.redirected) throw new Error('Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.');
            const data = await response.json();
            if (!response.ok) throw new Error(data.error || 'Chưa kiểm tra được phí giao hàng.');
            if (current !== sequence) return;
            showFee(data.fee);
            status.textContent = (data.verified ? `Cách shop ${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 2 }).format(data.distanceKm)} km theo đường thẳng. ` : '') + data.message;
            updatePreview();
            mapElement.hidden = true; frame.hidden = false;
            void drawMap(data, current);
        } catch (error) {
            if (current === sequence && error.name !== 'AbortError') {
                showFee(45000); status.textContent = `${error.message} Phí giao hàng tạm tính 45.000₫.`;
            }
        } finally { if (current === sequence) button.disabled = false; }
    };
    button.addEventListener('click', search);
    ['addressLine', 'ward', 'district', 'province'].forEach((name) => input(name)?.addEventListener('change', invalidate));
    addressSelect?.addEventListener('change', () => { invalidate(); if (isSaved()) void search(); });
    updatePreview();
    status.textContent = widget.dataset.initialMessage;
    if (widget.dataset.initialDistance) {
        status.textContent = `Cách shop ${Number(widget.dataset.initialDistance).toFixed(2)} km theo đường thẳng. ` + status.textContent;
        void drawMap({ verified: true, shopLat: Number(widget.dataset.shopLat), shopLng: Number(widget.dataset.shopLng), lat: Number(widget.dataset.lat), lng: Number(widget.dataset.lng) }, sequence);
    }
})();
