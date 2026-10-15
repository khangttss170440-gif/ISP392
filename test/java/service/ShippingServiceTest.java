package service;

import java.math.BigDecimal;

/** No Google key/network required; geocoding doubles verify the radius policy. */
public class ShippingServiceTest {
    public static void main(String[] args) throws Exception {
        check(quoteAt(0).fee.signum() == 0, "Shop location is free");
        check(quoteAt(4.99).fee.signum() == 0, "Inside five km is free");
        check(quoteAt(5).fee.signum() == 0, "Exactly five km is free");
        check(quoteAt(5.00001).fee.compareTo(new BigDecimal("45000")) == 0, "Immediately outside five km costs 45000");
        check(quoteAt(1000).fee.compareTo(new BigDecimal("45000")) == 0, "Nationwide shipping is flat, not per kilometer");
        ShippingService unknown = new ShippingService() { @Override protected Point geocode(String address) { return null; } };
        check(!unknown.quote("Unverified location").isVerified() && unknown.quote("Unverified location").fee.intValue() == 45000,
                "Missing geocode never fabricates a location or grants free shipping");
        ShippingService unavailable = new ShippingService() { @Override protected Point shopPoint() throws Exception { throw new java.io.IOException("offline"); } };
        check(!unavailable.quote("Address").isVerified(), "Unavailable geocoding produces an explicit estimate");
        String xml = "<GeocodeResponse><status>OK</status><result><geometry><location><lat>10.8</lat><lng>106.6</lng></location><location_type>ROOFTOP</location_type></geometry></result></GeocodeResponse>";
        check(ShippingService.parseGeocode(xml).latitude == 10.8, "Parse exact Google geocode XML");
        check(ShippingService.parseGeocode(xml.replace("ROOFTOP", "APPROXIMATE")) == null, "Do not grant free shipping from a broad area center");
        check(ShippingService.parseGeocode(xml.replace("<geometry>", "<partial_match>true</partial_match><geometry>")) == null, "Reject partially matched addresses");
        check(ShippingService.parseGeocode("<GeocodeResponse><status>ZERO_RESULTS</status></GeocodeResponse>") == null, "Handle unknown address");
        check(ShippingService.distanceKm(10, 106, 10, 106) == 0, "Zero radius");
        System.out.println("PASS shipping: inside/exactly/outside 5 km, flat 45000 fee, missing-key/API fallback, precise geocodes and partial/approximate result rejection.");
    }
    private static ShippingService.Quote quoteAt(double kilometers) {
        return new ShippingService() {
            @Override protected Point shopPoint() { return new Point(0, 0); }
            @Override protected Point geocode(String address) { return new Point(Math.toDegrees(kilometers / 6371.0088), 0); }
        }.quote("Test destination");
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
