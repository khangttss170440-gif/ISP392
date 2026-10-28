(() => {
    const link = document.getElementById('external-navigation');
    if (link) window.top.location.assign(link.href);
})();
