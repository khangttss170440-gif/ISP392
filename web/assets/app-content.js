(() => {
    if (window.parent === window) return;
    // Report after HTML parsing, without waiting for slow images or fonts.
    window.parent.postMessage({
        type: 'mensform-page-ready', url: window.location.href, title: document.title
    }, window.location.origin);
})();
