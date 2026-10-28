package service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import model.ChatbotCollection;
import model.ChatbotProduct;
import model.OutfitCollection;
import model.OutfitItem.Role;
import model.ProductSearchCriteria;
import model.ProductSearchCriteria.Sort;
import util.AppConfig;

/** Searches staff-published collections; validates every piece against live stock. */
public final class CollectionSearchService {
    public List<ChatbotCollection> search(ProductSearchCriteria criteria, List<ChatbotProduct> catalog,
            List<OutfitCollection> collections) {
        List<String> styles = new ArrayList<>();
        for (OutfitRecommendationService.Style style : OutfitRecommendationService.Style.values()) {
            List<String> aliases = AppConfig.list("CHATBOT_COLLECTION_STYLE_" + style.name());
            if (criteria.occasionTerms.stream().anyMatch(term -> aliases.stream()
                    .anyMatch(alias -> ProductSearchService.words(alias).equals(ProductSearchService.words(term)))))
                styles.add(style.getId());
        }
        List<ChatbotCollection> matches = new ArrayList<>();
        for (OutfitCollection source : collections) {
            if (!"Published".equals(source.getStatus()) || source.getId() <= 0
                    || criteria.shownCollectionIDs.contains(source.getId())) continue;
            if (!styles.isEmpty() && !styles.contains(source.getStyle())) continue;
            if (source.getPieces().stream().noneMatch(piece -> piece.getRole() == Role.TOP)
                    || source.getPieces().stream().noneMatch(piece -> piece.getRole() == Role.BOTTOM)) continue;
            List<ChatbotProduct> items = new ArrayList<>();
            Set<Integer> seen = new HashSet<>();
            for (OutfitCollection.Piece piece : source.getPieces()) {
                if (!seen.add(piece.getProductId())) break;
                ChatbotProduct available = catalog.stream().filter(product -> product.getProductID() == piece.getProductId())
                        .filter(product -> product.getQuantity() > 0 && product.getPrice() != null && product.getPrice().signum() >= 0)
                        .filter(product -> words(product.getColor()).equals(words(piece.getColor())))
                        .filter(product -> sizeMatches(product, piece.getRole(), criteria))
                        .min(Comparator.comparing(ChatbotProduct::getPrice)
                                .thenComparing(Comparator.comparingInt(ChatbotProduct::getQuantity).reversed())).orElse(null);
                if (available == null) break;
                items.add(available);
            }
            if (items.size() != source.getPieces().size()) continue;
            if (criteria.selectedProductID != null && items.stream().noneMatch(item -> item.getProductID() == criteria.selectedProductID)) continue;
            if (criteria.requestedFamilies.stream().anyMatch(group -> items.stream().noneMatch(item -> matchesGroup(item, group)))) continue;
            List<ChatbotProduct> colorTargets = criteria.requestedFamilies.size() == 1
                    ? items.stream().filter(item -> matchesGroup(item, criteria.requestedFamilies.get(0))).collect(Collectors.toList()) : items;
            if (!criteria.colors.isEmpty() && colorTargets.stream().noneMatch(item -> criteria.colors.stream()
                    .anyMatch(color -> sameColor(item.getColor(), color)))) continue;
            if (colorTargets.stream().anyMatch(item -> criteria.excludedColors.stream().anyMatch(color -> sameColor(item.getColor(), color)))) continue;
            ChatbotCollection collection = new ChatbotCollection(source, items);
            if (!budgetMatches(collection.fromPrice, criteria)) continue;
            String text = words(source.getTitle() + " " + source.getDescription() + " " + source.getStylingNotes() + " "
                    + items.stream().map(item -> item.getProductName() + " " + item.getCategoryName() + " " + item.getMaterial()).collect(Collectors.joining(" ")));
            if (criteria.terms.stream().anyMatch(term -> !(" " + text + " ").contains(" " + term + " "))) continue;
            matches.add(collection);
        }
        if (criteria.sort == Sort.PRICE_ASC) matches.sort(Comparator.comparing(collection -> collection.fromPrice));
        else if (criteria.sort == Sort.PRICE_DESC) matches.sort(Comparator.comparing((ChatbotCollection collection) -> collection.fromPrice).reversed());
        else matches.sort(Comparator.comparingInt((ChatbotCollection collection) -> titleScore(collection, criteria)).reversed());
        List<ChatbotCollection> selected = matches.stream().limit(AppConfig.integer("CHATBOT_COLLECTION_LIMIT", 1, 20)).collect(Collectors.toList());
        selected.forEach(collection -> criteria.shownCollectionIDs.add(collection.id));
        criteria.lastLowestPrice = selected.stream().map(collection -> collection.fromPrice).min(BigDecimal::compareTo).orElse(null);
        return selected;
    }

    private boolean sizeMatches(ChatbotProduct product, Role role, ProductSearchCriteria criteria) {
        if (criteria.size == null) return true;
        boolean namedPiece = criteria.requestedFamilies.size() == 1
                && criteria.requestedFamilies.stream().anyMatch(group -> matchesGroup(product, group));
        boolean clothingSize = words(criteria.size).matches("xxs|xs|s|m|l|xl|xxl|xxxl")
                && (role == Role.TOP || role == Role.BOTTOM || role == Role.LAYER);
        if (!namedPiece && !clothingSize) return true;
        return words(product.getSize()).replace(" ", "").equals(words(criteria.size).replace(" ", ""));
    }
    private boolean sameColor(String actual, String wanted) {
        if (words(actual).equals(words(wanted))) return true;
        return AppConfig.list("CHATBOT_COLOR_GROUPS").stream().map(group -> List.of(group.split(",")))
                .anyMatch(aliases -> aliases.stream().anyMatch(alias -> words(alias).equals(words(actual)))
                        && aliases.stream().anyMatch(alias -> words(alias).equals(words(wanted))));
    }
    private static boolean budgetMatches(BigDecimal total, ProductSearchCriteria criteria) {
        if (criteria.minPrice != null && (total.compareTo(criteria.minPrice) < 0 || criteria.minExclusive && total.compareTo(criteria.minPrice) == 0)) return false;
        return criteria.maxPrice == null || total.compareTo(criteria.maxPrice) < 0 || !criteria.maxExclusive && total.compareTo(criteria.maxPrice) == 0;
    }
    private static int titleScore(ChatbotCollection collection, ProductSearchCriteria criteria) {
        String title = words(collection.title);
        return (int) criteria.terms.stream().filter(title::contains).count();
    }
    private static boolean matchesGroup(ChatbotProduct product, List<String> group) {
        String text = " " + words(product.getProductName() + " " + product.getCategoryName()) + " ";
        return group.stream().anyMatch(alias -> text.contains(" " + words(alias) + " "));
    }
    private static String words(String value) { return ProductSearchService.words(value); }
}
