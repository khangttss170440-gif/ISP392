package controller;

import dao.ProductCatalogDAO;
import java.io.IOException;
import java.sql.SQLException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Cart;
import model.CartItem;
import model.Customer;
import model.ProductVariant;

@WebServlet("/cart")
public class CartController extends HttpServlet {

    private static final String CART_KEY = "CART";
    private static final String MESSAGE_KEY = "CART_MESSAGE";
    private static final String ERROR_KEY = "CART_ERROR";
    private static final int MAX_ITEM_QUANTITY = util.AppConfig.integer("CART_MAX_ITEM_QUANTITY", 1, 1000000);

    private ProductCatalogDAO productCatalogDAO;

    @Override
    public void init() {
        productCatalogDAO = new ProductCatalogDAO();
    }

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (!isCustomerLoggedIn(session)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        request.setAttribute("cart", getCart(session));
        request.setAttribute("message", takeSessionValue(session, MESSAGE_KEY));
        request.setAttribute("error", takeSessionValue(session, ERROR_KEY));

        request.getRequestDispatcher("/cart.jsp")
                .forward(request, response);
    }

    @Override
    protected void doPost(
            HttpServletRequest request,
            HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);

        if (!isCustomerLoggedIn(session)) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        try {
            String action = request.getParameter("action");

            if ("add".equals(action)) {
                addItem(request, session);
            } else if ("update".equals(action)) {
                updateItem(request, session);
            } else if ("remove".equals(action)) {
                removeItem(request, session);
            } else if ("clear".equals(action)) {
                getCart(session).clear();
                session.setAttribute(MESSAGE_KEY, "Cart cleared.");
            } else {
                session.setAttribute(ERROR_KEY, "Invalid cart action.");
            }

            response.sendRedirect(request.getContextPath() + "/cart");

        } catch (SQLException e) {
            throw new ServletException("Could not update the cart.", e);
        }
    }

    private void addItem(
            HttpServletRequest request,
            HttpSession session) throws SQLException {

        Integer variantID = positiveInt(request.getParameter("variantId"));
        Integer quantity = positiveInt(request.getParameter("quantity"));

        if (variantID == null || quantity == null
                || quantity > MAX_ITEM_QUANTITY) {
            session.setAttribute(ERROR_KEY, "Invalid variant or quantity.");
            return;
        }

        ProductVariant variant =
                productCatalogDAO.getActiveVariantById(variantID);

        if (variant == null) {
            session.setAttribute(ERROR_KEY, "This variant is unavailable.");
            return;
        }

        Cart cart = getCart(session);
        CartItem current = cart.getItem(variantID);
        int existingQuantity = current == null ? 0 : current.getQuantity();
        int newQuantity = existingQuantity + quantity;

        if (newQuantity > MAX_ITEM_QUANTITY
                || newQuantity > variant.getQuantity()) {
            session.setAttribute(
                    ERROR_KEY,
                    "Requested quantity exceeds available stock."
            );
            return;
        }

        cart.addItem(variant, quantity);
        session.setAttribute(MESSAGE_KEY, "Product added to cart.");
    }

    private void updateItem(
            HttpServletRequest request,
            HttpSession session) throws SQLException {

        Integer variantID = positiveInt(request.getParameter("variantId"));
        Integer quantity = nonNegativeInt(request.getParameter("quantity"));

        if (variantID == null || quantity == null
                || quantity > MAX_ITEM_QUANTITY) {
            session.setAttribute(ERROR_KEY, "Invalid variant or quantity.");
            return;
        }

        Cart cart = getCart(session);

        if (quantity == 0) {
            cart.removeItem(variantID);
            session.setAttribute(MESSAGE_KEY, "Product removed from cart.");
            return;
        }

        ProductVariant variant =
                productCatalogDAO.getActiveVariantById(variantID);

        if (variant == null || quantity > variant.getQuantity()) {
            session.setAttribute(
                    ERROR_KEY,
                    "Requested quantity exceeds available stock."
            );
            return;
        }

        cart.updateItem(variant, quantity);
        session.setAttribute(MESSAGE_KEY, "Cart updated.");
    }

    private void removeItem(
            HttpServletRequest request,
            HttpSession session) {

        Integer variantID = positiveInt(request.getParameter("variantId"));

        if (variantID == null) {
            session.setAttribute(ERROR_KEY, "Invalid variant.");
            return;
        }

        getCart(session).removeItem(variantID);
        session.setAttribute(MESSAGE_KEY, "Product removed from cart.");
    }

    private Cart getCart(HttpSession session) {
        Object value = session.getAttribute(CART_KEY);

        if (value instanceof Cart) {
            return (Cart) value;
        }

        Cart cart = new Cart();
        session.setAttribute(CART_KEY, cart);
        return cart;
    }

    private boolean isCustomerLoggedIn(HttpSession session) {
        return session != null
                && session.getAttribute("customer") instanceof Customer;
    }

    private Integer positiveInt(String value) {
        Integer number = nonNegativeInt(value);
        return number != null && number > 0 ? number : null;
    }

    private Integer nonNegativeInt(String value) {
        try {
            int number = Integer.parseInt(value);
            return number >= 0 ? number : null;
        } catch (Exception e) {
            return null;
        }
    }
    

    private Object takeSessionValue(HttpSession session, String key) {
        Object value = session.getAttribute(key);
        session.removeAttribute(key);
        return value;
    }
}