<%-- 
    Document   : reports
    Created on : Oct 9, 2026, 3:29:07 PM
    Author     : ASUS
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Báo cáo · MENSFORM Admin</title>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    </head>
    <body><div class="admin-layout"><%@include file="/admin/includes/sidebar.jspf"%><main class="admin-main">
        <%@page import="java.util.List,java.util.Locale,java.text.NumberFormat,java.time.LocalDate,model.AdminReport,model.DailySalesReport"%><% AdminReport report=(AdminReport)request.getAttribute("report"); List<DailySalesReport> dailySales=(List<DailySalesReport>)request.getAttribute("dailySales"); LocalDate from=(LocalDate)request.getAttribute("from"); LocalDate to=(LocalDate)request.getAttribute("to"); NumberFormat money=NumberFormat.getInstance(new Locale("vi","VN")); %>
        <div class="admin-topline"><div><small>BUSINESS / OVERVIEW</small><h1 class="admin-title">Báo cáo cửa hàng</h1></div><span class="admin-note">Tổng quan vận hành</span></div><p class="admin-subtitle">Doanh thu được ghi nhận từ các đơn đã thanh toán hoặc COD giao thành công.</p>
        <% if(request.getAttribute("error")!=null){ %><div class="alert error"><%=request.getAttribute("error")%></div><% } %>
        <section class="admin-panel"><form class="admin-filter" action="<%=request.getContextPath()%>/admin/reports" method="get"><label for="from">Từ ngày</label><input class="field" id="from" name="from" type="date" value="<%=from%>" required><label for="to">Đến ngày</label><input class="field" id="to" name="to" type="date" value="<%=to%>" required><button class="button small" type="submit">Cập nhật báo cáo</button></form></section>
        <div class="admin-grid"><div class="admin-stat"><span>Doanh thu ghi nhận</span><strong><%=money.format(report==null?java.math.BigDecimal.ZERO:report.getRevenue())%>₫</strong><small><%=from%> — <%=to%></small></div><div class="admin-stat"><span>Tổng đơn hàng</span><strong><%=report==null?0:report.getTotalOrders()%></strong><small>Trong khoảng thời gian đã chọn</small></div><div class="admin-stat"><span>Đơn đã hủy</span><strong><%=report==null?0:report.getCancelledOrders()%></strong><small>Tỷ lệ hủy: <%=report==null?"0":report.getCancellationRate().toPlainString()%>%</small></div><div class="admin-stat"><span>Tồn kho hiện tại</span><strong><%=report==null?0:report.getInventoryUnits()%></strong><small>Đơn vị hàng trong kho</small></div><div class="admin-stat"><span>Biến thể đang bán</span><strong><%=report==null?0:report.getActiveVariants()%></strong><small>SKU thuộc sản phẩm đang hoạt động</small></div><div class="admin-stat"><span>SKU tồn thấp</span><strong><%=report==null?0:report.getLowStockVariants()%></strong><small>Còn không quá 5 sản phẩm</small></div></div>
        <section class="admin-panel" style="margin-top:18px"><div class="admin-toolbar"><h2>Doanh thu theo ngày</h2><span class="admin-note">Đơn hàng · Doanh thu</span></div><div class="admin-table-wrap"><table class="admin-table"><thead><tr><th>Ngày</th><th>Số đơn</th><th>Doanh thu</th></tr></thead><tbody><% if(dailySales==null||dailySales.isEmpty()){ %><tr><td colspan="3" class="admin-empty">Chưa có dữ liệu bán hàng trong khoảng ngày này.</td></tr><% } else { for(DailySalesReport row:dailySales){ %><tr><td><%=row.getReportDate()%></td><td><%=row.getOrderCount()%></td><td><b><%=money.format(row.getRevenue())%>₫</b></td></tr><% }} %></tbody></table></div></section>
    </main></div>
    </body>
</html>
