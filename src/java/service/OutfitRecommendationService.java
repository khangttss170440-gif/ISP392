package service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import model.OutfitItem;
import model.OutfitItem.Role;
import model.OutfitSet;

/** Deterministic outfit suggestions based on the shop's categories, colors and available inventory. */
public final class OutfitRecommendationService {
    public enum Style {
        EVERYDAY("everyday","Hằng ngày","Gọn gàng, dễ mặc","Áo thoải mái kết hợp quần đơn giản cho nhịp sống mỗi ngày."),
        OFFICE("office","Đi làm","Chỉn chu vừa đủ","Áo sơ mi hoặc polo đi cùng quần gọn gàng, ưu tiên màu dễ kết hợp."),
        WEEKEND("weekend","Cuối tuần","Thoải mái đi chơi","Phối áo casual với jeans hoặc quần thoải mái và điểm nhấn vừa đủ."),
        EVENING("evening","Hẹn hò","Tối giản, có điểm nhấn","Ưu tiên tông màu trầm, đường nét gọn và phụ kiện tiết chế.");
        private final String id,label,title,description;
        Style(String id,String label,String title,String description){this.id=id;this.label=label;this.title=title;this.description=description;}
        public String getId(){return id;}
        public String getLabel(){return label;}
        public String getTitle(){return title;}
        public String getDescription(){return description;}
    }
    public static boolean validStyle(String id){return "all".equals(id)||Arrays.stream(Style.values()).anyMatch(s->s.id.equals(id));}

    public List<OutfitSet> recommend(List<OutfitItem> inventory,String styleId,BigDecimal budget,Integer anchorId) {
        List<OutfitItem> items=inventory.stream().filter(i->i.getRole()!=Role.UNKNOWN && i.getFromPrice()!=null).collect(Collectors.toList());
        Map<Style,List<OutfitSet>> byStyle=new LinkedHashMap<>();
        for(Style style:Style.values()) if("all".equals(styleId)||style.id.equals(styleId))
            byStyle.put(style,forStyle(items,style,budget,anchorId));
        // Alternate styles, deduplicate the actual combination, and limit repeated products.
        List<OutfitSet> result=new ArrayList<>();Set<String> signatures=new HashSet<>();
        Set<String> bases=new HashSet<>();
        for(int round=0;round<4 && result.size()<12;round++) for(List<OutfitSet> sets:byStyle.values()) {
            for(OutfitSet set:sets) {
                String base=set.getItems().get(0).getKey()+"|"+set.getItems().get(1).getKey();
                if(signatures.contains(set.getSignature()) || bases.contains(base))continue;
                signatures.add(set.getSignature());bases.add(base);result.add(set);break;
            }
            if(result.size()==12)break;
        }
        return result;
    }

    private List<OutfitSet> forStyle(List<OutfitItem> items,Style style,BigDecimal budget,Integer anchor) {
        boolean anchorTop=anchor!=null && anchorHasRole(items,anchor,Role.TOP);
        boolean anchorBottom=anchor!=null && anchorHasRole(items,anchor,Role.BOTTOM);
        Comparator<OutfitItem> priority=Comparator.comparingInt((OutfitItem i)->styleScore(i,style)).reversed()
                .thenComparing(OutfitItem::getFromPrice).thenComparingInt(OutfitItem::getProductId).thenComparing(OutfitItem::getColor);
        List<OutfitItem> tops=items.stream().filter(i->i.getRole()==Role.TOP && accepts(i,style))
                .filter(i->!anchorTop||i.getProductId()==anchor)
                .sorted(priority).limit(32).collect(Collectors.toList());
        List<OutfitItem> bottoms=items.stream().filter(i->i.getRole()==Role.BOTTOM && accepts(i,style))
                .filter(i->!anchorBottom||i.getProductId()==anchor)
                .sorted(priority).limit(32).collect(Collectors.toList());
        List<Pair> pairs=new ArrayList<>();
        for(OutfitItem top:tops) for(OutfitItem bottom:bottoms) {
            BigDecimal price=top.getFromPrice().add(bottom.getFromPrice());
            if(top.getProductId()==bottom.getProductId() || budget!=null && price.compareTo(budget)>0)continue;
            int colorScore=compatibility(top,bottom);
            if(colorScore<0)continue;
            pairs.add(new Pair(top,bottom,styleScore(top,style)+styleScore(bottom,style)+colorScore));
        }
        pairs.sort(Comparator.comparingInt((Pair p)->p.score).reversed()
                .thenComparing(p->p.top.getFromPrice().add(p.bottom.getFromPrice()))
                .thenComparing(p->p.top.getKey()+p.bottom.getKey()));
        List<OutfitSet> result=new ArrayList<>();
        for(Pair pair:pairs) {
            List<OutfitItem> selected=new ArrayList<>(List.of(pair.top,pair.bottom));
            if(anchor!=null && selected.stream().noneMatch(i->i.getProductId()==anchor)) {
                OutfitItem chosen=bestExtra(items,selected,style,budget,anchor,null,true);
                if(chosen==null)continue;
                selected.add(chosen);
            }
            List<Role> extraRoles=style==Style.WEEKEND?List.of(Role.SHOES,Role.ACCESSORY,Role.LAYER):List.of(Role.SHOES,Role.ACCESSORY);
            for(Role role:extraRoles) {
                if(selected.size()>=4 || selected.stream().anyMatch(i->i.getRole()==role))continue;
                OutfitItem extra=bestExtra(items,selected,style,budget,null,role,false);
                if(extra!=null)selected.add(extra);
            }
            List<String> reasons=new ArrayList<>();
            String topColor=tone(pair.top),bottomColor=tone(pair.bottom);
            if(!"unknown".equals(topColor) && !"unknown".equals(bottomColor)) {
                reasons.add(topColor.equals(bottomColor)?"Hai món cùng nhóm màu tạo tổng thể liền mạch."
                        :"Màu "+pair.top.getColor()+" của áo kết hợp màu "+pair.bottom.getColor()+" của quần theo bảng màu gọn, dễ mặc.");
            } else reasons.add("Kiểu áo và quần được ghép theo dịp sử dụng; bạn có thể xem ảnh để chọn tông màu ưng ý.");
            reasons.add(style.description);
            if(selected.stream().anyMatch(i->i.getRole()==Role.ACCESSORY)) reasons.add("Phụ kiện tạo điểm nhấn, đồng thời được cân đối với màu sắc và giá của set.");
            boolean accessory=selected.stream().anyMatch(i->i.getRole()==Role.ACCESSORY);
            String missing=accessory?"":"Chưa ghép phụ kiện: kho hiện chưa có lựa chọn phù hợp về màu sắc và mức giá cho set này.";
            result.add(new OutfitSet(style.id,style.title,style.description,selected,reasons,missing));
            if(result.size()==40)break;
        }
        return result;
    }

    private OutfitItem bestExtra(List<OutfitItem> items,List<OutfitItem> selected,Style style,BigDecimal budget,Integer anchor,Role role,boolean required) {
        BigDecimal total=selected.stream().map(OutfitItem::getFromPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
        return items.stream().filter(i->role==null?i.getRole()!=Role.TOP && i.getRole()!=Role.BOTTOM:i.getRole()==role)
                .filter(i->anchor==null||i.getProductId()==anchor)
                .filter(i->selected.stream().noneMatch(old->old.getProductId()==i.getProductId()))
                .filter(i->accepts(i,style))
                .filter(i->budget==null||total.add(i.getFromPrice()).compareTo(budget)<=0)
                .filter(i->required||i.getFromPrice().compareTo(total.multiply(i.getRole()==Role.SHOES?new BigDecimal("1.2"):new BigDecimal("0.6")))<=0)
                .filter(i->selected.stream().allMatch(old->compatibility(i,old)>=0))
                .sorted(Comparator.comparingInt((OutfitItem i)->styleScore(i,style)+selected.stream().mapToInt(old->compatibility(i,old)).sum()).reversed()
                        .thenComparing(OutfitItem::getFromPrice).thenComparing(OutfitItem::getKey))
                .findFirst().orElse(null);
    }
    private boolean anchorHasRole(List<OutfitItem> items,int anchor,Role role){return items.stream().anyMatch(i->i.getProductId()==anchor && i.getRole()==role);}
    private boolean accepts(OutfitItem item,Style style) {
        String text=item.getSearchText();
        if(style==Style.OFFICE) {
            if(item.getRole()==Role.TOP && OutfitItem.has(text,"t shirt","tshirt","hoodie","oversize","tank"))return false;
            if(item.getRole()==Role.BOTTOM && OutfitItem.has(text,"cargo","shorts","quan dui","rach","ripped"))return false;
            if(item.getRole()==Role.ACCESSORY && OutfitItem.has(text,"cap","hat","mu","non"))return false;
        }
        return true;
    }
    private int styleScore(OutfitItem item,Style style) {
        String text=item.getSearchText();int score=0;
        if(style==Style.OFFICE) {
            if(OutfitItem.has(text,"oxford","so mi","polo","chino","trousers","leather","da","belt"))score+=12;
            if(OutfitItem.has(text,"jeans"))score-=3;
        } else if(style==Style.WEEKEND) {
            if(OutfitItem.has(text,"t shirt","oversize","jeans","cargo","sneakers","cotton"))score+=10;
        } else if(style==Style.EVENING) {
            if(OutfitItem.has(text,"shirt","polo","slim","leather","belt","watch"))score+=8;
            if(Set.of("black","navy","gray","brown").contains(tone(item)))score+=8;
        } else if(OutfitItem.has(text,"basic","regular","cotton","t shirt","sneakers"))score+=10;
        if(neutral(tone(item)))score+=3;
        return score;
    }
    private int compatibility(OutfitItem first,OutfitItem second) {
        String a=tone(first),b=tone(second);
        if("unknown".equals(a)||"unknown".equals(b))return 1;
        if(a.equals(b))return 9;
        if(neutral(a)||neutral(b))return 12;
        if(Set.of(a,b).equals(Set.of("blue","navy")))return 7;
        return -1;
    }
    private boolean neutral(String tone){return Set.of("black","white","gray","beige","brown","navy").contains(tone);}
    private String tone(OutfitItem item) {
        String color=OutfitItem.normalize(item.getColor());
        if(OutfitItem.has(color,"den","black"))return "black";
        if(OutfitItem.has(color,"trang","white","ivory","off white"))return "white";
        if(OutfitItem.has(color,"xam","ghi","gray","grey","silver","bac"))return "gray";
        if(OutfitItem.has(color,"be","kem","beige","cream","khaki","vang","gold"))return "beige";
        if(OutfitItem.has(color,"nau","brown","tan","camel"))return "brown";
        if(OutfitItem.has(color,"navy","xanh den","xanh than"))return "navy";
        if(OutfitItem.has(color,"blue","xanh duong","xanh bien"))return "blue";
        if(OutfitItem.has(color,"green","xanh la","reu","olive"))return "green";
        if(OutfitItem.has(color,"do","red","burgundy","maroon"))return "red";
        return "unknown";
    }
    private static final class Pair {
        final OutfitItem top,bottom;final int score;
        Pair(OutfitItem top,OutfitItem bottom,int score){this.top=top;this.bottom=bottom;this.score=score;}
    }
}
