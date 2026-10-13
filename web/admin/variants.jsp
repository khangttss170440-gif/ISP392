<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Locale,java.text.NumberFormat,model.Product,model.ProductVariant"%>
<%
    List<Product> products = (List<Product>) request.getAttribute("products");
    List<ProductVariant> variants = (List<ProductVariant>) request.getAttribute("variants");
    int selectedProductId = request.getAttribute("selectedProductId") == null
            ? 0 : ((Integer) request.getAttribute("selectedProductId")).intValue();
    boolean warehouseMode = Boolean.TRUE.equals(request.getAttribute("warehouseMode"));
    String formAction = (String) request.getAttribute("formAction");
    String backUrl = (String) request.getAttribute("backUrl");
    NumberFormat money = NumberFormat.getInstance(new Locale("vi", "VN"));
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Biến thể & SKU · MENSFORM</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
</head>
<body>
<% if (warehouseMode) { %>
    <div class="portal-layout">
        <%@include file="/staff/includes/nav.jspf"%>
        <main class="portal-main">
<% } else { %>
    <div class="admin-layout">
        <%@include file="/admin/includes/sidebar.jspf"%>
        <main class="admin-main">
<% } %>
            <div class="admin-topline">
                <div><small><%=warehouseMode ? "WAREHOUSE / CATALOG / VARIANTS" : "CATALOG / INVENTORY SETUP"%></small>
                    <h1 class="admin-title">Biến thể & SKU</h1>
                </div>
                <a class="button line small" href="<%=backUrl%>"><%=warehouseMode ? "← Trang kho" : "← Sản phẩm"%></a>
            </div>
            <p class="admin-subtitle"><%=warehouseMode ? "Thêm SKU, màu, kích cỡ, giá và số lượng ban đầu cho sản phẩm." : "Thiết lập SKU, màu, kích cỡ và giá bán. Số lượng tồn có thể cập nhật tại đây khi thêm SKU; các thay đổi tồn kho sau đó do kho vận quản lý."%></p>
            <% if (request.getAttribute("message") != null) { %><div class="alert success"><%=request.getAttribute("message")%></div><% } %>
            <% if (request.getAttribute("error") != null) { %><div class="alert error"><%=request.getAttribute("error")%></div><% } %>

            <section class="admin-panel">
                <div class="admin-toolbar"><h2>Lọc theo sản phẩm</h2></div>
                <form class="admin-filter" action="<%=formAction%>" method="get">
                    <select class="select" name="productId">
                        <option value="0">Tất cả sản phẩm</option>
                        <% if (products != null) { for (Product product : products) { %>
                            <option value="<%=product.getProductID()%>" <%=selectedProductId == product.getProductID() ? "selected" : ""%>><%=product.getProductName()%></option>
                        <% } } %>
                    </select>
                    <button class="button small" type="submit">Lọc</button>
                </form>
            </section>

            <section class="admin-panel">
                <h2>Thêm biến thể mới</h2>
                <form class="admin-form-grid" action="<%=formAction%>" method="post">
                    <input type="hidden" name="action" value="create">
                    <div>
                        <label class="form-label" for="newProductId">Sản phẩm *</label>
                        <select class="select" id="newProductId" name="productId" required>
                            <option value="">Chọn sản phẩm</option>
                            <% if (products != null) { for (Product product : products) {
                                if ("Active".equalsIgnoreCase(product.getStatus())) { %>
                                <option value="<%=product.getProductID()%>" <%=selectedProductId == product.getProductID() ? "selected" : ""%>><%=product.getProductName()%></option>
                            <% } } } %>
                        </select>
                    </div>
                    <div><label class="form-label" for="newSku">SKU *</label><input class="field" id="newSku" name="sku" maxlength="50" required></div>
                    <div><label class="form-label" for="newColor">Màu *</label><input class="field" id="newColor" name="color" maxlength="50" required></div>
                    <div><label class="form-label" for="newSize">Kích cỡ *</label><input class="field" id="newSize" name="size" maxlength="20" required></div>
                    <div><label class="form-label" for="newPrice">Giá bán (₫) *</label><input class="field" id="newPrice" name="price" type="number" min="0" step="0.01" required></div>
                    <div><label class="form-label" for="newQuantity">Số lượng ban đầu *</label><input class="field" id="newQuantity" name="quantity" type="number" min="0" max="2147483647" step="1" value="0" required></div>
                    <div class="span-2"><button class="button small" type="submit">＋ Thêm SKU</button></div>
                </form>
            </section>

            <section class="admin-panel">
                <div class="admin-toolbar"><h2>Danh sách biến thể</h2><span class="admin-note"><%=variants == null ? 0 : variants.size()%> SKU</span></div>
                <div class="admin-table-wrap">
                    <table class="admin-table">
                        <thead><tr><th>Sản phẩm</th><th>SKU</th><th>Màu / Size</th><th>Giá (₫)</th><th>Số lượng</th><th>Trạng thái</th><% if (!warehouseMode) { %><th>Lưu</th><% } %></tr></thead>
                        <tbody>
                        <% if (variants == null || variants.isEmpty()) { %>
                            <tr><td colspan="7" class="admin-empty">Chưa có biến thể phù hợp.</td></tr>
                        <% } else { for (ProductVariant variant : variants) {
                            String formId = "variant-" + variant.getVariantID(); %>
                            <tr>
                                <td><%=variant.getProductName()%>
                                    <% if (!warehouseMode) { %>
                                        <form id="<%=formId%>" action="<%=formAction%>" method="post">
                                            <input type="hidden" name="action" value="update">
                                            <input type="hidden" name="variantId" value="<%=variant.getVariantID()%>">
                                            <input type="hidden" name="productId" value="<%=variant.getProductID()%>">
                                        </form>
                                    <% } %>
                                </td>
                                <td><% if (warehouseMode) { %><%=variant.getSku()%><% } else { %><input form="<%=formId%>" class="field" name="sku" maxlength="50" value="<%=variant.getSku()%>" required><% } %></td>
                                <td><% if (warehouseMode) { %><%=variant.getColor() == null ? "—" : variant.getColor()%> / <%=variant.getSize() == null ? "—" : variant.getSize()%><% } else { %><input form="<%=formId%>" class="field" name="color" maxlength="50" value="<%=variant.getColor() == null ? "" : variant.getColor()%>" placeholder="Màu"><input form="<%=formId%>" class="field" name="size" maxlength="20" value="<%=variant.getSize() == null ? "" : variant.getSize()%>" placeholder="Size"><% } %></td>
                                <td><% if (warehouseMode) { %><%=money.format(variant.getPrice())%><% } else { %><input form="<%=formId%>" class="field" name="price" type="number" min="0" step="0.01" value="<%=variant.getPrice()%>" required><% } %></td>
                                <td><b><%=variant.getQuantity()%></b></td>
                                <td><span class="admin-badge <%= "Active".equalsIgnoreCase(variant.getStatus()) ? "" : "off"%>"><%=variant.getStatus()%></span></td>
                                <% if (!warehouseMode) { %><td><button form="<%=formId%>" class="button small" type="submit">Lưu</button></td><% } %>
                            </tr>
                        <% } } %>
                        </tbody>
                    </table>
                </div>
            </section>
        </main>
    </div>
</body>
</html>
