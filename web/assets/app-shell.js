(() => {
    const shell = document.querySelector('[data-app-shell]');
    if (!shell) return;
    const frame = document.getElementById('app-page');
    const appRoot = new URL(shell.dataset.appRoot, window.location.origin);
    const localPage = (value) => {
        if (typeof value !== 'string' || !value) return null;
        try {
            const url = new URL(value, appRoot);
            return url.origin === appRoot.origin && url.pathname.startsWith(appRoot.pathname)
                && !url.username && !url.password ? url : null;
        } catch (_) { return null; }
    };
    const initialPage = localPage(shell.dataset.initialPage);
    // Store the current page in this tab's history state so Refresh restores
    // it without putting internal routes or query parameters in the address bar.
    const restoredPage = window.location.pathname === appRoot.pathname
        ? localPage(window.history.state?.mensformPage) : null;
    const page = restoredPage || initialPage;
    if (!page) return;
    const remember = (url) => window.history.replaceState(
        { ...window.history.state, mensformPage: url.href }, '', appRoot.pathname);
    window.addEventListener('message', (event) => {
        if (event.origin !== appRoot.origin || event.source !== frame.contentWindow
                || event.data?.type !== 'mensform-page-ready') return;
        const current = localPage(event.data.url);
        if (!current) return;
        remember(current);
        if (typeof event.data.title === 'string') document.title = event.data.title;
    });
    frame.addEventListener('load', () => {
        try {
            const current = localPage(frame.contentWindow.location.href);
            if (!current) return;
            remember(current);
            document.title = frame.contentDocument.title || frame.title;
        } catch (_) {
            // External providers handle their own page and origin.
        }
    });
    remember(page);
    frame.src = page.href;
})();
