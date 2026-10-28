<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Chuyển đến trang thanh toán</title>
    <script src="<%=request.getContextPath()%>/assets/external-navigation.js" defer></script>
</head>
<body>
    <p>Đang chuyển đến trang thanh toán…</p>
    <a id="external-navigation" target="_top" href="<%=util.AppConfig.escapeHtml(String.valueOf(request.getAttribute("externalNavigationUrl")))%>">Tiếp tục thanh toán</a>
</body>
</html>
