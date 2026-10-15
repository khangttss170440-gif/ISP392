(() => {
    document.querySelectorAll('.image-delete-form').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (!window.confirm('Xóa ảnh này khỏi sản phẩm?')) event.preventDefault();
        });
    });
    const input = document.getElementById('productImages');
    if (input) {
        const preview = document.getElementById('uploadPreview');
        const error = document.getElementById('imageUploadError');
        let urls = [];
        input.addEventListener('change', () => {
            urls.forEach((url) => URL.revokeObjectURL(url));
            urls = [];
            preview.replaceChildren();
            error.textContent = '';
            const files = Array.from(input.files);
            if (files.length > 6 || files.some((file) => file.size > 5 * 1024 * 1024 || !['image/jpeg', 'image/png'].includes(file.type))) {
                error.textContent = 'Vui lòng chọn tối đa 6 ảnh JPG/PNG, mỗi ảnh không quá 5 MB.';
                input.value = '';
                return;
            }
            files.forEach((file, index) => {
                const image = document.createElement('img');
                const url = URL.createObjectURL(file);
                urls.push(url);
                image.src = url;
                image.alt = `Ảnh vừa chọn ${index + 1}`;
                preview.appendChild(image);
            });
        });
    }
    const mainImage = document.getElementById('mainProductImage');
    document.querySelectorAll('[data-product-image]').forEach((button) => {
        button.addEventListener('click', () => {
            if (!mainImage) return;
            mainImage.src = button.dataset.productImage;
            document.querySelectorAll('[data-product-image]').forEach((item) => {
                item.setAttribute('aria-pressed', String(item === button));
            });
        });
    });
})();
