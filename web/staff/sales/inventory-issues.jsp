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
    <title>Sự cố kho | Sales | MENSFORM</title>
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
                <p>Xem báo cáo thiếu hàng, hư hỏng; xử lý sự cố hoặc hủy đơn và gửi thông báo cho khách.</p>
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
                        <p><%=salesIssueEscape(issue.getDescription())%></p>
                        <small class="portal-muted">Tạo lúc <%=salesIssueEscape(issue.getCreatedAt())%></small>
                    </div>
                    <form action="<%=request.getContextPath()%>/staff/sales/inventory-issues" method="post">
                        <input type="hidden" name="issueId" value="<%=issue.getIssueID()%>">
                        <label class="form-label" for="resolution-<%=issue.getIssueID()%>">Ghi chú xử lý / nội dung gửi khách *</label>
                        <textarea class="field" id="resolution-<%=issue.getIssueID()%>" name="resolutionNote" maxlength="1000" rows="3" required></textarea>
                        <div class="warehouse-issue-actions">
                            <button class="button small" name="action" value="resolve">Đã xử lý</button>
                            <button class="button line small" name="action" value="reject">Từ chối báo cáo</button>
                            <button class="button small" name="action" value="cancel_order" onclick="return confirm('Hủy đơn hàng này, cập nhật lại tồn kho và gửi nội dung trên cho khách?')">Hủy đơn &amp; báo khách</button>
                        </div>
                    </form>
                </article>
            <% }} %>
        </section>
    </main>
</div>
</body>
</html>
