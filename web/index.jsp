<%@page contentType="text/html" pageEncoding="UTF-8" import="java.util.List,model.Product,model.ProductCategory,model.OutfitSet,model.OutfitItem,service.OutfitRecommendationService.Style"%>
<!DOCTYPE html>
<html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>MENSFORM — Phụ kiện nam. Chuẩn form. Chất riêng.</title>
    <meta name="description" content="MENSFORM: đồng hồ, thắt lưng, ví, túi da, kính mát và cà vạt cho nam. Tinh tế trong từng chi tiết.">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/chatbot.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/home-theme.css?v=20261024e">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/mensform-home.css?v=20261024g">
</head><body class="home-theme mf-home">
<%@include file="/includes/header.jspf"%>
<% String homeCtx = request.getContextPath(); %>
<main class="wrap mf-main">
  <section class="hero" aria-labelledby="heroTitle">
    <div class="hero-copy">
      <span class="kicker">Phụ kiện nam · Bộ sưu tập mới</span>
      <h1 id="heroTitle"><span class="ln"><span>Chuẩn form.</span></span><span class="ln"><span style="--d:.12s">Chất riêng.</span></span></h1>
      <p>Tinh tế trong từng chi tiết, khẳng định phong cách riêng – tự tin từ công sở đến những cuộc hẹn cuối tuần.</p>
      <div><a class="pill" href="#cats">Khám phá phụ kiện <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></a></div>
      <div class="facts">
        <div><b>6 dòng</b><span>Phụ kiện da &amp; kim loại</span></div>
        <div><b>GHN</b><span>Giao toàn quốc</span></div>
        <div><b>VNPay</b><span>Hoặc COD</span></div>
      </div>
      <div class="perks">
        <div><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true"><path d="M5 19c8 0 14-6 14-14C11 5 5 11 5 19Zm0 0 7-7"/></svg>Chất liệu<br>chọn lọc</div>
        <div><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true"><path d="M12 3 4.5 6v5.5c0 4.5 3.2 8 7.5 9.5 4.3-1.5 7.5-5 7.5-9.5V6L12 3Z"/><path d="m9 12 2.2 2.2L15.5 10"/></svg>Bền bỉ<br>mỗi ngày</div>
        <div><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" aria-hidden="true"><path d="M7.5 15.5c-2 0-3.5-1.6-3.5-3.5s1.5-3.5 3.5-3.5c3.5 0 5.5 7 9 7 2 0 3.5-1.6 3.5-3.5S18.5 8.5 16.5 8.5c-3.5 0-5.5 7-9 7Z"/></svg>Phong cách<br>bền lâu</div>
      </div>
    </div>
    <div class="hero-pic">
      <img src="<%=homeCtx%>/assets/images/home/hero.webp" style="object-position:50% 20%" width="1152" height="1440" alt="Người đàn ông đeo kính mát và đồng hồ dây da, mặc áo thun đen">
      <div class="script" aria-hidden="true">Chất trong<br>từng chi tiết</div>
      <a class="detail" href="<%=homeCtx%>/products">
        <img src="<%=homeCtx%>/assets/images/home/detail.webp" width="800" height="450" alt="Cận cảnh đường may trên da hạt nâu" loading="lazy">
        <div><span>Tỉ mỉ từng<br>đường may</span><i aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></i></div>
      </a>
    </div>
  </section>

  <div class="belt-zone" id="beltZone" aria-hidden="true">
    <div class="strap strap-a"><span class="deboss">MENSFORM ✦ PHỤ KIỆN NAM ✦ MENSFORM ✦ PHỤ KIỆN NAM ✦ MENSFORM ✦ PHỤ KIỆN NAM ✦ MENSFORM</span></div>
    <div class="strap strap-b" id="strapB"><i></i><i></i><i></i><i></i><i></i><span class="deboss">MENSFORM ✦ ĐÚNG NẤC ✦ ĐÚNG BẠN ✦ MENSFORM ✦ ĐÚNG NẤC ✦ ĐÚNG BẠN</span></div>
    <svg class="buckle" viewBox="0 0 120 104">
      <defs><linearGradient id="metal" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#f3efe6"/><stop offset=".35" stop-color="#b9b4aa"/><stop offset=".6" stop-color="#e9e5dc"/><stop offset="1" stop-color="#8d887e"/></linearGradient></defs>
      <path d="M14 6h92a10 10 0 0 1 10 10v72a10 10 0 0 1-10 10H14A10 10 0 0 1 4 88V16A10 10 0 0 1 14 6Zm8 14v64h76V20Z" fill="url(#metal)" fill-rule="evenodd" stroke="#6f6a61" stroke-width="1"/>
      <rect class="prong" x="10" y="48" width="64" height="8" rx="4" fill="url(#metal)" stroke="#6f6a61" stroke-width="1"/>
    </svg>
    <p class="belt-cap">Cài đúng nấc. Chuẩn phong cách.</p>
  </div>

  <% List<ProductCategory> homeCategories = (List<ProductCategory>) request.getAttribute("homeCategories"); %>
  <section class="block reveal" id="cats" aria-labelledby="catTitle">
    <div class="head"><div><span class="kicker num rise">Danh mục cửa hàng</span><h2 id="catTitle" class="rise" style="--i:1">Tìm món đồ đúng gu của bạn</h2></div><a class="link-all rise" href="<%=homeCtx%>/products">Xem tất cả sản phẩm <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></a></div>
    <% if (homeCategories != null && !homeCategories.isEmpty()) { %>
    <div class="ix rise" id="catIndex">
      <ul class="ix-list">
        <% for (int i = 0; i < homeCategories.size(); i++) {
             ProductCategory category = homeCategories.get(i);
             String categoryDescription = category.getDescription();
             if (categoryDescription == null || categoryDescription.isBlank()) categoryDescription = "Khám phá " + category.getActiveProductCount() + " sản phẩm trong danh mục này.";
        %>
        <li><a class="ix-row <%=i == 0 ? "on" : ""%>" href="<%=homeCtx%>/products?categoryId=<%=category.getCategoryID()%>" data-i="<%=i%>">
          <img class="ix-thumb" src="<%=homeCtx%>/product-image?productId=<%=category.getFeaturedProductID()%>" width="640" height="800" alt="" loading="lazy">
          <span class="ix-name"><%=navEscape(category.getCategoryName())%></span><span class="ix-sub"><%=navEscape(categoryDescription)%></span>
          <span class="ix-count"><%=category.getActiveProductCount()%> mẫu</span><svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg>
        </a></li>
        <% } %>
      </ul>
      <div class="ix-stage" aria-hidden="true">
        <% for (int i = 0; i < homeCategories.size(); i++) {
             ProductCategory category = homeCategories.get(i);
             String categoryDescription = category.getDescription();
             if (categoryDescription == null || categoryDescription.isBlank()) categoryDescription = category.getActiveProductCount() + " sản phẩm đang hoạt động";
        %>
        <figure class="ix-fig <%=i == 0 ? "on" : ""%>"><img src="<%=homeCtx%>/product-image?productId=<%=category.getFeaturedProductID()%>" width="640" height="800" alt="<%=navEscape(category.getCategoryName())%>" loading="lazy"><figcaption><span><%=navEscape(category.getCategoryName())%></span><small><%=navEscape(categoryDescription)%></small></figcaption></figure>
        <% } %>
        <span class="ix-stamp">MENSFORM<br>Danh mục</span>
      </div>
    </div>
    <% } else { %>
    <p class="home-products-empty"><%=Boolean.TRUE.equals(request.getAttribute("homeCategoriesUnavailable")) ? "Chưa tải được danh mục. Bạn vui lòng thử lại sau." : "Danh mục sản phẩm sẽ sớm được cập nhật."%></p>
    <% } %>
  </section>

  <%@include file="/includes/home-products.jspf"%>

  <%
    List<Product> homeSpotProducts = (List<Product>) request.getAttribute("homeProducts");
    int homeSpotCount = homeSpotProducts == null ? 0 : Math.min(5, homeSpotProducts.size());
    String[] homeSpotPositions = {"left:10%;top:20%", "left:28%;top:9%", "left:46%;top:30%", "left:24%;top:66%", "left:72%;top:80%"};
  %>
  <% if (homeSpotCount > 0) { %>
  <section class="block reveal" id="look" aria-labelledby="lookTitle">
    <div class="head"><div><span class="kicker num rise">Phối phụ kiện</span><h2 id="lookTitle" class="rise" style="--i:1">Một mặt bàn. <%=homeSpotCount%> món. Một phong cách.</h2></div><p class="head-note rise" style="--i:2">Rê chuột lên từng điểm để xem sản phẩm đang có trong cửa hàng.</p></div>
    <div class="spot rise">
      <div class="spot-pic">
        <img src="<%=homeCtx%>/assets/images/home/flatlay.webp" width="1600" height="1067" alt="Các phụ kiện nam trên mặt bàn gỗ" loading="lazy">
        <% for (int i = 0; i < homeSpotCount; i++) {
             Product spotProduct = homeSpotProducts.get(i);
             String spotDescription = spotProduct.getDescription();
             if (spotDescription == null || spotDescription.isBlank()) {
                 spotDescription = spotProduct.getCategoryName() == null ? "Sản phẩm đang bán tại MENSFORM." : spotProduct.getCategoryName();
             }
        %>
        <a class="hot" style="<%=homeSpotPositions[i]%>" data-k="<%=i%>" data-name="<%=navEscape(spotProduct.getProductName())%>" data-description="<%=navEscape(spotDescription)%>" data-meta="Mã sản phẩm #<%=spotProduct.getProductID()%>" href="<%=homeCtx%>/product-detail?productId=<%=spotProduct.getProductID()%>" aria-label="<%=navEscape(spotProduct.getProductName())%> – xem sản phẩm"><span><%=i + 1%></span></a>
        <% } %>
      </div>
      <aside class="spot-card" aria-live="polite">
        <div class="spot-num-wrap pop" id="spWrap"><div class="spot-num" id="spNum">01</div>
          <svg class="spot-orn" viewBox="0 0 200 118" aria-hidden="true"><defs><linearGradient id="giltG" x1="0" x2="1"><stop offset="0" stop-color="#8a6420"/><stop offset=".45" stop-color="#e6c674"/><stop offset="1" stop-color="#9c7428"/></linearGradient></defs>
            <path class="fl" d="M10 108c34 10 88 8 128-6 22-8 40-4 44 8 3 9-6 15-13 10-6-4-2-12 5-10"/>
            <path class="fl thin" d="M70 112c20-2 36-8 44-18 6-8 2-15-5-13-6 2-5 10 1 10"/>
            <path class="fl thin" d="M118 30c12-14 30-18 40-8 7 8 1 18-7 15-6-2-5-10 2-10"/>
            <path class="fl thin" d="M150 52c14-2 24 4 24 12"/>
            <circle class="dot" cx="186" cy="108" r="1.6"/><circle class="dot" cx="160" cy="44" r="1.3"/><circle class="dot" cx="58" cy="114" r="1.2"/>
            <path class="spk" transform="translate(132 18)" d="M0-7 1.3-1.3 7 0 1.3 1.3 0 7-1.3 1.3-7 0-1.3-1.3Z"/><path class="spk" transform="translate(178 64) scale(.7)" d="M0-7 1.3-1.3 7 0 1.3 1.3 0 7-1.3 1.3-7 0-1.3-1.3Z"/><path class="spk" transform="translate(20 22) scale(.55)" d="M0-7 1.3-1.3 7 0 1.3 1.3 0 7-1.3 1.3-7 0-1.3-1.3Z"/><path class="spk" transform="translate(100 104) scale(.6)" d="M0-7 1.3-1.3 7 0 1.3 1.3 0 7-1.3 1.3-7 0-1.3-1.3Z"/>
          </svg></div>
        <h3 id="spName"></h3>
        <p id="spDesc"></p>
        <div class="spot-cta"><span id="spPrice">Có sẵn tại MENSFORM</span><a class="pill" id="spGo" href="<%=homeCtx%>/products">Xem sản phẩm <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></a></div>
        <ol class="spot-list" id="spList"></ol>
      </aside>
    </div>
  </section>
  <% } %>

  <section class="block reveal" id="form" aria-labelledby="formTitle">
    <div class="head"><div><span class="kicker num rise">Chọn form của bạn</span><h2 id="formTitle" class="rise" style="--i:1">Ba dáng cắt. Một chuẩn MENSFORM.</h2></div><p class="head-note rise" style="--i:2">Cùng một size, mỗi form cho cảm giác mặc khác nhau. Chọn form trước, size sau.</p></div>
    <div class="former">
      <figure class="fig rise">
        <svg viewBox="0 0 360 640" role="img" aria-labelledby="figCap">
          <defs><pattern id="mm" width="6" height="10" patternUnits="userSpaceOnUse"><path d="M0 0v4" stroke="#2a2416" stroke-width="1"/></pattern></defs>
          <path id="slvL" class="cloth"/><path id="slvR" class="cloth"/>
          <path id="pants" class="cloth"/>
          <path id="shirt" class="cloth"/>
          <path id="placket" class="seam"/>
          <g class="dim" id="dimChest"><rect class="tp" height="14" rx="2"/><rect class="tk" height="10" fill="url(#mm)"/><text class="dl" text-anchor="middle"></text></g>
          <g class="dim" id="dimHem"><rect class="tp" height="14" rx="2"/><rect class="tk" height="10" fill="url(#mm)"/><text class="dl" text-anchor="middle"></text></g>
        </svg>
        <figcaption id="figCap"><span id="figName">Form Regular</span><span id="figNote">Vòng ngực rộng hơn cơ thể 10 cm</span></figcaption>
      </figure>
      <div class="form-side">
        <div class="seg rise" role="radiogroup" aria-label="Chọn form">
          <button type="button" role="radio" aria-checked="false" data-f="slim">Slim</button>
          <button type="button" role="radio" aria-checked="true" data-f="regular">Regular</button>
          <button type="button" role="radio" aria-checked="false" data-f="relaxed">Relaxed</button>
        </div>
        <p class="form-desc rise" id="formDesc" aria-live="polite"></p>
        <dl class="specs rise" id="specs"></dl>
        <div class="finder rise">
          <h3>Tìm size trong 5 giây</h3>
          <div class="range"><label for="hIn"><span>Chiều cao</span><output id="hOut" for="hIn"></output></label><input class="tape-range" type="range" id="hIn" min="155" max="190" value="172"></div>
          <div class="range"><label for="wIn"><span>Cân nặng</span><output id="wOut" for="wIn"></output></label><input class="tape-range" type="range" id="wIn" min="45" max="95" value="66"></div>
          <div class="suggest"><p>Size phù hợp với bạn <b id="sugSize">Size L</b></p></div>
          <p class="finder-note">Gợi ý tham khảo theo chiều cao, cân nặng. Kiểm tra bảng size ở từng sản phẩm trước khi đặt.</p>
        </div>
      </div>
    </div>
  </section>

  <section class="story reveal" id="collection" aria-labelledby="storyTitle">
    <div class="story-pic"><img src="<%=homeCtx%>/assets/images/home/tie.webp" width="640" height="800" alt="Cà vạt chấm bi nâu trên sơ mi trắng, tay cầm áo vest xanh navy" loading="lazy" style="object-position:50% 25%"><a class="pill" href="<%=homeCtx%>/collections">Xem câu chuyện <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></a></div>
    <div class="story-copy">
      <span class="kicker rise">Tự tin trong từng chi tiết</span>
      <h2 id="storyTitle" class="rise" style="--i:1">Chi tiết nhỏ.<br>Dấu ấn lớn.</h2>
      <p class="rise" style="--i:2">Một chiếc cà vạt, chiếc đồng hồ hay thắt lưng đúng màu là đủ để bộ đồ quen thuộc trở nên khác biệt.</p>
      <a class="pill light rise" style="--i:3" href="<%=homeCtx%>/collections">Khám phá bộ sưu tập <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></a>
    </div>
  </section>

  <% List<OutfitSet> homeOutfits = (List<OutfitSet>) request.getAttribute("homeOutfits"); %>
  <% if (homeOutfits != null && !homeOutfits.isEmpty()) { %>
  <section class="block reveal" id="outfit" aria-labelledby="outfitTitle">
    <div class="outfit">
      <div class="outfit-pic rise"><img src="<%=homeCtx%>/assets/images/home/look-work.webp" width="640" height="800" alt="Ảnh minh họa bộ sưu tập phối đồ" loading="lazy"></div>
      <div>
        <span class="kicker num rise">Bộ sưu tập phối đồ</span>
        <div class="head" style="margin:0"><h2 id="outfitTitle" class="rise" style="--i:1">Phối đồ theo dịp, chọn đồ dễ hơn</h2></div>
        <div class="tabs rise" style="--i:2" role="tablist" aria-label="Chọn dịp">
          <% for (int i = 0; i < homeOutfits.size(); i++) {
               OutfitSet homeOutfit = homeOutfits.get(i);
               String homeStyleLabel = homeOutfit.getStyle();
               for (Style style : Style.values()) if (style.getId().equals(homeOutfit.getStyle())) homeStyleLabel = style.getLabel();
          %>
          <button type="button" role="tab" id="t-outfit-<%=i%>" aria-selected="<%=i == 0%>" aria-controls="outfitPanel-<%=i%>" data-outfit-tab="<%=i%>" <%=i == 0 ? "" : "tabindex=\"-1\""%>><%=navEscape(homeStyleLabel)%></button>
          <% } %>
        </div>
        <% for (int i = 0; i < homeOutfits.size(); i++) {
             OutfitSet homeOutfit = homeOutfits.get(i);
             int visibleItems = Math.min(4, homeOutfit.getItems().size());
        %>
        <div id="outfitPanel-<%=i%>" role="tabpanel" aria-labelledby="t-outfit-<%=i%>" class="rise" style="--i:3" data-outfit-panel="<%=i%>" <%=i == 0 ? "" : "hidden"%>>
          <p class="outfit-desc"><%=navEscape(homeOutfit.getDescription())%></p>
          <ul class="outfit-items">
            <% for (int itemIndex = 0; itemIndex < visibleItems; itemIndex++) { %>
            <li><%=navEscape(homeOutfit.getItems().get(itemIndex).getName())%></li>
            <% } %>
          </ul>
          <a class="pill" href="<%=homeCtx%>/collections?source=curated&amp;style=<%=homeOutfit.getStyle()%>">Xem bộ sưu tập phối đồ <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></a>
        </div>
        <% } %>
      </div>
    </div>
  </section>
  <% } %>

  <section class="block reveal" id="offers" aria-labelledby="offerTitle">
    <div class="head"><div><span class="kicker num rise">Ưu đãi đang diễn ra</span><h2 id="offerTitle" class="rise" style="--i:1">Lựa chọn có gu, ưu đãi xứng tầm</h2></div><a class="link-all rise" href="<%=homeCtx%>/promotions">Điều kiện áp dụng <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></a></div>
    <%@include file="/includes/home-promotions.jspf"%>
  </section>

  <section class="band reveal" aria-labelledby="bandTitle">
    <img src="<%=homeCtx%>/assets/images/home/band.webp" width="1800" height="1200" alt="Túi da nâu đeo chéo đặt trên bàn gỗ tối cạnh ly cà phê">
    <div class="band-copy">
      <h2 id="bandTitle" class="rise">Phong cách tốt cho<br>một ngày tốt hơn.</h2>
      <p class="rise" style="--i:1">Một chiếc túi da đúng màu biến bộ đồ đơn giản thành lựa chọn có chủ đích. Bắt đầu từ món bạn dùng mỗi ngày.</p>
      <a class="pill light rise" style="--i:2" href="<%=homeCtx%>/products">Xem sản phẩm <svg class="arrow" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true"><path d="M5 12h14m-5-5 5 5-5 5"/></svg></a>
    </div>
  </section>
</main>
<%@include file="/includes/footer.jspf"%>
<%@include file="/includes/chatbot.jspf"%>
<script src="<%=request.getContextPath()%>/assets/chatbot.js" defer></script>
<script src="<%=request.getContextPath()%>/assets/home-products.js" defer></script>
<script src="<%=request.getContextPath()%>/assets/mensform-home.js?v=20261024h" defer></script>
</body></html>
