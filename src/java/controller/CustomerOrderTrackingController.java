package controller;

import dao.CustomerOrderDAO;
import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.*;
import model.Customer;
import model.CustomerOrder;
import util.ShippingJson;

/** Read saved tracking only; ownership is checked in SQL on every request. */
@WebServlet("/customer/order-tracking")
public class CustomerOrderTrackingController extends HttpServlet {
    @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");response.setHeader("Cache-Control","no-store");
        HttpSession session=request.getSession(false);
        Object account=session==null?null:session.getAttribute("customer");
        if(!(account instanceof Customer)){reply(response,401,"Vui lòng đăng nhập lại.");return;}
        try {
            String selection=request.getParameter("orderIds");
            if(selection==null || selection.length()>1200)throw new IllegalArgumentException();
            Set<Integer> ids=new LinkedHashSet<>();
            for(String token:selection.split(",",-1)) {
                if(!token.matches("[1-9][0-9]{0,9}"))throw new IllegalArgumentException();
                ids.add(Integer.parseInt(token));
            }
            if(ids.isEmpty() || ids.size()>100)throw new IllegalArgumentException();
            List<CustomerOrder> orders=new CustomerOrderDAO().getTrackingOrders(((Customer)account).getCustomerID(),new ArrayList<>(ids));
            if(orders.size()!=ids.size()){reply(response,404,"Không tìm thấy đơn hàng.");return;}
            List<Object> rows=new ArrayList<>();
            for(CustomerOrder order:orders) {
                Map<String,Object> row=new LinkedHashMap<>();
                row.put("orderId",order.getOrderID());row.put("orderStatus",order.getOrderStatus());
                row.put("orderStatusLabel",order.getOrderStatusLabel());row.put("shippingStatusLabel",order.getShippingStatusLabel());
                row.put("paymentStatusLabel",order.getPaymentStatusLabel());row.put("carrierStatus",order.getCarrierStatus());
                row.put("trackingNumber",order.getTrackingNumber());row.put("carrier",order.getCarrier());
                row.put("lastSyncAt",order.getLastCarrierSync()==null?null:order.getLastCarrierSync().toLocalDateTime().toInstant(java.time.ZoneOffset.UTC).toString());
                row.put("shippingNotice",order.getShippingNotice());
                row.put("requestAllowed",Set.of("Pending","Confirmed","Delivered").contains(order.getOrderStatus()));
                rows.add(row);
            }
            response.getWriter().write(ShippingJson.write(Collections.singletonMap("orders",rows)));
        } catch(IllegalArgumentException e){reply(response,400,"Danh sách đơn hàng không hợp lệ.");}
        catch(SQLException e) {
            getServletContext().log("Could not read customer tracking (SQLState "+e.getSQLState()+").");
            reply(response,503,"Chưa tải được cập nhật. Hệ thống sẽ thử lại.");
        }
    }
    private void reply(HttpServletResponse response,int status,String message) throws IOException {
        response.setStatus(status);response.getWriter().write(ShippingJson.write(Collections.singletonMap("error",message)));
    }
}
