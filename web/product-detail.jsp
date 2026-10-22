<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Map,java.util.Set,java.util.LinkedHashSet,java.util.Locale,java.text.NumberFormat,model.Product,model.ProductVariant,model.ProductReview"%>
<%
    Product product = (Product) request.getAttribute("product");
    List<ProductVariant> variants = (List<ProductVariant>) request.getAttribute("variants");
    List<ProductReview> reviews = (List<ProductReview>) request.getAttribute("reviews");
    List<String> productImages = (List<String>) request.getAttribute("productImages");
    Map<String, String> imageColors = (Map<String, String>) request.getAttribute("imageColors");
    NumberFormat money = NumberFormat.getInstance(new Locale("vi", "VN"));
    Set<String> variantColors = new LinkedHashSet<>();
    if (variants != null) for (ProductVariant variant : variants) {
        variantColors.add(variant.getColor() == null || variant.getColor().isBlank() ? "Mặc định" : variant.getColor().trim());
    }
%>
<!DOCTYPE html><html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1">
    <title><%=navEscape(product.getProductName())%> — MENSFORM</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/product-images.css">
</head><body><%@include file="/includes/header.jspf"%>
<main class="page"><div class="shell">
    <div class="breadcrumb"><a href="<%=request.getContextPath()%>/products">Sản phẩm</a>　/　<%=navEscape(product.getCategoryName())%>　/　<%=navEscape(product.getProductName())%></div>
    <div class="detail-layout">
        <div class="product-gallery">
            <a id="fullProductImage" class="detail-art product-photo" href="<%=request.getContextPath()%>/product-image?productId=<%=product.getProductID()%>" target="_blank" rel="noopener" aria-label="Mở ảnh sản phẩm kích thước đầy đủ"><img id="mainProductImage" src="<%=request.getContextPath()%>/product-image?productId=<%=product.getProductID()%>" alt="<%=navEscape(product.getProductName())%>" decoding="async" fetchpriority="high"></a>
            <p class="product-meta">Bấm vào ảnh để xem kích thước đầy đủ.</p>
            <% if (productImages != null && !productImages.isEmpty()) { %>
            <div class="product-image-thumbs" aria-label="Chọn ảnh sản phẩm">
                <% for (int i = 0; i < productImages.size(); i++) {
                    String filename = productImages.get(i);
                    String imageUrl = request.getContextPath() + "/product-image?productId=" + product.getProductID() + "&amp;file=" + filename;
                    String imageColor = imageColors == null ? "" : imageColors.getOrDefault(filename, "");
                %>
                <button type="button" data-product-image="<%=imageUrl%>" data-image-color="<%=navEscape(imageColor)%>" aria-label="Xem ảnh <%=i + 1%><%=imageColor.isEmpty() ? "" : " · " + navEscape(imageColor)%>" aria-pressed="<%=i == 0%>"><img src="<%=imageUrl%>" alt="<%=imageColor.isEmpty() ? "Góc nhìn " + (i + 1) : "Màu " + navEscape(imageColor)%>" loading="lazy"></button>
                <% } %>
            </div>
            <% } %>
            <p id="colorImageNote" class="product-meta" role="status" hidden></p>
        </div>
        <div class="detail-copy">
            <span class="eyebrow"><%=navEscape(product.getBrand() == null ? "MENSFORM" : product.getBrand())%></span>
            <h1><%=navEscape(product.getProductName())%></h1>
            <p><%=navEscape(product.getDescription() == null ? "Một lựa chọn tinh giản, dễ kết hợp và phù hợp cho nhịp sống hằng ngày." : product.getDescription())%></p>
            <div class="detail-price" id="selected-price" aria-live="polite">Chọn màu và kích cỡ</div>
            <p><%=product.getMaterial() == null ? "" : "Chất liệu · " + navEscape(product.getMaterial())%></p>
            <% if (request.getAttribute("error") != null) { %><div class="alert error"><%=navEscape(request.getAttribute("error"))%></div><% } %>
            <form id="productVariantForm" action="<%=request.getContextPath()%>/cart" method="post">
                <input type="hidden" name="action" value="add">
                <div id="variantPicker" hidden>
                    <fieldset class="variant-color-fieldset"><legend class="form-label">Màu sắc <span id="selectedColorLabel"></span></legend>
                        <div class="variant-color-options">
                            <% for (String color : variantColors) { %><button type="button" class="variant-color-button" data-variant-color="<%=navEscape(color)%>" aria-pressed="false"><%=navEscape(color)%></button><% } %>
                        </div>
                    </fieldset>
                    <label class="form-label" for="variantSize">Kích cỡ</label>
                    <select class="select" id="variantSize" disabled><option value="">Chọn màu trước</option></select>
                </div>
                <div id="legacyVariantPicker">
                    <label class="form-label" for="variantId">Màu sắc · Kích cỡ</label>
                    <select class="select" id="variantId" name="variantId" required>
                        <option value="">Chọn phiên bản</option>
                        <% if (variants != null) for (ProductVariant variant : variants) {
                            String color = variant.getColor() == null || variant.getColor().isBlank() ? "Mặc định" : variant.getColor().trim();
                        %>
                        <option value="<%=variant.getVariantID()%>" data-color="<%=navEscape(color)%>" data-size="<%=navEscape(variant.getSize() == null || variant.getSize().isBlank() ? "Free size" : variant.getSize())%>" data-price="<%=money.format(variant.getPrice())%>" data-amount="<%=variant.getPrice().toPlainString()%>" data-stock="<%=variant.getQuantity()%>" <%=variant.getQuantity() <= 0 ? "disabled" : ""%>><%=navEscape(color)%> · <%=navEscape(variant.getSize() == null ? "Free size" : variant.getSize())%> — <%=variant.getQuantity() > 0 ? "Còn " + variant.getQuantity() + " sản phẩm" : "Hết hàng"%></option>
                        <% } %>
                    </select>
                </div>
                <p id="variantStock" class="product-meta" role="status"><%=variants == null || variants.isEmpty() ? "Sản phẩm hiện chưa có phiên bản bán hàng." : "Chọn phiên bản để xem tình trạng còn hàng."%></p>
                <div class="inline-form"><div class="grow"><label class="form-label" for="quantity">Số lượng</label><input class="field" id="quantity" name="quantity" type="number" min="1" max="99" value="1" required></div><button class="button" id="addVariantToCart" type="submit" <%=variants == null || variants.isEmpty() ? "disabled" : ""%>>Thêm vào giỏ　↗</button></div>
            </form>
            <p class="product-meta" style="margin-top:15px">Đơn từ 799.000₫ miễn phí giao hàng toàn quốc · Đơn dưới mức này tính phí tham khảo theo khu vực J&amp;T và số lượng sản phẩm</p>
            <p><a class="button line small" href="<%=request.getContextPath()%>/collections?productId=<%=product.getProductID()%>">Gợi ý phối cùng sản phẩm này ↗</a></p>
        </div>
    </div>
    <section class="section"><div class="section-head"><div><span class="eyebrow">Khách hàng chia sẻ</span><h2>Đánh giá sản phẩm</h2></div><div class="stars">★ <%=request.getAttribute("averageRating") == null ? "0" : request.getAttribute("averageRating")%> / 5 · <%=request.getAttribute("reviewCount") == null ? "0" : request.getAttribute("reviewCount")%> đánh giá</div></div><div class="review-list">
        <% if (reviews == null || reviews.isEmpty()) { %><div class="review"><p>Chưa có đánh giá cho sản phẩm này. Hãy là người đầu tiên chia sẻ cảm nhận của bạn.</p></div>
        <% } else { for (ProductReview review : reviews) { %><article class="review"><div class="stars"><%="★".repeat(Math.max(0, Math.min(5, review.getRating())))%><%="☆".repeat(Math.max(0, 5 - review.getRating()))%></div><b><%=navEscape(review.getCustomerName() == null ? "Khách hàng MENSFORM" : review.getCustomerName())%></b><p><%=navEscape(review.getComment())%></p></article><% } } %>
    </div></section>
</div></main><%@include file="/includes/footer.jspf"%>
<script src="<%=request.getContextPath()%>/assets/product-images.js" defer></script>
<script src="<%=request.getContextPath()%>/assets/product-variants.js" defer></script>
</body></html>
