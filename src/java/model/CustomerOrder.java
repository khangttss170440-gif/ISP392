package model;

import java.math.BigDecimal;
import java.sql.Timestamp;

public class CustomerOrder {

    private int orderID;
    private Timestamp orderDate;
    private String orderStatus;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal shippingFee;
    private BigDecimal totalAmount;
    private String paymentMethod;
    private String paymentStatus;
    private String shipmentStatus;
    private String trackingNumber;
    private String carrier;
    private String shippingAddress;
    private String carrierStatus;
    private Timestamp lastCarrierSync;

    public String getCarrierStatus() { return carrierStatus; }
    public void setCarrierStatus(String status) { carrierStatus=status; }
    public Timestamp getLastCarrierSync() { return lastCarrierSync; }
    public void setLastCarrierSync(Timestamp time) { lastCarrierSync=time; }
    public String getOrderStatusLabel() {
        if("cancel".equals(carrierStatus) && !"Cancelled".equalsIgnoreCase(orderStatus))
            return "Vận đơn đã hủy — Chờ cửa hàng xử lý";
        return service.GhnTrackingStatus.orderLabel(orderStatus);
    }
    public String getShippingNotice() {
        if(!"cancel".equals(carrierStatus) || "Cancelled".equalsIgnoreCase(orderStatus)) return "";
        return "Vận đơn GHN đã bị hủy. Cửa hàng cần xử lý giao lại hoặc hủy đơn mua hàng."
                +("Paid".equalsIgnoreCase(paymentStatus)
                ?" Đơn đã thanh toán; hủy vận đơn chưa đồng nghĩa đã hoàn tiền."
                :" Đơn mua hàng chưa tự động bị hủy.");
    }
    public String getPaymentStatusLabel() { return service.GhnTrackingStatus.paymentLabel(paymentStatus); }
    public String getShippingStatusLabel() {
        if("Cancelled".equalsIgnoreCase(orderStatus)) return "Đơn hàng đã hủy";
        if(carrierStatus!=null && !carrierStatus.isBlank()) return service.GhnTrackingStatus.label(carrierStatus);
        if("Delivered".equalsIgnoreCase(shipmentStatus)) return "Đã giao hàng";
        if("Shipped".equalsIgnoreCase(shipmentStatus)) return "Đang giao hàng";
        if("Preparing".equalsIgnoreCase(shipmentStatus) || "Confirmed".equalsIgnoreCase(orderStatus)) return "Đang chuẩn bị giao hàng";
        return "Chờ xác nhận giao hàng";
    }

    public int getOrderID() {
        return orderID;
    }

    public void setOrderID(int orderID) {
        this.orderID = orderID;
    }

    public Timestamp getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(Timestamp orderDate) {
        this.orderDate = orderDate;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getDiscountAmount() {
        return discountAmount;
    }

    public void setDiscountAmount(BigDecimal discountAmount) {
        this.discountAmount = discountAmount;
    }

    public BigDecimal getShippingFee() {
        return shippingFee;
    }

    public void setShippingFee(BigDecimal shippingFee) {
        this.shippingFee = shippingFee;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getShipmentStatus() {
        return shipmentStatus;
    }

    public void setShipmentStatus(String shipmentStatus) {
        this.shipmentStatus = shipmentStatus;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }
}
