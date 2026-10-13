<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Locale,java.text.NumberFormat,java.util.Set,java.util.HashSet,model.CustomerOrder,model.OrderLine"%>
<%!
    private String orderDetailEscape(Object value) {
        if (value == null) return "";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    CustomerOrder order = (CustomerOrder) request.getAttribute("order");
    List<OrderLine> lines = (List<OrderLine>) request.getAttribute("orderLines");
    Set<Integer> reviewedProductIDs = (Set<Integer>) request.getAttribute("reviewedProductIDs");
    if (reviewedProductIDs == null) reviewedProductIDs = new HashSet<Integer>();
    Set<Integer> shownReviewProducts = new HashSet<Integer>();
    boolean delivered = "Delivered".equalsIgnoreCase(order.getOrderStatus());
    NumberFormat money = NumberFormat.getInstance(new Locale("vi", "VN"));
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Chi tiết đơn hàng | MENSFORM</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
</head>
<body>
<%@include file="/includes/header.jspf"%>
<main class="shell page">
    <a href="<%=request.getContextPath()%>/customer/orders">← Đơn hàng của tôi</a>
    <div class="portal-head" style="margin-top:18px">
        <div>
            <span class="eyebrow">ĐƠN #<%=order.getOrderID()%></span>
            <h1 class="page-title">Chi tiết đơn hàng</h1>
            <p><%=order.getOrderDate()%> · <b><%=orderDetailEscape(order.getOrderStatus())%></b></p>
        </div>
        <% if ("Pending".equalsIgnoreCase(order.getOrderStatus())
                || "Confirmed".equalsIgnoreCase(order.getOrderStatus())
                || delivered) { %>
            <a class="button line" href="<%=request.getContextPath()%>/customer/order-request?orderId=<%=order.getOrderID()%>">Gửi yêu cầu</a>
        <% } %>
    </div>
    <% if (request.getAttribute("reviewMessage") != null) { %>
        <div class="alert success"><%=orderDetailEscape(request.getAttribute("reviewMessage"))%></div>
    <% } %>

    <div class="cart-layout">
        <section class="portal-panel">
            <h2>Sản phẩm</h2>
            <% if (lines != null) { for (OrderLine line : lines) { %>
                <article class="cart-item">
                    <div class="cart-thumb"></div>
                    <div>
                        <h3><%=orderDetailEscape(line.getProductName())%></h3>
                        <p><%=orderDetailEscape(line.getColor())%> / <%=orderDetailEscape(line.getSize())%> · SKU <%=orderDetailEscape(line.getSku())%></p>
                        <p><%=money.format(line.getUnitPrice())%>₫ × <%=line.getQuantity()%></p>
                        <% if (delivered && shownReviewProducts.add(line.getProductID())) { %>
                            <% if (reviewedProductIDs.contains(line.getProductID())) { %>
                                <span class="portal-badge">Đã đánh giá</span>
                            <% } else { %>
                                <a class="button line small" href="<%=request.getContextPath()%>/customer/review?orderId=<%=order.getOrderID()%>&amp;productId=<%=line.getProductID()%>">Đánh giá sản phẩm</a>
                            <% } %>
                        <% } %>
                    </div>
                    <b><%=money.format(line.getLineTotal())%>₫</b>
                </article>
            <% } } %>
            <% if (!delivered) { %>
                <p class="notice">Bạn có thể đánh giá sản phẩm sau khi đơn hàng được giao.</p>
            <% } %>
            <h2>Giao hàng</h2>
            <p><%=order.getShippingAddress() == null ? "Chưa có địa chỉ." : orderDetailEscape(order.getShippingAddress())%></p>
            <% if (order.getTrackingNumber() != null) { %>
                <p><b><%=orderDetailEscape(order.getCarrier())%></b> · <%=orderDetailEscape(order.getTrackingNumber())%></p>
            <% } %>
        </section>
        <aside class="summary">
            <h2>Thanh toán</h2>
            <div class="summary-row"><span>Tạm tính</span><b><%=money.format(order.getSubtotal())%>₫</b></div>
            <div class="summary-row"><span>Giảm giá</span><b><%=money.format(order.getDiscountAmount())%>₫</b></div>
            <div class="summary-row"><span>Phí giao hàng</span><b><%=money.format(order.getShippingFee())%>₫</b></div>
            <div class="summary-row total"><span>Tổng cộng</span><span><%=money.format(order.getTotalAmount())%>₫</span></div>
            <p><%=orderDetailEscape(order.getPaymentMethod())%> · <%=orderDetailEscape(order.getPaymentStatus())%></p>
        </aside>
    </div>
</main>
<%@include file="/includes/footer.jspf"%>
</body>
</html>
