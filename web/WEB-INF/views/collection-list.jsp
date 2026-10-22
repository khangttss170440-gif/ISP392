<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Set,model.OutfitCollection"%>
<%@include file="/includes/collection-admin-helpers.jspf"%>
<%
boolean warehouseMode=Boolean.TRUE.equals(request.getAttribute("warehouseMode"));
String collectionBase=(String)request.getAttribute("collectionBase");
List<OutfitCollection> managedCollections=(List<OutfitCollection>)request.getAttribute("managedCollections");
Set<String> availableKeys=(Set<String>)request.getAttribute("availableKeys");
%>
<!DOCTYPE html><html lang="vi"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Quản lý bộ sưu tập · MENSFORM</title>
<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/collection-manager.css">
</head><body>
<% if(warehouseMode){ %><div class="portal-layout"><%@include file="/staff/includes/nav.jspf"%><main class="portal-main">
<% }else{ %><div class="admin-layout"><%@include file="/admin/includes/sidebar.jspf"%><main class="admin-main"><% } %>
<div class="admin-topline"><div><small>CATALOG / COLLECTIONS</small><h1 class="admin-title">Bộ sưu tập phối đồ</h1></div><a class="button" href="<%=collectionBase%>/collection-form">+ Tạo bộ sưu tập</a></div>
<p class="admin-subtitle">Admin và nhân viên Kho cùng chọn áo, quần, phụ kiện để gợi ý cho khách hàng.</p>
<% if(request.getAttribute("message")!=null){ %><div class="alert success" role="status"><%=caEscape(request.getAttribute("message"))%></div><% } %>
<section class="admin-panel">
<div class="admin-toolbar"><h2>Các bộ sưu tập của cửa hàng</h2><a class="button line small" href="<%=request.getContextPath()%>/collections" target="_blank" rel="noopener">Xem trang khách hàng ↗</a></div>
<div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>Bộ sưu tập</th><th>Dịp sử dụng</th><th>Trạng thái</th><th>Sản phẩm còn hàng</th><th>Người cập nhật</th><th>Thao tác</th></tr></thead><tbody>
<% if(managedCollections.isEmpty()){ %><tr><td colspan="6" class="admin-empty">Chưa có bộ sưu tập. Chọn “Tạo bộ sưu tập” để bắt đầu.</td></tr><% } %>
<% for(OutfitCollection collection:managedCollections){long count=collection.getPieces().stream().filter(p->availableKeys.contains(p.getKey())).count(); %>
<tr><td><b><%=caEscape(collection.getTitle())%></b><br><small>#<%=collection.getId()%> · <%=collection.getPieces().size()%> món</small></td>
<td><%=caEscape(caStyle(collection.getStyle()))%></td>
<td><span class="admin-badge <%=!"Published".equals(collection.getStatus())?"off":""%>"><%=caEscape(collection.getStatusLabel())%></span>
<% if("Published".equals(collection.getStatus()) && count!=collection.getPieces().size()){ %><p class="collection-stock-warning">Tạm ẩn trên trang khách do thiếu hàng.</p><% } %></td>
<td><%=count%> / <%=collection.getPieces().size()%></td><td><%=caEscape(collection.getAuthor())%><br><small><%=caEscape(collection.getUpdatedAt().toLocalDateTime().atOffset(java.time.ZoneOffset.UTC).atZoneSameInstant(java.time.ZoneId.of("Asia/Ho_Chi_Minh")).format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")))%></small></td>
<td><a class="button line small" href="<%=collectionBase%>/collection-form?id=<%=collection.getId()%>">Chỉnh sửa / ẩn</a></td></tr>
<% } %></tbody></table></div>
<p class="admin-note">Bản nháp và bộ đã ẩn chỉ hiển thị ở đây. Bộ công khai có món hết hàng hoặc ngừng bán sẽ tạm ẩn với khách, và xuất hiện lại khi đủ hàng.</p>
</section></main></div></body></html>
