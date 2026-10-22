<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.text.NumberFormat,java.util.Locale,model.CustomerOrder"%>
<%!
private String ordersEscape(Object value) {
    return value == null ? "" : value.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
}
%>
<%
List<CustomerOrder> orders=(List<CustomerOrder>)request.getAttribute("orders");
NumberFormat money=NumberFormat.getInstance(new Locale("vi","VN"));
%>
<!DOCTYPE html>
<html lang="vi">
<head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Đơn hàng của tôi | MENSFORM</title>
<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/order-tracking.css">
</head>
<body>
<%@include file="/includes/header.jspf"%>
<main class="shell page" data-order-tracking data-tracking-endpoint="<%=request.getContextPath()%>/customer/order-tracking">
    <div class="portal-head">
        <div><span class="eyebrow">TÀI KHOẢN</span><h1 class="page-title">Đơn hàng của tôi</h1>
            <p>Theo dõi trạng thái giao hàng và yêu cầu sau mua.</p></div>
        <a class="button line" href="<%=request.getContextPath()%>/products">Tiếp tục mua sắm</a>
    </div>
    <% if(request.getAttribute("error")!=null){ %><div class="alert error"><%=ordersEscape(request.getAttribute("error"))%></div><% } %>
    <% if("1".equals(request.getParameter("requestSent"))){ %><div class="alert success">Yêu cầu của bạn đã được gửi.</div><% } %>
    <p class="order-tracking-meta" data-tracking-notice role="status">Trạng thái được tự động kiểm tra mỗi phút.</p>
    <section class="portal-panel">
    <% if(orders==null || orders.isEmpty()){ %>
        <div class="empty-state"><h2>Bạn chưa có đơn hàng</h2><a class="button" href="<%=request.getContextPath()%>/products">Mua sắm</a></div>
    <% } else for(CustomerOrder o:orders){
        String status=o.getOrderStatus()==null?"":o.getOrderStatus();
        boolean requestAllowed="Pending".equalsIgnoreCase(status)||"Confirmed".equalsIgnoreCase(status)||"Delivered".equalsIgnoreCase(status);
    %>
        <article class="portal-row" data-tracking-row data-order-id="<%=o.getOrderID()%>" data-order-status="<%=ordersEscape(status)%>">
            <div>
                <span class="eyebrow">ĐƠN #<%=o.getOrderID()%></span><p><%=o.getOrderDate()%></p>
                <span class="portal-badge" data-order-status><%=ordersEscape(o.getOrderStatusLabel())%></span>
                <p><b data-shipping-status><%=ordersEscape(o.getShippingStatusLabel())%></b></p>
                <p class="order-shipping-notice" data-shipping-notice <%=o.getShippingNotice().isEmpty()?"hidden":""%>><%=ordersEscape(o.getShippingNotice())%></p>
                <p class="order-tracking-meta"><span data-tracking-carrier><%=ordersEscape(o.getCarrier())%></span> · <span data-tracking-number><%=o.getTrackingNumber()==null?"Chưa có mã vận đơn":ordersEscape(o.getTrackingNumber())%></span></p>
                <p class="order-tracking-meta" data-carrier-last-sync><%=o.getLastCarrierSync()==null?"Chưa có lần đồng bộ từ hãng vận chuyển.":"Đã có cập nhật từ hãng vận chuyển."%></p>
            </div>
            <div><small class="portal-muted">Tổng thanh toán</small><h3><%=money.format(o.getTotalAmount())%>₫</h3>
                <span data-payment-status><%=ordersEscape(o.getPaymentStatusLabel())%></span></div>
            <div class="portal-actions">
                <a class="button line small" href="<%=request.getContextPath()%>/customer/order-detail?orderId=<%=o.getOrderID()%>">Chi tiết</a>
                <a data-order-request <%=requestAllowed?"":"hidden"%> class="button line small" href="<%=request.getContextPath()%>/customer/order-request?orderId=<%=o.getOrderID()%>"><%="Delivered".equalsIgnoreCase(status)?"Gửi yêu cầu":"Hủy đơn"%></a>
            </div>
        </article>
    <% } %>
    </section>
</main>
<%@include file="/includes/footer.jspf"%>
<script src="<%=request.getContextPath()%>/assets/order-tracking.js" defer></script>
</body>
</html>
