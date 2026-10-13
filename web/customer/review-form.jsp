<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Product"%>
<%!
    private String reviewEscape(Object value) {
        if (value == null) {
            return "";
        }
        return value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    Product product = (Product) request.getAttribute("product");
    Object orderId = request.getAttribute("orderId");
    Object productId = request.getAttribute("productId");
    boolean alreadyReviewed = Boolean.TRUE.equals(request.getAttribute("alreadyReviewed"));
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta charset="UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Đánh giá sản phẩm | MENSFORM</title>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    </head>
    <body>
        <%@include file="/includes/header.jspf"%>
        <main class="shell page">
            <a href="<%=request.getContextPath()%>/customer/order-detail?orderId=<%=orderId%>">← Quay lại đơn hàng #<%=orderId%></a>
            <h1 class="page-title">Đánh giá sản phẩm</h1>
            <h2><%=product == null ? "Sản phẩm" : reviewEscape(product.getProductName())%></h2>
            <% if (request.getAttribute("error") != null) {%>
            <div class="alert error"><%=reviewEscape(request.getAttribute("error"))%></div>
            <% } %>
            <section class="portal-panel">
                <% if (alreadyReviewed) {%>
                <p>Bạn đã gửi đánh giá cho sản phẩm này trong đơn hàng #<%=orderId%>.</p>
                <a class="button" href="<%=request.getContextPath()%>/customer/order-detail?orderId=<%=orderId%>">Quay lại chi tiết đơn hàng</a>
                <% } else {%>
                <form action="<%=request.getContextPath()%>/customer/review" method="post">
                    <input type="hidden" name="orderId" value="<%=orderId%>">
                    <input type="hidden" name="productId" value="<%=productId%>">
                    <label class="form-label" for="rating">Số sao *</label>
                    <select class="select" id="rating" name="rating" required>
                        <option value="5">★★★★★ — Rất hài lòng</option>
                        <option value="4">★★★★☆ — Hài lòng</option>
                        <option value="3">★★★☆☆ — Bình thường</option>
                        <option value="2">★★☆☆☆ — Chưa hài lòng</option>
                        <option value="1">★☆☆☆☆ — Không hài lòng</option>
                    </select>
                    <label class="form-label" for="comment">Nhận xét</label>
                    <textarea class="textarea" id="comment" name="comment" rows="6" maxlength="2000" placeholder="Chia sẻ trải nghiệm của bạn về sản phẩm"></textarea>
                    <button class="button" style="margin-top:14px" type="submit">Gửi đánh giá</button>
                </form>
                <% }%>
            </section>
        </main>
        <%@include file="/includes/footer.jspf"%>
    </body>
</html>
