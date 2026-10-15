package model;

import java.math.BigDecimal;

/** Run with: java model.PromotionTest (no external test library required). */
public class PromotionTest {

    public static void main(String[] args) {
        Promotion promotion = new Promotion();
        promotion.setDiscountType("Percentage");
        promotion.setDiscountValue(new BigDecimal("10"));
        check("50000.00", promotion.calculateDiscount(new BigDecimal("500000")));

        promotion.setMaxDiscount(new BigDecimal("30000"));
        check("30000.00", promotion.calculateDiscount(new BigDecimal("500000")));

        promotion.setMinOrderValue(new BigDecimal("500000"));
        expectRejected(promotion, "499999");
        check("30000.00", promotion.calculateDiscount(new BigDecimal("500000")));

        promotion.setMinOrderValue(null);
        promotion.setMaxDiscount(null);
        promotion.setDiscountType("Fixed");
        promotion.setDiscountValue(new BigDecimal("600000"));
        check("500000.00", promotion.calculateDiscount(new BigDecimal("500000")));
        check("0.00", promotion.calculateDiscount(BigDecimal.ZERO));

        promotion.setDiscountType("Percentage");
        promotion.setDiscountValue(new BigDecimal("12.5"));
        check("12500.13", promotion.calculateDiscount(new BigDecimal("100001")));
        promotion.setDiscountValue(new BigDecimal("100"));
        check("100001.00", promotion.calculateDiscount(new BigDecimal("100001")));
        promotion.setDiscountValue(new BigDecimal("101"));
        expectRejected(promotion, "100001");
        promotion.setDiscountType("Unknown");
        expectRejected(promotion, "100001");
        System.out.println("PromotionTest: all 10 cases passed.");
    }

    private static void check(String expected, BigDecimal actual) {
        if (new BigDecimal(expected).compareTo(actual) != 0) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
    }

    private static void expectRejected(Promotion promotion, String subtotal) {
        try {
            promotion.calculateDiscount(new BigDecimal(subtotal));
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected the promotion to be rejected.");
    }
}
