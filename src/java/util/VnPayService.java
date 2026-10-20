package util;

import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class VnPayService {

    private static final String PAYMENT_URL
            = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";

    // Mã danh mục VNPAY: Thời trang Nam
    private static final String ORDER_TYPE = "200003";

    private static final ZoneId VIETNAM_ZONE
            = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter DATE_FORMAT
            = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private VnPayService() {
    }

    public static void validateConfiguration() {
        String tmnCode = requiredEnvironmentValue("VNPAY_TMN_CODE");
        requiredEnvironmentValue("VNPAY_HASH_SECRET");
        if (!tmnCode.matches("[A-Za-z0-9]{8}")) {
            throw new IllegalStateException(
                    "VNPAY_TMN_CODE must contain 8 letters or digits."
            );
        }
    }

    public static String resolveReturnUrl(String localDefault) {
        String configured = System.getenv("VNPAY_RETURN_URL");
        return configured == null || configured.trim().isEmpty()
                ? localDefault : configured.trim();
    }

    public static String createPaymentUrl(
            int orderID,
            BigDecimal amountVnd,
            String clientIp,
            String returnUrl) {

        if (orderID <= 0) {
            throw new IllegalArgumentException("Order ID must be positive.");
        }

        if (amountVnd == null || amountVnd.signum() <= 0) {
            throw new IllegalArgumentException(
                    "Payment amount must be greater than zero."
            );
        }

        if (returnUrl == null || returnUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("Return URL is required.");
        }

        String tmnCode = requiredEnvironmentValue("VNPAY_TMN_CODE");
        String hashSecret = requiredEnvironmentValue("VNPAY_HASH_SECRET");

        if (!tmnCode.matches("[A-Za-z0-9]{8}")) {
            throw new IllegalStateException(
                    "VNPAY_TMN_CODE must contain 8 letters or digits."
            );
        }

        String amountMinorUnits;

        try {
            amountMinorUnits = amountVnd
                    .movePointRight(2)
                    .setScale(0, RoundingMode.UNNECESSARY)
                    .toPlainString();
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(
                    "Payment amount must have at most 2 decimal places.",
                    e
            );
        }

        // VNPAY quy định vnp_Amount có tối đa 12 chữ số.
        if (amountMinorUnits.length() > 12) {
            throw new IllegalArgumentException(
                    "Payment amount exceeds VNPAY's allowed limit."
            );
        }

        ZonedDateTime now = ZonedDateTime.now(VIETNAM_ZONE);

        Map<String, String> params = new TreeMap<>();
        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", tmnCode);
        params.put("vnp_Amount", amountMinorUnits);
        params.put("vnp_CurrCode", "VND");

        // Mã đơn hàng được dùng làm mã tham chiếu giao dịch.
        params.put("vnp_TxnRef", String.valueOf(orderID));

        // Nội dung không dấu, không chứa ký tự đặc biệt.
        params.put("vnp_OrderInfo", "Thanh toan don hang " + orderID);
        params.put("vnp_OrderType", ORDER_TYPE);
        params.put("vnp_Locale", "vn");
        params.put("vnp_ReturnUrl", returnUrl.trim());
        params.put("vnp_IpAddr", normalizeIp(clientIp));
        params.put("vnp_CreateDate", now.format(DATE_FORMAT));
        params.put(
                "vnp_ExpireDate",
                now.plusMinutes(15).format(DATE_FORMAT)
        );

        // API 2.1.0 ký dữ liệu đã URL-encode và sắp xếp theo tên tham số.
        String signedData = buildEncodedData(params);
        String secureHash = hmacSha512(hashSecret, signedData);

        return PAYMENT_URL + "?"
                + buildEncodedData(params)
                + "&vnp_SecureHash=" + secureHash;
    }

    public static boolean verifyResponse(
            Map<String, String> responseParams) {

        if (responseParams == null || responseParams.isEmpty()) {
            return false;
        }

        String receivedHash = responseParams.get("vnp_SecureHash");
        String tmnCode = requiredEnvironmentValue("VNPAY_TMN_CODE");
        String hashSecret = requiredEnvironmentValue("VNPAY_HASH_SECRET");

        if (receivedHash == null
                || !receivedHash.matches("[0-9a-fA-F]{128}")
                || !tmnCode.equals(responseParams.get("vnp_TmnCode"))) {
            return false;
        }

        Map<String, String> signedParams = new TreeMap<>();

        for (Map.Entry<String, String> entry : responseParams.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            if (key != null
                    && key.startsWith("vnp_")
                    && value != null
                    && !value.isEmpty()
                    && !"vnp_SecureHash".equals(key)
                    && !"vnp_SecureHashType".equals(key)) {
                signedParams.put(key, value);
            }
        }

        String calculatedHash = hmacSha512(
                hashSecret,
                buildEncodedData(signedParams)
        );

        return MessageDigest.isEqual(
                calculatedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII),
                receivedHash.toLowerCase().getBytes(StandardCharsets.US_ASCII)
        );
    }

    private static String buildEncodedData(Map<String, String> params) {
        StringBuilder data = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (data.length() > 0) {
                data.append('&');
            }

            data.append(urlEncode(entry.getKey()))
                    .append('=')
                    .append(urlEncode(entry.getValue()));
        }

        return data.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("UTF-8 is unavailable.", e);
        }
    }

    private static String hmacSha512(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec key = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8),
                    "HmacSHA512"
            );

            mac.init(key);

            byte[] bytes = mac.doFinal(
                    data.getBytes(StandardCharsets.UTF_8)
            );

            StringBuilder hex = new StringBuilder(bytes.length * 2);

            for (byte value : bytes) {
                hex.append(String.format("%02x", value & 0xff));
            }

            return hex.toString();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not sign the VNPAY request.",
                    e
            );
        }
    }

    private static String requiredEnvironmentValue(String name) {
        String value = System.getenv(name);

        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Set the " + name + " environment variable."
            );
        }

        return value.trim();
    }

    private static String normalizeIp(String ip) {
        if (ip == null || ip.trim().isEmpty()) {
            return "127.0.0.1";
        }

        String normalized = ip.trim();

        if (normalized.length() < 7 || normalized.length() > 45) {
            return "127.0.0.1";
        }

        return normalized;
    }
}
