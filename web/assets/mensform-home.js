/* MENSFORM home page interactions. Everything degrades to static content without JS. */
(function () {
  var motionOK = window.matchMedia('(prefers-reduced-motion: no-preference)').matches;
  var $ = function (id) { return document.getElementById(id); };
  var IMG = (document.querySelector('.hero-pic img') || {src: ''}).src.replace(/[^/]*$/, '');

  /* category index preview */
  var ix=document.getElementById('catIndex');
  if(ix){var rws=ix.querySelectorAll('.ix-row'),fgs=ix.querySelectorAll('.ix-fig'),cur=0,auto=null;
    function show(i){if(i===cur)return;rws[cur].classList.remove('on');fgs[cur].classList.remove('on');cur=i;rws[i].classList.add('on');fgs[i].classList.add('on')}
    function stop(){if(auto){clearInterval(auto);auto=null}}
    rws.forEach(function(r){var i=+r.dataset.i;r.addEventListener('mouseenter',function(){stop();show(i)});r.addEventListener('focus',function(){stop();show(i)})});
    if(motionOK&&'IntersectionObserver' in window){new IntersectionObserver(function(es){es.forEach(function(e){if(e.isIntersecting&&!auto&&!ix.dataset.done){ix.dataset.done=1;auto=setInterval(function(){show((cur+1)%rws.length)},3200)}else if(!e.isIntersecting)stop()})},{threshold:.4}).observe(ix)}
  }
  var bz=document.getElementById('beltZone');
  if(bz&&motionOK){var bt=false;function belt(){bt=false;var r=bz.getBoundingClientRect(),vh=innerHeight;var p=(vh-r.top)/(vh*0.62);p=Math.max(0,Math.min(1,p));bz.style.setProperty('--p',p.toFixed(3))}
    addEventListener('scroll',function(){if(!bt){bt=true;requestAnimationFrame(belt)}},{passive:true});belt();}

  /* ---- form finder ---- */
  var F={slim:{sh:72,ch:60,wa:52,sw:22,hp:58,kn:48,hm:36,ease:6,hem:16,name:'Slim',desc:'Ôm gọn theo vai và eo, tôn dáng cho người gầy hoặc vừa người.',fit:'Dáng gầy, vừa'},
         regular:{sh:76,ch:66,wa:62,sw:26,hp:62,kn:56,hm:44,ease:10,hem:18.5,name:'Regular',desc:'Cân bằng giữa gọn và thoải mái. Hợp với hầu hết dáng người, mặc cả ngày.',fit:'Mọi dáng người'},
         relaxed:{sh:84,ch:76,wa:74,sw:33,hp:70,kn:68,hm:56,ease:16,hem:21,name:'Relaxed',desc:'Rộng rãi, rủ tự nhiên. Hợp người vai to hoặc thích mặc thoải mái.',fit:'Vai to, thích rộng'}};
  var cx=180,curF='regular',P=Object.assign({},F.regular);
  function paths(p){
    var sh=p.sh,ch=p.ch,wa=p.wa,sw=p.sw,hp=p.hp,kn=p.kn,hm=p.hm;
    $('shirt').setAttribute('d','M'+(cx-20)+',34 Q'+cx+',50 '+(cx+20)+',34 L'+(cx+sh)+',52 Q'+(cx+ch+4)+',70 '+(cx+ch)+',116 L'+(cx+wa)+',262 Q'+cx+',274 '+(cx-wa)+',262 L'+(cx-ch)+',116 Q'+(cx-ch-4)+',70 '+(cx-sh)+',52 Z');
    var o=sw-22;
    $('slvL').setAttribute('d','M'+(cx-sh)+',52 L'+(cx-sh-16-o*.6)+',236 L'+(cx-sh-16-o*.6+sw)+',240 L'+(cx-ch+2)+',118 Z');
    $('slvR').setAttribute('d','M'+(cx+sh)+',52 L'+(cx+sh+16+o*.6)+',236 L'+(cx+sh+16+o*.6-sw)+',240 L'+(cx+ch-2)+',118 Z');
    $('pants').setAttribute('d','M'+(cx-hp+4)+',258 L'+(cx+hp-4)+',258 L'+(cx+hp)+',310 L'+(cx+6+kn)+',450 L'+(cx+6+hm)+',590 L'+(cx+6)+',590 L'+(cx+5)+',450 L'+cx+',340 L'+(cx-5)+',450 L'+(cx-6)+',590 L'+(cx-6-hm)+',590 L'+(cx-6-kn)+',450 L'+(cx-hp)+',310 Z');
    $('placket').setAttribute('d','M'+cx+',46 L'+cx+',266');
    dim('dimChest',cx-ch,cx+ch,140,'Ngực +'+Math.round(p.ease)+' cm');
    dim('dimHem',cx+6,cx+6+hm,598,'Ống '+(Math.round(p.hem*2)/2).toString().replace('.',',')+' cm');
  }
  function dim(id,x1,x2,y,t){var g=$(id),w=x2-x1;g.querySelector('.tp').setAttribute('x',x1);g.querySelector('.tp').setAttribute('y',y);g.querySelector('.tp').setAttribute('width',w);
    g.querySelector('.tk').setAttribute('x',x1);g.querySelector('.tk').setAttribute('y',y);g.querySelector('.tk').setAttribute('width',w);
    var tx=g.querySelector('.dl');tx.setAttribute('x',x1+w/2);tx.setAttribute('y',id==='dimHem'?y+32:y-8);tx.textContent=t;}
  function setForm(k){
    curF=k;var to=F[k],from=Object.assign({},P),t0=performance.now(),dur=motionOK?520:0;
    document.querySelectorAll('.seg button').forEach(function(b){var a=b.dataset.f===k;b.setAttribute('aria-checked',a);b.tabIndex=a?0:-1});
    $('formDesc').textContent=to.desc;$('figName').textContent='Form '+to.name;$('figNote').textContent='Vòng ngực rộng hơn cơ thể '+to.ease+' cm';
    $('specs').innerHTML='<div><dt>Độ rộng ngực</dt><dd>+'+to.ease+' cm</dd></div><div><dt>Hợp với</dt><dd>'+to.fit+'</dd></div>';
    (function step(now){var e=dur?Math.min(1,(now-t0)/dur):1;e=1-Math.pow(1-e,3);for(var q in to)if(typeof to[q]==='number')P[q]=from[q]+(to[q]-from[q])*e;paths(P);if(e<1)requestAnimationFrame(step)})(t0);
  }
  document.querySelectorAll('.seg button').forEach(function(b){b.addEventListener('click',function(){setForm(b.dataset.f)});
    b.addEventListener('keydown',function(e){var ks=['slim','regular','relaxed'],i=ks.indexOf(curF);if(e.key==='ArrowRight'||e.key==='ArrowLeft'){e.preventDefault();i=(i+(e.key==='ArrowRight'?1:2))%3;setForm(ks[i]);document.querySelector('.seg [data-f='+ks[i]+']').focus()}})});
  function suggest(){var h=+$('hIn').value,w=+$('wIn').value;$('hOut').textContent=h+' cm';$('wOut').textContent=w+' kg';
    var sizes=['S','M','L','XL','XXL'],i=w<55?0:w<64?1:w<73?2:w<83?3:4;if(h>=183&&i<4)i++;if(h<162&&i>0)i--;
    $('sugSize').textContent='Size '+sizes[i];}
  $('hIn').addEventListener('input',suggest);$('wIn').addEventListener('input',suggest);
  suggest();setForm('regular');

  /* ---- accessory hotspots: each point links to its product (IDs 41-45) ---- */
  var LK = [['Đồng hồ mặt gỗ dây da', 'Mặt gỗ mun, dây da đen. Hợp sơ mi sáng màu và bộ vest tối.', '1.290.000₫'],
            ['Khuy măng sét bạc', 'Điểm nhấn kim loại nhỏ cho sơ mi cổ tay gập.', '350.000₫'],
            ['Kính mát gọng gỗ', 'Gọng gỗ vuông, tròng tráng gương phân cực. Nhẹ và ấm tay.', '690.000₫'],
            ['Kính mát clubmaster', 'Viền trên đậm, gọng kim loại dưới. Kiểu cổ điển không lỗi thời.', '650.000₫'],
            ['Cà vạt hoa văn kèm ghim cài', 'Lụa hoa văn tông hồng khói, kèm ghim cài ve áo hình hoa hồng.', '420.000₫']];
  var hots = document.querySelectorAll('.hot');
  function lk(i) {
    var it = LK[i];
    $('spNum').textContent = '0' + (i + 1);
    var sw = $('spWrap'); sw.classList.remove('pop'); void sw.offsetWidth; sw.classList.add('pop');
    $('spName').textContent = it[0]; $('spDesc').textContent = it[1]; $('spPrice').textContent = 'Giá ' + it[2];
    if (hots[i]) $('spGo').href = hots[i].href;
    hots.forEach(function (b) { b.classList.toggle('is-on', +b.dataset.k === i); });
    document.querySelectorAll('#spList button').forEach(function (b, k) { b.setAttribute('aria-current', k === i); });
  }
  if ($('spList')) {
    $('spList').innerHTML = LK.map(function (it, k) { return '<li><button type="button"><span>0' + (k + 1) + ' · ' + it[0] + '</span><span>' + it[2] + '</span></button></li>'; }).join('');
    document.querySelectorAll('#spList button').forEach(function (b, k) { b.addEventListener('click', function () { lk(k); }); });
    /* hover or keyboard focus previews the piece; a click opens its product page */
    hots.forEach(function (b) { var k = +b.dataset.k; b.addEventListener('mouseenter', function () { lk(k); }); b.addEventListener('focus', function () { lk(k); }); });
    lk(0);
  }

  /* ---- outfit by occasion ---- */
  var sets = {
    work: {img: 'look-work.webp', alt: 'Bộ phối đi làm: áo khoác sáng màu và quần be', desc: 'Áo khoác nhẹ, quần be và thắt lưng da. Gọn gàng từ cuộc họp sáng đến bữa tối với đối tác.',
      items: ['Sơ mi kẻ sọc cổ đức', 'Quần kaki form slim', 'Thắt lưng da bò', 'Áo blazer nâu']},
    date: {img: 'look-date.webp', alt: 'Bộ phối hẹn hò: sơ mi linen trắng và kính râm', desc: 'Sơ mi linen trắng, quần sáng màu và kính râm. Lịch sự nhưng không quá trang trọng.',
      items: ['Sơ mi linen trắng', 'Quần tây đen', 'Đồng hồ dây da', 'Kính mát gọng tròn']},
    week: {img: 'look-weekend.webp', alt: 'Bộ phối cuối tuần: áo khoác nhung tăm và áo thun trắng', desc: 'Áo khoác nhung tăm ngoài áo thun trắng, quần form relaxed. Thoải mái đi cà phê, dạo phố.',
      items: ['Áo thun cotton trắng', 'Quần tây be form regular', 'Áo khoác nhung tăm', 'Túi da đeo chéo']}
  };
  var curO = 'work', panel = $('outfitPanel'), img = $('outfitImg');
  function pick(k) {
    curO = k; var s = sets[k];
    document.querySelectorAll('.tabs button').forEach(function (t) { var a = t.dataset.k === k; t.setAttribute('aria-selected', a); t.tabIndex = a ? 0 : -1; if (a) panel.setAttribute('aria-labelledby', t.id); });
    img.style.opacity = 0;
    setTimeout(function () { img.src = IMG + s.img; img.alt = s.alt; img.style.opacity = 1; }, motionOK ? 200 : 0);
    $('outfitDesc').textContent = s.desc;
    $('outfitItems').innerHTML = s.items.map(function (n) { return '<li>' + n + '</li>'; }).join('');
  }
  if (panel) document.querySelectorAll('.tabs button').forEach(function (t) {
    t.addEventListener('click', function () { pick(t.dataset.k); });
    t.addEventListener('keydown', function (e) {
      var ks = ['work', 'date', 'week'], i = ks.indexOf(curO);
      if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') { e.preventDefault(); i = (i + (e.key === 'ArrowRight' ? 1 : 2)) % 3; pick(ks[i]); $('t-' + ks[i]).focus(); }
    });
  });

  /* ---- copy promotion codes ---- */
  document.querySelectorAll('.code button').forEach(function (b) {
    b.addEventListener('click', function () {
      var done = function () { b.textContent = 'Đã chép'; setTimeout(function () { b.textContent = 'Sao chép'; }, 1600); };
      var sel = function () { var c = b.previousElementSibling, rg = document.createRange(); rg.selectNodeContents(c); var s = getSelection(); s.removeAllRanges(); s.addRange(rg); };
      try { navigator.clipboard.writeText(b.dataset.code).then(done, sel); } catch (e) { sel(); }
    });
  });

  /* ---- entrance motion ---- */
  if (motionOK && 'IntersectionObserver' in window) {
    document.documentElement.classList.add('js');
    var io = new IntersectionObserver(function (es) { es.forEach(function (e) { if (e.isIntersecting) { e.target.classList.add('seen'); io.unobserve(e.target); } }); }, {threshold: .12});
    document.querySelectorAll('.reveal').forEach(function (el) { io.observe(el); });
  }
})();
