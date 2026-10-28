<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Xác thực email — <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/login-theme.css?v=20261024e">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/register-theme.css?v=20261024e">
</head>
<body class="login-theme register-theme">
<%@include file="/includes/header.jspf"%>
<main class="page">
    <div class="shell">
        <div class="auth-wrap">
            <aside class="auth-aside">
                <div><span class="eyebrow">Bảo vệ tài khoản</span>
                    <h2>Xác thực<br>email của bạn.</h2>
                    <p>Mã xác thực chỉ dùng một lần và có hiệu lực trong <%=util.AppConfig.integer("AUTH_CODE_LIFETIME_SECONDS", 1, 86400)%> giây.</p>
                </div>
                <p>Nếu chưa thấy email, hãy kiểm tra cả thư mục Spam/Quảng cáo.</p>
            </aside>
            <section class="auth-form">
                <h1>Nhập mã xác thực</h1>
                <p><%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%> đã gửi mã đến <b><%=request.getAttribute("email")%></b>.</p>
                <% if (request.getAttribute("error") != null) { %>
                <div class="alert error"><%=request.getAttribute("error")%></div>
                <% } %>
                <% if (request.getAttribute("message") != null) { %>
                <div class="alert success"><%=request.getAttribute("message")%></div>
                <% } %>
                <form action="<%=request.getContextPath()%>/register/verify" method="post">
                    <label class="form-label" for="code">Mã 6 chữ số *</label>
                    <input class="field" id="code" name="code" inputmode="numeric"
                           autocomplete="one-time-code" pattern="[0-9]{6}" minlength="6"
                           maxlength="6" placeholder="Nhập mã trong email" required>
                    <button class="button" style="width:100%;margin-top:18px" type="submit">Xác thực và tạo tài khoản</button>
                </form>
                <form action="<%=request.getContextPath()%>/register/verify" method="post" style="margin-top:12px">
                    <input type="hidden" name="action" value="resend">
                    <button class="button line" style="width:100%" type="submit">Gửi lại mã</button>
                </form>
                <p style="margin-top:16px"><a class="text-link" href="<%=request.getContextPath()%>/register">Quay lại đăng ký</a></p>
            </section>
        </div>
    </div>
</main>
<%@include file="/includes/footer.jspf"%>
</body>
</html>
