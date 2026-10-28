<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,model.WarehouseIssue"%>
<%!
    private String salesIssueEscape(Object value) {
        if (value == null) return "";
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String salesIssueType(String type) {
        if ("Missing".equalsIgnoreCase(type)) return "Thiếu hàng";
        if ("Damaged".equalsIgnoreCase(type)) return "Hư hỏng";
        if ("Other".equalsIgnoreCase(type)) return "Khác";
        return salesIssueEscape(type);
    }
%>
<%
    List<WarehouseIssue> issues =
            (List<WarehouseIssue>) request.getAttribute("issues");
    Object message = request.getAttribute("message");
    Object error = request.getAttribute("error");
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sự cố kho | Sales | <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <style>
        .warehouse-issue{display:grid;grid-template-columns:minmax(0,1fr) minmax(260px,360px);gap:22px;padding:20px 0;border-bottom:1px solid var(--line)}
        .warehouse-issue:last-child{border-bottom:0;padding-bottom:0}.warehouse-issue:first-child{padding-top:0}
        .warehouse-issue p{white-space:pre-wrap;overflow-wrap:anywhere}
        .warehouse-issue form{margin:0}.warehouse-issue-actions{display:flex;gap:8px;flex-wrap:wrap;margin-top:10px}
        @media(max-width:760px){.warehouse-issue{grid-template-columns:1fr}}
    </style>
</head>
<body>
<div class="portal-layout">
    <%@include file="/staff/includes/nav.jspf"%>
    <main class="portal-main">
        <header class="portal-head">
            <div>
                <span class="eyebrow">SALES DESK</span>
                <h1>Sự cố kho</h1>
                <p>Xem báo cáo thiếu hàng, hư hỏng; xử lý sự cố hoặc mô phỏng hoàn tiền VNPay Sandbox trước khi hủy đơn.</p>
            </div>
        </header>
        <% if (message != null) { %>
            <div class="alert success"><%=salesIssueEscape(message)%></div>
        <% } %>
        <% if (error != null) { %>
            <div class="alert error"><%=salesIssueEscape(error)%></div>
        <% } %>

        <section class="portal-panel">
            <% if (issues == null || issues.isEmpty()) { %>
                <p class="admin-empty">Hiện không có sự cố kho đang chờ xử lý.</p>
            <% } else { for (WarehouseIssue issue : issues) { %>
                <article class="warehouse-issue">
                    <div>
                        <span class="portal-badge warn"><%=salesIssueType(issue.getIssueType())%></span>
                        <h2>Đơn #<%=issue.getOrderID()%> · <%=salesIssueEscape(issue.getProductName())%></h2>
                        <p>SKU <%=salesIssueEscape(issue.getSku())%> · Số lượng <%=issue.getQuantity()%> · Báo bởi <%=salesIssueEscape(issue.getReportedByName())%></p>
                        <p>Thanh toán: <%=salesIssueEscape(issue.getPaymentMethod())%> · <%=salesIssueEscape(issue.getPaymentStatus())%></p>
                        <p><%=salesIssueEscape(issue.getDescription())%></p>
                        <small class="portal-muted">Tạo lúc <%=salesIssueEscape(issue.getCreatedAt())%></small>
                    </div>
                    <form action="<%=request.getContextPath()%>/staff/sales/inventory-issues" method="post">
                        <input type="hidden" name="csrfToken" value="<%=salesIssueEscape(request.getAttribute("csrfToken"))%>">
                        <input type="hidden" name="issueId" value="<%=issue.getIssueID()%>">
                        <label class="form-label" for="resolution-<%=issue.getIssueID()%>">Ghi chú xử lý (hoàn tiền mô phỏng / gửi khách khi hủy) *</label>
                        <textarea class="field" id="resolution-<%=issue.getIssueID()%>" name="resolutionNote" maxlength="1000" rows="3" required></textarea>
                        <div class="warehouse-issue-actions">
                            <button class="button small" name="action" value="resolve">Đã xử lý</button>
                            <button class="button line small" name="action" value="reject">Từ chối báo cáo</button>
                            <% if ("Paid".equalsIgnoreCase(issue.getPaymentStatus()) && "VNPAY".equalsIgnoreCase(issue.getPaymentMethod())) { %>
                                <button class="button line small" name="action" value="simulate_refund" onclick="return confirm('Chỉ ghi nhận hoàn tiền mô phỏng trong VNPay Sandbox; không thực hiện giao dịch hoàn tiền thật. Tiếp tục?')">Mô phỏng hoàn tiền</button>
                                <span class="portal-muted">Hãy mô phỏng hoàn tiền trước khi hủy đơn.</span>
                            <% } else if ("Refunded".equalsIgnoreCase(issue.getPaymentStatus()) && "VNPAY".equalsIgnoreCase(issue.getPaymentMethod())) { %>
                                <span class="portal-badge">Đã hoàn tiền mô phỏng</span>
                                <button class="button small" name="action" value="cancel_order" onclick="return confirm('Hủy đơn hàng này, cập nhật lại tồn kho và gửi nội dung trên cho khách?')">Hủy đơn &amp; báo khách</button>
                            <% } else if (!"Paid".equalsIgnoreCase(issue.getPaymentStatus())) { %>
                                <button class="button small" name="action" value="cancel_order" onclick="return confirm('Hủy đơn hàng này, cập nhật lại tồn kho và gửi nội dung trên cho khách?')">Hủy đơn &amp; báo khách</button>
                            <% } else { %>
                                <span class="portal-muted">Đơn đã thanh toán; cần hoàn tiền trước khi hủy đơn.</span>
                            <% } %>
                        </div>
                    </form>
                </article>
            <% }} %>
        </section>
    </main>
</div>
</body>
</html>
