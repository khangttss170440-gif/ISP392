/* Collections page: "Thêm cả set vào giỏ" posts every piece of a look to /cart (one variant per request,
   the same action=add form the product page uses), then opens the cart. */
(function () {
  function msg(look, text) { var m = look.querySelector('.cx-buy-msg'); if (m) m.textContent = text; }
  document.querySelectorAll('.cx-buy').forEach(function (btn) {
    btn.addEventListener('click', function () {
      var look = btn.closest('.cx-look'), ids = [], missing = null;
      look.querySelectorAll('[data-buy]').forEach(function (f) {
        f.classList.remove('is-missing');
        if (f.value) ids.push(f.value); else if (!missing) { missing = f; }
        if (!f.value) f.classList.add('is-missing');
      });
      if (missing) { msg(look, 'Chọn size cho các món còn thiếu trước khi thêm cả set.'); missing.focus(); return; }
      if (!ids.length) { msg(look, 'Set này hiện chưa có món nào còn hàng.'); return; }
      btn.disabled = true; msg(look, 'Đang thêm ' + ids.length + ' món vào giỏ…');
      var cart = btn.dataset.cart, login = btn.dataset.login, i = 0;
      (function next() {
        if (i >= ids.length) { location.href = cart; return; }
        var body = new URLSearchParams({ action: 'add', variantId: ids[i++], quantity: '1' });
        fetch(cart, { method: 'POST', body: body, credentials: 'same-origin' }).then(function (r) {
          if (r.url && r.url.indexOf('/login') !== -1) { location.href = login; return; }
          next();
        }).catch(function () { btn.disabled = false; msg(look, 'Không kết nối được giỏ hàng. Vui lòng thử lại.'); });
      })();
    });
  });
  document.querySelectorAll('.cx-pick select').forEach(function (s) {
    s.addEventListener('change', function () { s.classList.toggle('is-missing', !s.value); });
  });
})();
