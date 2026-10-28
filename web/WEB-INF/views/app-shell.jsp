<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/app-shell.css">
    <script src="<%=request.getContextPath()%>/assets/app-shell.js" defer></script>
</head>
<body data-app-shell data-app-root="<%=util.AppConfig.escapeHtml(request.getContextPath() + "/")%>"
      data-initial-page="<%=util.AppConfig.escapeHtml(String.valueOf(request.getAttribute("navigationPage")))%>">
    <iframe id="app-page" title="<%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%>" allow="payment"></iframe>
    <noscript>Bạn cần bật JavaScript để sử dụng website.</noscript>
</body>
</html>
