package controller;

import dao.AdminProductDAO;
import dao.AdminPromotionDAO;
import dao.PromotionDAO;
import filter.HomePromotionFilter;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import model.Product;
import model.Promotion;
import model.Staff;
import service.ProductImageStore;

/** Controller tests with servlet/DAO doubles; never touches the application's database or photos. */
public class ManagementAccessTest {
    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory(Paths.get("build/verification"), "image-access-");
        Files.createDirectories(root.resolve("7"));
        Files.write(root.resolve("7/00-first.png"), new byte[]{1});
        Files.write(root.resolve("7/01-second.png"), new byte[]{2});
        ProductImageStore store = new ProductImageStore(root);
        ProductImageManagementController manager = new ProductImageManagementController();
        Product product = new Product(); product.setProductID(7); product.setProductName("Test");
        inject(manager, "images", store);
        inject(manager, "variants", new dao.AdminVariantDAO() {
            @Override public List<model.ProductVariant> getVariants(int id) {
                model.ProductVariant variant = new model.ProductVariant();
                variant.setProductID(id); variant.setColor("Đen");
                return List.of(variant);
            }
        });
        inject(manager, "products", new AdminProductDAO() {
            @Override public List<Product> getAllProducts() { return List.of(product); }
            @Override public Product getProductById(int id) { return id == 7 ? product : null; }
            @Override public void updateProduct(Product p, int staffId) { throw new AssertionError("Image editing must not update product metadata"); }
        });
        for (String role : List.of("Admin", "Warehouse")) {
            Exchange exchange = new Exchange(role);
            exchange.parameters.put("productId", "7");
            manager.doGet(exchange.request(), exchange.response());
            check("/admin/product-images.jsp".equals(exchange.forward), role + " can open image manager");
            exchange.parameters.put("csrfToken", exchange.session.get("productImages.csrf").toString());
            exchange.parameters.put("action", "cover");
            exchange.parameters.put("file", "01-second.png");
            manager.doPost(exchange.request(), exchange.response());
            check(store.list(7).get(0).equals("01-second.png"), role + " can choose a cover");
            check(exchange.redirect.contains(role.equals("Warehouse") ? "/staff/warehouse/" : "/admin/"), "Role-specific redirect");
            exchange.parameters.put("action", "color");
            exchange.parameters.put("color", "Đen");
            manager.doPost(exchange.request(), exchange.response());
            check("Đen".equals(store.imageColors(7).get("01-second.png")), role + " can assign a product variant color");
            exchange.parameters.put("color", "Màu không thuộc sản phẩm");
            manager.doPost(exchange.request(), exchange.response());
            check("Đen".equals(store.imageColors(7).get("01-second.png")), "Invalid variant color cannot alter mapping");
        }
        Exchange blocked = new Exchange("Sales");
        manager.doPost(blocked.request(), blocked.response());
        check(blocked.status == 403, "Sales cannot mutate images");
        Exchange inactive = new Exchange("Warehouse");
        ((Staff) inactive.session.get("staff")).setStatus("Inactive");
        manager.doGet(inactive.request(), inactive.response());
        check(inactive.status == 403, "Inactive staff denied");
        Exchange anonymous = new Exchange(null);
        manager.doGet(anonymous.request(), anonymous.response());
        check("/store/login".equals(anonymous.redirect), "Anonymous returns to login");
        Exchange invalidToken = new Exchange("Warehouse");
        invalidToken.parameters.putAll(Map.of("productId", "7", "action", "delete", "file", "01-second.png"));
        manager.doPost(invalidToken.request(), invalidToken.response());
        check(invalidToken.status == 403 && store.list(7).size() == 2, "Invalid token cannot delete images");
        Exchange valid = new Exchange("Warehouse");
        manager.doGet(valid.request(), valid.response());
        valid.parameters.putAll(invalidToken.parameters);
        valid.parameters.put("csrfToken", valid.session.get("productImages.csrf").toString());
        manager.doPost(valid.request(), valid.response());
        check(store.list(7).size() == 1, "Warehouse can delete image with valid session token");

        AdminPromotionController promotions = new AdminPromotionController();
        int[] calls = new int[3];
        inject(promotions, "promotionDAO", new AdminPromotionDAO() {
            @Override public List<Promotion> getAllPromotions() { return List.of(); }
            @Override public int createPromotion(Promotion p) { calls[0]++; return 9; }
            @Override public void updatePromotion(Promotion p) { check(p.getPromotionID() == 9, "Update ID"); calls[1]++; }
            @Override public void deletePromotion(int id) { check(id == 9, "Delete ID"); calls[2]++; }
        });
        Exchange admin = new Exchange("Admin");
        promotions.doGet(admin.request(), admin.response());
        admin.parameters.putAll(Map.of("csrfToken", admin.session.get("adminPromotions.csrf").toString(),
                "promotionId", "9", "promotionCode", "STYLE10", "promotionName", "Style offer",
                "discountType", "Percentage", "discountValue", "10", "startDate", "2026-10-01T00:00", "endDate", "2026-10-31T23:59"));
        admin.parameters.put("quantity", "10");
        for (String action : List.of("create", "update", "delete")) {
            admin.parameters.put("action", action);
            promotions.doPost(admin.request(), admin.response());
        }
        check(calls[0] == 1 && calls[1] == 1 && calls[2] == 1, "Admin can create/update/delete promotions");
        Exchange warehouse = new Exchange("Warehouse");
        warehouse.parameters.putAll(admin.parameters);
        promotions.doPost(warehouse.request(), warehouse.response());
        check(warehouse.status == 403 && calls[2] == 1, "Warehouse cannot manage promotions");
        admin.parameters.put("csrfToken", "invalid");
        promotions.doPost(admin.request(), admin.response());
        check(admin.status == 403 && calls[2] == 1, "Promotion deletion requires a valid token");

        HomePromotionFilter home = new HomePromotionFilter();
        Promotion offer = new Promotion(); offer.setPromotionCode("STYLE10");
        inject(home, "promotions", new PromotionDAO() {
            @Override public List<Promotion> getAvailablePromotions() { return List.of(offer); }
        });
        Exchange visitor = new Exchange(null);
        boolean[] continued = {false};
        home.doFilter(visitor.request(), visitor.response(), (req, res) -> continued[0] = true);
        check(continued[0] && ((List<?>) visitor.attributes.get("homePromotions")).size() == 1, "Homepage receives available offers");
        inject(home, "promotions", new PromotionDAO() {
            @Override public List<Promotion> getAvailablePromotions() throws SQLException { throw new SQLException("offline"); }
        });
        continued[0] = false;
        home.doFilter(visitor.request(), visitor.response(), (req, res) -> continued[0] = true);
        check(continued[0] && Boolean.TRUE.equals(visitor.attributes.get("homePromotionsUnavailable")), "Homepage still renders if promotions cannot load");
        System.out.println("PASS: Admin/Warehouse image permissions, cover/delete actions, blocked roles/tokens, Admin promotion CRUD, and homepage offers/failure fallback.");
    }

    private static void inject(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }

    private static class Exchange {
        final Map<String, Object> session = new HashMap<>(), attributes = new HashMap<>();
        final Map<String, String> parameters = new HashMap<>();
        int status; String redirect, forward;
        Exchange(String role) {
            if (role != null) { Staff staff = new Staff(); staff.setRole(role); staff.setStatus("Active"); session.put("staff", staff); }
        }
        HttpServletRequest request() {
            HttpSession sessionProxy = (HttpSession) Proxy.newProxyInstance(HttpSession.class.getClassLoader(), new Class<?>[]{HttpSession.class}, (o, method, args) -> {
                switch (method.getName()) {
                    case "getAttribute": return session.get(args[0]);
                    case "setAttribute": session.put((String) args[0], args[1]); return null;
                    case "removeAttribute": session.remove(args[0]); return null;
                    default: return null;
                }
            });
            return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class}, (o, method, args) -> {
                switch (method.getName()) {
                    case "getSession": return sessionProxy;
                    case "getParameter": return parameters.get(args[0]);
                    case "getContextPath": return "/store";
                    case "getAttribute": return attributes.get(args[0]);
                    case "setAttribute": attributes.put((String) args[0], args[1]); return null;
                    case "getServletContext": return Proxy.newProxyInstance(ServletContext.class.getClassLoader(), new Class<?>[]{ServletContext.class}, (c, action, inputs) -> null);
                    case "getRequestDispatcher":
                        String path = (String) args[0];
                        return Proxy.newProxyInstance(RequestDispatcher.class.getClassLoader(), new Class<?>[]{RequestDispatcher.class}, (d, action, inputs) -> { forward = path; return null; });
                    default: return null;
                }
            });
        }
        HttpServletResponse response() {
            return (HttpServletResponse) Proxy.newProxyInstance(HttpServletResponse.class.getClassLoader(), new Class<?>[]{HttpServletResponse.class}, (o, method, args) -> {
                if ("sendError".equals(method.getName())) status = (Integer) args[0];
                if ("sendRedirect".equals(method.getName())) redirect = (String) args[0];
                return null;
            });
        }
    }
}
