package controller;

import dao.CheckoutDAO;
import dao.CustomerAddressDAO;
import dao.PromotionDAO;
import java.math.BigDecimal;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Cart;
import model.Customer;
import model.CustomerAddress;
import service.ShippingService;

@WebServlet("/checkout")
public class CheckoutController extends HttpServlet {

    private static final String CART_KEY = "CART";
    private static final String ERROR_KEY = "CHECKOUT_ERROR";

    private CheckoutDAO checkoutDAO;
    private CustomerAddressDAO addressDAO;
    private final ShippingService shipping = new ShippingService();

    @Override
    public void init() {
        checkoutDAO = new CheckoutDAO();
        addressDAO = new CustomerAddressDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        Customer customer = getCustomer(session);

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Cart cart = getCart(session);

        if (cart == null || cart.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/cart");
            return;
        }

        request.setAttribute("cart", cart);
        request.setAttribute("customer", customer);
        request.setAttribute("error", takeError(session));
        String promotionCode = request.getParameter("promotionCode");
        if (promotionCode == null) {
            promotionCode = (String) session.getAttribute("CHECKOUT_PROMOTION_CODE");
        }
        promotionCode = value(promotionCode);
        session.setAttribute("CHECKOUT_PROMOTION_CODE", promotionCode);
        BigDecimal discount = BigDecimal.ZERO;
        if (!promotionCode.isEmpty()) {
            try {
                discount = new PromotionDAO().findAvailableByCode(promotionCode)
                        .calculateDiscount(cart.getSubtotal());
            } catch (IllegalArgumentException e) {
                request.setAttribute("promotionError", e.getMessage());
            } catch (SQLException e) {
                getServletContext().log("Could not check promotion.", e);
                request.setAttribute("promotionError", "Chưa kiểm tra được mã khuyến mãi. Vui lòng thử lại.");
            }
        }
        request.setAttribute("promotionCode", promotionCode);
        request.setAttribute("discountAmount", discount);
        try {
            List<CustomerAddress> addresses = addressDAO.findByCustomer(
                    customer.getCustomerID()
            );
            request.setAttribute("addresses", addresses);
            CustomerAddress selected = addresses.stream().filter(CustomerAddress::isDefaultAddress).findFirst()
                    .orElse(addresses.isEmpty() ? null : addresses.get(0));
            String selectedId = request.getParameter("addressId");
            if ("new".equals(selectedId)) selected = null;
            else if (selectedId != null) {
                selected = addresses.stream().filter(a -> Integer.toString(a.getAddressID()).equals(selectedId)).findFirst().orElse(null);
            }
            ShippingService.Quote quote = shipping.quote(selected == null ? "" : selected.locationAddress());
            request.setAttribute("selectedAddressId", selected == null ? "new" : Integer.toString(selected.getAddressID()));
            request.setAttribute("shippingQuote", quote);
            request.setAttribute("shippingFee", quote.fee);
            request.setAttribute("checkoutTotal", cart.getSubtotal().subtract(discount).add(quote.fee));
        } catch (SQLException e) {
            throw new ServletException("Could not load saved shipping addresses.", e);
        }

        request.getRequestDispatcher("/checkout.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        request.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        Customer customer = getCustomer(session);

        if (customer == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        Cart cart = getCart(session);

        if (cart == null || cart.isEmpty()) {
            response.sendRedirect(request.getContextPath() + "/cart");
            return;
        }

        String paymentMethod = value(request.getParameter("paymentMethod"));

        // VNPAY will be enabled when its signed payment request and callback
        // are implemented. Do not create a VNPAY order as if it were paid.
        if (!"COD".equals(paymentMethod)) {
            session.setAttribute(
                    ERROR_KEY,
                    "COD checkout is currently available."
            );
            response.sendRedirect(request.getContextPath() + "/checkout");
            return;
        }

        String submittedAddressId = value(request.getParameter("addressId"));
        String shippingAddress;
        CustomerAddress deliveryAddress;

        if (!submittedAddressId.isEmpty() && !"new".equals(submittedAddressId)) {
            try {
                int addressID = Integer.parseInt(submittedAddressId);
                CustomerAddress address = addressDAO.findById(
                        addressID,
                        customer.getCustomerID()
                );
                if (address == null) {
                    session.setAttribute(ERROR_KEY, "Địa chỉ giao hàng không hợp lệ.");
                    response.sendRedirect(request.getContextPath() + "/checkout");
                    return;
                }
                shippingAddress = formatShippingAddress(address);
                deliveryAddress = address;
            } catch (NumberFormatException e) {
                session.setAttribute(ERROR_KEY, "Địa chỉ giao hàng không hợp lệ.");
                response.sendRedirect(request.getContextPath() + "/checkout");
                return;
            } catch (SQLException e) {
                throw new ServletException("Could not load selected shipping address.", e);
            }
        } else {
            CustomerAddress newAddress = new CustomerAddress();
            newAddress.setRecipientName(value(request.getParameter("recipientName")));
            newAddress.setPhone(value(request.getParameter("recipientPhone")));
            newAddress.setAddressLine(value(request.getParameter("addressLine")));
            newAddress.setWard(value(request.getParameter("ward")));
            newAddress.setDistrict(value(request.getParameter("district")));
            newAddress.setProvince(value(request.getParameter("province")));

            String addressError = validateNewAddress(newAddress);
            if (addressError != null) {
                session.setAttribute(ERROR_KEY, addressError);
                response.sendRedirect(request.getContextPath() + "/checkout");
                return;
            }

            shippingAddress = formatShippingAddress(newAddress);
            deliveryAddress = newAddress;
            if (shippingAddress.length() > 500) {
                session.setAttribute(ERROR_KEY, "Địa chỉ giao hàng vượt quá độ dài cho phép.");
                response.sendRedirect(request.getContextPath() + "/checkout");
                return;
            }

            try {
                // Save checkout-entered addresses in the customer's address book.
                newAddress.setAddressID(addressDAO.save(newAddress, customer.getCustomerID()));
            } catch (SQLException e) {
                throw new ServletException("Could not save the new shipping address.", e);
            }
        }

        String notes = value(request.getParameter("notes"));
        String promotionCode = value(request.getParameter("promotionCode"));
        session.setAttribute("CHECKOUT_PROMOTION_CODE", promotionCode);

        if (shippingAddress.isEmpty()
                || shippingAddress.length() > 500
                || notes.length() > 1000) {
            session.setAttribute(
                    ERROR_KEY,
                    "Enter a valid shipping address and order note."
            );
            response.sendRedirect(request.getContextPath() + "/checkout");
            return;
        }

        ShippingService.Quote shippingQuote = shipping.quote(deliveryAddress.locationAddress());
        String previewFee = request.getParameter("shippingFeePreview");
        if (previewFee != null) {
            try {
                if (new BigDecimal(previewFee).compareTo(shippingQuote.fee) != 0) {
                    session.setAttribute(ERROR_KEY, "Phí giao hàng đã thay đổi. Vui lòng kiểm tra tổng tiền và đặt hàng lại.");
                    response.sendRedirect(request.getContextPath() + "/checkout?addressId=" + deliveryAddress.getAddressID());
                    return;
                }
            } catch (NumberFormatException e) {
                session.setAttribute(ERROR_KEY, "Phí giao hàng không hợp lệ. Vui lòng kiểm tra lại địa chỉ.");
                response.sendRedirect(request.getContextPath() + "/checkout");
                return;
            }
        }
        try {
            int orderID = checkoutDAO.createCodOrder(
                    customer.getCustomerID(),
                    cart,
                    shippingAddress,
                    notes,
                    promotionCode,
                    shippingQuote.fee
            );

            cart.clear();
            session.removeAttribute("CHECKOUT_PROMOTION_CODE");

            response.sendRedirect(
                    request.getContextPath()
                    + "/checkout-success.jsp?orderId=" + orderID
            );

        } catch (IllegalArgumentException | IllegalStateException e) {
            session.setAttribute(ERROR_KEY, e.getMessage());
            response.sendRedirect(request.getContextPath() + "/checkout");

        } catch (SQLException e) {
            throw new ServletException(
                    "Could not complete checkout.",
                    e
            );
        }
    }

    private Customer getCustomer(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute("customer");
        return value instanceof Customer ? (Customer) value : null;
    }

    private Cart getCart(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(CART_KEY);
        return value instanceof Cart ? (Cart) value : null;
    }

    private String takeError(HttpSession session) {
        if (session == null) {
            return null;
        }

        Object error = session.getAttribute(ERROR_KEY);
        session.removeAttribute(ERROR_KEY);
        return error == null ? null : error.toString();
    }

    private String value(String text) {
        return text == null ? "" : text.trim();
    }

    private String formatShippingAddress(CustomerAddress address) {
        StringBuilder formatted = new StringBuilder();
        appendPart(formatted, address.getRecipientName());
        appendPart(formatted, address.getPhone());
        appendPart(formatted, address.getAddressLine());
        appendPart(formatted, address.getWard());
        appendPart(formatted, address.getDistrict());
        appendPart(formatted, address.getProvince());
        return formatted.toString();
    }

    private void appendPart(StringBuilder formatted, String part) {
        String cleanPart = value(part);
        if (!cleanPart.isEmpty()) {
            if (formatted.length() > 0) {
                formatted.append(", ");
            }
            formatted.append(cleanPart);
        }
    }

    private String validateNewAddress(CustomerAddress address) {
        if (address.getRecipientName().isEmpty()
                || address.getRecipientName().length() > 300) {
            return "Vui lòng nhập tên người nhận hợp lệ.";
        }
        if (!address.getPhone().matches("^[0-9+(). -]{8,20}$")) {
            return "Vui lòng nhập số điện thoại người nhận hợp lệ.";
        }
        if (address.getAddressLine().isEmpty()
                || address.getAddressLine().length() > 600) {
            return "Vui lòng nhập địa chỉ chi tiết hợp lệ.";
        }
        if (address.getWard().length() > 100
                || address.getDistrict().length() > 100) {
            return "Phường hoặc quận/huyện vượt quá độ dài cho phép.";
        }
        if (address.getProvince().isEmpty()
                || address.getProvince().length() > 100) {
            return "Vui lòng nhập tỉnh hoặc thành phố.";
        }
        return null;
    }
}
