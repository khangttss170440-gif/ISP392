(() => {
    const form = document.getElementById('productVariantForm');
    if (!form) return;
    const variantId = document.getElementById('variantId');
    const size = document.getElementById('variantSize');
    const quantity = document.getElementById('quantity');
    const buy = document.getElementById('addVariantToCart');
    const price = document.getElementById('selected-price');
    const stock = document.getElementById('variantStock');
    const colorLabel = document.getElementById('selectedColorLabel');
    const colorButtons = Array.from(document.querySelectorAll('[data-variant-color]'));
    const variants = Array.from(variantId.options).filter((option) => option.value).map((option) => ({
        id: option.value, color: option.dataset.color, size: option.dataset.size,
        price: option.dataset.price, amount: Number(option.dataset.amount), stock: Number(option.dataset.stock)
    }));
    const mainImage = document.getElementById('mainProductImage');
    const defaultImage = mainImage.src;
    const defaultAlt = mainImage.alt;
    const thumbs = Array.from(document.querySelectorAll('[data-product-image]'));
    const imageNote = document.getElementById('colorImageNote');
    let selectedColor = '';
    const colorKey = (value) => (value || '').trim().toLocaleLowerCase('vi-VN');

    const updateGallery = (color) => {
        const matching = thumbs.filter((button) => colorKey(button.dataset.imageColor) === colorKey(color));
        const shared = thumbs.filter((button) => !button.dataset.imageColor);
        const available = matching.length ? matching.concat(shared) : shared;
        const first = available[0];
        thumbs.forEach((button) => {
            button.hidden = !available.includes(button);
            button.setAttribute('aria-pressed', String(button === first));
        });
        mainImage.src = first ? first.dataset.productImage : defaultImage;
        mainImage.alt = matching.length ? `${defaultAlt} · ${color}` : defaultAlt;
        imageNote.hidden = matching.length > 0;
        imageNote.textContent = matching.length ? '' : `Chưa có ảnh riêng cho màu ${color}. Đang hiển thị ảnh chung hoặc ảnh đại diện.`;
    };

    const updateSelection = () => {
        const selected = variants.find((variant) => variant.id === size.value && variant.color === selectedColor && variant.stock > 0);
        variantId.value = selected ? selected.id : '';
        buy.disabled = !selected;
        quantity.disabled = !selected;
        if (selected) {
            price.textContent = `${selected.price}₫`;
            stock.textContent = `Còn ${selected.stock} sản phẩm · ${selected.color} / ${selected.size}`;
            quantity.max = String(Math.min(99, selected.stock));
            const current = Number(quantity.value);
            quantity.value = String(Number.isInteger(current) && current > 0 ? Math.min(current, Number(quantity.max)) : 1);
        } else {
            const current = variants.filter((variant) => variant.color === selectedColor);
            const amounts = current.map((variant) => variant.amount);
            const min = Math.min(...amounts), max = Math.max(...amounts);
            const money = new Intl.NumberFormat('vi-VN');
            price.textContent = amounts.length ? (min === max ? `${money.format(min)}₫` : `${money.format(min)}₫ – ${money.format(max)}₫`) : 'Chọn màu và kích cỡ';
            stock.textContent = !selectedColor ? 'Chọn màu và kích cỡ để thêm vào giỏ.'
                : current.some((variant) => variant.stock > 0) ? 'Chọn kích cỡ để xem tồn kho.' : 'Màu này hiện đã hết hàng.';
        }
    };

    colorButtons.forEach((button) => button.addEventListener('click', () => {
        const oldSize = variants.find((variant) => variant.id === size.value)?.size;
        selectedColor = button.dataset.variantColor;
        colorButtons.forEach((item) => item.setAttribute('aria-pressed', String(item === button)));
        colorLabel.textContent = `· ${selectedColor}`;
        size.replaceChildren(new Option('Chọn kích cỡ', ''));
        variants.filter((variant) => variant.color === selectedColor).forEach((variant) => {
            const option = new Option(`${variant.size}${variant.stock <= 0 ? ' · Hết hàng' : ''}`, variant.id);
            option.disabled = variant.stock <= 0;
            size.appendChild(option);
        });
        size.disabled = !variants.some((variant) => variant.color === selectedColor && variant.stock > 0);
        const preserved = variants.find((variant) => variant.color === selectedColor && variant.size === oldSize && variant.stock > 0);
        size.value = preserved ? preserved.id : '';
        updateGallery(selectedColor);
        updateSelection();
    }));
    size.addEventListener('change', updateSelection);
    form.addEventListener('submit', (event) => {
        if (!variantId.value || buy.disabled) {
            event.preventDefault();
            stock.textContent = 'Vui lòng chọn màu và kích cỡ còn hàng.';
            if (selectedColor && !size.disabled) size.focus(); else colorButtons[0]?.focus();
        }
    });
    variantId.required = false;
    size.required = true;
    document.getElementById('legacyVariantPicker').hidden = true;
    document.getElementById('variantPicker').hidden = false;
    updateSelection();
})();
