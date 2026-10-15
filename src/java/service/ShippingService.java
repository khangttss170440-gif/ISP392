package service;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

/** Shipping uses straight-line radius, never a browser-submitted fee or coordinates. */
public class ShippingService {
    public static final String SHOP_ADDRESS = "138 Quang Trung, khu phố 7, thị trấn Hóc Môn, TP. Hồ Chí Minh, Việt Nam";
    public static final BigDecimal NATIONAL_FEE = new BigDecimal("45000");
    public static final double FREE_RADIUS_KM = 5.0;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    public Quote quote(String address) {
        if (address == null || address.isBlank()) return Quote.unknown("Chọn địa chỉ để kiểm tra miễn phí giao hàng trong bán kính 5 km.");
        try {
            Point shop = shopPoint();
            Point destination = geocode(address);
            if (shop == null || destination == null) return Quote.unknown("Chưa xác định chính xác khoảng cách. Phí giao hàng toàn quốc tạm tính 45.000₫.");
            double distance = distanceKm(shop.latitude, shop.longitude, destination.latitude, destination.longitude);
            boolean free = distance <= FREE_RADIUS_KM + 1e-9;
            return new Quote(free ? BigDecimal.ZERO : NATIONAL_FEE, distance, shop, destination,
                    free ? "Miễn phí giao hàng trong bán kính 5 km từ shop." : "Ngoài bán kính 5 km: phí giao hàng toàn quốc 45.000₫.");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            return Quote.unknown("Chưa xác minh được khoảng cách. Phí giao hàng toàn quốc tạm tính 45.000₫.");
        }
    }

    protected Point shopPoint() throws Exception {
        String lat = System.getenv("SHOP_LATITUDE"), lng = System.getenv("SHOP_LONGITUDE");
        if (lat != null && lng != null) return new Point(Double.parseDouble(lat), Double.parseDouble(lng));
        return geocode(SHOP_ADDRESS);
    }

    protected Point geocode(String address) throws Exception {
        String key = System.getenv("GOOGLE_MAPS_SERVER_API_KEY");
        if (key == null || key.isBlank()) return null;
        String url = "https://maps.googleapis.com/maps/api/geocode/xml?components=country%3AVN&language=vi&address="
                + URLEncoder.encode(address, StandardCharsets.UTF_8) + "&key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(8)).GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) throw new IOException("Geocoding service unavailable.");
        return parseGeocode(response.body());
    }

    public static Point parseGeocode(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        Element root = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml))).getDocumentElement();
        if (!"OK".equals(text(root, "status"))) return null;
        NodeList results = root.getElementsByTagName("result");
        if (results.getLength() != 1) return null;
        Element result = (Element) results.item(0);
        if ("true".equals(text(result, "partial_match"))) return null;
        String type = text(result, "location_type");
        if (!"ROOFTOP".equals(type) && !"RANGE_INTERPOLATED".equals(type)) return null;
        Element location = (Element) result.getElementsByTagName("location").item(0);
        if (location == null) return null;
        return new Point(Double.parseDouble(text(location, "lat")), Double.parseDouble(text(location, "lng")));
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    public static double distanceKm(double lat1, double lng1, double lat2, double lng2) {
        new Point(lat1, lng1); new Point(lat2, lng2);
        double lat = Math.toRadians(lat2 - lat1), lng = Math.toRadians(lng2 - lng1);
        double a = Math.pow(Math.sin(lat / 2), 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.pow(Math.sin(lng / 2), 2);
        return 6371.0088 * 2 * Math.asin(Math.sqrt(Math.min(1, a)));
    }

    public static String browserKey() {
        String key = System.getenv("GOOGLE_MAPS_BROWSER_API_KEY");
        return key != null && key.matches("[A-Za-z0-9_-]+") ? key : "";
    }

    public static class Point {
        public final double latitude, longitude;
        public Point(double latitude, double longitude) {
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) throw new IllegalArgumentException("Invalid coordinates.");
            this.latitude = latitude; this.longitude = longitude;
        }
    }

    public static class Quote {
        public final BigDecimal fee;
        public final Double distanceKm;
        public final Point shop, destination;
        public final String message;
        public Quote(BigDecimal fee, Double distanceKm, Point shop, Point destination, String message) {
            this.fee = fee; this.distanceKm = distanceKm; this.shop = shop; this.destination = destination; this.message = message;
        }
        public boolean isVerified() { return distanceKm != null; }
        public static Quote unknown(String message) { return new Quote(NATIONAL_FEE, null, null, null, message); }
    }
}
