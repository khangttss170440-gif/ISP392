package service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.Locale;

/** Estimated shipping based on J&T's published zone tariff; no map/API key required. */
public class ShippingService {
    public static final String SHOP_ADDRESS = "138 Quang Trung, khu phố 7, thị trấn Hóc Môn, TP. Hồ Chí Minh, Việt Nam";
    /** Kept for legacy callers; current quotes use the destination zone tariff. */
    public static final BigDecimal NATIONAL_FEE = new BigDecimal("45000");
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = new BigDecimal("799000");
    private static final int[] ZONE_A = {17700, 17700, 17700, 17700};
    private static final int[] ZONE_B = {22500, 24500, 26600, 28600};
    private static final int[] ZONE_C = {24000, 28000, 32000, 36000};
    private static final int[] ZONE_F = {25000, 29000, 33000, 37000};
    private static final int HALF_KG_A_B_INCREMENT = 2500;
    private static final int HALF_KG_C_F_INCREMENT = 4500;

    /** Compatibility overload: expects a comma-separated address ending in the province. */
    public Quote quote(String address) {
        return quote(address, BigDecimal.ZERO);
    }

    /** Compatibility overload for callers without cart quantity. */
    public Quote quote(String address, BigDecimal merchandiseSubtotal) {
        return quote(address, inferProvince(address), merchandiseSubtotal, 1);
    }

    public Quote quote(String address, String province, BigDecimal merchandiseSubtotal, int itemQuantity) {
        boolean freeByOrderValue = merchandiseSubtotal != null
                && merchandiseSubtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0;
        if (address == null || address.isBlank()) {
            return Quote.unavailable("Nhập địa chỉ giao hàng để tính phí.", freeByOrderValue);
        }
        if (province == null || province.isBlank()) {
            return Quote.unavailable("Cần nhập tỉnh/thành phố để tính phí theo vùng.", freeByOrderValue);
        }

        String zone = zoneFor(province);
        int halfKilogramUnits = Math.max(1, itemQuantity);
        BigDecimal carrierEstimate = estimate(zone, halfKilogramUnits);
        BigDecimal fee = freeByOrderValue ? BigDecimal.ZERO : carrierEstimate;
        String message = freeByOrderValue
                ? "Đơn hàng từ 799.000₫ được miễn phí giao hàng toàn quốc."
                : "Phí tham khảo vùng " + zone + " (" + zoneDescription(zone) + ") theo bảng giá J&T: " + fee.toPlainString()
                        + "₫. Ước tính mỗi sản phẩm nặng 0,5 kg; cước hãng có thể thay đổi theo cân nặng thực tế và hợp đồng.";
        return Quote.priced(fee, zone, halfKilogramUnits, message, freeByOrderValue);
    }

    public static BigDecimal estimate(String zone, int halfKilogramUnits) {
        int units = Math.max(1, halfKilogramUnits);
        int[] tariff;
        switch (zone == null ? "F" : zone.toUpperCase(Locale.ROOT)) {
            case "A": tariff = ZONE_A; break;
            case "B": tariff = ZONE_B; break;
            case "C": tariff = ZONE_C; break;
            default: tariff = ZONE_F;
        }
        int fee = tariff[Math.min(units, 4) - 1];
        if (units > 4) {
            int increment = tariff == ZONE_A || tariff == ZONE_B
                    ? HALF_KG_A_B_INCREMENT : HALF_KG_C_F_INCREMENT;
            fee += (units - 4) * increment;
        }
        return BigDecimal.valueOf(fee);
    }

    public static boolean isValidEstimatedFee(BigDecimal fee, int halfKilogramUnits) {
        if (fee == null || fee.signum() < 0) return false;
        if (fee.compareTo(BigDecimal.ZERO) == 0 || fee.compareTo(NATIONAL_FEE) == 0) return true;
        return fee.compareTo(estimate("A", halfKilogramUnits)) == 0
                || fee.compareTo(estimate("B", halfKilogramUnits)) == 0
                || fee.compareTo(estimate("C", halfKilogramUnits)) == 0
                || fee.compareTo(estimate("F", halfKilogramUnits)) == 0;
    }

    /** J&T destination zone groups for a HCMC-area shop; unlisted destinations use far zone F. */
    public static String zoneFor(String province) {
        String value = normalize(province);
        // Match common full names, abbreviations and colloquial spellings after accent/punctuation removal.
        if (containsAny(value, "HO CHI MINH", "HO CHI MIN", "SAI GON", "SAIGON", "TPHCM", "TP HCM",
                "THANH PHO HCM", "HCMC", "HCM CITY")) return "A";
        if (containsAny(value, "LONG AN", "BINH DUONG", "DONG NAI", "BA RIA", "VUNG TAU",
                "BRVT", "TIEN GIANG", "BEN TRE", "TRA VINH", "VINH LONG")) return "B";
        if (containsAny(value, "AN GIANG", "BAC LIEU", "BINH PHUOC", "BINH THUAN", "CA MAU",
                "CAN THO", "DONG THAP", "HAU GIANG", "SOC TRANG", "TAY NINH", "KIEN GIANG")) return "C";
        return "F";
    }

    private static String inferProvince(String address) {
        if (address == null) return "";
        String[] parts = address.split(",");
        for (int i = parts.length - 1; i >= 0; i--) {
            String candidate = parts[i].trim();
            if (candidate.isEmpty() || "VIET NAM".equals(normalize(candidate))) continue;
            return candidate;
        }
        return "";
    }

    private static String normalize(String text) {
        String decomposed = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "").replace('đ', 'd').replace('Đ', 'D')
                .toUpperCase(Locale.ROOT);
        return decomposed.replaceAll("[^A-Z0-9]+", " ").trim();
    }

    private static boolean containsAny(String text, String... terms) {
        for (String term : terms) if (text.contains(term)) return true;
        return false;
    }

    private static String zoneDescription(String zone) {
        switch (zone) {
            case "A": return "TP. Hồ Chí Minh";
            case "B": return "tỉnh lân cận";
            case "C": return "các tỉnh miền Nam khác";
            default: return "các tỉnh/thành còn lại";
        }
    }

    public static class Quote {
        public final BigDecimal fee;
        public final String zone;
        public final int estimatedHalfKilogramUnits;
        public final String message;
        public final boolean freeByOrderValue;
        private final boolean priced;

        private Quote(BigDecimal fee, String zone, int estimatedHalfKilogramUnits,
                      String message, boolean freeByOrderValue, boolean priced) {
            this.fee = fee;
            this.zone = zone;
            this.estimatedHalfKilogramUnits = estimatedHalfKilogramUnits;
            this.message = message;
            this.freeByOrderValue = freeByOrderValue;
            this.priced = priced;
        }

        private static Quote priced(BigDecimal fee, String zone, int units, String message, boolean freeByOrderValue) {
            return new Quote(fee, zone, units, message, freeByOrderValue, true);
        }

        private static Quote unavailable(String message, boolean freeByOrderValue) {
            if (freeByOrderValue) message += " Đơn này vẫn được miễn phí theo giá trị đơn hàng.";
            return new Quote(BigDecimal.ZERO, null, 0, message, freeByOrderValue, false);
        }

        public boolean isPriced() { return priced; }
        public boolean isCheckoutAllowed() { return priced || freeByOrderValue; }
    }
}
