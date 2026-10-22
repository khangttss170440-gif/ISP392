<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,model.Product"%>
<%!
    private String imageEscape(Object value) {
        return value == null ? "" : value.toString().replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<%
    boolean warehouseMode = Boolean.TRUE.equals(request.getAttribute("warehouseMode"));
    List<Product> imageProducts = (List<Product>) request.getAttribute("products");
    Product selectedProduct = (Product) request.getAttribute("selectedProduct");
    List<String> currentImages = (List<String>) request.getAttribute("productImages");
    String imageAction = imageEscape(request.getAttribute("imageAction"));
    String imageToken = imageEscape(request.getAttribute("csrfToken"));
    java.util.Map<String, String> imageColors = (java.util.Map<String, String>) request.getAttribute("imageColors");
    java.util.Set<String> productColors = (java.util.Set<String>) request.getAttribute("productColors");
%>
<!doctype html><html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Ảnh sản phẩm · MENSFORM</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/product-images.css">
</head><body>
<% if (warehouseMode) { %><div class="portal-layout"><%@include file="/staff/includes/nav.jspf"%><main class="portal-main">
<% } else { %><div class="admin-layout"><%@include file="/admin/includes/sidebar.jspf"%><main class="admin-main"><% } %>
    <div class="admin-topline"><div><small>CATALOG / IMAGES</small><h1 class="admin-title">Ảnh sản phẩm</h1></div></div>
    <p class="admin-subtitle">Quản lý ảnh và gắn từng ảnh với màu của biến thể. Khách chọn màu sẽ thấy ảnh tương ứng trên cùng trang sản phẩm.</p>
    <% if (request.getAttribute("message") != null) { %><div class="alert success" role="status"><%=imageEscape(request.getAttribute("message"))%></div><% } %>
    <% if (request.getAttribute("error") != null) { %><div class="alert error" role="alert"><%=imageEscape(request.getAttribute("error"))%></div><% } %>
    <section class="admin-panel">
        <form method="get" action="<%=imageAction%>">
            <label class="form-label" for="imageProductId">Sản phẩm cần sửa ảnh</label>
            <div class="inline-form"><select class="select grow" name="productId" id="imageProductId" required>
                <option value="">Chọn sản phẩm</option>
                <% if (imageProducts != null) { for (Product item : imageProducts) { %>
                <option value="<%=item.getProductID()%>" <%=selectedProduct != null && selectedProduct.getProductID() == item.getProductID() ? "selected" : ""%>>#<%=item.getProductID()%> · <%=imageEscape(item.getProductName())%></option>
                <% } } %>
            </select><button type="submit" class="button">Xem ảnh</button></div>
        </form>
    </section>
    <% if (selectedProduct != null) { int imageProductId = selectedProduct.getProductID(); %>
    <section class="admin-panel">
        <h2>#<%=imageProductId%> · <%=imageEscape(selectedProduct.getProductName())%></h2>
        <p class="admin-note"><%=currentImages == null ? 0 : currentImages.size()%>/6 ảnh. JPG/PNG, tối đa 10 MB và 12 megapixel mỗi ảnh. Giữ nguyên kích thước ảnh; JPG được lưu chất lượng cao, PNG không nén mất chi tiết.</p>
        <p class="admin-note">Nên dùng ảnh gốc từ 1200 × 1500 px (tốt hơn: 1600 × 2000 px). Ảnh nhỏ hoặc mờ cần được thay bằng nguồn rõ hơn; bấm ảnh để xem kích thước đầy đủ.</p>
        <% if (currentImages == null || currentImages.isEmpty()) { %>
        <p class="admin-empty">Sản phẩm chưa có ảnh. Bạn có thể tải ảnh đầu tiên bên dưới.</p>
        <% } else { %><div class="image-management-grid">
        <% for (int index = 0; index < currentImages.size(); index++) {
            String filename = currentImages.get(index);
            String imageUrl = request.getContextPath() + "/product-image?productId=" + imageProductId + "&amp;file=" + filename;
        %>
        <article class="image-management-card">
            <a href="<%=imageUrl%>" target="_blank" rel="noopener"><img src="<%=imageUrl%>" alt="Ảnh sản phẩm <%=index + 1%>" data-image-resolution decoding="async"></a>
            <p class="admin-note"><%=index == 0 ? "Ảnh đại diện" : "Ảnh " + (index + 1)%></p>
            <form action="<%=imageAction%>" method="post">
                <input type="hidden" name="csrfToken" value="<%=imageToken%>">
                <input type="hidden" name="productId" value="<%=imageProductId%>">
                <input type="hidden" name="file" value="<%=filename%>">
                <label class="form-label" for="imageColor<%=index%>">Màu trong ảnh</label>
                <select class="select" id="imageColor<%=index%>" name="color">
                    <option value="">Ảnh chung / mọi màu</option>
                    <% if (productColors != null) { for (String color : productColors) { %>
                    <option value="<%=imageEscape(color)%>" <%=imageColors != null && color.equals(imageColors.get(filename)) ? "selected" : ""%>><%=imageEscape(color)%></option>
                    <% } } %>
                </select>
                <button class="button line small" type="submit" name="action" value="color">Lưu màu ảnh</button>
            </form>
            <form action="<%=imageAction%>" method="post">
                <input type="hidden" name="csrfToken" value="<%=imageToken%>">
                <input type="hidden" name="productId" value="<%=imageProductId%>">
                <input type="hidden" name="file" value="<%=filename%>">
                <button type="submit" name="action" value="cover" class="button line small" <%=index == 0 ? "disabled" : ""%>>Đặt làm ảnh đại diện</button>
            </form>
            <form action="<%=imageAction%>" method="post" enctype="multipart/form-data" class="image-replace-form">
                <input type="hidden" name="csrfToken" value="<%=imageToken%>">
                <input type="hidden" name="productId" value="<%=imageProductId%>">
                <input type="hidden" name="file" value="<%=filename%>">
                <label class="form-label" for="replaceImage<%=index%>">Chọn ảnh thay thế</label>
                <input id="replaceImage<%=index%>" type="file" name="images" accept="image/jpeg,image/png" required>
                <button type="submit" name="action" value="replace" class="button small">Thay ảnh này</button>
            </form>
            <form action="<%=imageAction%>" method="post" class="image-delete-form">
                <input type="hidden" name="csrfToken" value="<%=imageToken%>">
                <input type="hidden" name="productId" value="<%=imageProductId%>">
                <input type="hidden" name="file" value="<%=filename%>">
                <button type="submit" name="action" value="delete" class="button line small">Xóa ảnh</button>
            </form>
        </article>
        <% } %></div><% } %>
        <form action="<%=imageAction%>" method="post" enctype="multipart/form-data" class="product-upload">
            <input type="hidden" name="csrfToken" value="<%=imageToken%>">
            <input type="hidden" name="productId" value="<%=imageProductId%>">
            <input type="hidden" name="action" value="upload">
            <label class="form-label" for="productImages">Tải thêm ảnh</label>
            <p id="imageHelp" class="admin-note">Tổng số ảnh sau khi tải thêm không vượt quá 6. Thay ảnh không làm tăng số lượng ảnh.</p>
            <input class="field" id="productImages" type="file" name="images" accept="image/jpeg,image/png" multiple required aria-describedby="imageHelp imageUploadError">
            <p id="imageUploadError" class="product-upload-error" role="alert"></p>
            <div class="product-image-thumbs" id="uploadPreview" aria-label="Ảnh vừa chọn"></div>
            <button type="submit" class="button">Tải ảnh lên</button>
        </form>
    </section>
    <% if (productColors == null || productColors.isEmpty()) { %><p class="admin-note">Hãy thêm màu trong mục SKU / Biến thể trước khi gắn màu cho ảnh.</p><% } %>
    <% } %>
</main></div><script src="<%=request.getContextPath()%>/assets/product-images.js" defer></script>
</body></html>
