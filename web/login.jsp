<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html><html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Đăng nhập — MENSFORM</title><link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/login-theme.css?v=20261024e">
</head><body class="login-theme">
<%@include file="/includes/header.jspf"%>
<main class="login-page"><div class="login-layout">
    <section class="login-form-panel" aria-labelledby="loginTitle">
        <span class="eyebrow">Không gian của bạn</span><h1 id="loginTitle">Chào mừng trở lại.</h1>
        <p>Đăng nhập để tiếp tục câu chuyện phong cách của bạn.</p>
        <% if (request.getAttribute("message") != null) { %><div class="alert success" role="status"><%=navEscape(request.getAttribute("message"))%></div><% } %>
        <% if (request.getAttribute("error") != null) { %><div class="alert error" role="alert"><%=navEscape(request.getAttribute("error"))%></div><% } %>
        <form action="<%=request.getContextPath()%>/login" method="post">
            <label class="form-label" for="accountType">Loại tài khoản</label>
            <select class="select" id="accountType" name="accountType"><option value="CUSTOMER" <%= "STAFF".equals(request.getAttribute("accountType")) ? "" : "selected" %>>Khách hàng</option><option value="STAFF" <%= "STAFF".equals(request.getAttribute("accountType")) ? "selected" : "" %>>Nhân viên / Admin</option></select>
            <label class="form-label" for="email">Email</label>
            <input class="field" id="email" name="email" type="email" autocomplete="username" placeholder="Địa chỉ email của bạn" required>
            <label class="form-label" for="password">Mật khẩu</label>
            <input class="field" id="password" name="password" type="password" autocomplete="current-password" placeholder="Nhập mật khẩu" required>
            <div style="text-align:right;margin:8px 0 16px"><a class="text-link" href="<%=request.getContextPath()%>/password/forgot">Quên mật khẩu khách hàng?</a></div>
            <button class="button" type="submit">Đăng nhập <span aria-hidden="true">→</span></button>
        </form>
        <div class="login-register">Chưa có tài khoản? <a href="<%=request.getContextPath()%>/register">Đăng ký ngay</a></div>
        <a class="login-home" href="<%=request.getContextPath()%>/index.jsp">← Trở về cửa hàng</a>
    </section>
    <aside class="login-image"><span class="eyebrow">MENSFORM · Phụ kiện nam</span><h2>Đơn giản trong lựa chọn.<br>Khác biệt trong phong cách.</h2><p>Những thiết kế đồng hành cùng bạn, từ nhịp sống thường ngày đến khoảnh khắc đặc biệt.</p></aside>
</div></main>
<%@include file="/includes/footer.jspf"%>
</body></html>
