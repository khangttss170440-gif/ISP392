package service;

import java.util.Set;

/** Shared presentation and terminal-state rules for warehouse and customer tracking. */
public final class GhnTrackingStatus {
    private GhnTrackingStatus() { }
    public static boolean terminal(String status) {
        return status!=null && ("simulation_delivered".equals(status)
                || Set.of("delivered","cancel","returned","exception","lost","damage","scrap").contains(status));
    }
    public static String label(String status) {
        if(status==null || status.isBlank()) return "Chưa có cập nhật vận chuyển";
        switch(status) {
            case "ready_to_pick":return "Chờ lấy hàng";
            case "picking":return "Đang đến lấy hàng";
            case "money_collect_picking":return "Thu tiền khi lấy hàng";
            case "picked":return "Đã lấy hàng";
            case "storing":return "Đang ở kho GHN";
            case "sorting":return "Đang phân loại";
            case "transporting":return "Đang vận chuyển";
            case "delivering":return "Đang giao hàng";
            case "money_collect_delivering":return "Thu tiền khi giao hàng";
            case "delivered":return "Giao thành công";
            case "simulation_picked":return "Đã lấy hàng (mô phỏng)";
            case "simulation_delivered":return "Giao thành công (mô phỏng)";
            case "delivery_fail":return "Giao hàng chưa thành công";
            case "waiting_to_return":return "Chờ trả về shop";
            case "return":return "Đã yêu cầu trả hàng";
            case "return_transporting":case "return_sorting":case "returning":return "Đang chuyển trả hàng";
            case "return_fail":return "Trả hàng chưa thành công";
            case "returned":return "Đã trả về shop";
            case "cancel":return "Đã hủy vận đơn";
            case "lost":return "Thất lạc — shop đang xử lý";
            case "damage":return "Hư hỏng — shop đang xử lý";
            case "scrap":return "Đã tiêu hủy — liên hệ shop";
            default:return "Có sự cố vận chuyển — liên hệ shop";
        }
    }
    public static String orderLabel(String status) {
        if(status==null) return "Đang cập nhật";
        switch(status) {
            case "Pending":return "Chờ xác nhận";
            case "Confirmed":return "Đã xác nhận";
            case "Processing":return "Đang chuẩn bị hàng";
            case "Shipped":return "Đang giao hàng";
            case "Delivered":return "Đã giao hàng";
            case "Cancelled":return "Đã hủy đơn";
            default:return "Đang xử lý";
        }
    }
    public static String paymentLabel(String status) {
        if(status==null) return "Chưa có cập nhật thanh toán";
        switch(status) {
            case "Paid":return "Đã thanh toán";
            case "Pending":return "Chưa thanh toán";
            case "Failed":return "Thanh toán chưa thành công";
            case "Refunded":return "Đã hoàn tiền";
            default:return "Đang xử lý thanh toán";
        }
    }
}
