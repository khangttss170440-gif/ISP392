<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Locale,java.text.NumberFormat,java.time.format.DateTimeFormatter,model.Promotion"%>
<%!
    private String offerEscape(Object value) {
        return value == null ? "" : value.toString().replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    List<Promotion> offers = (List<Promotion>) request.getAttribute("promotions");
    NumberFormat offerMoney = NumberFormat.getInstance(new Locale("vi", "VN"));
%>
<!DOCTYPE html><html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Khuyến mãi — MENSFORM</title><link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
</head><body>
<%@include file="/includes/header.jspf"%>
<main class="page"><div class="shell">
    <div class="breadcrumb"><a href="<%=request.getContextPath()%>/index.jsp">Trang chủ</a> / Khuyến mãi</div>
    <span class="eyebrow">Ưu đãi dành cho bạn</span><h1 class="page-title">Mặc đẹp hơn. Tiết kiệm hơn.</h1>
    <p class="page-intro">Chọn mã phù hợp và nhập tại bước đặt hàng. Mỗi đơn dùng một mã, không cộng dồn.</p>
    <% if (request.getAttribute("error") != null) { %><div class="alert error"><%=offerEscape(request.getAttribute("error"))%></div><% }
       else if (offers == null || offers.isEmpty()) { %>
        <div class="empty-state"><h2>Chưa có ưu đãi đang diễn ra</h2><p>Ghé lại sau để khám phá những chương trình mới.</p><a class="button small" href="<%=request.getContextPath()%>/products">Khám phá sản phẩm</a></div>
    <% } else { %><div class="promotion-grid">
    <% for (Promotion offer : offers) { %>
        <article class="promotion-card">
            <span class="eyebrow">Ưu đãi đang diễn ra</span>
            <h2>Giảm <%=offerMoney.format(offer.getDiscountValue())%><%="Percentage".equals(offer.getDiscountType()) ? "%" : "₫"%></h2>
            <h3><%=offerEscape(offer.getPromotionName())%></h3>
            <p><%=offer.getMinOrderValue() == null || offer.getMinOrderValue().signum() == 0 ? "Áp dụng cho mọi giá trị đơn hàng" : "Cho đơn từ " + offerMoney.format(offer.getMinOrderValue()) + "₫"%></p>
            <% if (offer.getMaxDiscount() != null) { %><p>Giảm tối đa <%=offerMoney.format(offer.getMaxDiscount())%>₫</p><% } %>
            <p>Hết hạn: <%=offer.getEndDate().toLocalDateTime().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))%></p>
            <div class="promotion-code"><span>Mã ưu đãi</span><strong><%=offerEscape(offer.getPromotionCode())%></strong></div>
            <a class="button line small" href="<%=request.getContextPath()%>/products">Mua sắm ngay ↗</a>
        </article>
    <% } %></div><% } %>
</div></main>
<%@include file="/includes/footer.jspf"%>
</body></html>
