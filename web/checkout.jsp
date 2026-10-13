<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Locale,java.text.NumberFormat,model.Cart,model.CartItem,model.Customer,model.CustomerAddress"%>
<%!
    private String checkoutEscape(Object value) {
        if (value == null) return "";
        return value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
    private String checkoutFullAddress(CustomerAddress address) {
        StringBuilder text = new StringBuilder();
        String[] parts = { address.getAddressLine(), address.getWard(),
            address.getDistrict(), address.getProvince() };
        for (String part : parts) {
            if (part != null && !part.trim().isEmpty()) {
                if (text.length() > 0) text.append(", ");
                text.append(part.trim());
            }
        }
        return text.toString();
    }
%>
<%
    Cart cart = (Cart) request.getAttribute("cart");
    Customer customer = (Customer) request.getAttribute("customer");
    List<CustomerAddress> addresses = (List<CustomerAddress>) request.getAttribute("addresses");
    NumberFormat money = NumberFormat.getInstance(new Locale("vi", "VN"));
    CustomerAddress defaultAddress = null;
    if (addresses != null) {
        for (CustomerAddress address : addresses) {
            if (address.isDefaultAddress()) { defaultAddress = address; break; }
        }
        if (defaultAddress == null && !addresses.isEmpty()) defaultAddress = addresses.get(0);
    }
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Đặt hàng — MENSFORM</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
</head>
<body>
<%@include file="/includes/header.jspf"%>
<main class="page">
    <div class="shell">
        <div class="breadcrumb"><a href="<%=request.getContextPath()%>/cart">Giỏ hàng</a>　/　Đặt hàng</div>
        <span class="eyebrow">Chỉ còn một bước</span>
        <h1 class="page-title">Thông tin giao hàng</h1>
        <p class="page-intro">Kiểm tra thông tin nhận hàng và xác nhận đơn của bạn.</p>
        <% if (request.getAttribute("error") != null) { %>
            <div class="alert error"><%=checkoutEscape(request.getAttribute("error"))%></div>
        <% } %>
        <div class="checkout-layout">
            <form class="cart-list" action="<%=request.getContextPath()%>/checkout" method="post">
                <h2 style="font:700 20px Manrope;margin:0 0 15px">Người nhận hàng</h2>
                <label class="form-label" for="recipientName">Họ và tên tài khoản</label>
                <input class="field" id="recipientName" value="<%=checkoutEscape(customer.getFullName())%>" readonly>

                <label class="form-label" for="addressId">Chọn địa chỉ giao hàng *</label>
                <select class="select" id="addressId" name="addressId">
                    <% if (addresses != null) { for (CustomerAddress address : addresses) { %>
                        <option value="<%=address.getAddressID()%>" data-preview="<%=checkoutEscape(address.getRecipientName() + " · " + (address.getPhone() == null ? "" : address.getPhone()) + " · " + checkoutFullAddress(address))%>"
                                <%=defaultAddress != null && defaultAddress.getAddressID() == address.getAddressID() ? "selected" : ""%>>
                            <%=checkoutEscape(address.getRecipientName())%> — <%=checkoutEscape(address.getPhone() == null ? "" : address.getPhone())%><%=address.isDefaultAddress() ? " (Mặc định)" : ""%>
                        </option>
                    <% } } %>
                    <option value="new" <%=defaultAddress == null ? "selected" : ""%>>Nhập địa chỉ giao hàng khác</option>
                </select>
                <p class="notice" style="margin:9px 0">
                    Quản lý địa chỉ tại <a href="<%=request.getContextPath()%>/customer/addresses">Sổ địa chỉ</a>.
                </p>
                <div id="savedAddressPreview" class="notice" aria-live="polite"></div>

                <div id="customAddressBox" <%=defaultAddress == null ? "" : "hidden"%>>
                    <p class="form-label">Lưu địa chỉ mới vào sổ địa chỉ</p>
                    <label class="form-label" for="newRecipientName">Tên người nhận *</label>
                    <input class="field" id="newRecipientName" data-custom-address name="recipientName" maxlength="150" <%=defaultAddress == null ? "required" : "disabled"%>>
                    <label class="form-label" for="recipientPhone">Số điện thoại *</label>
                    <input class="field" id="recipientPhone" data-custom-address name="recipientPhone" type="tel" maxlength="20" pattern="[0-9+(). -]{8,20}" <%=defaultAddress == null ? "required" : "disabled"%>>
                    <label class="form-label" for="addressLine">Số nhà, tên đường *</label>
                    <input class="field" id="addressLine" data-custom-address name="addressLine" maxlength="300" <%=defaultAddress == null ? "required" : "disabled"%>>
                    <label class="form-label" for="ward">Phường / xã</label>
                    <input class="field" id="ward" data-custom-address name="ward" maxlength="100" <%=defaultAddress == null ? "" : "disabled"%>>
                    <label class="form-label" for="district">Quận / huyện</label>
                    <input class="field" id="district" data-custom-address name="district" maxlength="100" <%=defaultAddress == null ? "" : "disabled"%>>
                    <label class="form-label" for="province">Tỉnh / thành phố *</label>
                    <input class="field" id="province" data-custom-address name="province" maxlength="100" <%=defaultAddress == null ? "required" : "disabled"%>>
                    <p class="notice">Địa chỉ này sẽ được lưu trong sổ địa chỉ để bạn có thể chọn ở những lần đặt hàng sau.</p>
                </div>

                <label class="form-label" for="notes">Ghi chú cho đơn hàng</label>
                <textarea class="textarea" id="notes" name="notes" maxlength="1000" placeholder="Khung giờ nhận hàng hoặc lưu ý khác"></textarea>
                <label class="form-label" for="paymentMethod">Phương thức thanh toán</label>
                <select class="select" id="paymentMethod" name="paymentMethod">
                    <option value="COD">Thanh toán khi nhận hàng (COD)</option>
                </select>
                <button class="button" style="margin-top:20px" type="submit">Xác nhận đặt hàng ↗</button>
            </form>

            <aside class="summary">
                <h2>Đơn hàng của bạn</h2>
                <% for (CartItem item : cart.getItems()) { %>
                    <div class="summary-row">
                        <span><%=checkoutEscape(item.getProductName())%> · <%=checkoutEscape(item.getColor())%> / <%=checkoutEscape(item.getSize())%> × <%=item.getQuantity()%></span>
                        <b><%=money.format(item.getLineTotal())%>₫</b>
                    </div>
                <% } %>
                <div class="summary-row"><span>Vận chuyển</span><span>0₫</span></div>
                <div class="summary-row total"><span>Tổng thanh toán</span><span><%=money.format(cart.getSubtotal())%>₫</span></div>
                <p class="notice">Bạn thanh toán khi nhận hàng.</p>
            </aside>
        </div>
    </div>
</main>
<%@include file="/includes/footer.jspf"%>
<script>
(function () {
    var selector = document.getElementById('addressId');
    var preview = document.getElementById('savedAddressPreview');
    var customBox = document.getElementById('customAddressBox');
    var customFields = customBox.querySelectorAll('[data-custom-address]');
    function updateAddressChoice() {
        var isCustom = selector.value === 'new';
        customBox.hidden = !isCustom;
        customFields.forEach(function (field) {
            field.disabled = !isCustom;
            field.required = isCustom && ['newRecipientName', 'recipientPhone', 'addressLine', 'province'].indexOf(field.id) >= 0;
        });
        preview.textContent = isCustom ? '' : (selector.options[selector.selectedIndex].dataset.preview || '');
    }
    selector.addEventListener('change', updateAddressChoice);
    updateAddressChoice();
}());
</script>
</body>
</html>


