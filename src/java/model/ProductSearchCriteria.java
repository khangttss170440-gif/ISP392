package model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Search state contains public shopping preferences only; never SQL or account data. */
public final class ProductSearchCriteria implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Sort { RELEVANCE, PRICE_ASC, PRICE_DESC }
    public List<String> family = new ArrayList<>();
    public List<List<String>> requestedFamilies = new ArrayList<>();
    public boolean collectionMode;
    public Set<Integer> shownCollectionIDs = new LinkedHashSet<>();
    public List<String> terms = new ArrayList<>();
    public List<String> colors = new ArrayList<>();
    public List<String> excludedColors = new ArrayList<>();
    public List<String> occasionTerms = new ArrayList<>();
    public Integer selectedProductID;
    public List<Integer> lastProductIDs = new ArrayList<>();
    public String size;
    public BigDecimal minPrice;
    public BigDecimal maxPrice;
    public boolean minExclusive;
    public boolean maxExclusive;
    public Sort sort = Sort.RELEVANCE;
    public Set<Integer> shownProductIDs = new LinkedHashSet<>();
    public BigDecimal lastLowestPrice;

    public ProductSearchCriteria copy() {
        ProductSearchCriteria copy = new ProductSearchCriteria();
        copy.family = new ArrayList<>(family);
        if (requestedFamilies != null) requestedFamilies.forEach(group -> copy.requestedFamilies.add(new ArrayList<>(group)));
        copy.collectionMode = collectionMode;
        if (shownCollectionIDs != null) copy.shownCollectionIDs = new LinkedHashSet<>(shownCollectionIDs);
        copy.terms = new ArrayList<>(terms);
        copy.colors = new ArrayList<>(colors);
        copy.excludedColors = new ArrayList<>(excludedColors);
        copy.occasionTerms = new ArrayList<>(occasionTerms);
        copy.selectedProductID = selectedProductID;
        copy.lastProductIDs = new ArrayList<>(lastProductIDs);
        copy.size = size;
        copy.minPrice = minPrice;
        copy.maxPrice = maxPrice;
        copy.minExclusive = minExclusive;
        copy.maxExclusive = maxExclusive;
        copy.sort = sort;
        copy.shownProductIDs = new LinkedHashSet<>(shownProductIDs);
        copy.lastLowestPrice = lastLowestPrice;
        return copy;
    }
}
