(() => {
    document.querySelectorAll('[data-product-carousel]').forEach((carousel) => {
        const track = carousel.querySelector('.home-product-track');
        const previous = carousel.querySelector('[data-carousel-prev]');
        const next = carousel.querySelector('[data-carousel-next]');
        const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
        const row = document.createElement('div');
        row.className = 'home-product-row';
        row.append(...track.children);
        track.append(row);
        track.classList.add('is-looping');
        const queue = [];
        let moving = false;

        const update = () => {
            const cards = [...row.children];
            if (cards.length) {
                const gap = parseFloat(getComputedStyle(row).columnGap) || 0;
                const visible = Math.max(1, Math.min(cards.length,
                    Math.round((row.clientWidth + gap) / (cards[0].offsetWidth + gap))));
                const center = Math.floor((visible - 1) / 2);
                cards.forEach((card, index) => card.classList.toggle('is-featured', index === center));
            }
            const cannotScroll = row.children.length < 2 || row.scrollWidth <= track.clientWidth + 2;
            previous.disabled = cannotScroll;
            next.disabled = cannotScroll;
        };
        const play = async () => {
            if (moving) return;
            moving = true;
            try {
                while (queue.length) {
                    const direction = queue.shift();
                    update();
                    if (next.disabled) continue;
                    track.scrollLeft = 0;
                    const gap = parseFloat(getComputedStyle(row).columnGap) || 0;
                    const step = row.firstElementChild.offsetWidth + gap;
                    // Keep the real cards adjacent across the end of the list.
                    if (direction < 0) row.prepend(row.lastElementChild);
                    if (!reducedMotion.matches) {
                        const animation = row.animate([
                            { transform: `translateX(${direction > 0 ? 0 : -step}px)` },
                            { transform: `translateX(${direction > 0 ? -step : 0}px)` }
                        ], { duration: 280, easing: 'ease-in-out', fill: 'forwards' });
                        await animation.finished;
                        if (direction > 0) row.append(row.firstElementChild);
                        animation.cancel();
                    } else if (direction > 0) {
                        row.append(row.firstElementChild);
                    }
                    track.scrollLeft = 0;
                }
            } finally {
                moving = false;
                update();
            }
        };
        const move = (direction) => {
            queue.push(direction);
            play();
        };
        previous.hidden = false;
        next.hidden = false;
        previous.addEventListener('click', () => move(-1));
        next.addEventListener('click', () => move(1));
        track.addEventListener('keydown', (event) => {
            if (event.target !== track || !['ArrowLeft', 'ArrowRight'].includes(event.key)) return;
            event.preventDefault();
            move(event.key === 'ArrowRight' ? 1 : -1);
        });
        new ResizeObserver(update).observe(track);
        new MutationObserver(update).observe(row, { childList: true });
        update();
    });
})();
