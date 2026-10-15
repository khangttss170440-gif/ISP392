<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.ArrayList,java.sql.Timestamp,java.time.format.DateTimeFormatter,model.Promotion,model.Staff"%>
<%!
    private String promotionEscape(Object value) {
        return value == null ? "" : value.toString().replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
    private String promotionDate(Timestamp date) {
        return date == null ? "" : date.toLocalDateTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
    }
%>
<%
    Object promotionStaff = session.getAttribute("staff");
    if (!(promotionStaff instanceof Staff)
            || !"Admin".equalsIgnoreCase(((Staff) promotionStaff).getRole())
            || !"Active".equalsIgnoreCase(((Staff) promotionStaff).getStatus())) {
        response.sendError(403);
        return;
    }
    List<Promotion> promotions = (List<Promotion>) request.getAttribute("promotions");
    List<Promotion> promotionForms = new ArrayList<>();
    Promotion newPromotion = new Promotion();
    newPromotion.setStatus("Active");
    newPromotion.setDiscountType("Percentage");
    promotionForms.add(newPromotion);
    if (promotions != null) promotionForms.addAll(promotions);
%>
<!DOCTYPE html>
<html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Khuyến mãi · MENSFORM Admin</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
</head><body><div class="admin-layout">
<%@include file="/admin/includes/sidebar.jspf"%>
<main class="admin-main">
    <div class="admin-topline"><div><small>MARKETING / PROMOTIONS</small><h1 class="admin-title">Khuyến mãi</h1></div>
        <a class="button line small" href="<%=request.getContextPath()%>/promotions">Xem trang ưu đãi ↗</a></div>
    <p class="admin-subtitle">Thêm, sửa hoặc xóa khuyến mãi. Các mã đang bật và trong thời gian áp dụng tự động hiển thị trên trang chủ và trang ưu đãi.</p>
    <p class="admin-note">Mỗi đơn thành công trừ ngay 1 lượt coupon, không chờ Sales xác nhận. Mã hết lượt tự ngừng hiển thị. Thời gian theo máy chủ cơ sở dữ liệu.</p>
    <% if (request.getAttribute("message") != null) { %><div class="alert success"><%=promotionEscape(request.getAttribute("message"))%></div><% } %>
    <% if (request.getAttribute("error") != null) { %><div class="alert error"><%=promotionEscape(request.getAttribute("error"))%></div><% } %>
    <% for (Promotion p : promotionForms) { boolean creating = p.getPromotionID() == 0; String prefix = "promo-" + p.getPromotionID() + "-"; %>
    <details class="admin-panel promotion-editor" <%=creating ? "open" : ""%>>
        <summary><b><%=creating ? "＋ Tạo khuyến mãi" : promotionEscape(p.getPromotionCode()) + " · " + promotionEscape(p.getPromotionName())%></b>
            <% if (!creating) { %><span class="admin-badge <%= "Active".equals(p.getStatus()) ? "" : "off"%>"><%= "Active".equals(p.getStatus()) ? "Đang bật" : "Đã tắt"%></span><% } %>
        </summary>
        <form class="admin-form-grid" action="<%=request.getContextPath()%>/admin/promotions" method="post">
            <input type="hidden" name="csrfToken" value="<%=promotionEscape(request.getAttribute("csrfToken"))%>">
            <input type="hidden" name="action" value="<%=creating ? "create" : "update"%>">
            <input type="hidden" name="promotionId" value="<%=p.getPromotionID()%>">
            <input type="hidden" name="originalQuantity" value="<%=p.getQuantity()%>">
            <div><label class="form-label" for="<%=prefix%>code">Mã khuyến mãi *</label><input class="field" id="<%=prefix%>code" name="promotionCode" value="<%=promotionEscape(p.getPromotionCode())%>" pattern="[A-Za-z0-9_-]{3,50}" maxlength="50" placeholder="VD: MENSFORM10" required></div>
            <div><label class="form-label" for="<%=prefix%>name">Tên chương trình *</label><input class="field" id="<%=prefix%>name" name="promotionName" value="<%=promotionEscape(p.getPromotionName())%>" maxlength="150" required></div>
            <div><label class="form-label" for="<%=prefix%>type">Loại giảm *</label><select class="select" id="<%=prefix%>type" name="discountType"><option value="Percentage" <%= "Percentage".equals(p.getDiscountType()) ? "selected" : ""%>>Phần trăm (%)</option><option value="Fixed" <%= "Fixed".equals(p.getDiscountType()) ? "selected" : ""%>>Số tiền (₫)</option></select></div>
            <div><label class="form-label" for="<%=prefix%>value">Giá trị giảm *</label><input class="field" id="<%=prefix%>value" name="discountValue" type="number" min="0.01" step="0.01" value="<%=promotionEscape(p.getDiscountValue())%>" required></div>
            <div><label class="form-label" for="<%=prefix%>start">Bắt đầu *</label><input class="field" id="<%=prefix%>start" name="startDate" type="datetime-local" value="<%=promotionDate(p.getStartDate())%>" required></div>
            <div><label class="form-label" for="<%=prefix%>end">Kết thúc *</label><input class="field" id="<%=prefix%>end" name="endDate" type="datetime-local" value="<%=promotionDate(p.getEndDate())%>" required></div>
            <div><label class="form-label" for="<%=prefix%>min">Đơn tối thiểu (₫)</label><input class="field" id="<%=prefix%>min" name="minOrderValue" type="number" min="0" step="0.01" value="<%=promotionEscape(p.getMinOrderValue())%>" placeholder="Không giới hạn"></div>
            <div><label class="form-label" for="<%=prefix%>max">Giảm tối đa (₫)</label><input class="field" id="<%=prefix%>max" name="maxDiscount" type="number" min="0" step="0.01" value="<%=promotionEscape(p.getMaxDiscount())%>" placeholder="Không giới hạn"></div>
            <div><label class="form-label" for="<%=prefix%>status">Trạng thái</label><select class="select" id="<%=prefix%>status" name="status"><option value="Active" <%= "Active".equals(p.getStatus()) ? "selected" : ""%>>Bật</option><option value="Inactive" <%= "Inactive".equals(p.getStatus()) ? "selected" : ""%>>Tắt</option></select></div>
            <div><label class="form-label" for="<%=prefix%>quantity">Số lượt coupon còn lại *</label><input class="field" id="<%=prefix%>quantity" name="quantity" type="number" min="0" max="2147483647" step="1" value="<%=p.getQuantity()%>" required><small class="admin-note">Đặt 0 để hết lượt. Sửa trường khác sẽ giữ các lượt đã được khách sử dụng.</small></div>
            <div class="promotion-save"><button class="button small" type="submit"><%=creating ? "Tạo khuyến mãi" : "Lưu thay đổi"%></button></div>
        </form>
        <% if (!creating) { %>
        <form action="<%=request.getContextPath()%>/admin/promotions" method="post" style="margin-top:18px" onsubmit="return confirm('Xóa vĩnh viễn khuyến mãi này?')">
            <input type="hidden" name="csrfToken" value="<%=promotionEscape(request.getAttribute("csrfToken"))%>">
            <input type="hidden" name="action" value="delete">
            <input type="hidden" name="promotionId" value="<%=p.getPromotionID()%>">
            <button class="button line small" type="submit">Xóa khuyến mãi</button>
        </form>
        <% } %>
    </details>
    <% } %>
    <% if (promotions != null && promotions.isEmpty()) { %><p class="admin-note">Chưa có chương trình khuyến mãi. Hãy tạo mã đầu tiên ở trên.</p><% } %>
</main></div></body></html>
