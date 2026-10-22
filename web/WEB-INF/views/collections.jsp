<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Collection,java.util.Locale,java.math.BigDecimal,java.text.NumberFormat,java.net.URLEncoder,java.nio.charset.StandardCharsets,model.OutfitItem,model.OutfitSet,service.OutfitRecommendationService.Style"%>
<%!
private String collectionEscape(Object value) {
    return value==null?"":value.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
}
private String collectionColor(String color){return URLEncoder.encode(color,StandardCharsets.UTF_8);}
private String collectionUrl(String context,String source,String style,BigDecimal budget,Integer product) {
    return context+"/collections?source="+source+"&style="+style+(budget==null?"":"&budget="+budget.toPlainString())+(product==null?"":"&productId="+product)+"#outfitResults";
}
%>
<%
List<OutfitSet> outfits=(List<OutfitSet>)request.getAttribute("outfits");
Collection<OutfitItem> outfitProducts=(Collection<OutfitItem>)request.getAttribute("outfitProducts");
Style[] outfitStyles=(Style[])request.getAttribute("outfitStyles");
String selectedStyle=(String)request.getAttribute("selectedStyle");
String selectedSource=(String)request.getAttribute("selectedSource");
boolean curated="curated".equals(selectedSource);
BigDecimal selectedBudget=(BigDecimal)request.getAttribute("selectedBudget");
Integer selectedProductId=(Integer)request.getAttribute("selectedProductId");
NumberFormat outfitMoney=NumberFormat.getInstance(new Locale("vi","VN"));
boolean unavailable=Boolean.TRUE.equals(request.getAttribute("collectionsUnavailable"));
%>
<!DOCTYPE html>
<html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Bộ sưu tập phối đồ — MENSFORM</title>
    <meta name="description" content="Gợi ý phối áo, quần và phụ kiện từ sản phẩm MENSFORM còn hàng. Chọn set theo dịp sử dụng và ngân sách.">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/collections.css">
</head><body class="collections-page">
<%@include file="/includes/header.jspf"%>
<main>
    <section class="collection-intro">
        <div class="shell collection-intro-grid">
            <div>
                <div class="breadcrumb"><a href="<%=request.getContextPath()%>/index.jsp">Trang chủ</a> / Bộ sưu tập</div>
                <span class="eyebrow">MENSFORM · PHỐI ĐỒ</span>
                <h1>Bộ sưu tập<br><span>phối theo dịp.</span></h1>
                <p>Khám phá những bộ áo, quần và phụ kiện do cửa hàng lựa chọn. Tìm cách phối theo dịp sử dụng, mức chi hoặc một món đồ bạn thích.</p>
                <a class="button" href="#outfitFilters">Tìm set phù hợp <span aria-hidden="true">↓</span></a>
            </div>
            <aside class="collection-how" aria-label="Cách chọn một set đồ">
                <span class="collection-small-title">MẶC ĐẸP, CHỌN DỄ HƠN</span>
                <ol>
                    <li><span>01</span><div><b>Chọn dịp</b><p>Đi làm, đi chơi hay một buổi hẹn.</p></div></li>
                    <li><span>02</span><div><b>Xem cách phối</b><p>Cân đối màu sắc, kiểu dáng và mức giá.</p></div></li>
                    <li><span>03</span><div><b>Chọn size từng món</b><p>Xem chi tiết rồi thêm món phù hợp vào giỏ.</p></div></li>
                </ol>
            </aside>
        </div>
    </section>
    <section class="shell collection-content" aria-label="Gợi ý bộ sưu tập">
        <nav class="collection-sources" aria-label="Nguồn bộ sưu tập">
            <a class="<%=curated?"is-active":""%>" href="<%=collectionEscape(collectionUrl(request.getContextPath(),"curated",selectedStyle,selectedBudget,selectedProductId))%>" <%=curated?"aria-current=\"page\"":""%>>Bộ sưu tập của cửa hàng</a>
            <a class="<%=curated?"":"is-active"%>" href="<%=collectionEscape(collectionUrl(request.getContextPath(),"suggested",selectedStyle,selectedBudget,selectedProductId))%>" <%=curated?"":"aria-current=\"page\""%>>Gợi ý phối tự động</a>
        </nav>
        <div class="collection-style-grid" aria-label="Chọn dịp sử dụng">
            <% for(Style style:outfitStyles){ %>
            <a class="collection-style <%=style.getId().equals(selectedStyle)?"is-active":""%>" href="<%=collectionEscape(collectionUrl(request.getContextPath(),selectedSource,style.getId(),selectedBudget,selectedProductId))%>" <%=style.getId().equals(selectedStyle)?"aria-current=\"true\"":""%>>
                <span><%=collectionEscape(style.getLabel())%> <span aria-hidden="true">↗</span></span>
                <b><%=collectionEscape(style.getTitle())%></b>
            </a>
            <% } %>
        </div>
        <form id="outfitFilters" class="collection-filters" action="<%=request.getContextPath()%>/collections#outfitResults" method="get">
            <input type="hidden" name="source" value="<%=selectedSource%>">
            <div><label class="form-label" for="outfitStyle">Dịp sử dụng</label>
                <select class="select" id="outfitStyle" name="style">
                    <option value="all" <%="all".equals(selectedStyle)?"selected":""%>>Tất cả dịp</option>
                    <% for(Style style:outfitStyles){ %><option value="<%=style.getId()%>" <%=style.getId().equals(selectedStyle)?"selected":""%>><%=collectionEscape(style.getLabel())%></option><% } %>
                </select>
            </div>
            <div><label class="form-label" for="outfitBudget">Ngân sách tối đa / set (₫)</label>
                <input class="field" id="outfitBudget" name="budget" type="number" min="1" max="50000000" step="1" inputmode="numeric" placeholder="Ví dụ: 1000000" value="<%=selectedBudget==null?"":selectedBudget.toPlainString()%>">
            </div>
            <div><label class="form-label" for="outfitProduct">Phối cùng sản phẩm</label>
                <select class="select" id="outfitProduct" name="productId">
                    <option value="">Để cửa hàng gợi ý</option>
                    <% if(Boolean.TRUE.equals(request.getAttribute("selectedProductUnavailable"))){ %><option value="<%=selectedProductId%>" selected>Sản phẩm không còn phù hợp để phối</option><% } %>
                    <% for(OutfitItem product:outfitProducts){ %><option value="<%=product.getProductId()%>" <%=Integer.valueOf(product.getProductId()).equals(selectedProductId)?"selected":""%>><%=collectionEscape(product.getName())%> · <%=collectionEscape(product.getRoleLabel())%></option><% } %>
                </select>
            </div>
            <div class="collection-filter-actions"><button class="button" type="submit">Tìm bộ phù hợp</button><a href="<%=request.getContextPath()%>/collections?source=<%=selectedSource%>">Xóa bộ lọc</a></div>
        </form>
        <div class="collection-results-head" id="outfitResults">
            <div><span class="eyebrow"><%=curated?"CỬA HÀNG LỰA CHỌN":"GỢI Ý THEO MÀU VÀ KIỂU DÁNG"%></span><h2>Những món hợp nhau.</h2></div>
            <p><%=outfits.size()%> set phù hợp<%=unavailable?"":" · Chỉ gồm màu/size còn hàng"%></p>
        </div>
        <% if(unavailable){ %>
        <div class="collection-empty" role="alert"><h2>Chưa tải được bộ sưu tập</h2><p>Kết nối đến kho hàng đang gián đoạn. Vui lòng thử lại sau.</p><a class="button" href="<%=request.getContextPath()%>/collections">Thử lại</a></div>
        <% }else if(outfits.isEmpty()){ %>
        <div class="collection-empty">
            <span class="eyebrow">THỬ MỘT LỰA CHỌN KHÁC</span><h2>Chưa có set phù hợp</h2>
            <p><%=Boolean.TRUE.equals(request.getAttribute("selectedProductUnavailable"))?"Sản phẩm đã chọn không còn màu/size khả dụng. Bạn có thể chọn món khác.":curated?"Chưa có bộ sưu tập công khai, còn đủ hàng và khớp bộ lọc. Bạn có thể đổi bộ lọc hoặc xem các gợi ý phối tự động.":!Boolean.TRUE.equals(request.getAttribute("hasOutfitBasics"))?"Cửa hàng hiện chưa đủ cả áo và quần còn hàng để ghép thành set. Bạn có thể khám phá các sản phẩm đang có.":"Bạn có thể tăng ngân sách, đổi dịp sử dụng hoặc bỏ sản phẩm đang chọn. Các set chỉ được tạo khi có áo và quần phù hợp còn hàng."%></p>
            <div class="collection-empty-actions"><a class="button" href="<%=request.getContextPath()%>/collections?source=<%=selectedSource%>">Xóa bộ lọc</a><a class="button line" href="<%=request.getContextPath()%>/collections?source=suggested">Gợi ý phối tự động</a><a class="button line" href="<%=request.getContextPath()%>/products">Khám phá sản phẩm</a></div>
        </div>
        <% }else{ %>
        <div class="outfit-grid">
            <% int outfitIndex=0;for(OutfitSet outfit:outfits){outfitIndex++;String styleLabel="";for(Style style:outfitStyles)if(style.getId().equals(outfit.getStyle()))styleLabel=style.getLabel(); %>
            <article class="outfit-card" aria-labelledby="outfitTitle<%=outfitIndex%>">
                <div class="outfit-card-top"><span>LOOK <%=String.format("%02d",outfitIndex)%></span><span><%=collectionEscape(styleLabel)%> · <%=outfit.getItems().size()%> món</span></div>
                <div class="outfit-visual">
                    <% for(OutfitItem item:outfit.getItems()){String colorQuery=collectionColor(item.getColor()); %>
                    <a class="outfit-photo" href="<%=request.getContextPath()%>/product-detail?productId=<%=item.getProductId()%>&amp;color=<%=colorQuery%>" aria-label="<%=collectionEscape(item.getName()+", màu "+item.getColor())%>">
                        <img src="<%=request.getContextPath()%>/product-image?productId=<%=item.getProductId()%>&amp;color=<%=colorQuery%>" alt="<%=collectionEscape(item.getName()+" · "+item.getColor())%>" loading="lazy" decoding="async" width="400" height="500">
                        <span><%=collectionEscape(item.getRoleLabel())%></span>
                    </a>
                    <% } %>
                </div>
                <div class="outfit-copy">
                    <h3 id="outfitTitle<%=outfitIndex%>"><%=collectionEscape(outfit.getTitle())%></h3>
                    <p class="outfit-description"><%=collectionEscape(outfit.getDescription())%></p>
                    <ul class="outfit-items">
                        <% for(OutfitItem item:outfit.getItems()){ %>
                        <li><div><a href="<%=request.getContextPath()%>/product-detail?productId=<%=item.getProductId()%>&amp;color=<%=collectionColor(item.getColor())%>"><%=collectionEscape(item.getName())%> <span aria-hidden="true">↗</span></a>
                            <small><%=collectionEscape(item.getColor())%> · Size <%=collectionEscape(item.getSizesLabel())%></small></div><b>Từ <%=outfitMoney.format(item.getFromPrice())%>₫</b></li>
                        <% } %>
                    </ul>
                    <details class="outfit-reasons"><summary>Vì sao phối set này?</summary><ul><% for(String reason:outfit.getReasons()){ %><li><%=collectionEscape(reason)%></li><% } %></ul></details>
                    <% if(!outfit.getMissingNote().isEmpty()){ %><p class="outfit-missing"><%=collectionEscape(outfit.getMissingNote())%></p><% } %>
                    <div class="outfit-total"><div><small>Tổng giá tham khảo từ</small><strong><%=outfitMoney.format(outfit.getFromPrice())%>₫</strong></div><span>Chọn size từng món<br>ở trang sản phẩm</span></div>
                </div>
            </article>
            <% } %>
        </div>
        <% } %>
        <aside class="collection-price-note"><b>Lưu ý khi chọn set</b><p>Giá từ được cộng theo màu gợi ý và size còn hàng có giá thấp nhất, chưa gồm phí giao hàng hay khuyến mãi. Giá và tồn kho được kiểm tra lại khi mua. Ảnh ưu tiên màu gợi ý; nếu chưa có ảnh riêng, ảnh đại diện được dùng thay thế.</p></aside>
    </section>
</main>
<%@include file="/includes/footer.jspf"%>
</body></html>
