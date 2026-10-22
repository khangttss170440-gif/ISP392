package model;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.LinkedHashSet;
import java.util.Set;

/** One purchasable product/color, with a price range across its available sizes. */
public final class OutfitItem {
    public enum Role { TOP, BOTTOM, LAYER, SHOES, ACCESSORY, UNKNOWN }
    private final int productId;
    private final String name, category, material, color, searchText;
    private final Role role;
    private BigDecimal fromPrice;
    private final Set<String> sizes = new LinkedHashSet<>();

    public OutfitItem(int productId,String name,String category,String material,String color) {
        this.productId=productId;this.name=name;this.category=category;this.material=material;
        this.color=color==null || color.isBlank()?"Mặc định":color.trim();
        this.role=classify(category,name);
        this.searchText=normalize(name+" "+category+" "+material);
    }
    public void addSize(String size,BigDecimal price) {
        if(price==null || price.signum()<0) return;
        if(fromPrice==null || price.compareTo(fromPrice)<0) fromPrice=price;
        sizes.add(size==null || size.isBlank()?"Free size":size.trim());
    }
    public int getProductId(){return productId;}
    public String getName(){return name;}
    public String getCategory(){return category;}
    public String getMaterial(){return material;}
    public String getColor(){return color;}
    public Role getRole(){return role;}
    public BigDecimal getFromPrice(){return fromPrice;}
    public String getSizesLabel(){return String.join(" / ",sizes);}
    public String getRoleLabel() {
        switch(role) {
            case TOP:return "Áo";
            case BOTTOM:return "Quần";
            case LAYER:return "Áo khoác";
            case SHOES:return "Giày";
            case ACCESSORY:return "Phụ kiện";
            default:return "Sản phẩm";
        }
    }
    public String getSearchText(){return searchText;}
    public String getKey(){return productId+":"+color;}
    public OutfitItem asRole(Role selectedRole) {
        OutfitItem copy=new OutfitItem(productId,name,category,material,color,selectedRole);
        copy.fromPrice=fromPrice;copy.sizes.addAll(sizes);return copy;
    }
    private OutfitItem(int id,String name,String category,String material,String color,Role role) {
        this.productId=id;this.name=name;this.category=category;this.material=material;
        this.color=color;this.role=role;this.searchText=normalize(name+" "+category+" "+material);
    }
    public static String normalize(String value) {
        return Normalizer.normalize(value==null?"":value,Normalizer.Form.NFD)
                .replaceAll("\\p{M}+","").toLowerCase(java.util.Locale.ROOT).replace('đ','d')
                .replaceAll("[^a-z0-9]+"," ").trim();
    }
    public static boolean has(String value,String... phrases) {
        String text=" "+normalize(value)+" ";
        for(String phrase:phrases) if(text.contains(" "+normalize(phrase)+" ")) return true;
        return false;
    }
    private static Role classify(String category,String name) {
        Role categoryRole=classifyText(category);
        return categoryRole==Role.UNKNOWN?classifyText(name):categoryRole;
    }
    private static Role classifyText(String value) {
        if(has(value,"jacket","coat","blazer","ao khoac","vest"))return Role.LAYER;
        if(has(value,"pants","trousers","jeans","shorts","quan","chino"))return Role.BOTTOM;
        if(has(value,"shoes","sneakers","loafers","boots","giay","dep"))return Role.SHOES;
        if(has(value,"accessories","accessory","phu kien","belt","that lung","day nit","watch","dong ho",
                "necklace","day chuyen","vong","tie","ca vat","bag","tui","cap","hat","mu","non","kinh"))return Role.ACCESSORY;
        if(has(value,"t shirt","tshirt","shirt","polo","hoodie","sweater","ao","so mi"))return Role.TOP;
        return Role.UNKNOWN;
    }
}
