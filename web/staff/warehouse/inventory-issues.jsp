<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,model.WarehouseIssue,model.WarehouseOrder,model.OrderLine"%>
<%!
    private String warehouseIssueEscape(Object value) {
        if (value == null) return "";
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String warehouseIssueStatus(String status) {
        if ("Open".equalsIgnoreCase(status)) return "Đang chờ Sales";
        if ("Resolved".equalsIgnoreCase(status)) return "Đã xử lý";
        if ("Rejected".equalsIgnoreCase(status)) return "Đã từ chối";
        return warehouseIssueEscape(status);
    }

    private String warehouseIssueType(String type) {
        if ("Missing".equalsIgnoreCase(type)) return "Thiếu hàng";
        if ("Damaged".equalsIgnoreCase(type)) return "Hư hỏng";
        if ("Other".equalsIgnoreCase(type)) return "Khác";
        return warehouseIssueEscape(type);
    }
%>
<%
    List<WarehouseIssue> issues =
            (List<WarehouseIssue>) request.getAttribute("issues");
    List<WarehouseOrder> eligibleOrders =
            (List<WarehouseOrder>) request.getAttribute("eligibleOrders");
    List<OrderLine> orderLines =
            (List<OrderLine>) request.getAttribute("orderLines");
    WarehouseOrder selectedOrder =
            (WarehouseOrder) request.getAttribute("selectedOrder");
    Object message = request.getAttribute("message");
    Object error = request.getAttribute("error");
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Báo cáo sự cố kho | <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <style>
        .issue-status{display:inline-flex;padding:6px 10px;border-radius:999px;background:#fff3d6;color:#8a5a00;font-size:13px;font-weight:700;white-space:nowrap}
        .issue-status.resolved{background:#e8f4ec;color:#26734d}.issue-status.rejected{background:#fcebea;color:#a52a24}
        .issue-description{white-space:pre-wrap;overflow-wrap:anywhere;max-width:460px}
        .issue-resolution{min-width:210px;max-width:360px;white-space:pre-wrap;overflow-wrap:anywhere}
        .issue-heading{display:flex;align-items:center;justify-content:space-between;gap:18px;flex-wrap:wrap}
        .issue-order-picker{display:flex;align-items:end;gap:12px;max-width:720px;margin:18px 0 0}
        .issue-order-picker .form-label{flex:1;margin:0}
        @media(max-width:600px){.issue-order-picker{display:block}.issue-order-picker .button{margin-top:12px;width:100%}}
    </style>
</head>
<body>
<div class="portal-layout">
    <%@include file="/staff/includes/nav.jspf"%>
    <main class="portal-main">
        <header class="portal-head">
            <div>
                <span class="eyebrow">WAREHOUSE</span>
                <h1>Báo cáo sự cố kho</h1>
                <p>Theo dõi các báo cáo thiếu hàng, hư hỏng và phản hồi xử lý từ Sales.</p>
            </div>
        </header>

        <% if (message != null) { %>
            <div class="alert success"><%=warehouseIssueEscape(message)%></div>
        <% } %>
        <% if (error != null) { %>
            <div class="alert error"><%=warehouseIssueEscape(error)%></div>
        <% } %>

        <section class="portal-panel">
            <h2>Tạo báo cáo sự cố</h2>
            <p class="portal-muted">Chọn đơn đã được xác nhận để xem các SKU trong đơn và báo số lượng thiếu hoặc hư hỏng.</p>
            <form class="issue-order-picker" action="<%=request.getContextPath()%>/staff/warehouse/inventory-issues" method="get">
                <label class="form-label" for="issueOrderId">
                    Đơn hàng
                    <select class="select" id="issueOrderId" name="orderId" required>
                        <option value="">-- Chọn đơn hàng --</option>
                        <% if (eligibleOrders != null) { for (WarehouseOrder order : eligibleOrders) { %>
                            <option value="<%=order.getOrderID()%>" <%=selectedOrder != null && selectedOrder.getOrderID() == order.getOrderID() ? "selected" : ""%>>
                                Đơn #<%=order.getOrderID()%> · <%=warehouseIssueEscape(order.getCustomerName())%> · <%=warehouseIssueEscape(order.getOrderStatus())%>
                            </option>
                        <% }} %>
                    </select>
                </label>
                <button class="button" type="submit">Xem sản phẩm</button>
            </form>
            <% if (eligibleOrders == null || eligibleOrders.isEmpty()) { %>
                <p class="portal-muted">Hiện chưa có đơn hàng ở trạng thái được phép báo sự cố.</p>
            <% } %>

            <% if (selectedOrder != null) { %>
                <h3 style="margin-top:24px">Đơn #<%=selectedOrder.getOrderID()%> · <%=warehouseIssueEscape(selectedOrder.getCustomerName())%></h3>
                <% if (orderLines == null || orderLines.isEmpty()) { %>
                    <p class="portal-muted">Đơn hàng này chưa có sản phẩm để báo sự cố.</p>
                <% } else { %>
                    <div class="portal-table-wrap">
                        <table class="portal-table">
                            <thead><tr><th>Sản phẩm</th><th>SKU</th><th>Màu / cỡ</th><th>Số lượng đặt</th><th>Báo sự cố</th></tr></thead>
                            <tbody>
                                <% for (OrderLine line : orderLines) { %>
                                    <tr>
                                        <td><b><%=warehouseIssueEscape(line.getProductName())%></b></td>
                                        <td><%=warehouseIssueEscape(line.getSku())%></td>
                                        <td><%=warehouseIssueEscape(line.getColor())%> / <%=warehouseIssueEscape(line.getSize())%></td>
                                        <td><%=line.getQuantity()%></td>
                                        <td>
                                            <details>
                                                <summary>Thiếu / hư hỏng</summary>
                                                <form action="<%=request.getContextPath()%>/staff/warehouse/inventory-issues" method="post">
                                                    <input type="hidden" name="orderId" value="<%=selectedOrder.getOrderID()%>">
                                                    <input type="hidden" name="variantId" value="<%=line.getVariantID()%>">
                                                    <label class="form-label">Số lượng bị ảnh hưởng *</label>
                                                    <input class="field" type="number" name="quantity" min="1" max="<%=line.getQuantity()%>" required>
                                                    <label class="form-label">Loại sự cố</label>
                                                    <select class="select" name="issueType">
                                                        <option value="Missing">Thiếu hàng</option>
                                                        <option value="Damaged">Hư hỏng</option>
                                                        <option value="Other">Khác</option>
                                                    </select>
                                                    <label class="form-label">Mô tả *</label>
                                                    <textarea class="field" name="description" maxlength="1000" rows="3" required></textarea>
                                                    <button class="button small" type="submit">Gửi báo cáo</button>
                                                </form>
                                            </details>
                                        </td>
                                    </tr>
                                <% } %>
                            </tbody>
                        </table>
                    </div>
                <% } %>
            <% } %>
        </section>

        <section class="portal-panel" style="margin-top:22px">
            <div class="issue-heading">
                <div>
                    <h2>Báo cáo của bạn</h2>
                    <p class="portal-muted">Báo cáo mới được gửi cho Sales; trạng thái và ghi chú xử lý sẽ hiển thị tại đây.</p>
                </div>
                <span class="portal-badge"><%=issues == null ? 0 : issues.size()%> báo cáo</span>
            </div>
            <div class="portal-table-wrap">
                <table class="portal-table">
                    <thead>
                        <tr>
                            <th>Mã / ngày tạo</th>
                            <th>Đơn hàng</th>
                            <th>Sản phẩm</th>
                            <th>Sự cố</th>
                            <th>Mô tả</th>
                            <th>Trạng thái</th>
                            <th>Phản hồi Sales</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (issues == null || issues.isEmpty()) { %>
                            <tr><td colspan="7" class="admin-empty">Bạn chưa gửi báo cáo sự cố nào. Mở một đơn xuất kho để báo thiếu hoặc hư hỏng.</td></tr>
                        <% } else { for (WarehouseIssue issue : issues) {
                            String statusClass = "Resolved".equalsIgnoreCase(issue.getStatus())
                                    ? "resolved"
                                    : "Rejected".equalsIgnoreCase(issue.getStatus())
                                            ? "rejected" : "";
                        %>
                            <tr>
                                <td><b>#<%=issue.getIssueID()%></b><div class="portal-muted"><%=warehouseIssueEscape(issue.getCreatedAt())%></div></td>
                                <td><a href="<%=request.getContextPath()%>/staff/warehouse/order-detail?orderId=<%=issue.getOrderID()%>">#<%=issue.getOrderID()%></a></td>
                                <td><b><%=warehouseIssueEscape(issue.getProductName())%></b><div class="portal-muted"><%=warehouseIssueEscape(issue.getSku())%></div></td>
                                <td><%=warehouseIssueType(issue.getIssueType())%><div class="portal-muted">Số lượng: <%=issue.getQuantity()%></div></td>
                                <td><div class="issue-description"><%=warehouseIssueEscape(issue.getDescription())%></div></td>
                                <td><span class="issue-status <%=statusClass%>"><%=warehouseIssueStatus(issue.getStatus())%></span><% if (issue.getUpdatedAt() != null) { %><div class="portal-muted"><%=warehouseIssueEscape(issue.getUpdatedAt())%></div><% } %></td>
                                <td><div class="issue-resolution"><%=warehouseIssueEscape(issue.getResolutionNote())%></div><% if (issue.getResolvedByName() != null) { %><div class="portal-muted">Sales: <%=warehouseIssueEscape(issue.getResolvedByName())%></div><% } %></td>
                            </tr>
                        <% }} %>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
</body>
</html>
