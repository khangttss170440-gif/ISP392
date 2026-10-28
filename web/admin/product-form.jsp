<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,model.Product,model.ProductCategory"%>
<%
    Product product = (Product) request.getAttribute("product");
    List<ProductCategory> categories = (List<ProductCategory>) request.getAttribute("categories");
    boolean hasProduct = product != null;
    boolean editing = hasProduct && product.getProductID() > 0;
    boolean warehouseCreator = Boolean.TRUE.equals(request.getAttribute("warehouseCreator"));
    String formAction = (String) request.getAttribute("formAction");
    String cancelUrl = (String) request.getAttribute("cancelUrl");
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Sản phẩm · <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%></title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/product-images.css">
</head>
<body>
<% if (warehouseCreator) { %>
    <div class="portal-layout">
        <%@include file="/staff/includes/nav.jspf"%>
        <main class="portal-main">
<% } else { %>
    <div class="admin-layout">
        <%@include file="/admin/includes/sidebar.jspf"%>
        <main class="admin-main">
<% } %>
            <div class="admin-topline">
                <div>
                    <small><%=warehouseCreator ? "WAREHOUSE / CATALOG / NEW" : "CATALOG / PRODUCTS / " + (editing ? "EDIT" : "NEW")%></small>
                    <h1 class="admin-title"><%=editing ? "Cập nhật sản phẩm" : "Tạo sản phẩm mới"%></h1>
                </div>
                <a class="button line small" href="<%=cancelUrl%>"><%=warehouseCreator ? "← Trang kho" : "← Danh sách sản phẩm"%></a>
            </div>
            <p class="admin-subtitle"><%=warehouseCreator ? "Nhập thông tin để đăng sản phẩm; sau đó thêm SKU, màu, kích cỡ, giá và số lượng." : "Thông tin sản phẩm là dữ liệu gốc; giá và tồn kho được quản lý ở phần biến thể."%></p>
            <% if (request.getAttribute("message") != null) { %>
                <div class="alert success"><%=request.getAttribute("message")%></div>
            <% } %>
            <% if (request.getAttribute("error") != null) { %>
                <div class="alert error"><%=request.getAttribute("error")%></div>
            <% } %>
            <section class="admin-panel">
                <form action="<%=formAction%>" method="post" enctype="multipart/form-data">
                    <% if (editing) { %><input type="hidden" name="productId" value="<%=product.getProductID()%>"><% } %>
                    <div class="admin-form-grid">
                        <div class="span-2">
                            <label class="form-label" for="productName">Tên sản phẩm *</label>
                            <input class="field" id="productName" name="productName" maxlength="200" required value="<%=hasProduct && product.getProductName() != null ? product.getProductName() : ""%>">
                        </div>
                        <div>
                            <label class="form-label" for="categoryId">Danh mục *</label>
                            <select class="select" id="categoryId" name="categoryId" required>
                                <option value="">Chọn danh mục</option>
                                <% if (categories != null) { for (ProductCategory category : categories) { %>
                                    <option value="<%=category.getCategoryID()%>" <%=hasProduct && category.getCategoryID() == product.getCategoryID() ? "selected" : ""%>><%=category.getCategoryName()%></option>
                                <% } } %>
                            </select>
                        </div>
                        <div>
                            <label class="form-label" for="brand">Thương hiệu</label>
                            <input class="field" id="brand" name="brand" maxlength="100" value="<%=hasProduct && product.getBrand() != null ? product.getBrand() : ""%>">
                        </div>
                        <div>
                            <label class="form-label" for="material">Chất liệu</label>
                            <input class="field" id="material" name="material" maxlength="100" value="<%=hasProduct && product.getMaterial() != null ? product.getMaterial() : ""%>">
                        </div>
                        <% if (!warehouseCreator) { %>
                            <div>
                                <label class="form-label" for="status">Trạng thái</label>
                                <select class="select" id="status" name="status">
                                    <option value="Active" <%=!hasProduct || product.getStatus() == null || "Active".equalsIgnoreCase(product.getStatus()) ? "selected" : ""%>>Đang hoạt động</option>
                                    <option value="Inactive" <%=hasProduct && "Inactive".equalsIgnoreCase(product.getStatus()) ? "selected" : ""%>>Đã tắt</option>
                                </select>
                            </div>
                        <% } %>
                        <div class="span-2">
                            <label class="form-label" for="description">Mô tả</label>
                            <textarea class="textarea" id="description" name="description" maxlength="1000"><%=hasProduct && product.getDescription() != null ? product.getDescription() : ""%></textarea>
                            <small class="admin-note">Tối đa 1.000 ký tự.</small>
                        </div>
                    </div>
                    <div class="product-upload">
                        <label class="form-label" for="productImages">Ảnh sản phẩm</label>
                        <p class="admin-note" id="imageHelp">Chọn tối đa <%=service.ProductImageStore.MAX_IMAGES%> ảnh JPG/PNG, mỗi ảnh tối đa <%=service.ProductImageStore.MAX_FILE_SIZE / (1024 * 1024)%> MB và <%=service.ProductImageStore.MAX_PIXELS / 1000000%> megapixel. Giữ nguyên kích thước ảnh, lưu JPG chất lượng cao. Nên chọn ảnh gốc từ 1200 × 1500 px để xem rõ chi tiết. Ảnh đầu tiên là ảnh đại diện.</p>
                        <input class="field" id="productImages" name="images" type="file" accept="image/jpeg,image/png" multiple aria-describedby="imageHelp imageUploadError">
                        <p id="imageUploadError" role="alert" class="product-upload-error"></p>
                        <div class="product-image-thumbs" id="uploadPreview" aria-label="Ảnh vừa chọn"></div>
                        <% List<String> productImages = (List<String>) request.getAttribute("productImages");
                           if (editing && productImages != null && !productImages.isEmpty()) { %>
                        <p class="admin-note">Ảnh hiện tại (<%=productImages.size()%>/6)</p>
                        <div class="product-image-thumbs">
                            <% for (String image : productImages) { %>
                            <a href="<%=request.getContextPath()%>/product-image?productId=<%=product.getProductID()%>&amp;file=<%=image%>" target="_blank" rel="noopener">
                                <img src="<%=request.getContextPath()%>/product-image?productId=<%=product.getProductID()%>&amp;file=<%=image%>" alt="Ảnh sản phẩm hiện tại" loading="lazy">
                            </a>
                            <% } %>
                        </div>
                        <% } %>
                        <% if (editing) { %>
                        <p class="admin-note">Mã sản phẩm: #<%=product.getProductID()%>. Bạn cũng có thể chép ảnh vào thư mục mang mã sản phẩm trong kho ảnh.</p>
                        <a class="button line small" href="<%=request.getContextPath()%>/admin/product-images?productId=<%=product.getProductID()%>">Quản lý ảnh: thay, xóa, chọn ảnh đại diện →</a>
                        <% } %>
                    </div>
                    <div class="admin-actions" style="margin-top:20px">
                        <button class="button" type="submit"><%=editing ? "Lưu thay đổi" : "Đăng sản phẩm"%></button>
                        <a class="button line" href="<%=cancelUrl%>">Hủy</a>
                    </div>
                </form>
            </section>
        </main>
    </div>
    <script src="<%=request.getContextPath()%>/assets/product-images.js" defer></script>
</body>
</html>

