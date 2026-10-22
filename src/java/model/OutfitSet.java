package model;

import java.math.BigDecimal;
import java.util.List;

public final class OutfitSet {
    private final String style, title, description;
    private final List<OutfitItem> items;
    private final List<String> reasons;
    private final String missingNote;
    public OutfitSet(String style,String title,String description,List<OutfitItem> items,List<String> reasons,String missingNote) {
        this.style=style;this.title=title;this.description=description;
        this.items=List.copyOf(items);this.reasons=List.copyOf(reasons);this.missingNote=missingNote;
    }
    public String getStyle(){return style;}
    public String getTitle(){return title;}
    public String getDescription(){return description;}
    public List<OutfitItem> getItems(){return items;}
    public List<String> getReasons(){return reasons;}
    public String getMissingNote(){return missingNote;}
    public BigDecimal getFromPrice(){return items.stream().map(OutfitItem::getFromPrice).reduce(BigDecimal.ZERO,BigDecimal::add);}
    public String getSignature(){return items.stream().map(OutfitItem::getKey).sorted().collect(java.util.stream.Collectors.joining("|"));}
}
