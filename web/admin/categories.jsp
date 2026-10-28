<%-- 
    Document   : categories
    Created on : Oct 9, 2026, 3:28:50 PM
    Author     : ASUS
--%>

<%@page contentType="text/html" pageEncoding="UTF-8" import="java.util.List,model.ProductCategory"%>
<%!
    private String categoryEscape(Object value) {
        return value == null ? "" : value.toString().replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Danh mục · <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%> Admin</title>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    </head>
    <body><div class="admin-layout"><%@include file="/admin/includes/sidebar.jspf"%><main class="admin-main">
        <% List<ProductCategory> categories=(List<ProductCategory>)request.getAttribute("categories"); %>
        <div class="admin-topline"><div><small>CATALOG / CATEGORIES</small><h1 class="admin-title">Danh mục sản phẩm</h1></div><a class="button line small" href="<%=request.getContextPath()%>/admin/products">← Sản phẩm</a></div><p class="admin-subtitle">Tạo và cập nhật nhóm sản phẩm hiển thị trong cửa hàng.</p>
        <% if(request.getAttribute("message")!=null){ %><div class="alert success"><%=request.getAttribute("message")%></div><% } if(request.getAttribute("error")!=null){ %><div class="alert error"><%=request.getAttribute("error")%></div><% } %>
        <section class="admin-panel"><h2>Thêm danh mục</h2><form class="admin-form-grid" action="<%=request.getContextPath()%>/admin/categories" method="post"><input type="hidden" name="action" value="create"><div><label class="form-label" for="newCategoryName">Tên danh mục</label><input class="field" id="newCategoryName" name="categoryName" maxlength="100" required></div><div><label class="form-label" for="newCategoryDescription">Mô tả</label><input class="field" id="newCategoryDescription" name="description" maxlength="500" placeholder="Mô tả ngắn, không bắt buộc"></div><div class="span-2"><button class="button small" type="submit">＋ Thêm danh mục</button></div></form></section>
        <section class="admin-panel">
            <div class="admin-toolbar"><h2>Danh sách danh mục</h2><span class="admin-note"><%=categories==null?0:categories.size()%> danh mục</span></div>
            <form action="<%=request.getContextPath()%>/admin/categories" method="post">
                <input type="hidden" name="action" value="update-all">
                <div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>Mã</th><th>Tên &amp; mô tả</th><th>Trạng thái</th></tr></thead><tbody>
                <% if(categories==null||categories.isEmpty()){ %>
                    <tr><td colspan="3" class="admin-empty">Chưa có danh mục.</td></tr>
                <% } else { for(ProductCategory category:categories){ %>
                    <tr>
                        <td>#<%=category.getCategoryID()%><input type="hidden" name="categoryId" value="<%=category.getCategoryID()%>"></td>
                        <td><div class="admin-row-form">
                            <input class="field" name="categoryName" maxlength="100" required value="<%=categoryEscape(category.getCategoryName())%>" aria-label="Tên danh mục">
                            <input class="field" name="description" maxlength="500" value="<%=categoryEscape(category.getDescription())%>" placeholder="Mô tả" aria-label="Mô tả">
                        </div></td>
                        <td><select class="select" name="status"><option value="Active" <%= "Active".equalsIgnoreCase(category.getStatus())?"selected":"" %>>Đang hoạt động</option><option value="Inactive" <%= "Inactive".equalsIgnoreCase(category.getStatus())?"selected":"" %>>Đã tắt</option></select></td>
                    </tr>
                <% }} %>
                </tbody></table></div>
                <% if(categories!=null&&!categories.isEmpty()){ %><div class="admin-toolbar"><span class="admin-note">Chỉnh sửa nhiều dòng rồi lưu cùng lúc.</span><button class="button" type="submit">Lưu tất cả thay đổi</button></div><% } %>
            </form>
        </section>
    </main></div>
    </body>
</html>
