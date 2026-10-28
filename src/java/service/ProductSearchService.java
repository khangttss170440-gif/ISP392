package service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import model.ChatbotProduct;
import model.ProductSearchCriteria;
import model.ProductSearchCriteria.Sort;
import util.AppConfig;

/** Deterministic retrieval from current catalog facts, with per-conversation shopping filters. */
public final class ProductSearchService {
    private static final String NUMBER = "\\d+(?:[.,]\\d+)*";
    private static final String UNIT = "(?:trieu|tr|nghin|ngan|k|vnd|dong|d)";
    private static final Pattern RANGE = Pattern.compile("(?<![\\p{L}\\d])(?:tu\\s*)?(" + NUMBER
            + ")\\s*(" + UNIT + ")?\\s*(?:den|toi|[-–])\\s*(" + NUMBER + ")\\s*(" + UNIT + ")?(?![\\p{L}\\d])");
    private static final Pattern MONEY = Pattern.compile("(?<![\\p{L}\\d])(?:(khong qua|toi da|nho hon|duoi|tren|it nhat|tu|den|tam|khoang|ngan sach|gia|budget)\\s*)?("
            + NUMBER + ")\\s*(" + UNIT + ")?(?![\\p{L}\\d])");
    private static final Pattern MILLION_SHORT = Pattern.compile("(?<![\\p{L}\\d])(\\d+)\\s*(?:trieu|tr)\\s*(\\d{1,3})(?![\\p{L}\\d])");
    private final List<List<String>> productGroups = groups("CHATBOT_PRODUCT_SYNONYMS");
    private final List<List<String>> colorGroups = groups("CHATBOT_COLOR_GROUPS");
    private final List<List<String>> occasions = groups("CHATBOT_OCCASION_GROUPS");
    private final Set<String> stopWords = new LinkedHashSet<>(AppConfig.list("CHATBOT_SEARCH_STOP_WORDS"));
    private final Map<String,String> colorIdentities = new java.util.concurrent.ConcurrentHashMap<>();

    public Result search(String message, ProductSearchCriteria previous, List<ChatbotProduct> catalog) {
        String query = words(message);
        String raw = rawWords(message);
        List<Hit> families = familyHits(query);
        if (families.isEmpty()) {
            catalog.stream().map(ChatbotProduct::getCategoryName).filter(name -> name != null)
                    .distinct().filter(name -> contains(query, words(name)))
                    .sorted(Comparator.comparingInt(String::length).reversed()).findFirst()
                    .ifPresent(name -> families.add(new Hit(query.indexOf(words(name)), query.indexOf(words(name)) + words(name).length(), List.of(name))));
        }
        List<String> family = families.stream().flatMap(hit -> hit.group.stream()).distinct().collect(Collectors.toList());
        boolean restart = hasAny(query, AppConfig.list("CHATBOT_SEARCH_RESTART_PHRASES"));
        boolean changedFamily = !family.isEmpty() && previous != null
                && !normalized(family).equals(normalized(previous.family));
        boolean collectionIntent = hasAny(query, AppConfig.list("CHATBOT_COLLECTION_INTENT_PHRASES"));
        boolean explicitProduct = !collectionIntent && !family.isEmpty()
                && hasAny(query, AppConfig.list("CHATBOT_SEARCH_INTENT_PHRASES"));
        boolean changedMode = previous != null && (collectionIntent && !previous.collectionMode
                || explicitProduct && previous.collectionMode);
        ProductSearchCriteria criteria = previous == null || restart || changedFamily || changedMode
                ? new ProductSearchCriteria() : previous.copy();
        if (!family.isEmpty()) {
            criteria.family = family; criteria.selectedProductID = null;
            criteria.requestedFamilies = families.stream().map(hit -> hit.group).distinct()
                    .map(ArrayList::new).collect(Collectors.toList());
        }
        if (collectionIntent) criteria.collectionMode = true;
        else if (explicitProduct) criteria.collectionMode = false;
        boolean clearColor = hasAny(query, AppConfig.list("CHATBOT_CLEAR_COLOR_PHRASES"));
        boolean clearPrice = hasAny(query, AppConfig.list("CHATBOT_CLEAR_PRICE_PHRASES"));
        boolean clearSize = hasAny(query, AppConfig.list("CHATBOT_CLEAR_SIZE_PHRASES"));
        if (clearColor) { criteria.colors.clear(); criteria.excludedColors.clear(); }
        if (clearPrice) { criteria.minPrice = null; criteria.maxPrice = null; criteria.minExclusive = false; criteria.maxExclusive = false; }
        if (clearSize) criteria.size = null;

        List<List<String>> availableColors = new ArrayList<>(colorGroups);
        catalog.stream().map(ChatbotProduct::getColor).filter(color -> color != null && !color.isBlank()).distinct()
                .filter(color -> hits(rawWords(color), colorGroups, false).isEmpty())
                .forEach(color -> availableColors.add(List.of(color, fold(color))));
        List<Hit> colorHits = hits(raw, availableColors, false);
        List<String> requestedColors = new ArrayList<>();
        List<String> rejectedColors = new ArrayList<>();
        for (Hit hit : colorHits) {
            String prefix = words(raw.substring(0, hit.start));
            String color = hit.group.get(0);
            if (raw.substring(hit.start, hit.end).equals("tim") && hit.start == 0 && hit.end < raw.length()) continue;
            if (raw.substring(hit.start, hit.end).equals("do") && family.isEmpty() && previous == null
                    && !contains(query, "mau do")) continue;
            if (prefix.matches(".*(?:^| )(khong|tru|ngoai tru|loai|not|except)(?: lay| chon| phai| mau| tong)*$"))
                rejectedColors.add(color);
            else requestedColors.add(color);
        }
        if (!requestedColors.isEmpty()) { criteria.colors = requestedColors; criteria.excludedColors.removeAll(requestedColors); }
        if (!rejectedColors.isEmpty()) { criteria.excludedColors.addAll(rejectedColors); criteria.colors.removeAll(rejectedColors); }
        String size = findSize(message, catalog);
        if (size != null) criteria.size = size;
        Price price = parsePrice(fold(message));
        if (price.present) {
            criteria.minPrice = price.min; criteria.maxPrice = price.max;
            criteria.minExclusive = price.minExclusive; criteria.maxExclusive = price.maxExclusive;
        }
        boolean cheapest = hasAny(query, AppConfig.list("CHATBOT_PRICE_ASC_PHRASES"));
        boolean expensive = hasAny(query, AppConfig.list("CHATBOT_PRICE_DESC_PHRASES"));
        if (cheapest) criteria.sort = Sort.PRICE_ASC;
        if (expensive) criteria.sort = Sort.PRICE_DESC;
        if (contains(query, "re hon") && previous != null && previous.lastLowestPrice != null && !price.present) {
            criteria.maxPrice = previous.lastLowestPrice;
            criteria.maxExclusive = true;
            if (criteria.minPrice != null && criteria.minPrice.compareTo(criteria.maxPrice) >= 0) criteria.minPrice = null;
            criteria.sort = Sort.PRICE_ASC;
        }
        List<Hit> occasionHits = hits(query, occasions, true);
        if (!occasionHits.isEmpty()) criteria.occasionTerms = occasionHits.stream()
                .flatMap(hit -> hit.group.stream()).distinct().collect(Collectors.toList());
        boolean next = hasAny(query, AppConfig.list("CHATBOT_SEARCH_NEXT_PHRASES"))
                || hasAny(query, AppConfig.list("CHATBOT_COLLECTION_NEXT_PHRASES"));
        Matcher ordinal = Pattern.compile("(?:mau|cai|san pham) (?:thu |so )?(\\d+)").matcher(query);
        boolean selected = false;
        if (ordinal.find() && previous != null) {
            int index;
            try { index = Integer.parseInt(ordinal.group(1)) - 1; } catch (NumberFormatException error) { index = -1; }
            if (index >= 0 && index < previous.lastProductIDs.size()) {
                criteria.selectedProductID = previous.lastProductIDs.get(index); selected = true;
            }
        }

        String clean = words(MONEY.matcher(expandMillionShorthand(fold(message))).replaceAll(" "));
        for (Hit hit : families) clean = remove(clean, query.substring(hit.start, hit.end));
        for (String phrase : allIgnoredPhrases(family, colorHits, size, occasionHits)) clean = remove(clean, words(phrase));
        clean = clean.replaceAll("(?:mau|cai|san pham) (?:thu |so )?\\d+", " ");
        List<String> terms = new ArrayList<>();
        for (String token : clean.trim().split("\\s+")) {
            if (!token.isBlank() && !stopWords.contains(token) && !token.matches("\\d+")) terms.add(token);
        }
        boolean knownCatalogTerm = terms.stream().anyMatch(term -> catalog.stream().anyMatch(product ->
                contains(primaryText(product) + " " + words(product.getMaterial()) + " " + words(product.getBrand()), term)
                        || contains(words(product.getSku()), term) || closeToken(primaryText(product), term)));
        boolean search = !family.isEmpty() || !colorHits.isEmpty() || size != null || price.present
                || clearColor || clearPrice || clearSize || cheapest || expensive || next || selected
                || !occasionHits.isEmpty() || knownCatalogTerm || collectionIntent
                || hasAny(query, AppConfig.list("CHATBOT_SEARCH_INTENT_PHRASES"))
                || (previous != null && hasAny(query, AppConfig.list("CHATBOT_SEARCH_FOLLOWUP_PHRASES")));
        if (!search) return new Result(false, previous, List.of(), false, false, "", List.of());

        if (restart && family.isEmpty() && colorHits.isEmpty() && size == null && !price.present && terms.isEmpty()) {
            List<String> categories = catalog.stream().map(ChatbotProduct::getCategoryName).filter(name -> name != null)
                    .distinct().limit(AppConfig.integer("CHATBOT_SUGGESTION_LIMIT", 1, 10))
                    .map(name -> "Tìm " + name).collect(Collectors.toList());
            return new Result(true, criteria, List.of(), false, false, "", categories);
        }
        boolean control = clearColor || clearPrice || clearSize || next || selected || cheapest || expensive;
        if (!terms.isEmpty() && !control) criteria.terms = terms.stream().distinct().collect(Collectors.toList());
        if (restart && family.isEmpty()) criteria.family.clear();
        if (!next) { criteria.shownProductIDs.clear(); criteria.shownCollectionIDs.clear(); }
        if (next) criteria.selectedProductID = null;
        if (criteria.minPrice != null && criteria.maxPrice != null && criteria.minPrice.compareTo(criteria.maxPrice) > 0)
            throw new IllegalArgumentException("Khoảng giá chưa hợp lệ. Bạn nhập giá thấp trước, giá cao sau nhé.");

        List<Scored> matches = new ArrayList<>();
        boolean outOfStock = false;
        for (ChatbotProduct product : catalog) {
            int score = score(product, criteria);
            if (score < 0) continue;
            if (product.getQuantity() <= 0) { outOfStock = true; continue; }
            if (!criteria.shownProductIDs.contains(product.getProductID())) matches.add(new Scored(product, score));
        }
        Comparator<Scored> relevance = Comparator.comparingInt((Scored value) -> value.score).reversed();
        Comparator<Scored> priceOrder = Comparator.comparing(value -> value.product.getPrice());
        Comparator<Scored> order = criteria.sort == Sort.PRICE_ASC ? priceOrder.thenComparing(relevance)
                : criteria.sort == Sort.PRICE_DESC ? priceOrder.reversed().thenComparing(relevance)
                : relevance.thenComparing(priceOrder);
        matches.sort(order.thenComparing(Comparator.comparingInt((Scored value) -> value.product.getQuantity()).reversed())
                .thenComparing(Comparator.comparingInt((Scored value) -> value.product.getProductID()).reversed()));
        Map<Integer,ChatbotProduct> unique = new LinkedHashMap<>();
        for (Scored match : matches) unique.putIfAbsent(match.product.getProductID(), match.product);
        List<ChatbotProduct> products = diverseSelection(new ArrayList<>(unique.values()), criteria.requestedFamilies);
        List<String> missing = criteria.requestedFamilies.stream().filter(group -> unique.values().stream()
                .noneMatch(product -> hasAny(primaryText(product), group))).map(group -> group.get(0)).collect(Collectors.toList());
        criteria.lastProductIDs = products.stream().map(ChatbotProduct::getProductID).collect(Collectors.toList());
        criteria.shownProductIDs.addAll(criteria.lastProductIDs);
        criteria.lastLowestPrice = products.stream().map(ChatbotProduct::getPrice).min(BigDecimal::compareTo).orElse(null);
        boolean advice = hasAny(query, AppConfig.list("CHATBOT_ADVICE_PHRASES"));
        return new Result(true, criteria, products, outOfStock, advice, summary(criteria), suggestions(criteria, products.isEmpty()), missing);
    }

    private List<ChatbotProduct> diverseSelection(List<ChatbotProduct> ranked, List<List<String>> requested) {
        int limit = AppConfig.integer("CHATBOT_PRODUCT_LIMIT", 1, 100);
        if (requested.size() < 2) return ranked.stream().limit(limit).collect(Collectors.toList());
        Map<Integer,ChatbotProduct> selected = new LinkedHashMap<>();
        boolean added;
        do {
            added = false;
            for (List<String> group : requested) {
                if (selected.size() >= limit) break;
                ChatbotProduct next = ranked.stream().filter(product -> !selected.containsKey(product.getProductID()))
                        .filter(product -> hasAny(primaryText(product), group)).findFirst().orElse(null);
                if (next != null) { selected.put(next.getProductID(), next); added = true; }
            }
        } while (added && selected.size() < limit);
        return new ArrayList<>(selected.values());
    }

    private List<Hit> familyHits(String query) {
        List<Hit> found = hits(query, productGroups, true);
        if (AppConfig.integer("CHATBOT_TYPO_MAX_EDITS", 0, 1) == 0) return found;
        List<Hit> approximate = new ArrayList<>();
        List<Hit> priceSpans = new ArrayList<>();
        Matcher price = MONEY.matcher(query);
        while (price.find()) if (price.group(1) != null || price.group(3) != null)
            priceSpans.add(new Hit(price.start(), price.end(), List.of()));
        priceSpans.addAll(hits(query, occasions, true));
        priceSpans.addAll(hits(query, AppConfig.list("CHATBOT_COLLECTION_INTENT_PHRASES").stream()
                .map(List::of).collect(Collectors.toList()), true));
        for (List<String> group : productGroups) for (String alias : normalized(group).stream().distinct().collect(Collectors.toList())) {
            if (alias.length() < AppConfig.integer("CHATBOT_TYPO_MIN_LENGTH", 3, 20)) continue;
            int count = alias.split(" ").length;
            Matcher matcher = Pattern.compile("(?<![a-z0-9])(?=([a-z0-9]+(?: [a-z0-9]+){" + (count - 1) + "})(?![a-z0-9]))").matcher(query);
            while (matcher.find()) {
                int start = matcher.start(1), end = matcher.end(1);
                if (matcher.group(1).length() < AppConfig.integer("CHATBOT_TYPO_MIN_LENGTH", 3, 20)
                        || java.util.Arrays.stream(matcher.group(1).split(" ")).allMatch(stopWords::contains)) continue;
                if (found.stream().anyMatch(hit -> start < hit.end && end > hit.start)) continue;
                if (priceSpans.stream().anyMatch(hit -> start < hit.end && end > hit.start)) continue;
                if (oneEdit(alias, matcher.group(1))) approximate.add(new Hit(start, end, group));
            }
        }
        approximate.sort(Comparator.comparingInt((Hit hit) -> hit.end - hit.start).reversed());
        for (Hit hit : approximate) if (found.stream().noneMatch(existing -> hit.start < existing.end && hit.end > existing.start)) found.add(hit);
        found.sort(Comparator.comparingInt(hit -> hit.start));
        return found;
    }

    private static boolean oneEdit(String left, String right) {
        if (Math.abs(left.length() - right.length()) > 1) return false;
        int a = 0, b = 0, differences = 0;
        while (a < left.length() && b < right.length()) {
            if (left.charAt(a) == right.charAt(b)) { a++; b++; continue; }
            if (++differences > 1) return false;
            if (left.length() >= right.length()) a++;
            if (right.length() >= left.length()) b++;
        }
        return differences + (left.length() - a) + (right.length() - b) <= 1;
    }

    private int score(ChatbotProduct product, ProductSearchCriteria criteria) {
        if (product.getPrice() == null || product.getPrice().signum() < 0) return -1;
        if (criteria.selectedProductID != null && product.getProductID() != criteria.selectedProductID) return -1;
        String primary = primaryText(product);
        if (!criteria.family.isEmpty() && !hasAny(primary, criteria.family)) return -1;
        String color = product.getColor();
        if (!criteria.colors.isEmpty() && criteria.colors.stream().noneMatch(wanted -> sameColor(color, wanted))) return -1;
        if (criteria.excludedColors.stream().anyMatch(unwanted -> sameColor(color, unwanted))) return -1;
        if (criteria.size != null && !sizeKey(criteria.size).equals(sizeKey(product.getSize()))) return -1;
        if (criteria.minPrice != null) {
            int compared = product.getPrice().compareTo(criteria.minPrice);
            if (compared < 0 || compared == 0 && criteria.minExclusive) return -1;
        }
        if (criteria.maxPrice != null) {
            int compared = product.getPrice().compareTo(criteria.maxPrice);
            if (compared > 0 || compared == 0 && criteria.maxExclusive) return -1;
        }
        String name = words(product.getProductName());
        String details = words(value(product.getDescription()) + " " + value(product.getMaterial()) + " "
                + value(product.getBrand()) + " " + value(product.getSku()));
        int total = 0;
        for (String term : criteria.terms) {
            if (contains(name, term)) total += AppConfig.integer("CHATBOT_NAME_MATCH_SCORE", 1, 1000);
            else if (contains(primary, term)) total += AppConfig.integer("CHATBOT_CATEGORY_MATCH_SCORE", 1, 1000);
            else if (contains(details, term)) total += AppConfig.integer("CHATBOT_DETAIL_MATCH_SCORE", 1, 1000);
            else if (closeToken(primary, term)) total += AppConfig.integer("CHATBOT_TYPO_MATCH_SCORE", 1, 1000);
            else return -1;
        }
        if (!criteria.occasionTerms.isEmpty()) {
            if (hasAny(primary + " " + details, criteria.occasionTerms)) total += AppConfig.integer("CHATBOT_OCCASION_MATCH_SCORE", 1, 1000);
            else if (criteria.family.isEmpty() && criteria.terms.isEmpty()) return -1;
        }
        return total;
    }

    private boolean closeToken(String text, String term) {
        int edits = AppConfig.integer("CHATBOT_TYPO_MAX_EDITS", 0, 1);
        if (edits == 0 || term.length() < AppConfig.integer("CHATBOT_TYPO_MIN_LENGTH", 3, 20)) return false;
        for (String token : text.split(" ")) {
            if (Math.abs(token.length() - term.length()) > edits) continue;
            int[] previous = new int[token.length() + 1];
            for (int i = 0; i < previous.length; i++) previous[i] = i;
            for (int i = 1; i <= term.length(); i++) {
                int[] current = new int[token.length() + 1]; current[0] = i;
                for (int j = 1; j <= token.length(); j++) current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1),
                        previous[j - 1] + (term.charAt(i - 1) == token.charAt(j - 1) ? 0 : 1));
                previous = current;
            }
            if (previous[token.length()] <= edits) return true;
        }
        return false;
    }

    private boolean sameColor(String actual, String wanted) {
        String identity = colorIdentities.computeIfAbsent(value(actual), color -> {
            List<Hit> actualGroups = hits(rawWords(color), colorGroups, false);
            return actualGroups.isEmpty() ? words(color) : words(actualGroups.get(0).group.get(0));
        });
        return identity.equals(words(wanted));
    }

    private String findSize(String message, List<ChatbotProduct> catalog) {
        String raw = value(message).toLowerCase(Locale.ROOT);
        Matcher explicit = Pattern.compile("(?:^|[^\\p{L}\\d])(?:size|kích cỡ|cỡ)\\s*[:=]?\\s*(free\\s+size|freesize|[a-z0-9]+(?:\\s*(?:cm|mm))?)(?![\\p{L}\\d])").matcher(raw);
        if (explicit.find()) return explicit.group(1).toUpperCase(Locale.ROOT);
        String query = words(message);
        return catalog.stream().map(ChatbotProduct::getSize).filter(size -> size != null && !size.isBlank()).distinct()
                .sorted(Comparator.comparingInt(String::length).reversed()).filter(size -> contains(query, words(size)))
                .findFirst().orElse(null);
    }

    private static String sizeKey(String size) { return words(size).replace(" ", ""); }

    private static String expandMillionShorthand(String input) {
        Matcher shorthand = MILLION_SHORT.matcher(input);
        StringBuffer expanded = new StringBuffer();
        while (shorthand.find()) {
            BigDecimal amount = new BigDecimal(shorthand.group(1)).multiply(BigDecimal.valueOf(1_000_000))
                    .add(new BigDecimal(shorthand.group(2)).movePointLeft(shorthand.group(2).length()).multiply(BigDecimal.valueOf(1_000_000)));
            shorthand.appendReplacement(expanded, amount.toPlainString() + " dong");
        }
        shorthand.appendTail(expanded);
        return expanded.toString();
    }

    private Price parsePrice(String input) {
        String query = expandMillionShorthand(input);
        Matcher range = RANGE.matcher(query);
        if (range.find() && (range.group(2) != null || range.group(4) != null
                || amount(range.group(1), null).compareTo(BigDecimal.valueOf(1000)) >= 0)) {
            Price price = new Price(); price.present = true;
            price.min = amount(range.group(1), range.group(2) == null ? range.group(4) : range.group(2));
            price.max = amount(range.group(3), range.group(4) == null ? range.group(2) : range.group(4));
            return price;
        }
        Price price = new Price();
        Matcher values = MONEY.matcher(query);
        while (values.find()) {
            if (values.group(3) == null && values.group(1) == null && values.group(2).replaceAll("[.,]", "").length() < 4) continue;
            BigDecimal amount = amount(values.group(2), values.group(3));
            String operator = value(values.group(1));
            if (values.group(3) == null && amount.compareTo(BigDecimal.valueOf(1000)) < 0) continue;
            price.present = true;
            switch (operator) {
                case "duoi": case "nho hon": price.max = amount; price.maxExclusive = true; break;
                case "khong qua": case "toi da": case "den": price.max = amount; price.maxExclusive = false; break;
                case "tren": price.min = amount; price.minExclusive = true; break;
                case "tu": case "it nhat": price.min = amount; price.minExclusive = false; break;
                default:
                    price.min = amount.multiply(AppConfig.decimal("CHATBOT_PRICE_LOWER_RATIO"));
                    price.max = amount.multiply(AppConfig.decimal("CHATBOT_PRICE_UPPER_RATIO"));
            }
        }
        return price;
    }

    private static BigDecimal amount(String text, String unit) {
        String number = text.matches("\\d{1,3}(?:[.,]\\d{3})+") ? text.replaceAll("[.,]", "") : text.replace(',', '.');
        BigDecimal amount = new BigDecimal(number);
        if (unit != null && unit.matches("trieu|tr")) return amount.multiply(BigDecimal.valueOf(1_000_000));
        if (unit != null && unit.matches("nghin|ngan|k")) return amount.multiply(BigDecimal.valueOf(1_000));
        return amount;
    }

    private List<String> allIgnoredPhrases(List<String> family, List<Hit> colors, String size, List<Hit> occasionHits) {
        List<String> ignored = new ArrayList<>(family);
        for (Hit color : colors) ignored.addAll(color.group);
        for (Hit occasion : occasionHits) ignored.addAll(occasion.group);
        if (size != null) ignored.add(size);
        for (String key : List.of("CHATBOT_CLEAR_COLOR_PHRASES", "CHATBOT_CLEAR_PRICE_PHRASES", "CHATBOT_CLEAR_SIZE_PHRASES",
                "CHATBOT_PRICE_ASC_PHRASES", "CHATBOT_PRICE_DESC_PHRASES", "CHATBOT_SEARCH_NEXT_PHRASES",
                "CHATBOT_SEARCH_INTENT_PHRASES", "CHATBOT_SEARCH_FOLLOWUP_PHRASES", "CHATBOT_ADVICE_PHRASES",
                "CHATBOT_SEARCH_RESTART_PHRASES")) ignored.addAll(AppConfig.list(key));
        ignored.addAll(AppConfig.list("CHATBOT_COLLECTION_INTENT_PHRASES"));
        ignored.addAll(AppConfig.list("CHATBOT_COLLECTION_NEXT_PHRASES"));
        ignored.sort(Comparator.comparingInt(String::length).reversed());
        return ignored;
    }

    private String summary(ProductSearchCriteria criteria) {
        List<String> parts = new ArrayList<>();
        if (criteria.collectionMode) parts.add("Bộ sưu tập");
        if (!criteria.requestedFamilies.isEmpty()) parts.add(criteria.requestedFamilies.stream().map(group -> group.get(0)).collect(Collectors.joining(" + ")));
        else if (!criteria.family.isEmpty()) parts.add(criteria.family.get(0));
        if (!criteria.occasionTerms.isEmpty()) parts.add(criteria.occasionTerms.get(0));
        parts.addAll(criteria.terms);
        if (!criteria.colors.isEmpty()) parts.add("màu " + String.join(" / ", criteria.colors));
        if (!criteria.excludedColors.isEmpty()) parts.add("trừ màu " + String.join(" / ", criteria.excludedColors));
        if (criteria.size != null) parts.add("size " + criteria.size);
        if (criteria.minPrice != null) parts.add((criteria.minExclusive ? "trên " : "từ ") + AppConfig.money(criteria.minPrice));
        if (criteria.maxPrice != null) parts.add((criteria.maxExclusive ? "dưới " : "tối đa ") + AppConfig.money(criteria.maxPrice));
        if (criteria.sort == Sort.PRICE_ASC) parts.add("giá tăng dần");
        if (criteria.sort == Sort.PRICE_DESC) parts.add("giá giảm dần");
        return String.join(" · ", parts);
    }

    private List<String> suggestions(ProductSearchCriteria criteria, boolean empty) {
        List<String> suggestions = new ArrayList<>();
        if (empty) {
            if (!criteria.colors.isEmpty() || !criteria.excludedColors.isEmpty()) suggestions.add("Bỏ điều kiện màu");
            if (criteria.size != null) suggestions.add("Bỏ điều kiện size");
            if (criteria.minPrice != null || criteria.maxPrice != null) suggestions.add("Không giới hạn giá");
        } else { suggestions.add("Giá thấp nhất"); suggestions.add("Xem mẫu khác"); }
        suggestions.add("Tìm sản phẩm mới");
        return suggestions;
    }

    private static List<List<String>> groups(String key) {
        return AppConfig.list(key).stream().map(group -> List.of(group.split("\\s*,\\s*"))).collect(Collectors.toList());
    }
    private static List<String> normalized(List<String> values) { return values.stream().map(ProductSearchService::words).collect(Collectors.toList()); }
    private static List<Hit> hits(String text, List<List<String>> groups, boolean fold) {
        List<Hit> candidates = new ArrayList<>();
        for (List<String> group : groups) for (String alias : group) {
            String term = fold ? words(alias) : rawWords(alias);
            if (term.isBlank()) continue;
            Matcher matcher = Pattern.compile("(?<![\\p{L}\\d])" + Pattern.quote(term) + "(?![\\p{L}\\d])").matcher(text);
            while (matcher.find()) candidates.add(new Hit(matcher.start(), matcher.end(), group));
        }
        candidates.sort(Comparator.comparingInt((Hit hit) -> hit.end - hit.start).reversed().thenComparingInt(hit -> hit.start));
        List<Hit> result = new ArrayList<>();
        for (Hit hit : candidates) if (result.stream().noneMatch(accepted -> hit.start < accepted.end && hit.end > accepted.start)) result.add(hit);
        result.sort(Comparator.comparingInt(hit -> hit.start));
        return result;
    }
    private static String primaryText(ChatbotProduct product) { return words(value(product.getProductName()) + " " + value(product.getCategoryName())); }
    private static boolean hasAny(String text, List<String> phrases) { return phrases.stream().anyMatch(phrase -> contains(text, words(phrase))); }
    private static boolean contains(String text, String phrase) { return !phrase.isBlank() && (" " + text + " ").contains(" " + phrase + " "); }
    private static String remove(String text, String phrase) { return (" " + text + " ").replace(" " + phrase + " ", " ").trim().replaceAll("\\s+", " "); }
    public static String fold(String value) { return Normalizer.normalize(value(value), Normalizer.Form.NFD).replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim(); }
    public static String words(String value) { return fold(value).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " "); }
    private static String rawWords(String value) { return value(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\d]+", " ").trim().replaceAll("\\s+", " "); }
    private static String value(String value) { return value == null ? "" : value; }
    private static final class Hit {
        final int start, end; final List<String> group;
        Hit(int start, int end, List<String> group) { this.start = start; this.end = end; this.group = group; }
    }
    private static final class Price { boolean present, minExclusive, maxExclusive; BigDecimal min, max; }
    private static final class Scored { final ChatbotProduct product; final int score; Scored(ChatbotProduct product, int score) { this.product = product; this.score = score; } }

    public static final class Result {
        public final boolean search;
        public final ProductSearchCriteria criteria;
        public final List<ChatbotProduct> products;
        public final boolean outOfStock;
        public final boolean advice;
        public final String summary;
        public final List<String> suggestions;
        public final List<String> missingGroups;
        Result(boolean search, ProductSearchCriteria criteria, List<ChatbotProduct> products, boolean outOfStock,
                boolean advice, String summary, List<String> suggestions) {
            this(search, criteria, products, outOfStock, advice, summary, suggestions, List.of());
        }
        Result(boolean search, ProductSearchCriteria criteria, List<ChatbotProduct> products, boolean outOfStock,
                boolean advice, String summary, List<String> suggestions, List<String> missingGroups) {
            this.search = search; this.criteria = criteria; this.products = products;
            this.outOfStock = outOfStock; this.advice = advice; this.summary = summary; this.suggestions = suggestions;
            this.missingGroups = List.copyOf(missingGroups);
        }
    }
}
