<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.Objects"%>
<%
    Boolean paymentSuccess = (Boolean) request.getAttribute("paymentSuccess");
    String paymentMessage = Objects.toString(request.getAttribute("paymentMessage"),
            "Không nhận được kết quả thanh toán.");
    Object orderId = request.getAttribute("orderId");
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Kết quả thanh toán — MENSFORM</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
</head>
<body>
<%@include file="/includes/header.jspf"%>
<main class="page">
    <div class="shell">
        <section class="empty-state" style="max-width:680px;margin:55px auto">
            <div style="font-size:42px;color:<%=Boolean.TRUE.equals(paymentSuccess) ? "var(--green)" : "#b42318"%>">
                <%=Boolean.TRUE.equals(paymentSuccess) ? "✓" : "!"%>
            </div>
            <span class="eyebrow">Kết quả giao dịch</span>
            <h1 class="page-title" style="margin-top:12px">
                <%=Boolean.TRUE.equals(paymentSuccess) ? "Thanh toán thành công" : "Chưa hoàn tất thanh toán"%>
            </h1>
            <p><%=paymentMessage.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")%></p>
            <% if (orderId != null) { %><p>Mã đơn hàng: <b>#<%=orderId%></b></p><% } %>
            <div style="display:flex;gap:10px;justify-content:center;flex-wrap:wrap;margin-top:20px">
                <a class="button" href="<%=request.getContextPath()%>/customer/orders">Theo dõi đơn hàng</a>
                <a class="button line" href="<%=request.getContextPath()%>/products">Tiếp tục mua sắm</a>
            </div>
        </section>
    </div>
</main>
<%@include file="/includes/footer.jspf"%>
</body>
</html>
