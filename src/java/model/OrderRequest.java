package model;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class OrderRequest {

    private int requestID;
    private int orderID;
    private int customerID;
    private String customerName;
    private String customerPhone;
    private String requestType;
    private String reason;
    private String requestStatus;
    private String orderStatus;
    private Timestamp requestedAt;
    private String staffResponse;
    private List<OrderRequestLine> lines = new ArrayList<>();

    public int getRequestID() {
        return requestID;
    }

    public void setRequestID(int requestID) {
        this.requestID = requestID;
    }

    public int getOrderID() {
        return orderID;
    }

    public void setOrderID(int orderID) {
        this.orderID = orderID;
    }

    public int getCustomerID() {
        return customerID;
    }

    public void setCustomerID(int customerID) {
        this.customerID = customerID;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getRequestType() {
        return requestType;
    }

    public void setRequestType(String requestType) {
        this.requestType = requestType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestStatus() {
        return requestStatus;
    }

    public void setRequestStatus(String requestStatus) {
        this.requestStatus = requestStatus;
    }

    public String getOrderStatus() {
        return orderStatus;
    }

    public void setOrderStatus(String orderStatus) {
        this.orderStatus = orderStatus;
    }

    public Timestamp getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Timestamp requestedAt) {
        this.requestedAt = requestedAt;
    }

    public String getStaffResponse() {
        return staffResponse;
    }

    public void setStaffResponse(String staffResponse) {
        this.staffResponse = staffResponse;
    }

    public List<OrderRequestLine> getLines() {
        return lines;
    }

    public void setLines(List<OrderRequestLine> lines) {
        this.lines = lines == null ? new ArrayList<OrderRequestLine>() : lines;
    }
}