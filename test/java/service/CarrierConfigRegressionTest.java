package service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Offline carrier stub verifies pagination cursors and configured package fields. */
public final class CarrierConfigRegressionTest {
    private static int requests;
    private static int checks;
    private static int fee = 100;
    public static void main(String[] args) {
        System.setProperty("GHN_SHOP_PAGE_SIZE", "2");
        System.setProperty("GHN_ESTIMATED_ITEM_WEIGHT_GRAMS", "600");
        System.setProperty("GHN_ESTIMATED_LENGTH_CM", "7");
        System.setProperty("GHN_ESTIMATED_WIDTH_CM", "8");
        System.setProperty("GHN_ESTIMATED_HEIGHT_CM", "9");
        GhnClient fake = new GhnClient() {
            @Override public Object cached(String path, Map<String,Object> body, long ttl) {
                requests++;
                if (path.equals("v2/shop/all?offset=0&limit=2")) {
                    return Map.of("shops", List.of(Map.of("_id", 1), Map.of("_id", 2)), "last_offset", 999);
                }
                if (path.equals("v2/shop/all?offset=999&limit=2")) {
                    return Map.of("shops", List.of(Map.of("_id", 123, "district_id", 10, "ward_code", "001")));
                }
                if (path.equals("v2/shipping-order/fee")) {
                    require(body.get("weight").equals(1800), "configured weight sent to GHN");
                    require(body.get("length").equals(7) && body.get("width").equals(8)
                            && body.get("height").equals(9), "configured dimensions sent to GHN");
                    return Map.of("total", fee);
                }
                throw new AssertionError("Unexpected carrier request path");
            }
        };
        GhnShippingService shipping = new GhnShippingService(fake);
        require(shipping.fee(Map.of("to_district_id", 20, "to_ward_code", "002"), 3)
                .compareTo(new BigDecimal("100")) == 0, "carrier fee returned");
        require(requests == 3, "carrier pagination follows returned cursor");
        fee = Integer.MAX_VALUE;
        try {
            shipping.fee(Map.of("to_district_id", 20, "to_ward_code", "002"), 3);
            throw new AssertionError("excessive carrier fee accepted");
        } catch (IllegalArgumentException expected) { }
        int[] quoteCalls = {0};
        boolean[] unavailable = {false};
        GhnShippingService quotedCarrier = new GhnShippingService() {
            @Override public Map<String,Object> destination(model.CustomerAddress address) {
                if (address == null) throw new IllegalArgumentException("Carrier address unavailable");
                return Map.of("to_district_id", 20, "to_ward_code", "002");
            }
            @Override public BigDecimal fee(Map<String,Object> destination, int quantity) {
                quoteCalls[0]++;
                if (unavailable[0]) throw new IllegalArgumentException("Carrier quote unavailable");
                return new BigDecimal("12345");
            }
        };
        ShippingService checkoutShipping = new ShippingService(quotedCarrier);
        model.CustomerAddress address = new model.CustomerAddress();
        ShippingService.Quote paid = checkoutShipping.quote(address,
                ShippingService.FREE_SHIPPING_THRESHOLD.subtract(BigDecimal.ONE), 3);
        require(paid.isCheckoutAllowed() && paid.fee.compareTo(new BigDecimal("12345")) == 0,
                "checkout uses carrier fee rather than a preset tariff");
        ShippingService.Quote free = checkoutShipping.quote(address, ShippingService.FREE_SHIPPING_THRESHOLD, 3);
        require(free.isCheckoutAllowed() && free.freeByOrderValue && free.fee.signum() == 0,
                "configured free-shipping policy preserved");
        require(quoteCalls[0] == 1, "free order validates destination without requesting a fee");
        ShippingService.Quote invalidAddress = checkoutShipping.quote(null, ShippingService.FREE_SHIPPING_THRESHOLD, 3);
        require(!invalidAddress.isCheckoutAllowed() && !invalidAddress.isPriced(),
                "free order cannot bypass carrier address validation");
        unavailable[0] = true;
        ShippingService.Quote unavailableQuote = checkoutShipping.quote(address, BigDecimal.ZERO, 3);
        require(!unavailableQuote.isCheckoutAllowed() && !unavailableQuote.isPriced(),
                "unavailable carrier does not fall back to fabricated fees");
        System.out.println("Carrier configuration/pagination regression checks passed: " + checks);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); checks++; }
}
