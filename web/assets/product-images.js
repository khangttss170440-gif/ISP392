(() => {
    document.querySelectorAll('.image-delete-form').forEach((form) => {
        form.addEventListener('submit', (event) => {
            if (!window.confirm('Xóa ảnh này khỏi sản phẩm?')) event.preventDefault();
        });
    });
    const MAX_FILE_BYTES = 10 * 1024 * 1024;
    const MAX_PIXELS = 12000000;
    document.querySelectorAll('input[type="file"][name="images"]').forEach(input => {
        const form = input.form;
        if (!form) return;
        const isMain = input.id === 'productImages';
        const preview = (isMain && document.getElementById('uploadPreview')) || document.createElement('div');
        const error = (isMain && document.getElementById('imageUploadError')) || document.createElement('p');
        error.classList.add('product-upload-error');
        error.setAttribute('role', 'alert');
        preview.classList.add('product-upload-preview');
        if (!error.parentNode) input.insertAdjacentElement('afterend', error);
        if (!preview.parentNode) error.insertAdjacentElement('afterend', preview);
        let urls = [];
        let generation = 0;
        let pending = 0;
        const clearPreview = () => {
            urls.forEach(url => URL.revokeObjectURL(url));
            urls = [];
            preview.replaceChildren();
        };
        const reject = message => {
            generation++;
            pending = 0;
            clearPreview();
            error.textContent = message;
            input.value = '';
        };
        input.addEventListener('change', () => {
            const selection = ++generation;
            clearPreview();
            pending = 0;
            error.textContent = '';
            const files = Array.from(input.files);
            const maximum = input.multiple ? 6 : 1;
            if (files.length > maximum || files.some(file => file.size > MAX_FILE_BYTES || !['image/jpeg', 'image/png'].includes(file.type))) {
                reject(`Vui lòng chọn tối đa ${maximum} ảnh JPG/PNG, mỗi ảnh không quá 10 MB.`);
                return;
            }
            pending = files.length;
            files.forEach((file, index) => {
                const image = document.createElement('img');
                const figure = document.createElement('figure');
                const caption = document.createElement('figcaption');
                const url = URL.createObjectURL(file);
                urls.push(url);
                image.alt = `Ảnh vừa chọn ${index + 1}`;
                image.onload = () => {
                    if (selection !== generation) return;
                    const width = image.naturalWidth, height = image.naturalHeight;
                    if (!width || !height || width * height > MAX_PIXELS) {
                        reject('Ảnh tối đa 12 megapixel, ví dụ 3000 × 4000 px. Vui lòng chọn lại ảnh.');
                        return;
                    }
                    caption.textContent = `${width} × ${height} px · ${(file.size / 1024 / 1024).toFixed(2)} MB`;
                    if (Math.min(width, height) < 1200) {
                        const warning = document.createElement('span');
                        warning.className = 'product-image-quality-note';
                        warning.textContent = 'Ảnh nhỏ; nên chọn nguồn có cạnh ngắn từ 1200 px để xem rõ hơn.';
                        caption.appendChild(warning);
                    }
                    pending--;
                    if (pending === 0) error.textContent = '';
                };
                image.onerror = () => {
                    if (selection === generation) reject('Không đọc được ảnh. Vui lòng chọn ảnh JPG/PNG khác.');
                };
                figure.append(image, caption);
                preview.appendChild(figure);
                image.src = url;
            });
        });
        form.addEventListener('submit', event => {
            if (pending > 0) {
                event.preventDefault();
                error.textContent = 'Đang đọc độ phân giải ảnh. Vui lòng đợi một chút rồi tải lên.';
            }
        });
        window.addEventListener('pagehide', () => urls.forEach(url => URL.revokeObjectURL(url)), { once: true });
    });
    document.querySelectorAll('[data-image-resolution]').forEach(image => {
        const caption = document.createElement('p');
        caption.className = 'admin-note product-image-resolution';
        image.parentElement.insertAdjacentElement('afterend', caption);
        const describe = () => {
            if (!image.naturalWidth || !image.naturalHeight) return;
            caption.textContent = `${image.naturalWidth} × ${image.naturalHeight} px`;
            if (Math.min(image.naturalWidth, image.naturalHeight) < 1200)
                caption.textContent += ' · Ảnh nhỏ; thay bằng ảnh gốc lớn hơn để rõ hơn.';
        };
        image.addEventListener('load', describe);
        if (image.complete) describe();
    });
    const mainImage = document.getElementById('mainProductImage');
    const fullImage = document.getElementById('fullProductImage');
    if (mainImage && fullImage) {
        const syncFullImage = () => { fullImage.href = mainImage.src; };
        mainImage.addEventListener('load', syncFullImage);
        fullImage.addEventListener('click', syncFullImage);
        syncFullImage();
    }
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
