/* MENSFORM admin reports: draws the daily trend (revenue area or order bars) from the
   #rpSeries JSON that reports.jsp prints, with a crosshair tooltip and a table view. */
(function () {
    var panel = document.querySelector('[data-rp-trend]');
    var source = document.getElementById('rpSeries');
    if (!panel || !source) return;

    var rows = [];
    try { rows = JSON.parse(source.textContent || '[]'); } catch (e) { rows = []; }
    var data = rows.map(function (r) {
        var p = r[0].split('-');
        return { iso: r[0], day: p[2] + '/' + p[1], year: p[0], revenue: +r[1] || 0, orders: +r[2] || 0 };
    });

    var box = panel.querySelector('[data-chart]');
    var tip = panel.querySelector('[data-tip]');
    var table = panel.querySelector('[data-table]');
    var tabs = panel.querySelectorAll('[data-view]');
    var view = 'revenue';
    var NS = 'http://www.w3.org/2000/svg';

    var money = new Intl.NumberFormat('vi-VN');
    function vnd(v) { return money.format(Math.round(v)) + '₫'; }
    function compact(v) {
        if (v >= 1e9) return (v / 1e9).toFixed(v >= 1e10 ? 0 : 1).replace('.', ',').replace(',0', '') + ' tỷ';
        if (v >= 1e6) return (v / 1e6).toFixed(v >= 1e8 ? 0 : 1).replace('.', ',').replace(',0', '') + ' tr';
        if (v >= 1e3) return Math.round(v / 1e3) + 'k';
        return String(Math.round(v));
    }
    function niceMax(v) {
        if (v <= 0) return 1;
        var mag = Math.pow(10, Math.floor(Math.log10(v)));
        var steps = [1, 1.5, 2, 2.5, 3, 4, 5, 6, 8, 10];
        for (var i = 0; i < steps.length; i++) if (steps[i] * mag >= v) return steps[i] * mag;
        return 10 * mag;
    }
    function el(name, attrs, parent) {
        var node = document.createElementNS(NS, name);
        for (var k in attrs) node.setAttribute(k, attrs[k]);
        if (parent) parent.appendChild(node);
        return node;
    }

    function draw() {
        box.innerHTML = '';
        hideTip();
        if (view === 'table' || !data.length) {
            if (!data.length && view !== 'table') box.innerHTML = '<p class="rp-empty">Khoảng ngày quá dài để vẽ biểu đồ. Hãy chọn tối đa 400 ngày.</p>';
            return;
        }
        var key = view;
        var W = box.clientWidth, H = box.clientHeight;
        var m = { l: 52, r: 14, t: 26, b: 30 };
        var w = Math.max(10, W - m.l - m.r), h = Math.max(10, H - m.t - m.b);
        var n = data.length;
        var max = 0;
        data.forEach(function (d) { if (d[key] > max) max = d[key]; });
        var top = key === 'orders' ? Math.max(4, Math.ceil(max / 4) * 4) : niceMax(max);
        var step = w / n;
        function x(i) { return m.l + step * i + step / 2; }
        function y(v) { return m.t + h - (v / top) * h; }

        var svg = el('svg', { viewBox: '0 0 ' + W + ' ' + H, width: W, height: H }, box);
        var defs = el('defs', {}, svg);
        var grad = el('linearGradient', { id: 'rpFade', x1: 0, y1: 0, x2: 0, y2: 1 }, defs);
        el('stop', { offset: '0%', style: 'stop-color:var(--rp-rev);stop-opacity:.28' }, grad);
        el('stop', { offset: '100%', style: 'stop-color:var(--rp-rev);stop-opacity:0' }, grad);

        var grid = el('g', { 'class': 'grid' }, svg);
        var axis = el('g', { 'class': 'axis' }, svg);
        for (var g = 1; g <= 4; g++) {
            var val = top * g / 4, gy = y(val);
            el('line', { x1: m.l, x2: m.l + w, y1: gy, y2: gy }, grid);
            var t = el('text', { x: m.l - 10, y: gy + 4, 'text-anchor': 'end' }, axis);
            t.textContent = max <= 0 ? '' : (key === 'orders' ? val : compact(val));
        }
        if (max <= 0) {
            var none = el('text', { x: m.l + w / 2, y: m.t + h / 2, 'text-anchor': 'middle' }, axis);
            none.textContent = key === 'orders' ? 'Chưa có đơn hàng trong kỳ' : 'Chưa có doanh thu trong kỳ';
        }
        var zero = el('text', { x: m.l - 10, y: y(0) + 4, 'text-anchor': 'end' }, axis);
        zero.textContent = '0';
        el('line', { 'class': 'base', x1: m.l, x2: m.l + w, y1: y(0), y2: y(0) }, svg);

        var labels = Math.min(n, Math.max(2, Math.floor(w / 70)));
        var every = Math.max(1, Math.ceil(n / labels));
        for (var i = 0; i < n; i += every) {
            var lt = el('text', { x: x(i), y: H - 8, 'text-anchor': 'middle' }, axis);
            lt.textContent = data[i].day;
        }

        var marks = [];
        if (key === 'revenue') {
            var line = '', area = '';
            data.forEach(function (d, i) {
                line += (i ? 'L' : 'M') + x(i).toFixed(1) + ' ' + y(d.revenue).toFixed(1);
            });
            if (n === 1) line += 'L' + (x(0) + 0.1) + ' ' + y(data[0].revenue);
            area = line + 'L' + x(n - 1).toFixed(1) + ' ' + y(0) + 'L' + x(0).toFixed(1) + ' ' + y(0) + 'Z';
            el('path', { 'class': 'area', d: area }, svg);
            var path = el('path', { 'class': 'line', d: line }, svg);
            var len = path.getTotalLength ? path.getTotalLength() : 0;
            if (len && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
                path.style.strokeDasharray = len;
                path.style.strokeDashoffset = len;
                path.getBoundingClientRect();
                path.style.transition = 'stroke-dashoffset 1.1s cubic-bezier(.2,.7,.2,1)';
                path.style.strokeDashoffset = 0;
            }
            if (max > 0) {
                var best = 0;
                data.forEach(function (d, i) { if (d.revenue > data[best].revenue) best = i; });
                var peak = el('g', { 'class': 'peak' }, svg);
                el('circle', { cx: x(best), cy: y(data[best].revenue), r: 4.5 }, peak);
                var anchor = x(best) > m.l + w - 80 ? 'end' : (x(best) < m.l + 80 ? 'start' : 'middle');
                var pt = el('text', { x: x(best), y: y(data[best].revenue) - 12, 'text-anchor': anchor }, peak);
                pt.textContent = 'Cao nhất ' + compact(data[best].revenue);
            }
        } else {
            var bw = Math.max(2, Math.min(18, step * 0.62));
            data.forEach(function (d, i) {
                if (!d.orders) return;
                var bx = x(i) - bw / 2, by = y(d.orders), bh = y(0) - by, r = Math.min(4, bw / 2, bh);
                var p = 'M' + bx + ' ' + y(0) + 'V' + (by + r) + 'Q' + bx + ' ' + by + ' ' + (bx + r) + ' ' + by
                    + 'H' + (bx + bw - r) + 'Q' + (bx + bw) + ' ' + by + ' ' + (bx + bw) + ' ' + (by + r) + 'V' + y(0) + 'Z';
                marks[i] = el('path', { 'class': 'bar', d: p }, svg);
            });
        }

        var cross = el('line', { 'class': 'cross', y1: m.t, y2: y(0), visibility: 'hidden' }, svg);
        var dot = key === 'revenue' ? el('circle', { 'class': 'dot', r: 5, visibility: 'hidden' }, svg) : null;
        var hit = el('rect', { x: m.l, y: 0, width: w, height: H, fill: 'transparent' }, svg);
        var current = -1;

        function show(i) {
            if (i === current) return;
            if (marks[current]) marks[current].classList.remove('is-on');
            current = i;
            var d = data[i], cx = x(i);
            cross.setAttribute('x1', cx); cross.setAttribute('x2', cx); cross.setAttribute('visibility', 'visible');
            if (dot) { dot.setAttribute('cx', cx); dot.setAttribute('cy', y(d.revenue)); dot.setAttribute('visibility', 'visible'); }
            if (marks[i]) marks[i].classList.add('is-on');
            tip.innerHTML = '<span>' + d.day + '/' + d.year + '</span><b>' + vnd(d.revenue) + '</b><span>' + d.orders + ' đơn hàng</span>';
            tip.hidden = false;
            var boxRect = box.getBoundingClientRect(), panelRect = panel.getBoundingClientRect();
            var left = boxRect.left - panelRect.left + cx;
            var half = tip.offsetWidth / 2 + 8;
            left = Math.max(half, Math.min(panel.clientWidth - half, left));
            var anchorY = key === 'revenue' ? y(d.revenue) : y(d.orders);
            tip.style.left = left + 'px';
            tip.style.top = (boxRect.top - panelRect.top + Math.max(m.t + 40, anchorY)) + 'px';
        }
        function pick(clientX) {
            var r = svg.getBoundingClientRect();
            var i = Math.floor((clientX - r.left - m.l) / step);
            show(Math.max(0, Math.min(n - 1, i)));
        }
        hit.addEventListener('mousemove', function (e) { pick(e.clientX); });
        hit.addEventListener('touchstart', function (e) { pick(e.touches[0].clientX); }, { passive: true });
        hit.addEventListener('touchmove', function (e) { pick(e.touches[0].clientX); }, { passive: true });
        hit.addEventListener('mouseleave', function () {
            hideTip();
            cross.setAttribute('visibility', 'hidden');
            if (dot) dot.setAttribute('visibility', 'hidden');
            if (marks[current]) marks[current].classList.remove('is-on');
            current = -1;
        });
    }

    function hideTip() { tip.hidden = true; }

    tabs.forEach(function (btn) {
        btn.addEventListener('click', function () {
            view = btn.getAttribute('data-view');
            tabs.forEach(function (b) { b.setAttribute('aria-selected', b === btn ? 'true' : 'false'); });
            box.hidden = view === 'table';
            table.hidden = view !== 'table';
            draw();
        });
    });

    var timer;
    window.addEventListener('resize', function () { clearTimeout(timer); timer = setTimeout(draw, 120); });
    draw();
})();
