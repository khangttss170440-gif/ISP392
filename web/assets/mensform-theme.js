/* MENSFORM light/dark mode. Loaded without defer at the top of <body> so the
   saved theme is applied before the page paints. Any [data-theme-toggle]
   button switches it; the choice is kept in localStorage. */
(function () {
    var KEY = 'mf-theme', root = document.documentElement;
    function saved() { try { return localStorage.getItem(KEY); } catch (e) { return null; } }
    function system() { return window.matchMedia && matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'; }
    function sync() {
        var dark = root.getAttribute('data-theme') === 'dark';
        var buttons = document.querySelectorAll('[data-theme-toggle]');
        for (var i = 0; i < buttons.length; i++) {
            buttons[i].setAttribute('aria-pressed', dark ? 'true' : 'false');
            buttons[i].setAttribute('aria-label', dark ? 'Chuyển sang chế độ sáng' : 'Chuyển sang chế độ tối');
            buttons[i].title = dark ? 'Chế độ sáng' : 'Chế độ tối';
        }
    }
    function apply(theme) { root.setAttribute('data-theme', theme); sync(); }
    apply(saved() || system());
    if (window.matchMedia) {
        var mq = matchMedia('(prefers-color-scheme: dark)');
        var onChange = function (e) { if (!saved()) apply(e.matches ? 'dark' : 'light'); };
        if (mq.addEventListener) mq.addEventListener('change', onChange); else if (mq.addListener) mq.addListener(onChange);
    }
    document.addEventListener('click', function (e) {
        var btn = e.target.closest && e.target.closest('[data-theme-toggle]');
        if (!btn) return;
        var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        try { localStorage.setItem(KEY, next); } catch (err) {}
        apply(next);
    });
    document.addEventListener('DOMContentLoaded', sync);
})();
