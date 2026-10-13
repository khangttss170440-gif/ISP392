<%-- 
    Document   : categories
    Created on : Oct 9, 2026, 3:28:50 PM
    Author     : ASUS
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Danh mục · MENSFORM Admin</title>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    </head>
    <body><div class="admin-layout"><%@include file="/admin/includes/sidebar.jspf"%><main class="admin-main">
        <%@page import="java.util.List,model.ProductCategory"%><% List<ProductCategory> categories=(List<ProductCategory>)request.getAttribute("categories"); %>
        <div class="admin-topline"><div><small>CATALOG / CATEGORIES</small><h1 class="admin-title">Danh mục sản phẩm</h1></div><a class="button line small" href="<%=request.getContextPath()%>/admin/products">← Sản phẩm</a></div><p class="admin-subtitle">Tạo và cập nhật nhóm sản phẩm hiển thị trong cửa hàng.</p>
        <% if(request.getAttribute("message")!=null){ %><div class="alert success"><%=request.getAttribute("message")%></div><% } if(request.getAttribute("error")!=null){ %><div class="alert error"><%=request.getAttribute("error")%></div><% } %>
        <section class="admin-panel"><h2>Thêm danh mục</h2><form class="admin-form-grid" action="<%=request.getContextPath()%>/admin/categories" method="post"><input type="hidden" name="action" value="create"><div><label class="form-label" for="newCategoryName">Tên danh mục</label><input class="field" id="newCategoryName" name="categoryName" maxlength="100" required></div><div><label class="form-label" for="newCategoryDescription">Mô tả</label><input class="field" id="newCategoryDescription" name="description" maxlength="500" placeholder="Mô tả ngắn, không bắt buộc"></div><div class="span-2"><button class="button small" type="submit">＋ Thêm danh mục</button></div></form></section>
        <section class="admin-panel"><div class="admin-toolbar"><h2>Danh sách danh mục</h2><span class="admin-note"><%=categories==null?0:categories.size()%> danh mục</span></div><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>Mã</th><th>Tên & mô tả</th><th>Trạng thái</th><th>Cập nhật</th></tr></thead><tbody><% if(categories==null||categories.isEmpty()){ %><tr><td colspan="4" class="admin-empty">Chưa có danh mục.</td></tr><% } else { for(ProductCategory category:categories){ String formId="category-"+category.getCategoryID(); %><tr><td>#<%=category.getCategoryID()%><form id="<%=formId%>" action="<%=request.getContextPath()%>/admin/categories" method="post"><input type="hidden" name="action" value="update"><input type="hidden" name="categoryId" value="<%=category.getCategoryID()%>"></form></td><td><div class="admin-row-form"><input form="<%=formId%>" class="field" name="categoryName" maxlength="100" value="<%=category.getCategoryName()%>" aria-label="Tên danh mục"><input form="<%=formId%>" class="field" name="description" maxlength="500" value="<%=category.getDescription()==null?"":category.getDescription()%>" placeholder="Mô tả" aria-label="Mô tả"></div></td><td><select form="<%=formId%>" class="select" name="status"><option value="Active" <%= "Active".equalsIgnoreCase(category.getStatus())?"selected":"" %>>Đang hoạt động</option><option value="Inactive" <%= "Inactive".equalsIgnoreCase(category.getStatus())?"selected":"" %>>Đã tắt</option></select></td><td><button form="<%=formId%>" class="button small" type="submit">Lưu</button></td></tr><% }} %></tbody></table></div></section>
    </main></div>
    </body>
</html>
