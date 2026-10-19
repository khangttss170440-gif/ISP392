<%@page contentType="text/html" pageEncoding="UTF-8"%>
<%@page import="java.util.List,model.ProductVariant,model.StocktakingRecord"%>
<%!
    private String stocktakingEscape(Object value) {
        if (value == null) return "";
        return value.toString()
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String stocktakingCycle(String cycle) {
        if ("Weekly".equalsIgnoreCase(cycle)) return "Hàng tuần";
        if ("Monthly".equalsIgnoreCase(cycle)) return "Hàng tháng";
        return stocktakingEscape(cycle);
    }
%>
<%
    List<ProductVariant> variants =
            (List<ProductVariant>) request.getAttribute("variants");
    List<StocktakingRecord> history =
            (List<StocktakingRecord>) request.getAttribute("history");
    String message = (String) request.getAttribute("message");
    String error = (String) request.getAttribute("error");
%>
<!DOCTYPE html>
<html lang="vi">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title>Kiểm kê tồn kho | MENSFORM</title>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/assets/store.css">
    <style>
        .stocktaking-toolbar{display:flex;align-items:end;justify-content:space-between;gap:16px;flex-wrap:wrap;margin:18px 0}
        .stocktaking-search{max-width:420px;margin:0}
        .stocktaking-note{color:var(--muted);font-size:14px;margin:12px 0 18px}
        .stocktaking-number{width:112px;min-width:90px;padding:9px 10px}
        .stocktaking-delta{font-weight:700;white-space:nowrap}
        .stocktaking-delta.positive{color:#b54708}.stocktaking-delta.negative{color:#b42318}.stocktaking-delta.zero{color:#26734d}
        .stocktaking-actions{display:flex;align-items:center;gap:14px;flex-wrap:wrap;margin-top:18px}
        .stocktaking-count{color:var(--muted);font-size:14px}
        .stocktaking-panel h2{margin-top:0}
        @media(max-width:700px){.stocktaking-search{max-width:none;width:100%}.stocktaking-number{width:95px}}
    </style>
</head>
<body>
<div class="portal-layout">
    <%@include file="/staff/includes/nav.jspf"%>
    <main class="portal-main">
        <header class="portal-head">
            <div>
                <span class="eyebrow">WAREHOUSE</span>
                <h1>Kiểm kê tồn kho</h1>
                <p>Đối chiếu số lượng thực tế với tồn kho trên hệ thống theo từng SKU.</p>
            </div>
        </header>

        <% if (message != null) { %>
            <div class="alert success"><%=stocktakingEscape(message)%></div>
        <% } %>
        <% if (error != null) { %>
            <div class="alert error"><%=stocktakingEscape(error)%></div>
        <% } %>

        <section class="portal-panel stocktaking-panel">
            <h2>Tạo phiếu kiểm kê</h2>
            <p class="stocktaking-note">Chỉ nhập số lượng thực tế cho những biến thể đã đếm. Khi lưu, tồn kho của các dòng đã nhập sẽ được cập nhật ngay. Mỗi phiếu tối đa 50 SKU.</p>

            <div class="stocktaking-toolbar">
                <label class="form-label stocktaking-search">
                    Tìm sản phẩm / SKU
                    <input class="field" id="variantSearch" type="search" placeholder="Nhập tên sản phẩm, SKU, màu hoặc cỡ">
                </label>
                <span class="portal-muted">Đang có <b id="visibleVariantCount"><%=variants == null ? 0 : variants.size()%></b> biến thể hoạt động</span>
            </div>

            <form id="stocktakingForm" action="<%=request.getContextPath()%>/staff/warehouse/stocktaking" method="post">
                <div class="portal-form-grid" style="margin-bottom:18px">
                    <div>
                        <label class="form-label" for="cycleType">Loại kiểm kê *</label>
                        <select class="select" id="cycleType" name="cycleType" required>
                            <option value="Weekly">Kiểm kê hàng tuần</option>
                            <option value="Monthly">Kiểm kê hàng tháng</option>
                        </select>
                    </div>
                    <div>
                        <label class="form-label" for="stocktakingNote">Ghi chú</label>
                        <input class="field" id="stocktakingNote" name="note" maxlength="500" placeholder="Ví dụ: Kiểm kê kệ A ngày 16/10">
                    </div>
                </div>

                <div class="portal-table-wrap">
                    <table class="portal-table" id="variantTable">
                        <thead>
                            <tr>
                                <th>Sản phẩm</th>
                                <th>SKU</th>
                                <th>Màu / cỡ</th>
                                <th>Tồn hệ thống</th>
                                <th>Số lượng thực tế</th>
                            </tr>
                        </thead>
                        <tbody>
                            <% if (variants == null || variants.isEmpty()) { %>
                                <tr><td colspan="5" class="admin-empty">Không có biến thể đang hoạt động để kiểm kê.</td></tr>
                            <% } else { for (ProductVariant variant : variants) {
                                String searchText = (variant.getProductName() == null ? "" : variant.getProductName())
                                        + " " + (variant.getSku() == null ? "" : variant.getSku())
                                        + " " + (variant.getColor() == null ? "" : variant.getColor())
                                        + " " + (variant.getSize() == null ? "" : variant.getSize());
                            %>
                                <tr class="variant-row" data-search="<%=stocktakingEscape(searchText).toLowerCase(java.util.Locale.ROOT)%>">
                                    <td><b><%=stocktakingEscape(variant.getProductName())%></b><% if (variant.getBrand() != null && !variant.getBrand().trim().isEmpty()) { %><div class="portal-muted"><%=stocktakingEscape(variant.getBrand())%></div><% } %></td>
                                    <td><%=stocktakingEscape(variant.getSku())%><input type="hidden" name="variantId" value="<%=variant.getVariantID()%>"></td>
                                    <td><%=stocktakingEscape(variant.getColor())%> / <%=stocktakingEscape(variant.getSize())%></td>
                                    <td><b><%=variant.getQuantity()%></b></td>
                                    <td><input class="field stocktaking-number actual-quantity" type="number" name="actualQuantity" min="0" max="1000000" step="1" inputmode="numeric" aria-label="Số lượng thực tế: <%=stocktakingEscape(variant.getProductName())%> <%=stocktakingEscape(variant.getSku())%>" placeholder="Nhập SL"></td>
                                </tr>
                            <% }} %>
                        </tbody>
                    </table>
                </div>
                <div class="stocktaking-actions">
                    <button class="button" type="submit" <%=variants == null || variants.isEmpty() ? "disabled" : ""%>>Lưu phiếu và cập nhật tồn kho</button>
                    <span class="stocktaking-count"><b id="enteredCount">0</b> SKU được chọn</span>
                </div>
            </form>
        </section>

        <section class="portal-panel stocktaking-panel" style="margin-top:22px">
            <div class="portal-head">
                <div>
                    <h2>Lịch sử kiểm kê gần đây</h2>
                    <p>Hiển thị tối đa 100 dòng mới nhất; chênh lệch = số thực tế trừ số trên hệ thống.</p>
                </div>
            </div>
            <div class="portal-table-wrap">
                <table class="portal-table">
                    <thead>
                        <tr>
                            <th>Thời gian</th>
                            <th>Sản phẩm / SKU</th>
                            <th>Loại</th>
                            <th>Hệ thống</th>
                            <th>Thực tế</th>
                            <th>Chênh lệch</th>
                            <th>Nhân viên</th>
                            <th>Ghi chú</th>
                        </tr>
                    </thead>
                    <tbody>
                        <% if (history == null || history.isEmpty()) { %>
                            <tr><td colspan="8" class="admin-empty">Chưa có phiếu kiểm kê nào.</td></tr>
                        <% } else { for (StocktakingRecord record : history) {
                            int difference = record.getDifferenceQuantity();
                            String deltaClass = difference > 0 ? "positive" : difference < 0 ? "negative" : "zero";
                            String deltaText = difference > 0 ? "+" + difference : String.valueOf(difference);
                        %>
                            <tr>
                                <td><%=record.getStocktakingDate() == null ? "" : stocktakingEscape(record.getStocktakingDate())%></td>
                                <td><b><%=stocktakingEscape(record.getProductName())%></b><div class="portal-muted"><%=stocktakingEscape(record.getSku())%> · <%=stocktakingEscape(record.getColor())%> / <%=stocktakingEscape(record.getSize())%></div></td>
                                <td><%=stocktakingCycle(record.getCycleType())%></td>
                                <td><%=record.getSystemQuantity()%></td>
                                <td><%=record.getActualQuantity()%></td>
                                <td><span class="stocktaking-delta <%=deltaClass%>"><%=deltaText%></span></td>
                                <td><%=stocktakingEscape(record.getStaffName())%></td>
                                <td><%=stocktakingEscape(record.getNote())%></td>
                            </tr>
                        <% }} %>
                    </tbody>
                </table>
            </div>
        </section>
    </main>
</div>
<script>
(function () {
    const search = document.getElementById('variantSearch');
    const rows = Array.from(document.querySelectorAll('.variant-row'));
    const visibleCount = document.getElementById('visibleVariantCount');
    const enteredCount = document.getElementById('enteredCount');
    const form = document.getElementById('stocktakingForm');

    function updateCounts() {
        let visible = 0;
        let entered = 0;
        rows.forEach(function (row) {
            if (!row.hidden) visible++;
            const input = row.querySelector('.actual-quantity');
            if (input && input.value.trim() !== '') entered++;
        });
        visibleCount.textContent = visible;
        enteredCount.textContent = entered;
    }

    search.addEventListener('input', function () {
        const query = search.value.trim().toLocaleLowerCase('vi');
        rows.forEach(function (row) {
            row.hidden = query !== '' && !row.dataset.search.includes(query);
        });
        updateCounts();
    });

    rows.forEach(function (row) {
        const input = row.querySelector('.actual-quantity');
        if (input) input.addEventListener('input', updateCounts);
    });

    form.addEventListener('submit', function (event) {
        const inputs = Array.from(form.querySelectorAll('.actual-quantity'));
        const filled = inputs.filter(function (input) {
            return input.value.trim() !== '';
        });
        if (filled.length === 0) {
            event.preventDefault();
            alert('Vui lòng nhập số lượng thực tế cho ít nhất một SKU.');
            return;
        }
        if (filled.length > 50) {
            event.preventDefault();
            alert('Mỗi lần kiểm kê tối đa 50 SKU.');
            return;
        }
        for (const input of filled) {
            const quantity = Number(input.value);
            if (!Number.isInteger(quantity) || quantity < 0 || quantity > 1000000) {
                event.preventDefault();
                input.focus();
                alert('Số lượng thực tế phải là số nguyên từ 0 đến 1.000.000.');
                return;
            }
        }

        rows.forEach(function (row) {
            const input = row.querySelector('.actual-quantity');
            if (input && input.value.trim() === '') {
                input.disabled = true;
                row.querySelector('input[name="variantId"]').disabled = true;
            }
        });
    });

    updateCounts();
})();
</script>
</body>
</html>
