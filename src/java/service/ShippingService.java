package service;

import java.math.BigDecimal;

/** Checkout shipping quotes come from the configured GHN staging service. */
public class ShippingService {
    private final GhnShippingService carrier;

    public ShippingService() { this(new GhnShippingService()); }
    ShippingService(GhnShippingService carrier) { this.carrier = java.util.Objects.requireNonNull(carrier); }

    /** Real GHN staging quote for the checkout's structured address. Never falls back to an estimated tariff. */
    public Quote quote(model.CustomerAddress address, BigDecimal subtotal, int quantity) {
        boolean free = qualifiesForFreeShipping(subtotal);
        try {
            GhnShippingService.weight(quantity);
            java.util.Map<String,Object> destination = carrier.destination(address);
            BigDecimal fee = free ? BigDecimal.ZERO : carrier.fee(destination, quantity);
            Quote quote = Quote.priced(fee, "GHN_TEST", quantity,
                    free ? freeShippingMessage() + " Địa chỉ đã đối chiếu với GHN test."
                    : "Phí từ API GHN test: " + fee.toPlainString() + "₫. Kiện ước tính " + GhnShippingService.weight(quantity)
                    + " g, " + GhnShippingService.packageDimensions() + " cm; kho cần cân đo lại trước khi tạo vận đơn.", free);
            quote.destinationJson = util.ShippingJson.write(destination);
            return quote;
        } catch (IllegalArgumentException e) {
            // Even a free order needs a valid carrier destination before booking.
            return Quote.unavailable(e.getMessage());
        }
    }
    public static final String SHOP_ADDRESS = util.AppConfig.get("SHOP_ADDRESS");
    public static final BigDecimal FREE_SHIPPING_THRESHOLD = util.AppConfig.decimal("SHIPPING_FREE_THRESHOLD");

    public static boolean qualifiesForFreeShipping(BigDecimal subtotal) {
        return subtotal != null && subtotal.compareTo(FREE_SHIPPING_THRESHOLD) >= 0;
    }

    public static String freeShippingMessage() {
        return "Đơn từ " + util.AppConfig.money(FREE_SHIPPING_THRESHOLD) + " được miễn phí giao hàng toàn quốc.";
    }

    public static class Quote {
        public final BigDecimal fee;
        public final String zone;
        public final int estimatedHalfKilogramUnits;
        public final String message;
        public final boolean freeByOrderValue;
        private final boolean priced;
        public String destinationJson;

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

        private static Quote unavailable(String message) {
            return new Quote(BigDecimal.ZERO, null, 0, message, false, false);
        }

        public boolean isPriced() { return priced; }
        public boolean isCheckoutAllowed() { return priced; }
    }
}
