<%@page contentType="text/html" pageEncoding="UTF-8" import="model.CustomerAddress"%>
<%
    CustomerAddress a = (CustomerAddress) request.getAttribute("address");
    if (a == null) a = new CustomerAddress();
    boolean editing = a.getAddressID() > 0;
%>
<!doctype html><html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Địa chỉ — <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/shipping-map.css">
</head><body><%@include file="/includes/header.jspf"%>
<main class="shell page">
    <a href="<%=request.getContextPath()%>/customer/addresses">← Sổ địa chỉ</a>
    <h1 class="page-title"><%=editing ? "Sửa địa chỉ" : "Thêm địa chỉ"%></h1>
    <p class="page-intro">Nhập địa chỉ nhận hàng để kiểm tra phí giao hàng theo khu vực.</p>
    <% if (request.getAttribute("error") != null) { %><div class="alert error"><%=navEscape(request.getAttribute("error"))%></div><% } %>
    <section class="portal-panel"><form action="<%=request.getContextPath()%>/customer/address-form" method="post" class="portal-form-grid">
        <% if (editing) { %><input type="hidden" name="addressId" value="<%=a.getAddressID()%>"><% } %>
        <div><label class="form-label" for="addressRecipientName">Tên người nhận *</label><input class="field" id="addressRecipientName" name="recipientName" maxlength="300" required value="<%=navEscape(a.getRecipientName())%>"></div>
        <div><label class="form-label" for="addressPhone">Số điện thoại *</label><input class="field" id="addressPhone" name="phone" type="tel" maxlength="20" required value="<%=navEscape(a.getPhone())%>"></div>
        <div class="span-all"><label class="form-label" for="addressLine">Địa chỉ chi tiết *</label><input class="field" id="addressLine" name="addressLine" autocomplete="address-line1" maxlength="1000" required value="<%=navEscape(a.getAddressLine())%>"></div>
        <div><label class="form-label" for="addressWard">Phường / xã</label><input class="field" id="addressWard" name="ward" maxlength="100" value="<%=navEscape(a.getWard())%>"></div>
        <div><label class="form-label" for="addressDistrict">Quận / huyện</label><input class="field" id="addressDistrict" name="district" maxlength="100" value="<%=navEscape(a.getDistrict())%>"></div>
        <div><label class="form-label" for="addressProvince">Tỉnh / thành phố *</label><input class="field" id="addressProvince" name="province" autocomplete="address-level1" maxlength="100" required value="<%=navEscape(a.getProvince())%>"></div>
        <label><input type="checkbox" name="isDefault" value="true" <%=a.isDefaultAddress() ? "checked" : ""%>> Đặt làm địa chỉ mặc định</label>
        <div class="span-all"><%@include file="/includes/shipping-map.jspf"%></div>
        <div class="span-all portal-actions"><button class="button" type="submit">Lưu địa chỉ</button><a class="button line" href="<%=request.getContextPath()%>/customer/addresses">Hủy</a></div>
    </form></section>
</main><%@include file="/includes/footer.jspf"%>
<script src="<%=request.getContextPath()%>/assets/shipping-map.js" defer></script>
</body></html>
