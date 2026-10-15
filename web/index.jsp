<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>MENSFORM — Phong cách từ những điều tinh giản</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/chatbot.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/home-theme.css">
</head><body class="home-theme">
<%@include file="/includes/header.jspf"%>
<main>
    <section class="editorial-hero" aria-labelledby="heroTitle">
        <div class="shell">
            <span class="eyebrow">THE MODERN GENTLEMAN</span>
            <h1 id="heroTitle">Phong cách nằm<br>ở từng chi tiết.</h1>
            <p>Những lựa chọn tinh giản cho người đàn ông hiện đại. Tự tin trong phom dáng, khác biệt trong cách mặc.</p>
            <a class="button light" href="<%=request.getContextPath()%>/products">Khám phá ngay <span aria-hidden="true">→</span></a>
        </div>
        <div class="hero-index" aria-hidden="true">MENSFORM <span></span> THE EVERYDAY EDIT</div>
    </section>
    <section class="wardrobe-section" id="discover" aria-labelledby="discoverTitle">
        <div class="shell">
            <div class="editorial-intro"><span class="eyebrow">THE WARDROBE</span><h2 id="discoverTitle">Định hình phong cách riêng.</h2><p>Từ ngày làm việc đến những cuộc hẹn cuối tuần. Luôn là chính bạn.</p></div>
            <div class="wardrobe-grid">
                <a class="wardrobe-card" href="<%=request.getContextPath()%>/products"><small>01 / THE EVERYDAY EDIT</small><h3>Chỉn chu mỗi ngày</h3><span class="wardrobe-link">Khám phá sản phẩm →</span></a>
                <a class="wardrobe-card" href="<%=request.getContextPath()%>/products"><small>02 / LESS, BUT BETTER</small><h3>Tinh giản mà khác biệt</h3><span class="wardrobe-link">Khám phá sản phẩm →</span></a>
                <a class="wardrobe-card" href="<%=request.getContextPath()%>/products"><small>03 / YOUR SIGNATURE</small><h3>Dấu ấn của bạn</h3><span class="wardrobe-link">Khám phá sản phẩm →</span></a>
            </div>
        </div>
    </section>
    <section class="editorial-offer" aria-labelledby="offerTitle"><div class="shell">
        <div><span class="eyebrow">A LITTLE SOMETHING FOR YOU</span><h2 id="offerTitle">Lựa chọn có gu. Ưu đãi xứng tầm.</h2><p>Khám phá các mã khuyến mãi và điều kiện áp dụng cho đơn hàng của bạn.</p></div>
        <a class="button" href="<%=request.getContextPath()%>/promotions">Xem ưu đãi <span aria-hidden="true">→</span></a>
    </div>
    <%@include file="/includes/home-promotions.jspf"%>
    </section>
    <section class="collection-story" id="collection" aria-labelledby="collectionTitle">
        <div class="collection-image" role="img" aria-label="Phong cách thời trang nam tinh giản"></div>
        <div class="collection-copy"><span class="eyebrow">MENSFORM COLLECTION</span><h2 id="collectionTitle">Tối giản.<br>Tinh tế. Khác biệt.</h2><p>Không cần quá nhiều chi tiết để tạo nên dấu ấn. Chọn những thiết kế phù hợp, phối theo cách của bạn và tự tin trong từng khoảnh khắc.</p><a class="button" href="<%=request.getContextPath()%>/products">Xem bộ sưu tập <span aria-hidden="true">→</span></a></div>
    </section>
    <div class="store-values-band"><div class="shell store-values"><div><b>THIẾT KẾ TINH GIẢN</b><p>Phom dáng hiện đại, dễ dàng kết hợp.</p></div><div><b>PHONG CÁCH CỦA BẠN</b><p>Chọn đúng thiết kế, thể hiện chất riêng.</p></div><div><b>MUA SẮM AN TÂM</b><p>Thanh toán khi nhận hàng tiện lợi.</p></div></div></div>
</main>
<%@include file="/includes/footer.jspf"%>
<%@include file="/includes/chatbot.jspf"%>
<script src="<%=request.getContextPath()%>/assets/chatbot.js" defer></script>
</body></html>
