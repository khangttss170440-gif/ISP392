<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="model.Customer"%>
<%!
    private String pwEscape(Object value) {
        return value == null ? "" : value.toString().replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    String flow = "change".equals(request.getAttribute("passwordFlow")) ? "change" : "forgot";
    String stage = request.getAttribute("stage") == null ? "start" : request.getAttribute("stage").toString();
    String action = request.getContextPath() + ("change".equals(flow) ? "/password/change" : "/password/forgot");
    String title = "change".equals(flow) ? "Đổi mật khẩu" : "Quên mật khẩu";
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%=title%> | MENSFORM</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <style>
        .password-card{max-width:620px;margin:42px auto;padding:clamp(24px,5vw,46px);background:#fff;border:1px solid #e6e1d8;border-radius:16px;box-shadow:0 12px 34px #192b2110}
        .password-card h1{font:700 clamp(30px,4vw,40px)/1.15 Arial,sans-serif;margin:8px 0}
        .password-card>p{color:var(--muted);margin:0 0 20px}
        .password-card .button{margin-top:22px}
        .password-card .form-label{margin-top:16px}
        @media(max-width:650px){.password-card{margin:24px auto}}
    </style>
</head>
<body>
<%@include file="/includes/header.jspf"%>
<main class="shell page">
    <section class="password-card">
        <span class="eyebrow">BẢO MẬT TÀI KHOẢN</span>
        <h1><%=title%></h1>
        <% if (request.getAttribute("error") != null) { %>
            <div class="alert error" role="alert"><%=pwEscape(request.getAttribute("error"))%></div>
        <% } %>
        <% if (request.getAttribute("message") != null) { %>
            <div class="alert success" role="status"><%=pwEscape(request.getAttribute("message"))%></div>
        <% } %>

        <% if ("done".equals(stage)) { %>
            <p>Mật khẩu mới đã được lưu.</p>
            <a class="button" href="<%=request.getContextPath()%>/customer/account.jsp">Về tài khoản</a>
        <% } else if ("verify".equals(stage)) { %>
            <p>Nhập mã 6 chữ số trong email cùng mật khẩu mới. Mã hết hạn sau 10 phút.</p>
            <form action="<%=action%>" method="post" autocomplete="off">
                <input type="hidden" name="stage" value="verify">
                <label class="form-label" for="code">Mã xác nhận</label>
                <input class="field" id="code" name="code" type="text" inputmode="numeric" pattern="[0-9]{6}" maxlength="6" autocomplete="one-time-code" required>
                <label class="form-label" for="newPassword">Mật khẩu mới</label>
                <input class="field" id="newPassword" name="newPassword" type="password" minlength="8" maxlength="128" autocomplete="new-password" required>
                <label class="form-label" for="confirmPassword">Nhập lại mật khẩu mới</label>
                <input class="field" id="confirmPassword" name="confirmPassword" type="password" minlength="8" maxlength="128" autocomplete="new-password" required>
                <button class="button" type="submit">Xác nhận và lưu mật khẩu</button>
            </form>
        <% } else { %>
            <% if ("change".equals(flow)) { %>
                <p>Nhập mật khẩu hiện tại. MENSFORM sẽ gửi mã xác nhận đến email đăng ký của bạn.</p>
                <form action="<%=action%>" method="post">
                    <input type="hidden" name="stage" value="start">
                    <label class="form-label" for="currentPassword">Mật khẩu hiện tại</label>
                    <input class="field" id="currentPassword" name="currentPassword" type="password" autocomplete="current-password" required>
                    <button class="button" type="submit">Gửi mã xác nhận</button>
                </form>
            <% } else { %>
                <p>Nhập email tài khoản. Nếu email đang được đăng ký, chúng tôi sẽ gửi mã xác nhận để bạn đặt mật khẩu mới.</p>
                <form action="<%=action%>" method="post">
                    <input type="hidden" name="stage" value="start">
                    <label class="form-label" for="email">Email tài khoản</label>
                    <input class="field" id="email" name="email" type="email" maxlength="150" autocomplete="email" required>
                    <button class="button" type="submit">Gửi mã qua email</button>
                </form>
            <% } %>
        <% } %>
        <p style="margin-top:22px"><a class="text-link" href="<%=request.getContextPath()%>/login">← Quay lại đăng nhập</a></p>
    </section>
</main>
<%@include file="/includes/footer.jspf"%>
</body>
</html>
