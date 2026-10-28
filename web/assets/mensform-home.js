/* MENSFORM home page interactions. Everything degrades to static content without JS. */
(function () {
  var settings = document.querySelector('[data-runtime-settings]').dataset;
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
    var sizes=settings.fitSizes.split('|'), limits=settings.fitWeights.split(',').map(Number);
    var i=limits.findIndex(function(limit){return w<limit});if(i<0)i=sizes.length-1;
    if(h>=Number(settings.fitTall)&&i<sizes.length-1)i++;if(h<Number(settings.fitShort)&&i>0)i--;
    $('sugSize').textContent='Size '+sizes[i];}
  $('hIn').addEventListener('input',suggest);$('wIn').addEventListener('input',suggest);
  suggest();setForm('regular');

  /* ---- product hotspots are rendered from active database products ---- */
  var hots = document.querySelectorAll('.hot');
  var LK = Array.prototype.map.call(hots, function (b) {
    return [b.dataset.name, b.dataset.description, b.dataset.meta];
  });
  function lk(i) {
    var it = LK[i];
    $('spNum').textContent = '0' + (i + 1);
    var sw = $('spWrap'); sw.classList.remove('pop'); void sw.offsetWidth; sw.classList.add('pop');
    $('spName').textContent = it[0]; $('spDesc').textContent = it[1]; $('spPrice').textContent = it[2];
    if (hots[i]) $('spGo').href = hots[i].href;
    hots.forEach(function (b) { b.classList.toggle('is-on', +b.dataset.k === i); });
    document.querySelectorAll('#spList button').forEach(function (b, k) { b.setAttribute('aria-current', k === i); });
  }
  if ($('spList')) {
    LK.forEach(function (it, k) {
      var li = document.createElement('li');
      var button = document.createElement('button');
      var name = document.createElement('span');
      var meta = document.createElement('span');
      button.type = 'button';
      name.textContent = String(k + 1).padStart(2, '0') + ' · ' + it[0];
      meta.textContent = it[2];
      button.appendChild(name);
      button.appendChild(meta);
      button.addEventListener('click', function () { lk(k); });
      li.appendChild(button);
      $('spList').appendChild(li);
    });
    /* hover or keyboard focus previews the piece; a click opens its product page */
    hots.forEach(function (b) { var k = +b.dataset.k; b.addEventListener('mouseenter', function () { lk(k); }); b.addEventListener('focus', function () { lk(k); }); });
    lk(0);
  }

  /* ---- staff-published outfit styles and products ---- */
  var outfitTabs = Array.prototype.slice.call(document.querySelectorAll('[data-outfit-tab]'));
  var outfitPanels = Array.prototype.slice.call(document.querySelectorAll('[data-outfit-panel]'));
  function selectOutfit(index) {
    outfitTabs.forEach(function (tab, i) {
      var active = i === index;
      tab.setAttribute('aria-selected', active);
      tab.tabIndex = active ? 0 : -1;
      outfitPanels[i].hidden = !active;
    });
  }
  outfitTabs.forEach(function (tab, index) {
    tab.addEventListener('click', function () { selectOutfit(index); });
    tab.addEventListener('keydown', function (e) {
      if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
        e.preventDefault();
        var next = (index + (e.key === 'ArrowRight' ? 1 : outfitTabs.length - 1)) % outfitTabs.length;
        selectOutfit(next);
        outfitTabs[next].focus();
      }
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
