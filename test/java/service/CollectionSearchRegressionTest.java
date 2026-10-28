package service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import model.ChatbotCollection;
import model.ChatbotProduct;
import model.OutfitCollection;
import model.OutfitCollection.Piece;
import model.OutfitItem.Role;
import model.ProductSearchCriteria;

/** Published-only, live-stock, whole-set budget and conversation regression checks. */
public final class CollectionSearchRegressionTest {
    private static int checks;
    private static final ProductSearchService parser = new ProductSearchService();
    private static final CollectionSearchService finder = new CollectionSearchService();
    private static final List<ChatbotProduct> catalog = new ArrayList<>();
    private static final List<OutfitCollection> collections = new ArrayList<>();
    public static void main(String[] args) {
        item(1,"Áo sơ mi Oxford","Áo sơ mi","Trắng","M",400000,5);
        item(1,"Áo sơ mi Oxford","Áo sơ mi","Trắng","L",450000,5);
        item(2,"Áo polo cotton","Áo polo","Navy","L",350000,5);
        item(3,"Quần âu slim","Quần âu","Đen","L",500000,5);
        item(4,"Giày loafer da bò","Giày","Đen","40",1190000,5);
        item(5,"Giày sneaker trắng","Giày","Trắng","42",1000000,5);
        item(6,"Đồng hồ Classic","Đồng hồ","Nâu","40mm",1590000,5);
        item(7,"Cà vạt lụa","Cà vạt","Xám","Free Size",180000,5);
        item(8,"Áo thun hết hàng","Áo thun","Đen","L",250000,0);
        OutfitCollection office = collection(10,"Công sở gọn gàng","office","Published",
                piece(1,"Trắng",Role.TOP),piece(3,"Đen",Role.BOTTOM),piece(4,"Đen",Role.SHOES));
        OutfitCollection elegant = collection(11,"Lịch lãm công sở","office","Published",
                piece(1,"Trắng",Role.TOP),piece(3,"Đen",Role.BOTTOM),piece(6,"Nâu",Role.ACCESSORY));
        collection(12,"Cuối tuần năng động","weekend","Published",
                piece(2,"Navy",Role.TOP),piece(3,"Đen",Role.BOTTOM),piece(5,"Trắng",Role.SHOES));
        collection(13,"Bộ nháp bí mật","office","Draft",piece(1,"Trắng",Role.TOP),piece(3,"Đen",Role.BOTTOM));
        collection(14,"Bộ đã ẩn","office","Archived",piece(1,"Trắng",Role.TOP),piece(3,"Đen",Role.BOTTOM));
        collection(15,"Bộ hết áo","office","Published",piece(8,"Đen",Role.TOP),piece(3,"Đen",Role.BOTTOM));
        collection(16,"Bộ thiếu hàng","office","Published",piece(999,"Đen",Role.TOP),piece(3,"Đen",Role.BOTTOM));
        collection(17,"Bộ sai cấu trúc","office","Published",piece(1,"Trắng",Role.TOP),piece(6,"Nâu",Role.ACCESSORY));

        ProductSearchCriteria work = parse("tìm bộ sưu tập đi làm dưới 3 triệu",null);
        List<ChatbotCollection> first = finder.search(work,catalog,collections);
        check(work.collectionMode && ids(first).equals(Set.of(10,11)), "published office collections only");
        ProductSearchCriteria weekend = parse("tìm bộ sưu tập cuối tuần",null);
        check(weekend.requestedFamilies.isEmpty() && weekend.terms.isEmpty()
                && ids(finder.search(weekend,catalog,collections)).equals(Set.of(12)),
                "occasion cuối tuần is not mistaken for the quần category");
        check(ids(finder.search(parse("tìm bộ đi làm",null),catalog,collections)).equals(Set.of(10,11)), "natural abbreviated collection request");
        check(first.stream().allMatch(set->set.items.size()==3), "complete item list");
        check(first.stream().filter(set->set.id==10).findFirst().orElseThrow().fromPrice.compareTo(new BigDecimal("2090000"))==0,
                "whole-set price uses actual cheapest in-stock variants");
        check(finder.search(parse("còn dưới 2 triệu",work),catalog,collections).isEmpty(), "budget applies to whole collection and preserves office occasion");
        ProductSearchCriteria anyPrice = parse("không giới hạn giá",work);
        check(ids(finder.search(anyPrice,catalog,collections)).equals(Set.of(10,11)), "follow-up removes budget only");
        check(finder.search(parse("xem bộ sưu tập khác",work),catalog,collections).isEmpty(), "collection pagination excludes previously shown sets");
        check(ids(finder.search(parse("tìm bộ sưu tập có giày đen",null),catalog,collections)).equals(Set.of(10)), "color bound to requested piece, not another item");
        check(ids(finder.search(parse("tìm bộ có giày size 42",null),catalog,collections)).equals(Set.of(12)), "size bound to requested shoe");
        check(ids(finder.search(parse("bộ sưu tập có đồng hồ",null),catalog,collections)).equals(Set.of(11)), "collection contents search");
        check(ids(finder.search(parse("tìm bộ sưu tập Lịch lãm",null),catalog,collections)).equals(Set.of(11)), "collection title keyword search");
        check(finder.search(parse("tìm bộ sưu tập nháp bí mật",null),catalog,collections).isEmpty(), "draft titles not disclosed");
        check(ids(finder.search(parse("bộ sưu tập đi làm từ 2tr đến 2tr5",null),catalog,collections)).equals(Set.of(10,11)), "full-budget range and shorthand");
        ProductSearchCriteria sizeL = parse("bộ sưu tập đi làm size L",null);
        check(finder.search(sizeL,catalog,collections).stream().filter(set->set.id==10).findFirst().orElseThrow()
                .fromPrice.compareTo(new BigDecimal("2140000"))==0,"total reflects requested clothing size while accessories retain their sizes");
        ProductSearchCriteria ascending = parse("bộ sưu tập đi làm giá thấp nhất",null);
        check(finder.search(ascending,catalog,collections).get(0).id==10,"collection price ordering");
        ProductSearchCriteria incomplete = parse("bộ sưu tập đi làm gồm quần áo giày đồng hồ cà vatk",null);
        check(incomplete.requestedFamilies.size()==5 && finder.search(incomplete,catalog,collections).isEmpty(),"do not claim an incomplete set satisfies a five-category request");
        collection(18,"Trọn bộ công sở","office","Published",piece(1,"Trắng",Role.TOP),piece(3,"Đen",Role.BOTTOM),
                piece(4,"Đen",Role.SHOES),piece(6,"Nâu",Role.ACCESSORY),piece(7,"Xám",Role.ACCESSORY));
        check(ids(finder.search(parse("bộ sưu tập đi làm gồm quần áo giày đồng hồ cà vatk dưới 4 triệu",null),catalog,collections)).equals(Set.of(18)),
                "all requested groups and whole-set budget respected");
        check(!parser.search("cảm ơn",work,catalog).search,"greeting does not rerun collection browsing");
        check(office.getStatus().equals("Published") && elegant.getPieces().size()==3,"search never mutates curated collections");
        System.out.println("Collection search regression checks passed: "+checks);
    }
    private static ProductSearchCriteria parse(String text,ProductSearchCriteria previous) {
        ProductSearchService.Result result=parser.search(text,previous,catalog);
        if(!result.search)throw new AssertionError("Collection intent missed: "+text);
        return result.criteria;
    }
    private static Set<Integer> ids(List<ChatbotCollection> values){return values.stream().map(set->set.id).collect(Collectors.toSet());}
    private static void item(int id,String name,String category,String color,String size,int price,int stock){
        ChatbotProduct product=new ChatbotProduct();product.setProductID(id);product.setProductName(name);product.setCategoryName(category);
        product.setColor(color);product.setSize(size);product.setPrice(BigDecimal.valueOf(price));product.setQuantity(stock);catalog.add(product);
    }
    private static Piece piece(int id,String color,Role role){return new Piece(id,color,role,"");}
    private static OutfitCollection collection(int id,String name,String style,String status,Piece... pieces){
        OutfitCollection collection=new OutfitCollection();collection.setId(id);collection.setTitle(name);collection.setStyle(style);collection.setStatus(status);
        collection.getPieces().addAll(List.of(pieces));collections.add(collection);return collection;
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;}
}
