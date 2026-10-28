package util;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import model.OutfitItem;
import service.GhnShippingService;
import service.ShippingService;

/** Offline regression checks for configurable policy and signed payment requests. */
public final class ConfigurationRegressionTest {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile(Path.of("test/.scratch"), "config-test-", ".properties");
        Files.writeString(file, "AUDIT_SOURCE=external\nSHIPPING_FREE_THRESHOLD=900000\n"
                + "GHN_ESTIMATED_ITEM_WEIGHT_GRAMS=600\nOUTFIT_ROLE_TOP=custom top\n"
                + "VNPAY_EXPIRY_MINUTES=17\nVNPAY_ORDER_TYPE=999999\n", StandardCharsets.UTF_8);
        System.setProperty("MENSFORM_CONFIG_FILE", file.toAbsolutePath().toString());
        try {
            check("external".equals(AppConfig.require("AUDIT_SOURCE")), "external UTF-8 file");
            System.setProperty("AUDIT_SOURCE", "jvm");
            check("jvm".equals(AppConfig.require("AUDIT_SOURCE")), "JVM overrides file");
            System.setProperty("AUDIT_ENV", "jvm");
            check("environment".equals(AppConfig.require("AUDIT_ENV")), "environment overrides JVM");
            check(AppConfig.integer("MAIL_SMTP_PORT", 1, 65535) > 0, "packaged defaults");
            expectInvalid(() -> AppConfig.require("AUDIT_MISSING"));
            System.setProperty("AUDIT_INTEGER", "-1");
            expectInvalid(() -> AppConfig.integer("AUDIT_INTEGER", 1, 100));
            System.setProperty("AUDIT_DECIMAL", "NaN");
            expectInvalid(() -> AppConfig.decimal("AUDIT_DECIMAL"));
            System.setProperty("AUDIT_URL", "http://example.invalid");
            expectInvalid(() -> AppConfig.httpsUrl("AUDIT_URL"));
            System.setProperty("AUDIT_BOOLEAN", "maybe");
            expectInvalid(() -> AppConfig.bool("AUDIT_BOOLEAN"));
            check(AppConfig.escapeHtml("A & <B>\"'").equals("A &amp; &lt;B&gt;&quot;&#39;"), "HTML escaping");

            check(!ShippingService.qualifiesForFreeShipping(new BigDecimal("899999")),
                    "below configured free-shipping threshold");
            check(ShippingService.qualifiesForFreeShipping(new BigDecimal("900000")),
                    "configured threshold boundary");
            check(!ShippingService.qualifiesForFreeShipping(null), "missing subtotal is not free shipping");
            check(GhnShippingService.weight(33) == 19800, "configured item weight");
            try { GhnShippingService.weight(34); throw new AssertionError("overweight accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            check(new OutfitItem(1, "Example", "custom top", "", "").getRole() == OutfitItem.Role.TOP,
                    "configurable category classification");

            String url = VnPayService.createPaymentUrl(123, new BigDecimal("100000"),
                    "127.0.0.1", "https://example.invalid/payment/vnpay-return");
            Map<String,String> fields = new LinkedHashMap<>();
            for (String pair : url.substring(url.indexOf('?') + 1).split("&")) {
                String[] parts = pair.split("=", 2);
                fields.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
            }
            check("999999".equals(fields.get("vnp_OrderType")), "configured VNPAY order type");
            DateTimeFormatter date = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
            check(LocalDateTime.parse(fields.get("vnp_CreateDate"), date).plusMinutes(17)
                    .equals(LocalDateTime.parse(fields.get("vnp_ExpireDate"), date)), "configured VNPAY expiry");
            check(VnPayService.verifyResponse(fields), "signature preserved");
            fields.put("vnp_Amount", "1");
            check(!VnPayService.verifyResponse(fields), "tampered signature rejected");
            String ipv6Url = VnPayService.createPaymentUrl(123, new BigDecimal("100000"),
                    "::1", "https://example.invalid/payment/vnpay-return");
            check(URLDecoder.decode(ipv6Url, StandardCharsets.UTF_8).contains("vnp_IpAddr=0:0:0:0:0:0:0:1"),
                    "actual IPv6 address preserved");
            for (String invalidIp : new String[]{null, "", "example.invalid", "999.1.1.1"}) {
                try {
                    VnPayService.createPaymentUrl(123, new BigDecimal("100000"), invalidIp,
                            "https://example.invalid/payment/vnpay-return");
                    throw new AssertionError("invalid client IP replaced with fabricated data");
                } catch (IllegalArgumentException expected) { checks++; }
            }
            String passwordHash = PasswordUtil.hashPassword("test-only-password");
            check(PasswordUtil.verifyPassword("test-only-password", passwordHash), "password hash compatibility");
            check(!PasswordUtil.verifyPassword("wrong-password", passwordHash), "incorrect password rejected");
            System.out.println("Configuration regression checks passed: " + checks);
        } finally { Files.deleteIfExists(file); }
    }

    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        checks++;
    }
    private static void expectInvalid(Runnable action) {
        try { action.run(); throw new AssertionError("invalid configuration accepted"); }
        catch (IllegalStateException expected) { checks++; }
    }
}
