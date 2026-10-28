package controller;

import dao.CheckoutDAO;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import util.VnPayService;

@WebServlet(urlPatterns = {"/payment/vnpay-return", "/payment/vnpay-ipn"})
public class VnPayCallbackController extends HttpServlet {

    private final CheckoutDAO checkoutDAO = new CheckoutDAO();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        boolean ipn = request.getServletPath().endsWith("vnpay-ipn");
        request.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");

        Map<String, String> params = readParams(request);
        String code = "99";
        String message = "Invalid payment notification";
        boolean valid = false;
        int orderID = parsePositiveInt(params.get("vnp_TxnRef"));

        try {
            if (orderID > 0 && VnPayService.verifyResponse(params)) {
                BigDecimal amount = parseAmount(params.get("vnp_Amount"));
                String transactionCode = params.get("vnp_TransactionNo");
                String responseCode = params.get("vnp_ResponseCode");
                String transactionStatus = params.get("vnp_TransactionStatus");
                if (amount != null && transactionCode != null && !transactionCode.trim().isEmpty()
                        && responseCode != null && transactionStatus != null) {
                    boolean[] newlyPaid = new boolean[1];
                    valid = checkoutDAO.recordVnPayResult(orderID, transactionCode,
                            responseCode, transactionStatus, amount,
                            params.get("vnp_Message"), newlyPaid);
                    if (valid) {
                        if (newlyPaid[0]) service.OrderPaidEmailService.notifyPaidOrder(orderID);
                        code = "00";
                        message = "Confirm Success";
                    } else {
                        code = "01";
                        message = "Order or amount does not match";
                    }
                } else {
                    code = "97";
                    message = "Invalid payment data";
                }
            } else {
                code = "97";
                message = "Invalid signature or order reference";
            }
        } catch (IllegalStateException e) {
            getServletContext().log("VNPay callback configuration is incomplete.", e);
            code = "99";
            message = "Merchant configuration error";
        } catch (Exception e) {
            getServletContext().log("Could not process VNPay callback.", e);
            code = "99";
            message = "Could not update payment";
        }

        if (ipn) {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"RspCode\":\"" + code
                    + "\",\"Message\":\"" + message + "\"}");
            return;
        }

        request.setAttribute("orderId", orderID > 0 ? orderID : null);
        request.setAttribute("paymentSuccess", valid
                && "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus")));
        request.setAttribute("paymentMessage", valid
                ? ("00".equals(params.get("vnp_ResponseCode"))
                    && "00".equals(params.get("vnp_TransactionStatus"))
                    ? "Thanh toán VNPay thành công. Đơn hàng đã được cập nhật."
                    : "Giao dịch chưa thành công. Bạn có thể kiểm tra lại trong đơn hàng.")
                : "Không xác minh được kết quả thanh toán. Vui lòng liên hệ cửa hàng trước khi thanh toán lại.");
        request.getRequestDispatcher("/payment-result.jsp").forward(request, response);
    }

    private Map<String, String> readParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> entry : request.getParameterMap().entrySet()) {
            String[] values = entry.getValue();
            if (values != null && values.length == 1) {
                params.put(entry.getKey(), values[0]);
            }
        }
        return params;
    }

    private int parsePositiveInt(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    private BigDecimal parseAmount(String amount) {
        try {
            if (amount == null || !amount.matches("[0-9]{1,12}")) return null;
            return new BigDecimal(amount).movePointLeft(2);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
