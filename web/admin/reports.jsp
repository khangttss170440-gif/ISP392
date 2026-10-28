<%--
    Document   : reports
    Created on : Oct 9, 2026, 3:29:07 PM
    Author     : ASUS
--%>

<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,java.util.Map,java.util.HashMap,java.util.Locale,java.text.NumberFormat,java.math.BigDecimal,java.math.RoundingMode,java.time.LocalDate,java.time.format.DateTimeFormatter,java.time.temporal.ChronoUnit,model.AdminReport,model.DailySalesReport,model.ReportBreakdown"%>
<%!
    private static String h(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /* Percentage change between two periods, or null when there is nothing to compare with. */
    private static BigDecimal change(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.signum() == 0) return null;
        return nz(current).subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 1, RoundingMode.HALF_UP);
    }

    private static String deltaPill(BigDecimal pct, boolean higherIsBetter) {
        if (pct == null) return "<span class=\"rp-delta is-flat\">Chưa có kỳ trước</span>";
        int sign = pct.signum();
        String mood = sign == 0 ? "is-flat" : ((sign > 0) == higherIsBetter ? "is-good" : "is-bad");
        String arrow = sign > 0 ? "↗" : (sign < 0 ? "↘" : "→");
        String text = (sign > 0 ? "+" : "") + pct.toPlainString().replace('.', ',') + "%";
        return "<span class=\"rp-delta " + mood + "\"><b aria-hidden=\"true\">" + arrow + "</b>" + text + " <i>so với kỳ trước</i></span>";
    }

    private static String statusLabel(String status) {
        String key = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        switch (key) {
            case "PENDING": return "Chờ xác nhận";
            case "CONFIRMED": return "Đã xác nhận";
            case "PROCESSING": return "Đang chuẩn bị";
            case "SHIPPING": case "SHIPPED": case "DELIVERING": return "Đang giao";
            case "DELIVERED": return "Đã giao";
            case "COMPLETED": return "Hoàn tất";
            case "CANCELLED": case "CANCELED": return "Đã hủy";
            case "RETURNED": return "Hoàn trả";
            default: return status == null || status.isEmpty() ? "Khác" : status;
        }
    }

    /* Each status keeps the same colour slot whatever the period, so the strap never repaints. */
    private static String statusSlot(String status) {
        String key = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        switch (key) {
            case "COMPLETED": return "s1";
            case "PENDING": return "s2";
            case "CONFIRMED": case "PROCESSING": return "s3";
            case "CANCELLED": case "CANCELED": return "s4";
            case "SHIPPING": case "SHIPPED": case "DELIVERING": return "s5";
            case "DELIVERED": return "s6";
            default: return "s0";
        }
    }

    private static String paymentLabel(String method) {
        String key = method == null ? "" : method.trim().toUpperCase(Locale.ROOT);
        if ("COD".equals(key) || "CASH ON DELIVERY".equals(key)) return "Thanh toán khi nhận (COD)";
        if ("VNPAY".equals(key)) return "VNPAY";
        if ("UNKNOWN".equals(key) || key.isEmpty()) return "Chưa có thanh toán";
        return method;
    }

    private static String share(long part, long total) {
        if (total <= 0) return "0";
        return BigDecimal.valueOf(part * 100.0 / total).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().replace('.', ',');
    }

    private static String width(BigDecimal part, BigDecimal max) {
        if (max == null || max.signum() <= 0 || part == null) return "0";
        return part.multiply(BigDecimal.valueOf(100)).divide(max, 2, RoundingMode.HALF_UP).toPlainString();
    }
%>
<%
    AdminReport report = (AdminReport) request.getAttribute("report");
    AdminReport previous = (AdminReport) request.getAttribute("previousReport");
    List<DailySalesReport> dailySales = (List<DailySalesReport>) request.getAttribute("dailySales");
    List<ReportBreakdown> topProducts = (List<ReportBreakdown>) request.getAttribute("topProducts");
    List<ReportBreakdown> categorySales = (List<ReportBreakdown>) request.getAttribute("categorySales");
    List<ReportBreakdown> statusMix = (List<ReportBreakdown>) request.getAttribute("statusMix");
    List<ReportBreakdown> paymentMix = (List<ReportBreakdown>) request.getAttribute("paymentMix");
    List<ReportBreakdown> lowStock = (List<ReportBreakdown>) request.getAttribute("lowStock");
    LocalDate from = (LocalDate) request.getAttribute("from");
    LocalDate to = (LocalDate) request.getAttribute("to");
    LocalDate previousFrom = (LocalDate) request.getAttribute("previousFrom");
    LocalDate previousTo = (LocalDate) request.getAttribute("previousTo");
    Long customerCount = (Long) request.getAttribute("customerCount");
    Long previousCustomerCount = (Long) request.getAttribute("previousCustomerCount");

    if (report == null) report = new AdminReport();
    if (to == null) to = LocalDate.now();
    if (from == null) from = to.withDayOfMonth(1);

    String ctx = request.getContextPath();
    NumberFormat money = NumberFormat.getInstance(new Locale("vi", "VN"));
    DateTimeFormatter dm = DateTimeFormatter.ofPattern("dd/MM");
    DateTimeFormatter dmy = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    LocalDate today = LocalDate.now();
    long days = ChronoUnit.DAYS.between(from, to) + 1;

    long kept = report.getTotalOrders() - report.getCancelledOrders();
    BigDecimal revenue = nz(report.getRevenue());
    BigDecimal average = kept > 0 ? revenue.divide(BigDecimal.valueOf(kept), 0, RoundingMode.HALF_UP) : BigDecimal.ZERO;
    BigDecimal prevRevenue = previous == null ? null : nz(previous.getRevenue());
    long prevKept = previous == null ? 0 : previous.getTotalOrders() - previous.getCancelledOrders();
    BigDecimal prevAverage = previous == null || prevKept <= 0 ? null : prevRevenue.divide(BigDecimal.valueOf(prevKept), 0, RoundingMode.HALF_UP);

    /* Fill the days with no orders so the trend line does not skip them. */
    Map<LocalDate, DailySalesReport> byDay = new HashMap<>();
    if (dailySales != null) for (DailySalesReport row : dailySales) if (row.getReportDate() != null) byDay.put(row.getReportDate(), row);
    StringBuilder series = new StringBuilder("[");
    BigDecimal bestDay = BigDecimal.ZERO; LocalDate bestDate = null; int activeDays = 0;
    for (LocalDate d = from; !d.isAfter(to) && days <= 400; d = d.plusDays(1)) {
        DailySalesReport row = byDay.get(d);
        BigDecimal dayRevenue = row == null ? BigDecimal.ZERO : nz(row.getRevenue());
        long dayOrders = row == null ? 0 : row.getOrderCount();
        if (dayOrders > 0) activeDays++;
        if (dayRevenue.compareTo(bestDay) > 0) { bestDay = dayRevenue; bestDate = d; }
        if (series.length() > 1) series.append(',');
        series.append("[\"").append(d).append("\",").append(dayRevenue.setScale(0, RoundingMode.HALF_UP).toPlainString()).append(',').append(dayOrders).append(']');
    }
    series.append(']');

    String periodTitle;
    if (from.getDayOfMonth() == 1 && (to.equals(from.withDayOfMonth(from.lengthOfMonth())) || (to.equals(today) && from.getMonth() == today.getMonth() && from.getYear() == today.getYear()))) {
        periodTitle = "tháng " + from.getMonthValue() + "/" + from.getYear();
    } else if (days == 1) {
        periodTitle = "ngày " + from.format(dmy);
    } else {
        periodTitle = days + " ngày";
    }

    LocalDate monthStart = today.withDayOfMonth(1);
    LocalDate lastMonthStart = monthStart.minusMonths(1);
    String[][] presets = {
        {"7 ngày", today.minusDays(6).toString(), today.toString()},
        {"30 ngày", today.minusDays(29).toString(), today.toString()},
        {"Tháng này", monthStart.toString(), today.toString()},
        {"Tháng trước", lastMonthStart.toString(), monthStart.minusDays(1).toString()},
        {"Quý gần nhất", today.minusDays(89).toString(), today.toString()}
    };

    long statusTotal = 0;
    if (statusMix != null) for (ReportBreakdown row : statusMix) statusTotal += row.getCount();
    long paymentTotal = 0;
    if (paymentMix != null) for (ReportBreakdown row : paymentMix) paymentTotal += row.getCount();
    BigDecimal categoryTotal = BigDecimal.ZERO, categoryMax = BigDecimal.ZERO;
    if (categorySales != null) for (ReportBreakdown row : categorySales) { categoryTotal = categoryTotal.add(row.getAmount()); if (row.getAmount().compareTo(categoryMax) > 0) categoryMax = row.getAmount(); }
    BigDecimal productMax = BigDecimal.ZERO;
    if (topProducts != null) for (ReportBreakdown row : topProducts) if (row.getAmount().compareTo(productMax) > 0) productMax = row.getAmount();
%>
<!DOCTYPE html>
<html lang="vi">
    <head>
        <meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>Báo cáo · <%=util.AppConfig.escapeHtml(util.AppConfig.require("APP_NAME"))%> Admin</title>
        <link rel="stylesheet" href="<%=ctx%>/assets/store.css">
    </head>
    <body><div class="admin-layout"><%@include file="/admin/includes/sidebar.jspf"%>
        <link rel="stylesheet" href="<%=ctx%>/assets/mensform-reports.css?v=20261024g">
        <main class="admin-main rp">

        <header class="rp-head">
            <div>
                <p class="rp-kicker">Sổ tay kinh doanh · cập nhật <%=today.format(dmy)%></p>
                <h1 class="rp-title">Báo cáo <em><%=h(periodTitle)%></em></h1>
                <p class="rp-sub"><%=from.format(dmy)%> — <%=to.format(dmy)%><% if (previousFrom != null) { %> · so với <%=previousFrom.format(dm)%> — <%=previousTo.format(dmy)%><% } %></p>
            </div>
            <button class="rp-print" type="button" onclick="window.print()"><svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 9V3h10v6M7 17H4v-7h16v7h-3M7 14h10v7H7z"/></svg>In báo cáo</button>
        </header>

        <% if (request.getAttribute("error") != null) { %><div class="alert error"><%=request.getAttribute("error")%></div><% } %>

        <form class="rp-filter" action="<%=ctx%>/admin/reports" method="get">
            <nav class="rp-presets" aria-label="Khoảng thời gian nhanh">
                <% for (String[] p : presets) { boolean on = p[1].equals(from.toString()) && p[2].equals(to.toString()); %>
                <a class="<%=on ? "is-on" : ""%>" href="<%=ctx%>/admin/reports?from=<%=p[1]%>&amp;to=<%=p[2]%>"><%=p[0]%></a>
                <% } %>
            </nav>
            <div class="rp-range">
                <label for="from">Từ</label><input id="from" name="from" type="date" value="<%=from%>" required>
                <label for="to">đến</label><input id="to" name="to" type="date" value="<%=to%>" required>
                <button type="submit">Xem báo cáo</button>
            </div>
        </form>

        <section class="rp-top">
            <article class="rp-hero">
                <div class="rp-strap" aria-hidden="true"><span class="rp-buckle"></span></div>
                <p class="rp-label">Doanh thu ghi nhận</p>
                <p class="rp-money"><%=money.format(revenue)%><small>₫</small></p>
                <%=deltaPill(change(revenue, prevRevenue), true)%>
                <dl class="rp-hero-facts">
                    <div><dt>Kỳ trước</dt><dd><%=prevRevenue == null ? "—" : money.format(prevRevenue) + "₫"%></dd></div>
                    <div><dt>Ngày cao nhất</dt><dd><%=bestDate == null ? "—" : money.format(bestDay) + "₫"%><% if (bestDate != null) { %> <i><%=bestDate.format(dm)%></i><% } %></dd></div>
                    <div><dt>Ngày có đơn</dt><dd><%=activeDays%> / <%=days%></dd></div>
                </dl>
            </article>

            <div class="rp-kpis">
                <article class="rp-kpi">
                    <p class="rp-label">Đơn hàng</p>
                    <p class="rp-num"><%=money.format(report.getTotalOrders())%></p>
                    <%=deltaPill(previous == null ? null : change(BigDecimal.valueOf(report.getTotalOrders()), BigDecimal.valueOf(previous.getTotalOrders())), true)%>
                </article>
                <article class="rp-kpi">
                    <p class="rp-label">Giá trị trung bình / đơn</p>
                    <p class="rp-num"><%=money.format(average)%><small>₫</small></p>
                    <%=deltaPill(change(average, prevAverage), true)%>
                </article>
                <article class="rp-kpi">
                    <p class="rp-label">Khách đặt hàng</p>
                    <p class="rp-num"><%=customerCount == null ? "—" : money.format(customerCount)%></p>
                    <%=deltaPill(customerCount == null || previousCustomerCount == null ? null : change(BigDecimal.valueOf(customerCount), BigDecimal.valueOf(previousCustomerCount)), true)%>
                </article>
                <article class="rp-kpi">
                    <p class="rp-label">Tỷ lệ hủy</p>
                    <p class="rp-num"><%=report.getCancellationRate().stripTrailingZeros().toPlainString().replace('.', ',')%><small>%</small></p>
                    <p class="rp-foot"><%=report.getCancelledOrders()%> đơn đã hủy<% if (previous != null) { %> · kỳ trước <%=previous.getCancellationRate().stripTrailingZeros().toPlainString().replace('.', ',')%>%<% } %></p>
                </article>
            </div>
        </section>

        <section class="rp-panel rp-trend" data-rp-trend>
            <div class="rp-panel-head">
                <div><span class="rp-no">01</span><h2>Nhịp bán hàng</h2><p>Mỗi ngày trong kỳ, kể cả những ngày không có đơn.</p></div>
                <div class="rp-tabs" role="tablist">
                    <button type="button" role="tab" aria-selected="true" data-view="revenue">Doanh thu</button>
                    <button type="button" role="tab" aria-selected="false" data-view="orders">Số đơn</button>
                    <button type="button" role="tab" aria-selected="false" data-view="table">Bảng số liệu</button>
                </div>
            </div>
            <div class="rp-chart" data-chart aria-label="Biểu đồ theo ngày" role="img"></div>
            <div class="rp-tip" data-tip hidden></div>
            <div class="rp-table" data-table hidden>
                <table>
                    <thead><tr><th>Ngày</th><th>Số đơn</th><th>Doanh thu</th></tr></thead>
                    <tbody>
                    <% if (dailySales == null || dailySales.isEmpty()) { %>
                        <tr><td colspan="3" class="rp-empty">Chưa có dữ liệu bán hàng trong khoảng ngày này.</td></tr>
                    <% } else { for (DailySalesReport row : dailySales) { %>
                        <tr><td><%=row.getReportDate() == null ? "" : row.getReportDate().format(dmy)%></td><td><%=row.getOrderCount()%></td><td><b><%=money.format(nz(row.getRevenue()))%>₫</b></td></tr>
                    <% }} %>
                    </tbody>
                </table>
            </div>
            <script type="application/json" id="rpSeries"><%=series%></script>
        </section>

        <div class="rp-grid">
            <section class="rp-panel">
                <div class="rp-panel-head"><div><span class="rp-no">02</span><h2>Doanh số theo danh mục</h2><p>Giá bán × số lượng của các đơn không hủy.</p></div></div>
                <% if (categorySales == null || categorySales.isEmpty()) { %>
                    <p class="rp-empty">Chưa có sản phẩm nào được bán trong kỳ.</p>
                <% } else { %>
                <ol class="rp-bars">
                    <% for (ReportBreakdown row : categorySales) { %>
                    <li tabindex="0" data-tip-text="<%=h(row.getLabel())%> · <%=row.getCount()%> sản phẩm · <%=money.format(row.getAmount())%>₫">
                        <span class="rp-bar-label"><%=h(row.getLabel())%></span>
                        <span class="rp-bar-track"><span class="rp-bar" style="width:<%=width(row.getAmount(), categoryMax)%>%"></span></span>
                        <span class="rp-bar-value"><%=money.format(row.getAmount())%>₫ <i><%=categoryTotal.signum() > 0 ? row.getAmount().multiply(BigDecimal.valueOf(100)).divide(categoryTotal, 0, RoundingMode.HALF_UP).toPlainString() : "0"%>%</i></span>
                    </li>
                    <% } %>
                </ol>
                <% } %>
            </section>

            <section class="rp-panel">
                <div class="rp-panel-head"><div><span class="rp-no">03</span><h2>Đơn hàng đang ở đâu</h2><p><%=statusTotal%> đơn trong kỳ, chia theo trạng thái.</p></div></div>
                <% if (statusMix == null || statusMix.isEmpty()) { %>
                    <p class="rp-empty">Chưa có đơn hàng trong kỳ.</p>
                <% } else { %>
                <div class="rp-belt" role="img" aria-label="Tỷ lệ đơn theo trạng thái">
                    <% for (ReportBreakdown row : statusMix) { %><span class="rp-seg <%=statusSlot(row.getLabel())%>" style="flex-grow:<%=row.getCount()%>" tabindex="0" data-tip-text="<%=h(statusLabel(row.getLabel()))%> · <%=row.getCount()%> đơn · <%=share(row.getCount(), statusTotal)%>%"></span><% } %>
                    <span class="rp-belt-buckle" aria-hidden="true"></span>
                </div>
                <ul class="rp-legend">
                    <% for (ReportBreakdown row : statusMix) { %>
                    <li><i class="rp-dot <%=statusSlot(row.getLabel())%>"></i><span><%=h(statusLabel(row.getLabel()))%></span><b><%=row.getCount()%></b><em><%=share(row.getCount(), statusTotal)%>%</em></li>
                    <% } %>
                </ul>
                <% } %>
                <h3 class="rp-minor">Khách thanh toán bằng</h3>
                <% if (paymentMix == null || paymentMix.isEmpty()) { %>
                    <p class="rp-empty">Chưa có thanh toán trong kỳ.</p>
                <% } else { int slot = 0; %>
                <ul class="rp-pay">
                    <% for (ReportBreakdown row : paymentMix) { String key = row.getLabel() == null ? "" : row.getLabel().toUpperCase(Locale.ROOT); String cls = key.contains("COD") || key.contains("CASH") ? "s1" : ("VNPAY".equals(key) ? "s3" : "s0"); %>
                    <li><span class="rp-pay-name"><i class="rp-dot <%=cls%>"></i><%=h(paymentLabel(row.getLabel()))%></span><span class="rp-pay-track"><span class="<%=cls%>" style="width:<%=share(row.getCount(), paymentTotal).replace(',', '.')%>%"></span></span><b><%=share(row.getCount(), paymentTotal)%>%</b><em><%=row.getCount()%> đơn</em></li>
                    <% } %>
                </ul>
                <% } %>
            </section>
        </div>

        <div class="rp-grid">
            <section class="rp-panel">
                <div class="rp-panel-head"><div><span class="rp-no">04</span><h2>Sản phẩm bán chạy</h2><p>Năm mẫu mang về doanh số cao nhất trong kỳ.</p></div><a class="rp-link" href="<%=ctx%>/admin/products">Tất cả sản phẩm →</a></div>
                <% if (topProducts == null || topProducts.isEmpty()) { %>
                    <p class="rp-empty">Chưa có sản phẩm nào được bán trong kỳ.</p>
                <% } else { int rank = 0; %>
                <ol class="rp-rank">
                    <% for (ReportBreakdown row : topProducts) { rank++; %>
                    <li>
                        <span class="rp-rank-no"><%=rank < 10 ? "0" + rank : String.valueOf(rank)%></span>
                        <div class="rp-rank-body">
                            <p><b><%=h(row.getLabel())%></b><span><%=money.format(row.getAmount())%>₫</span></p>
                            <span class="rp-bar-track"><span class="rp-bar" style="width:<%=width(row.getAmount(), productMax)%>%"></span></span>
                            <small><%=h(row.getDetail() == null ? "" : row.getDetail())%> · <%=row.getCount()%> sản phẩm đã bán</small>
                        </div>
                    </li>
                    <% } %>
                </ol>
                <% } %>
            </section>

            <section class="rp-panel">
                <div class="rp-panel-head"><div><span class="rp-no">05</span><h2>Kho hàng</h2><p>Số liệu hiện tại, không phụ thuộc khoảng ngày.</p></div><a class="rp-link" href="<%=ctx%>/admin/variants">Quản lý SKU →</a></div>
                <dl class="rp-stock">
                    <div><dt>Tồn kho</dt><dd><%=money.format(report.getInventoryUnits())%></dd><small>sản phẩm</small></div>
                    <div><dt>SKU đang bán</dt><dd><%=money.format(report.getActiveVariants())%></dd><small>biến thể</small></div>
                    <div class="<%=report.getLowStockVariants() > 0 ? "is-warn" : ""%>"><dt>Sắp hết</dt><dd><%=money.format(report.getLowStockVariants())%></dd><small>còn ≤ 5</small></div>
                </dl>
                <% if (lowStock == null || lowStock.isEmpty()) { %>
                    <p class="rp-calm"><span aria-hidden="true">✓</span> Không có SKU nào sắp hết hàng.</p>
                <% } else { %>
                <h3 class="rp-minor">Cần nhập thêm</h3>
                <ul class="rp-low">
                    <% for (ReportBreakdown row : lowStock) { boolean soldOut = row.getCount() <= 0; %>
                    <li><div><b><%=h(row.getLabel())%></b><small><%=h(row.getDetail())%></small></div><span class="rp-qty <%=soldOut ? "is-out" : "is-low"%>"><i aria-hidden="true"><%=soldOut ? "✕" : "!"%></i><%=soldOut ? "Hết hàng" : "Còn " + row.getCount()%></span></li>
                    <% } %>
                </ul>
                <% } %>
            </section>
        </div>

        <p class="rp-note">Doanh thu chỉ tính đơn đã thanh toán, hoặc đơn COD đã giao thành công: tiền hàng trừ giảm giá, cộng phí giao. Doanh số theo danh mục và sản phẩm tính trên giá bán của các đơn không hủy, nên có thể cao hơn doanh thu ghi nhận.</p>
    </main></div>
    <script src="<%=ctx%>/assets/mensform-reports.js?v=20261024g" defer></script>
    </body>
</html>
