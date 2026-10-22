<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Set,model.OutfitCollection,model.OutfitCollection.Piece,model.OutfitItem,model.OutfitItem.Role,service.OutfitRecommendationService.Style"%>
<%@include file="/includes/collection-admin-helpers.jspf"%>
<%
boolean warehouseMode=Boolean.TRUE.equals(request.getAttribute("warehouseMode"));
String collectionBase=(String)request.getAttribute("collectionBase");
OutfitCollection collectionDraft=(OutfitCollection)request.getAttribute("collectionDraft");
List<OutfitItem> catalogOptions=(List<OutfitItem>)request.getAttribute("catalogOptions");
Set<String> availableKeys=(Set<String>)request.getAttribute("availableKeys");
Style[] outfitStyles=(Style[])request.getAttribute("outfitStyles");
Role[] defaultRoles={Role.TOP,Role.BOTTOM,Role.SHOES,Role.ACCESSORY,Role.LAYER,Role.ACCESSORY};
%>
<!DOCTYPE html><html lang="vi"><head>
<meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title><%=collectionDraft.getId()>0?"Chỉnh sửa":"Tạo"%> bộ sưu tập · MENSFORM</title>
<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
<link rel="stylesheet" href="<%=request.getContextPath()%>/assets/collection-manager.css">
</head><body>
<% if(warehouseMode){ %><div class="portal-layout"><%@include file="/staff/includes/nav.jspf"%><main class="portal-main">
<% }else{ %><div class="admin-layout"><%@include file="/admin/includes/sidebar.jspf"%><main class="admin-main"><% } %>
<div class="admin-topline"><div><small>CATALOG / COLLECTIONS</small><h1 class="admin-title"><%=collectionDraft.getId()>0?"Chỉnh sửa":"Tạo"%> bộ sưu tập</h1></div><a class="button line small" href="<%=collectionBase%>/collections">← Danh sách</a></div>
<p class="admin-subtitle">Chọn sản phẩm, màu phối và giải thích cách kết hợp. Khách sẽ tự chọn size khi mua từng món.</p>
<% if(request.getAttribute("error")!=null){ %><div class="alert error" role="alert"><%=caEscape(request.getAttribute("error"))%></div><% } %>
<form action="<%=collectionBase%>/collection-form" method="post">
<input type="hidden" name="csrfToken" value="<%=caEscape(request.getAttribute("csrfToken"))%>">
<input type="hidden" name="id" value="<%=collectionDraft.getId()%>">
<input type="hidden" name="revision" value="<%=caEscape(collectionDraft.getRevision())%>">
<section class="admin-panel"><h2>Thông tin bộ sưu tập</h2><div class="admin-form-grid">
<div class="span-2"><label class="form-label" for="collectionTitle">Tên bộ sưu tập *</label><input class="field" id="collectionTitle" name="title" required maxlength="120" placeholder="Ví dụ: Cuối tuần cùng sắc đen và denim" value="<%=caEscape(collectionDraft.getTitle())%>"></div>
<div><label class="form-label" for="collectionStyle">Dịp sử dụng *</label><select class="select" id="collectionStyle" name="style" required>
<% for(Style style:outfitStyles){ %><option value="<%=style.getId()%>" <%=style.getId().equals(collectionDraft.getStyle())?"selected":""%>><%=caEscape(style.getLabel())%></option><% } %></select></div>
<div><label class="form-label" for="collectionStatus">Hiển thị *</label><select class="select" id="collectionStatus" name="status" required>
<option value="Draft" <%="Draft".equals(collectionDraft.getStatus())?"selected":""%>>Bản nháp — chỉ nhân viên xem</option>
<option value="Published" <%="Published".equals(collectionDraft.getStatus())?"selected":""%>>Công khai cho khách hàng</option>
<option value="Archived" <%="Archived".equals(collectionDraft.getStatus())?"selected":""%>>Ẩn khỏi trang khách hàng</option></select></div>
<div class="span-2"><label class="form-label" for="collectionDescription">Mô tả</label><textarea class="field" id="collectionDescription" name="description" maxlength="1000" rows="3" placeholder="Phong cách và hoàn cảnh phù hợp của bộ đồ"><%=caEscape(collectionDraft.getDescription())%></textarea></div>
<div class="span-2"><label class="form-label" for="collectionNotes">Vì sao phối set này?</label><textarea class="field" id="collectionNotes" name="stylingNotes" maxlength="1000" rows="4" placeholder="Mỗi dòng một gợi ý: phối màu, kiểu dáng hoặc cách dùng phụ kiện"><%=caEscape(collectionDraft.getStylingNotes())%></textarea></div>
</div></section>
<section class="admin-panel"><h2>Các món trong bộ sưu tập</h2><p class="admin-note">Tối đa 6 sản phẩm, mỗi sản phẩm chọn một màu. Để công khai, cần ít nhất áo và quần còn hàng. Chọn mục trống để bỏ món khỏi bộ.</p>
<div class="collection-piece-list">
<% for(int row=0;row<6;row++){Piece selected=row<collectionDraft.getPieces().size()?collectionDraft.getPieces().get(row):null;String selectedKey=selected==null?"":selected.getKey();Role selectedRole=selected==null?defaultRoles[row]:selected.getRole();boolean optionExists=false;for(OutfitItem option:catalogOptions)if(option.getKey().equals(selectedKey))optionExists=true; %>
<div class="collection-piece-row">
<div class="collection-piece-photo"><img data-piece-preview <%=selected==null?"hidden":""%> src="<%=request.getContextPath()%>/product-image?productId=<%=selected==null?0:selected.getProductId()%>&amp;color=<%=selected==null?"":caColor(selected.getColor())%>" alt="Ảnh món phối" width="80" height="100" loading="lazy"></div>
<div><label class="form-label" for="piece<%=row%>">Món <%=row+1%> · Sản phẩm / màu</label>
<select class="select" name="piece" id="piece<%=row%>" data-piece-select data-image-base="<%=request.getContextPath()%>/product-image"><option value="">— Không chọn —</option>
<% if(selected!=null && !optionExists){ %><option selected value="<%=selected.getProductId()%>|<%=caColor(selected.getColor())%>">#<%=selected.getProductId()%> · <%=caEscape(selected.getColor())%> — không còn biến thể, hãy chọn lại</option><% } %>
<% for(OutfitItem option:catalogOptions){boolean inStock=availableKeys.contains(option.getKey()); %>
<option value="<%=option.getProductId()%>|<%=caColor(option.getColor())%>" data-role="<%=option.getRole().name()%>" <%=option.getKey().equals(selectedKey)?"selected":""%>>#<%=option.getProductId()%> · <%=caEscape(option.getName())%> · <%=caEscape(option.getColor())%> (<%=caEscape(option.getCategory())%>)<%=inStock?"":" — hết hàng / ngừng bán"%></option>
<% } %></select></div>
<div><label class="form-label" for="role<%=row%>">Vai trò phối</label><select class="select" name="role" id="role<%=row%>" data-piece-role>
<% for(Role role:Role.values()){if(role==Role.UNKNOWN)continue; %><option value="<%=role.name()%>" <%=role==selectedRole?"selected":""%>><%=caEscape(caRole(role))%></option><% } %></select></div>
</div><% } %></div>
<p class="admin-note">Ảnh dùng theo màu đã chọn; nếu chưa có ảnh màu riêng sẽ dùng ảnh đại diện. Giá được lấy theo size còn hàng tại lúc khách xem.</p>
</section>
<div class="portal-actions"><button class="button" type="submit">Lưu bộ sưu tập</button><a class="button line" href="<%=collectionBase%>/collections">Quay lại</a></div>
</form></main></div><script src="<%=request.getContextPath()%>/assets/collection-manager.js" defer></script></body></html>
