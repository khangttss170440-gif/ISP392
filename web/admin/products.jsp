<%-- 
    Document   : products
    Created on : Oct 9, 2026, 3:28:36 PM
    Author     : ASUS
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Sản phẩm · <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%> Admin</title>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/product-images.css"></head>
    <body><div class="admin-layout"><%@include file="/admin/includes/sidebar.jspf"%><main class="admin-main">
        <%@page import="java.util.List,model.Product"%><% List<Product> products=(List<Product>)request.getAttribute("products"); %>
        <div class="admin-topline"><div><small>CATALOG / PRODUCTS</small><h1 class="admin-title">Sản phẩm</h1></div><a class="button" href="<%=request.getContextPath()%>/admin/product-form">＋ Tạo sản phẩm</a></div><p class="admin-subtitle">Quản lý thông tin sản phẩm và các biến thể bán hàng.</p>
        <% if(request.getAttribute("message")!=null){ %><div class="alert success"><%=request.getAttribute("message")%></div><% } if(request.getAttribute("error")!=null){ %><div class="alert error"><%=request.getAttribute("error")%></div><% } %>
        <section class="admin-panel"><div class="admin-toolbar"><h2>Danh sách sản phẩm</h2><span class="admin-note"><%=products==null?0:products.size()%> sản phẩm</span></div><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>Mã</th><th>Sản phẩm</th><th>Danh mục</th><th>Thương hiệu / Chất liệu</th><th>Trạng thái</th><th>Thao tác</th></tr></thead><tbody><% if(products==null||products.isEmpty()){ %><tr><td colspan="6" class="admin-empty">Chưa có sản phẩm.</td></tr><% } else { for(Product product:products){ %><tr><td>#<%=product.getProductID()%></td><td><img class="admin-product-thumb" src="<%=request.getContextPath()%>/product-image?productId=<%=product.getProductID()%>" alt="Ảnh sản phẩm" loading="lazy"><br><b><%=product.getProductName()%></b><div class="admin-note"><%=product.getDescription()==null?"":product.getDescription()%></div></td><td><%=product.getCategoryName()%></td><td><%=product.getBrand()==null?"—":product.getBrand()%><div class="admin-note"><%=product.getMaterial()==null?"":product.getMaterial()%></div></td><td><span class="admin-badge <%= "Active".equalsIgnoreCase(product.getStatus())?"":"off"%>"><%=product.getStatus()%></span></td><td><div class="admin-actions"><a class="button line small" href="<%=request.getContextPath()%>/admin/product-form?productId=<%=product.getProductID()%>">Sửa</a><a class="button line small" href="<%=request.getContextPath()%>/admin/product-images?productId=<%=product.getProductID()%>">Ảnh</a><a class="button line small" href="<%=request.getContextPath()%>/admin/variants?productId=<%=product.getProductID()%>">SKU</a><form action="<%=request.getContextPath()%>/admin/products" method="post"><input type="hidden" name="productId" value="<%=product.getProductID()%>"><input type="hidden" name="status" value="<%= "Active".equalsIgnoreCase(product.getStatus())?"Inactive":"Active"%>"><button class="button small" type="submit"><%= "Active".equalsIgnoreCase(product.getStatus())?"Tắt":"Bật"%></button></form></div></td></tr><% }} %></tbody></table></div></section>
    </main></div>
    </body>
</html>
