<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,model.SupportTicket,model.SupportMessage"%>
<%!
    private String supportEscape(Object value) {
        if (value == null) return "";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    List<SupportTicket> tickets = (List<SupportTicket>) request.getAttribute("tickets");
    List<SupportMessage> messages = (List<SupportMessage>) request.getAttribute("messages");
    SupportTicket selectedTicket = (SupportTicket) request.getAttribute("selectedTicket");
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Hỗ trợ khách hàng | <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
</head>
<body>
<%@include file="/includes/header.jspf"%>
<main class="shell page">
    <div class="breadcrumb"><a href="<%=request.getContextPath()%>/customer/account.jsp">Tài khoản</a>　/　Hỗ trợ</div>
    <header class="portal-head">
        <div>
            <span class="eyebrow">CHĂM SÓC KHÁCH HÀNG</span>
            <h1 class="page-title">Hỗ trợ</h1>
            <p>Gửi câu hỏi về đơn hàng hoặc trao đổi với đội ngũ <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%>.</p>
        </div>
        <a class="button line" href="<%=request.getContextPath()%>/customer/orders">Đơn hàng của tôi</a>
    </header>

    <% if (request.getAttribute("message") != null) { %>
        <div class="alert success"><%=supportEscape(request.getAttribute("message"))%></div>
    <% } %>
    <% if (request.getAttribute("error") != null) { %>
        <div class="alert error"><%=supportEscape(request.getAttribute("error"))%></div>
    <% } %>

    <div class="cart-layout">
        <section class="portal-panel">
            <h2>Yêu cầu của tôi</h2>
            <% if (tickets == null || tickets.isEmpty()) { %>
                <p class="portal-muted">Bạn chưa gửi yêu cầu hỗ trợ nào.</p>
            <% } else { for (SupportTicket ticket : tickets) { %>
                <a class="portal-row" href="<%=request.getContextPath()%>/customer/support?ticketId=<%=ticket.getTicketID()%>">
                    <div>
                        <b>#<%=ticket.getTicketID()%> · <%=supportEscape(ticket.getSubject())%></b>
                        <p class="portal-muted"><%=ticket.getUpdatedAt() == null ? "" : ticket.getUpdatedAt()%></p>
                        <small><%=supportEscape(ticket.getLastMessage())%></small>
                    </div>
                    <span class="portal-badge"><%=supportEscape(ticket.getStatus())%></span>
                </a>
            <% } } %>
        </section>

        <section class="portal-panel">
            <% if (selectedTicket == null) { %>
                <h2>Gửi yêu cầu mới</h2>
                <p class="portal-muted">Mô tả vấn đề, đội ngũ hỗ trợ sẽ phản hồi tại đây.</p>
                <form action="<%=request.getContextPath()%>/customer/support" method="post">
                    <input type="hidden" name="action" value="create">
                    <label class="form-label" for="subject">Chủ đề *</label>
                    <input class="field" id="subject" name="subject" maxlength="150" required>
                    <label class="form-label" for="orderId">Mã đơn hàng (không bắt buộc)</label>
                    <input class="field" id="orderId" name="orderId" type="number" min="1" step="1">
                    <label class="form-label" for="message">Nội dung *</label>
                    <textarea class="textarea" id="message" name="message" maxlength="2000" rows="6" required></textarea>
                    <button class="button" style="margin-top:14px" type="submit">Gửi yêu cầu ↗</button>
                </form>
            <% } else { %>
                <a class="text-link" href="<%=request.getContextPath()%>/customer/support">← Tạo yêu cầu mới</a>
                <h2 style="margin-top:12px">#<%=selectedTicket.getTicketID()%> · <%=supportEscape(selectedTicket.getSubject())%></h2>
                <p class="portal-muted">
                    <% if (selectedTicket.getOrderID() != null) { %>Đơn hàng #<%=selectedTicket.getOrderID()%> · <% } %>
                    <span class="portal-badge"><%=supportEscape(selectedTicket.getStatus())%></span>
                </p>
                <div class="portal-conversation">
                    <% if (messages != null) { for (SupportMessage supportMessage : messages) { %>
                        <article class="portal-message <%= "Staff".equalsIgnoreCase(supportMessage.getSenderType()) ? "staff" : "" %>">
                            <b><%=supportEscape(supportMessage.getSenderName())%></b>
                            <p style="white-space:pre-wrap"><%=supportEscape(supportMessage.getMessageBody())%></p>
                            <small><%=supportMessage.getCreatedAt() == null ? "" : supportMessage.getCreatedAt()%></small>
                        </article>
                    <% } } %>
                </div>
                <% if (!"Closed".equalsIgnoreCase(selectedTicket.getStatus())) { %>
                    <form action="<%=request.getContextPath()%>/customer/support" method="post">
                        <input type="hidden" name="action" value="reply">
                        <input type="hidden" name="ticketId" value="<%=selectedTicket.getTicketID()%>">
                        <label class="form-label" for="replyMessage">Phản hồi</label>
                        <textarea class="textarea" id="replyMessage" name="message" maxlength="2000" rows="4" required></textarea>
                        <button class="button" style="margin-top:12px" type="submit">Gửi phản hồi ↗</button>
                    </form>
                <% } else { %>
                    <p class="portal-muted">Yêu cầu này đã đóng.</p>
                <% } %>
            <% } %>
        </section>
    </div>
</main>
<%@include file="/includes/footer.jspf"%>
</body>
</html>
