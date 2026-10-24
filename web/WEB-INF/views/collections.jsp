<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Collection,java.util.Locale,java.math.BigDecimal,java.text.NumberFormat,java.net.URLEncoder,java.nio.charset.StandardCharsets,model.OutfitItem,model.OutfitSet,model.ProductVariant,dao.ProductCatalogDAO,java.util.Map,java.util.HashMap,java.util.ArrayList,service.OutfitRecommendationService.Style"%>
<%!
private String collectionEscape(Object value) {
    return value==null?"":value.toString().replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&#39;");
}
private String collectionColor(String color){return URLEncoder.encode(color,StandardCharsets.UTF_8);}
private String collectionUrl(String context,String source,String style,BigDecimal budget,Integer product) {
    return context+"/collections?source="+source+"&style="+style+(budget==null?"":"&budget="+budget.toPlainString())+(product==null?"":"&productId="+product)+"#outfitResults";
}
/** Variants of one product/colour that can be bought now, sizes in wearing order. */
private List<ProductVariant> collectionVariants(Map<Integer,List<ProductVariant>> cache,int productId,String color){
    List<ProductVariant> all=cache.computeIfAbsent(productId,id->{try{return new ProductCatalogDAO().getActiveVariantsByProduct(id);}catch(Exception e){return new ArrayList<>();}});
    List<ProductVariant> out=new ArrayList<>();
    for(ProductVariant v:all) if(v.getQuantity()>0 && color.equalsIgnoreCase(v.getColor()==null?"":v.getColor().trim())) out.add(v);
    List<String> order=java.util.Arrays.asList("XS","S","M","L","XL","XXL","3XL");
    out.sort((a,b)->{int x=order.indexOf(a.getSize()),y=order.indexOf(b.getSize());return x>=0&&y>=0?x-y:String.valueOf(a.getSize()).compareTo(String.valueOf(b.getSize()));});
    return out;
}
private String collectionPhoto(String styleId){
    if("office".equals(styleId))return "tie";
    if("weekend".equals(styleId))return "look-work";
    if("evening".equals(styleId))return "cat-sweaters";
    return "look-weekend";
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
Map<Integer,List<ProductVariant>> collectionVariantCache=new HashMap<>();
%>
<!DOCTYPE html>
<html lang="vi"><head>
    <meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
    <title>Bộ sưu tập phối đồ — MENSFORM</title>
    <meta name="description" content="Gợi ý phối áo, quần và phụ kiện từ sản phẩm MENSFORM còn hàng. Chọn set theo dịp sử dụng và ngân sách.">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/mensform-collections.css?v=20261024f">
</head><body class="collections-page cx">
<%@include file="/includes/header.jspf"%>
<main>
    <section class="cx-hero">
        <div class="cx-wrap cx-hero-grid">
            <div class="cx-hero-copy">
                <div class="breadcrumb"><a href="<%=request.getContextPath()%>/index.jsp">Trang chủ</a> / Bộ sưu tập</div>
                <span class="eyebrow">Lookbook · Thu Đông 2026</span>
                <h1>Bộ sưu tập<em>phối theo dịp.</em></h1>
                <p>Những bộ áo, quần và phụ kiện được ghép sẵn. Chọn dịp, đặt mức chi hoặc bắt đầu từ một món bạn thích, phần còn lại để MENSFORM lo.</p>
                <div class="cx-hero-actions">
                    <a class="cx-btn" href="#outfitFilters">Tìm set phù hợp <span aria-hidden="true">↓</span></a>
                    <a class="cx-link" href="<%=collectionEscape(collectionUrl(request.getContextPath(),"suggested",selectedStyle,selectedBudget,selectedProductId))%>">Xem gợi ý tự động</a>
                </div>
                <ol class="cx-steps" aria-label="Cách chọn một set đồ">
                    <li><b>Chọn dịp</b><span>Đi làm, đi chơi hay hẹn hò</span></li>
                    <li><b>Xem cách phối</b><span>Màu, dáng và mức giá</span></li>
                    <li><b>Chọn size</b><span>Từng món, ngay trang sản phẩm</span></li>
                </ol>
            </div>
            <div class="cx-collage" aria-hidden="true">
                <span class="cx-strap"></span>
                <figure class="cx-shot cx-shot--a"><img src="<%=request.getContextPath()%>/assets/images/home/tie.webp" alt="" width="640" height="800"><figcaption>Đi làm</figcaption></figure>
                <figure class="cx-shot cx-shot--b"><img src="<%=request.getContextPath()%>/assets/images/home/look-date.webp" alt="" width="640" height="800"><figcaption>Hẹn hò</figcaption></figure>
                <figure class="cx-shot cx-shot--c"><img src="<%=request.getContextPath()%>/assets/images/home/look-weekend.webp" alt="" width="640" height="800"><figcaption>Hằng ngày</figcaption></figure>
                <span class="cx-script">chọn ít, mặc chất</span>
            </div>
        </div>
    </section>

    <section class="cx-wrap cx-occasions" aria-labelledby="cxOccasionTitle">
        <div class="cx-head">
            <div><span class="eyebrow">Theo dịp</span><h2 id="cxOccasionTitle">Hôm nay bạn mặc cho dịp nào?</h2></div>
            <nav class="cx-sources" aria-label="Nguồn bộ sưu tập">
                <a class="<%=curated?"is-active":""%>" href="<%=collectionEscape(collectionUrl(request.getContextPath(),"curated",selectedStyle,selectedBudget,selectedProductId))%>" <%=curated?"aria-current=\"page\"":""%>>Cửa hàng chọn</a>
                <a class="<%=curated?"":"is-active"%>" href="<%=collectionEscape(collectionUrl(request.getContextPath(),"suggested",selectedStyle,selectedBudget,selectedProductId))%>" <%=curated?"":"aria-current=\"page\""%>>Gợi ý tự động</a>
            </nav>
        </div>
        <div class="cx-occasion-grid" aria-label="Chọn dịp sử dụng">
            <% for(Style style:outfitStyles){ boolean on=style.getId().equals(selectedStyle); %>
            <a class="cx-occasion <%=on?"is-active":""%>" href="<%=collectionEscape(collectionUrl(request.getContextPath(),selectedSource,style.getId(),selectedBudget,selectedProductId))%>" <%=on?"aria-current=\"true\"":""%>>
                <img src="<%=request.getContextPath()%>/assets/images/home/<%=collectionPhoto(style.getId())%>.webp" alt="" width="640" height="800" loading="lazy">
                <span class="cx-occasion-label"><%=collectionEscape(style.getLabel())%><% if(on){ %><i>Đang xem</i><% } %></span>
                <span class="cx-occasion-copy"><b><%=collectionEscape(style.getTitle())%></b><small><%=collectionEscape(style.getDescription())%></small></span>
                <span class="cx-occasion-go" aria-hidden="true">↗</span>
            </a>
            <% } %>
        </div>
    </section>

    <section class="cx-wrap">
        <form id="outfitFilters" class="cx-filters" action="<%=request.getContextPath()%>/collections#outfitResults" method="get">
            <input type="hidden" name="source" value="<%=selectedSource%>">
            <p class="cx-filters-title"><span>Tinh chỉnh</span>theo ý bạn</p>
            <div><label for="outfitStyle">Dịp sử dụng</label>
                <select class="select" id="outfitStyle" name="style">
                    <option value="all" <%="all".equals(selectedStyle)?"selected":""%>>Tất cả dịp</option>
                    <% for(Style style:outfitStyles){ %><option value="<%=style.getId()%>" <%=style.getId().equals(selectedStyle)?"selected":""%>><%=collectionEscape(style.getLabel())%></option><% } %>
                </select>
            </div>
            <div><label for="outfitBudget">Ngân sách tối đa / set (₫)</label>
                <input class="field" id="outfitBudget" name="budget" type="number" min="1" max="50000000" step="1" inputmode="numeric" placeholder="Ví dụ: 1000000" value="<%=selectedBudget==null?"":selectedBudget.toPlainString()%>">
            </div>
            <div><label for="outfitProduct">Phối cùng sản phẩm</label>
                <select class="select" id="outfitProduct" name="productId">
                    <option value="">Để cửa hàng gợi ý</option>
                    <% if(Boolean.TRUE.equals(request.getAttribute("selectedProductUnavailable"))){ %><option value="<%=selectedProductId%>" selected>Sản phẩm không còn phù hợp để phối</option><% } %>
                    <% for(OutfitItem product:outfitProducts){ %><option value="<%=product.getProductId()%>" <%=Integer.valueOf(product.getProductId()).equals(selectedProductId)?"selected":""%>><%=collectionEscape(product.getName())%> · <%=collectionEscape(product.getRoleLabel())%></option><% } %>
                </select>
            </div>
            <div class="cx-filter-actions"><button class="cx-btn cx-btn--gold" type="submit">Tìm bộ phù hợp</button><a href="<%=request.getContextPath()%>/collections?source=<%=selectedSource%>">Xóa bộ lọc</a></div>
        </form>
    </section>

    <section class="cx-wrap cx-results" aria-label="Gợi ý bộ sưu tập">
        <div class="cx-head" id="outfitResults">
            <div><span class="eyebrow"><%=curated?"Cửa hàng lựa chọn":"Gợi ý theo màu và kiểu dáng"%></span><h2>Những món hợp nhau.</h2></div>
            <p class="cx-count"><b><%=outfits.size()%></b><span>set phù hợp<%=unavailable?"":"<br>chỉ gồm màu, size còn hàng"%></span></p>
        </div>
        <% if(unavailable){ %>
        <div class="cx-empty" role="alert">
            <div class="cx-empty-art" aria-hidden="true"><img src="<%=request.getContextPath()%>/assets/images/home/flatlay.webp" alt="" width="640" height="480" loading="lazy"></div>
            <div class="cx-empty-copy">
                <span class="eyebrow">Tạm gián đoạn</span>
                <h2>Chưa tải được bộ sưu tập.</h2>
                <p>Kết nối đến kho hàng đang gián đoạn. Vui lòng thử lại sau ít phút, hoặc xem các gợi ý phối tự động trong lúc chờ.</p>
                <div class="cx-empty-actions"><a class="cx-btn" href="<%=request.getContextPath()%>/collections">Thử lại</a><a class="cx-btn cx-btn--line" href="<%=request.getContextPath()%>/collections?source=suggested">Gợi ý phối tự động</a></div>
            </div>
        </div>
        <% }else if(outfits.isEmpty()){ %>
        <div class="cx-empty">
            <div class="cx-empty-art" aria-hidden="true"><img src="<%=request.getContextPath()%>/assets/images/home/flatlay.webp" alt="" width="640" height="480" loading="lazy"></div>
            <div class="cx-empty-copy">
                <span class="eyebrow">Thử một lựa chọn khác</span><h2>Chưa có set phù hợp.</h2>
                <p><%=Boolean.TRUE.equals(request.getAttribute("selectedProductUnavailable"))?"Sản phẩm đã chọn không còn màu/size khả dụng. Bạn có thể chọn món khác.":curated?"Chưa có bộ sưu tập công khai, còn đủ hàng và khớp bộ lọc. Bạn có thể đổi bộ lọc hoặc xem các gợi ý phối tự động.":!Boolean.TRUE.equals(request.getAttribute("hasOutfitBasics"))?"Cửa hàng hiện chưa đủ cả áo và quần còn hàng để ghép thành set. Bạn có thể khám phá các sản phẩm đang có.":"Bạn có thể tăng ngân sách, đổi dịp sử dụng hoặc bỏ sản phẩm đang chọn. Các set chỉ được tạo khi có áo và quần phù hợp còn hàng."%></p>
                <div class="cx-empty-actions"><a class="cx-btn" href="<%=request.getContextPath()%>/collections?source=<%=selectedSource%>">Xóa bộ lọc</a><a class="cx-btn cx-btn--line" href="<%=request.getContextPath()%>/collections?source=suggested">Gợi ý phối tự động</a><a class="cx-link" href="<%=request.getContextPath()%>/products">Khám phá sản phẩm</a></div>
            </div>
        </div>
        <% }else{ %>
        <div class="cx-looks">
            <% int outfitIndex=0;for(OutfitSet outfit:outfits){outfitIndex++;String styleLabel="";for(Style style:outfitStyles)if(style.getId().equals(outfit.getStyle()))styleLabel=style.getLabel(); %>
            <article class="cx-look" aria-labelledby="outfitTitle<%=outfitIndex%>">
                <div class="cx-look-visual cx-n<%=Math.min(outfit.getItems().size(),4)%>">
                    <% for(OutfitItem item:outfit.getItems()){String colorQuery=collectionColor(item.getColor()); %>
                    <a class="cx-look-photo" href="<%=request.getContextPath()%>/product-detail?productId=<%=item.getProductId()%>&amp;color=<%=colorQuery%>" aria-label="<%=collectionEscape(item.getName()+", màu "+item.getColor())%>">
                        <img src="<%=request.getContextPath()%>/product-image?productId=<%=item.getProductId()%>&amp;color=<%=colorQuery%>" alt="<%=collectionEscape(item.getName()+" · "+item.getColor())%>" loading="lazy" decoding="async" width="400" height="500">
                        <span><%=collectionEscape(item.getRoleLabel())%></span>
                    </a>
                    <% } %>
                </div>
                <div class="cx-look-copy">
                    <div class="cx-look-top"><span class="cx-look-no">Look <%=String.format("%02d",outfitIndex)%></span><span><%=collectionEscape(styleLabel)%> · <%=outfit.getItems().size()%> món</span></div>
                    <h3 id="outfitTitle<%=outfitIndex%>"><%=collectionEscape(outfit.getTitle())%></h3>
                    <p class="cx-look-desc"><%=collectionEscape(outfit.getDescription())%></p>
                    <ul class="cx-look-items">
                        <% for(OutfitItem item:outfit.getItems()){ %>
                        <% List<ProductVariant> buyable=collectionVariants(collectionVariantCache,item.getProductId(),item.getColor()); String sizeId="cxSize"+outfitIndex+"_"+item.getProductId(); %>
                        <li><div><a href="<%=request.getContextPath()%>/product-detail?productId=<%=item.getProductId()%>&amp;color=<%=collectionColor(item.getColor())%>"><%=collectionEscape(item.getName())%></a>
                            <small><%=collectionEscape(item.getColor())%></small></div><i aria-hidden="true"></i>
                            <span class="cx-pick">
                            <% if(buyable.isEmpty()){ %><em>Hết hàng</em><% }else if(buyable.size()==1){ ProductVariant only=buyable.get(0); %><input type="hidden" data-buy value="<%=only.getVariantID()%>"><em><%=collectionEscape(only.getSize())%></em><% }else{ %>
                            <label class="sr-only" for="<%=sizeId%>">Size <%=collectionEscape(item.getName())%></label>
                            <select id="<%=sizeId%>" data-buy aria-label="Size <%=collectionEscape(item.getName())%>"><option value="">Size</option><% for(ProductVariant v:buyable){ %><option value="<%=v.getVariantID()%>"><%=collectionEscape(v.getSize())%></option><% } %></select><% } %>
                            </span><b><%=outfitMoney.format(item.getFromPrice())%>₫</b></li>
                        <% } %>
                    </ul>
                    <details class="cx-look-why"><summary>Vì sao phối set này?</summary><ul><% for(String reason:outfit.getReasons()){ %><li><%=collectionEscape(reason)%></li><% } %></ul></details>
                    <% if(!outfit.getMissingNote().isEmpty()){ %><p class="cx-look-missing"><%=collectionEscape(outfit.getMissingNote())%></p><% } %>
                    <div class="cx-look-total">
                        <div class="cx-tag"><small>Cả set từ</small><strong><%=outfitMoney.format(outfit.getFromPrice())%>₫</strong></div>
                        <button class="cx-btn cx-buy" type="button" data-cart="<%=request.getContextPath()%>/cart" data-login="<%=request.getContextPath()%>/login">Thêm cả set vào giỏ <span aria-hidden="true">→</span></button>
                    </div>
                    <p class="cx-buy-msg" role="status" aria-live="polite"></p>
                </div>
            </article>
            <% } %>
        </div>
        <% } %>
        <aside class="cx-note"><b>Lưu ý khi chọn set</b><p>Giá từ được cộng theo màu gợi ý và size còn hàng có giá thấp nhất, chưa gồm phí giao hàng hay khuyến mãi. Giá và tồn kho được kiểm tra lại khi mua. Ảnh ưu tiên màu gợi ý; nếu chưa có ảnh riêng, ảnh đại diện được dùng thay thế.</p></aside>
    </section>
</main>
<%@include file="/includes/footer.jspf"%>
<script src="<%=request.getContextPath()%>/assets/mensform-collections.js?v=20261024f" defer></script>
</body></html>
