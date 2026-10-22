package dao;

import java.sql.*;
import java.util.*;
import java.math.BigDecimal;
import model.OrderLine;
import service.GhnClient;
import util.DBConnection;
import util.ShippingJson;

/** Persistent booking identity makes retries safe even if the carrier response is lost. */
public class GhnDeliveryDAO {
    public static void insert(Connection c,int order,String recipient) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("INSERT dbo.GhnDelivery(OrderID,ShopID,ClientOrderCode,RecipientJson) VALUES(?,?,?,?)")) {
            s.setInt(1,order);s.setInt(2,GhnClient.shopId());
            s.setString(3,"MW-"+UUID.randomUUID().toString());s.setNString(4,recipient);s.executeUpdate();
        }
    }
    public Map<String,Object> find(int order) throws SQLException {
        try(Connection c=DBConnection.getConnection(); Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT OBJECT_ID('dbo.GhnDelivery','U')")) {
            r.next(); if(r.getObject(1)==null) return null;
            return load(c,order);
        }
    }
    private Map<String,Object> load(Connection c,int order) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("SELECT * FROM dbo.GhnDelivery WHERE OrderID=?")) {
            s.setInt(1,order);
            try(ResultSet r=s.executeQuery()) {
                if(!r.next()) return null;
                Map<String,Object> row=new LinkedHashMap<>();
                for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.put(r.getMetaData().getColumnName(i),r.getObject(i));
                return row;
            }
        }
    }
    public void book(int order,int weight,int length,int width,int height) throws SQLException {
        if(weight<1 || weight>=20_000 || length<1 || length>200 || width<1 || width>200 || height<1 || height>200)
            throw new IllegalArgumentException("Nhập cân nặng 1–19.999 g, mỗi chiều kiện từ 1–200 cm.");
        Map<String,Object> payload;
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                lockOrder(c,order);
                Map<String,Object> row=required(load(c,order)); checkShop(row);
                String state=ShippingJson.string(row,"BookingState");
                if("Booked".equals(state)) throw new IllegalArgumentException("Đơn đã có vận đơn GHN. Bấm cập nhật trạng thái.");
                if("Booking".equals(state) && ((Timestamp)row.get("UpdatedAt")).getTime()>System.currentTimeMillis()-120_000)
                    throw new IllegalArgumentException("Đơn đang gửi GHN. Vui lòng đợi rồi cập nhật trạng thái.");
                String old=ShippingJson.string(row,"BookingPayload");
                payload=old.isEmpty()?payload(c,order,row,weight,length,width,height):ShippingJson.object(ShippingJson.read(old));
                try(PreparedStatement s=c.prepareStatement("UPDATE dbo.GhnDelivery SET BookingState='Booking',BookingPayload=?,UpdatedAt=SYSDATETIME() WHERE OrderID=?")) {
                    s.setNString(1,ShippingJson.write(payload));s.setInt(2,order);s.executeUpdate();
                }
                c.commit();
            } catch(SQLException|RuntimeException e) {c.rollback();throw e;}
        }
        try {
            Map<String,Object> result=ShippingJson.object(new GhnClient().post("v2/shipping-order/create",payload));
            String code=ShippingJson.string(result,"order_code");
            if(!code.matches("[A-Za-z0-9_-]{1,100}")) throw new IllegalArgumentException("GHN chưa trả về mã vận đơn hợp lệ. Dùng cập nhật trạng thái để đối soát.");
            saveBooking(order,code,result);
        } catch(SQLException|RuntimeException e) {
            try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement("UPDATE dbo.GhnDelivery SET BookingState='Unknown',UpdatedAt=SYSDATETIME() WHERE OrderID=? AND BookingState='Booking'")) {
                s.setInt(1,order);s.executeUpdate();
            } catch(SQLException secondary) { e.addSuppressed(secondary); }
            throw e;
        }
    }
    private Map<String,Object> payload(Connection c,int order,Map<String,Object> row,int weight,int length,int width,int height) throws SQLException {
        Map<String,Object> p=new LinkedHashMap<>(ShippingJson.object(ShippingJson.read(ShippingJson.string(row,"RecipientJson"))));
        String sql="SELECT TOP(1) o.OrderStatus,p.PaymentMethod,p.PaymentStatus,p.Amount,s.ShipmentStatus FROM dbo.SalesOrder o JOIN dbo.Payment p ON p.OrderID=o.OrderID JOIN dbo.Shipment s ON s.OrderID=o.OrderID WHERE o.OrderID=? ORDER BY p.PaymentID DESC";
        try(PreparedStatement s=c.prepareStatement(sql)) {
            s.setInt(1,order);try(ResultSet r=s.executeQuery()) {
                if(!r.next() || !"Confirmed".equalsIgnoreCase(r.getString("OrderStatus")) || !"Pending".equalsIgnoreCase(r.getString("ShipmentStatus")))
                    throw new IllegalArgumentException("Chỉ tạo GHN cho đơn đã được Sales xác nhận và chưa xuất kho.");
                boolean cod="COD".equalsIgnoreCase(r.getString("PaymentMethod"));
                if((cod && !"Pending".equalsIgnoreCase(r.getString("PaymentStatus")))
                        || (!cod && !("VNPAY".equalsIgnoreCase(r.getString("PaymentMethod")) && "Paid".equalsIgnoreCase(r.getString("PaymentStatus")))))
                    throw new IllegalArgumentException("Đơn chưa đủ điều kiện thanh toán để tạo vận đơn.");
                // Shop pays the carrier. Customer shipping is already included in Payment.Amount.
                int amount=r.getBigDecimal("Amount").intValueExact();
                if(amount<0 || (cod && amount>50_000_000)) throw new IllegalArgumentException("Số tiền COD không hợp lệ.");
                p.put("cod_amount",cod?amount:0);p.put("payment_type_id",1);
            }
        }
        List<Object> items=new ArrayList<>();
        for(OrderLine line:new WarehouseOrderDAO().getOrderLines(order)) {
            Map<String,Object> item=new LinkedHashMap<>(); item.put("name",line.getProductName());item.put("code",line.getSku());
            item.put("quantity",line.getQuantity());item.put("price",line.getUnitPrice().intValueExact());items.add(item);
        }
        if(items.isEmpty()) throw new IllegalArgumentException("Đơn không có sản phẩm.");
        String phone=ShippingJson.string(p,"to_phone").replaceAll("[ .()-]","");
        if(phone.startsWith("+84")) phone="0"+phone.substring(3);
        if(!phone.matches("0[0-9]{9,10}")) throw new IllegalArgumentException("Số điện thoại người nhận không hợp lệ.");
        p.put("to_phone",phone);p.put("items",items);p.put("client_order_code",row.get("ClientOrderCode"));
        p.put("weight",weight);p.put("length",length);p.put("width",width);p.put("height",height);
        p.put("service_type_id",2);p.put("required_note","CHOXEMHANGKHONGTHU");return p;
    }
    private void saveBooking(int order,String code,Map<String,Object> result) throws SQLException {
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                lockOrder(c,order);
                try(PreparedStatement s=c.prepareStatement("UPDATE dbo.GhnDelivery SET TrackingNumber=?,BookingState='Booked',CarrierStatus=COALESCE(CarrierStatus,'ready_to_pick'),CarrierFee=COALESCE(?,CarrierFee),UpdatedAt=SYSDATETIME() WHERE OrderID=?")) {
                    s.setString(1,code);
                    s.setBigDecimal(2,carrierFee(result));s.setInt(3,order);s.executeUpdate();
                }
                try(PreparedStatement s=c.prepareStatement("UPDATE dbo.Shipment SET Carrier='GHN (Test)',TrackingNumber=?,ShipmentStatus=CASE WHEN ShipmentStatus='Pending' THEN 'Preparing' ELSE ShipmentStatus END WHERE OrderID=?")) {
                    s.setString(1,code);s.setInt(2,order);s.executeUpdate();
                }
                c.commit();
            } catch(SQLException|RuntimeException e) {c.rollback();throw e;}
        }
    }
    public String sync(int order) throws SQLException { return syncWithLease(order,false); }
    public void syncScheduled(int order) throws SQLException { syncWithLease(order,true); }

    public List<Integer> dueForSync(int limit) throws SQLException {
        List<Integer> orders=new ArrayList<>();
        String sql="SELECT TOP (?) g.OrderID FROM dbo.GhnDelivery g JOIN dbo.SalesOrder o ON o.OrderID=g.OrderID "
                +"WHERE g.ShopID=? AND g.Environment='staging' AND g.BookingState IN ('Booked','Unknown','Booking') "
                +"AND (g.BookingState<>'Booking' OR g.UpdatedAt<DATEADD(minute,-2,SYSDATETIME())) "
                +"AND (g.WebhookGeneration>g.LastWebhookGenerationSynced OR ISNULL(g.CarrierStatus,'') NOT IN ('delivered','cancel','returned','exception','lost','damage','scrap')) "
                +"AND (o.OrderStatus IN ('Confirmed','Shipped','Processing') OR (o.OrderStatus='Delivered' AND g.WebhookGeneration>g.LastWebhookGenerationSynced)) AND g.NextSyncAt<=SYSUTCDATETIME() "
                +"AND (g.SyncLeaseUntil IS NULL OR g.SyncLeaseUntil<SYSUTCDATETIME()) ORDER BY g.NextSyncAt,g.OrderID";
        try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(sql)) {
            s.setQueryTimeout(5);s.setInt(1,Math.min(100,Math.max(1,limit)));s.setInt(2,GhnClient.shopId());
            try(ResultSet r=s.executeQuery()){while(r.next())orders.add(r.getInt(1));}
        }
        return orders;
    }
    private String syncWithLease(int order,boolean scheduled) throws SQLException {
        UUID lease=UUID.randomUUID();
        String sql="UPDATE dbo.GhnDelivery SET SyncLeaseToken=?,SyncLeaseUntil=DATEADD(second,120,SYSUTCDATETIME()),NextSyncAt=DATEADD(second,60,SYSUTCDATETIME()) "
                +"WHERE OrderID=? AND ShopID=? AND Environment='staging' AND BookingState<>'Ready' "
                +"AND (SyncLeaseUntil IS NULL OR SyncLeaseUntil<SYSUTCDATETIME())"
                +(scheduled?" AND NextSyncAt<=SYSUTCDATETIME() AND (WebhookGeneration>LastWebhookGenerationSynced OR ISNULL(CarrierStatus,'') NOT IN ('delivered','cancel','returned','exception','lost','damage','scrap'))":"");
        try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(sql)) {
            s.setQueryTimeout(5);s.setString(1,lease.toString());s.setInt(2,order);s.setInt(3,GhnClient.shopId());
            if(s.executeUpdate()!=1) {
                if(scheduled) return null;
                throw new IllegalArgumentException("Đơn chưa có vận đơn hoặc đang được đồng bộ. Vui lòng đợi một chút rồi thử lại.");
            }
        }
        boolean success=false;
        try {
            String status=syncClaimed(order,lease);
            success=true;return status;
        } finally {
            String release="UPDATE dbo.GhnDelivery SET SyncLeaseToken=NULL,SyncLeaseUntil=NULL,SyncFailureCount="
                    +(success?"0":"CASE WHEN SyncFailureCount<1000000 THEN SyncFailureCount+1 ELSE SyncFailureCount END")
                    +(success?"":",NextSyncAt=DATEADD(second,60,SYSUTCDATETIME())")+" WHERE OrderID=? AND SyncLeaseToken=?";
            try(Connection c=DBConnection.getConnection();PreparedStatement s=c.prepareStatement(release)) {
                s.setQueryTimeout(5);s.setInt(1,order);s.setString(2,lease.toString());s.executeUpdate();
            } catch(SQLException releaseFailure) {
                java.util.logging.Logger.getLogger(GhnDeliveryDAO.class.getName()).warning("GHN sync lease release deferred for order "+order+"; lease will expire.");
            }
        }
    }
    private String syncClaimed(int order,UUID lease) throws SQLException {
        Map<String,Object> row=required(find(order)); checkShop(row);
        long webhookGeneration=((Number)row.get("WebhookGeneration")).longValue();
        if("Ready".equals(row.get("BookingState"))) throw new IllegalArgumentException("Đơn chưa được gửi sang GHN.");
        String code=ShippingJson.string(row,"TrackingNumber");
        String path=code.isEmpty()?"v2/shipping-order/detail-by-client-code?client_order_code="+row.get("ClientOrderCode")
                :"v2/shipping-order/detail?order_code="+code;
        Map<String,Object> data=ShippingJson.object(new GhnClient().get(path));
        String returned=ShippingJson.string(data,"order_code"),status=ShippingJson.string(data,"status");
        if(!returned.matches("[A-Za-z0-9_-]{1,100}") || !status.matches("[a-z_]{1,50}")
                || (!code.isEmpty() && !code.equals(returned))) throw new IllegalArgumentException("Dữ liệu đối soát GHN không khớp.");
        // A persisted carrier code plus its owning shop identifies an already booked order.
        // Some detail responses omit the optional merchant reference. Recovery without a
        // persisted tracking code still requires that reference to match exactly.
        if(ShippingJson.number(data,"shop_id")!=((Number)row.get("ShopID")).intValue())
            throw new IllegalArgumentException("Shop sở hữu vận đơn GHN không khớp.");
        String merchantCode=ShippingJson.string(data,"client_order_code");
        if((!merchantCode.isEmpty() && !merchantCode.equals(row.get("ClientOrderCode")))
                || (merchantCode.isEmpty() && code.isEmpty()))
            throw new IllegalArgumentException("Mã đơn GHN không khớp đơn của shop.");
        saveBooking(order,returned,data);
        try(Connection c=DBConnection.getConnection()) {
            c.setAutoCommit(false);
            try {
                lockOrder(c,order);
                try(PreparedStatement s=c.prepareStatement("UPDATE dbo.GhnDelivery SET CarrierStatus=?,LastSyncedAt=SYSUTCDATETIME(),UpdatedAt=SYSDATETIME(),LastWebhookGenerationSynced=? WHERE OrderID=? AND SyncLeaseToken=?")) {
                    s.setQueryTimeout(5);s.setString(1,status);s.setLong(2,webhookGeneration);s.setInt(3,order);s.setString(4,lease.toString());
                    if(s.executeUpdate()!=1) throw new SQLException("GHN_SYNC_LEASE_EXPIRED");
                }
                boolean moving=Set.of("picked","storing","transporting","sorting","delivering","money_collect_delivering","delivery_fail","waiting_to_return","return","return_transporting","return_sorting","returning","return_fail","returned","delivered").contains(status);
                if(moving) {
                    execute(c,"UPDATE dbo.Shipment SET ShipmentStatus='Shipped',ShippedDate=COALESCE(ShippedDate,SYSDATETIME()) WHERE OrderID=? AND ShipmentStatus IN ('Pending','Preparing')",order);
                    execute(c,"UPDATE dbo.SalesOrder SET OrderStatus='Shipped',UpdatedAt=SYSDATETIME() WHERE OrderID=? AND OrderStatus='Confirmed'",order);
                }
                if("delivered".equals(status)) {
                    execute(c,"UPDATE dbo.Shipment SET ShipmentStatus='Delivered',DeliveredDate=COALESCE(DeliveredDate,SYSDATETIME()) WHERE OrderID=? AND ShipmentStatus='Shipped'",order);
                    execute(c,"UPDATE dbo.SalesOrder SET OrderStatus='Delivered',UpdatedAt=SYSDATETIME() WHERE OrderID=? AND OrderStatus='Shipped'",order);
                    execute(c,"UPDATE dbo.Payment SET PaymentStatus='Paid',PaymentDate=COALESCE(PaymentDate,SYSDATETIME()) WHERE OrderID=? AND PaymentMethod='COD' AND PaymentStatus='Pending'",order);
                }
                if("cancel".equals(status)) {
                    // Carrier cancellation stops this shipment, not the paid sales order.
                    // Refund/stock decisions remain in the existing shop workflow.
                    execute(c,"UPDATE dbo.Shipment SET ShipmentStatus='Cancelled' WHERE OrderID=? AND ShipmentStatus IN ('Pending','Preparing')",order);
                }
                c.commit();
            } catch(SQLException|RuntimeException e){c.rollback();throw e;}
        }
        // Tracking details do not always include prices. Fetch the existing shipment's fee
        // separately; a fee failure must not discard a successfully synchronized status.
        try {
            Map<String,Object> fees=ShippingJson.object(new GhnClient().cached("v2/shipping-order/soc?order_code="+returned,null,60_000));
            if(returned.equals(ShippingJson.string(fees,"order_code"))) {
                BigDecimal fee=carrierFee(fees);
                if(fee!=null) try(Connection c=DBConnection.getConnection()) {
                    execute(c,"UPDATE dbo.GhnDelivery SET CarrierFee=? WHERE OrderID=? AND TrackingNumber=?",fee,order,returned);
                }
            }
        } catch(IllegalArgumentException | SQLException ignored) {
            java.util.logging.Logger.getLogger(GhnDeliveryDAO.class.getName())
                    .warning("GHN fee refresh unavailable for local order "+order+"; tracking status was retained.");
        }
        return status;
    }
    private static BigDecimal carrierFee(Map<String,Object> response) {
        try {
            Object total=response.get("total_fee");
            Object nested=response.get("detail");
            if(!(nested instanceof Map)) nested=response.get("fee");
            if(total==null && nested instanceof Map) {
                Map<?,?> detail=(Map<?,?>)nested;
                total=detail.get("total");
                if(total==null) {
                    BigDecimal sum=BigDecimal.ZERO; boolean found=false;
                    for(String key:new String[]{"main_service","insurance","cod_fee","station_do","station_pu","return","r2s","return_again","coupon","document_return","double_check","double_check_deliver","pick_remote_areas_fee","deliver_remote_areas_fee","pick_remote_areas_fee_return","deliver_remote_areas_fee_return","cod_failed_fee","change_to_address_fee","change_return_address_fee"}) {
                        if(detail.get(key)!=null) {sum=sum.add(new BigDecimal(detail.get(key).toString()));found=true;}
                    }
                    if(found) total=sum;
                }
            }
            if(total==null) return null;
            BigDecimal amount=new BigDecimal(total.toString()).setScale(0,java.math.RoundingMode.UNNECESSARY);
            return amount.signum()<0 || amount.compareTo(new BigDecimal("5000000"))>0?null:amount;
        } catch(IllegalArgumentException | ArithmeticException invalidFee) { return null; }
    }
    private static void execute(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}
    }
    private static void lockOrder(Connection c,int order) throws SQLException {
        try(PreparedStatement s=c.prepareStatement("SELECT OrderStatus FROM dbo.SalesOrder WITH(UPDLOCK,ROWLOCK) WHERE OrderID=?")) {
            s.setInt(1,order);try(ResultSet r=s.executeQuery()) {
                if(!r.next() || "Cancelled".equalsIgnoreCase(r.getString(1))) throw new IllegalArgumentException("Đơn không tồn tại hoặc đã hủy.");
            }
        }
    }
    private static Map<String,Object> required(Map<String,Object> row) {
        if(row==null) throw new IllegalArgumentException("Đơn cũ chưa lưu địa chỉ GHN. Dùng giao hàng thủ công cho đơn này.");return row;
    }
    private static void checkShop(Map<String,Object> row) {
        if(((Number)row.get("ShopID")).intValue()!=GhnClient.shopId()) throw new IllegalArgumentException("Shop ID hiện tại khác shop đã lưu cùng đơn hàng.");
    }
    public static void guardManual(Connection c,int order) throws SQLException {
        // Conditional SQL keeps old databases usable before the GHN migration is installed.
        try(PreparedStatement s=c.prepareStatement("IF OBJECT_ID('dbo.GhnDelivery','U') IS NULL SELECT CAST(NULL AS varchar(20)) AS BookingState WHERE 1=0 ELSE EXEC sp_executesql N'SELECT BookingState FROM dbo.GhnDelivery WHERE OrderID=@id',N'@id int',@id=?")) {
            s.setInt(1,order);try(ResultSet r=s.executeQuery()) {
                if(r.next() && !"Ready".equals(r.getString(1))) throw new IllegalArgumentException("Đơn đã gửi GHN. Dùng nút cập nhật trạng thái GHN để đối soát.");
            }
        }
    }
}
