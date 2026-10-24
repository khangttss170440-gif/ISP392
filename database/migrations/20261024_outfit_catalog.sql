-- More MENSFORM products so the outfit builder can form sets, plus the five accessories
-- from the homepage flat lay, plus 8 published store collections (2 per occasion).
-- Requires dbo.OutfitCollection / dbo.OutfitCollectionItem (20261022_outfit_collections.sql).
-- Backup taken before running: MenWearStore_before_outfits_20261024.bak
-- Run: sqlcmd -S localhost -E -d MenWearStore -C -f 65001 -i 20261024_outfit_catalog.sql
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @Categories TABLE (CategoryName nvarchar(100), Description nvarchar(500));
INSERT INTO @Categories VALUES
    (N'Áo sơ mi', N'Sơ mi nam Oxford và linen'),
    (N'Áo polo', N'Áo polo nam cotton piqué'),
    (N'Áo thun', N'Áo thun nam cổ tròn'),
    (N'Quần chino', N'Quần chino nam dáng regular'),
    (N'Quần jeans', N'Quần jeans nam dáng slim'),
    (N'Phụ kiện cài áo', N'Khuy măng sét, ghim cài áo');

INSERT INTO dbo.ProductCategory (CategoryName, Description, Status)
SELECT c.CategoryName, c.Description, 'Active'
FROM @Categories c
WHERE NOT EXISTS (SELECT 1 FROM dbo.ProductCategory pc WHERE pc.CategoryName = c.CategoryName);

DECLARE @Products TABLE (CategoryName nvarchar(100), ProductName nvarchar(200), Material nvarchar(100), Description nvarchar(1000));
INSERT INTO @Products VALUES
    (N'Áo sơ mi', N'Áo sơ mi Oxford dài tay', N'100% cotton Oxford',
        N'Sơ mi Oxford dày vừa, cổ button-down, mặc đi làm với quần âu hoặc xắn tay với chino cuối tuần.'),
    (N'Áo sơ mi', N'Áo sơ mi linen cổ bẻ', N'55% linen, 45% cotton',
        N'Sơ mi linen thoáng mát, rủ nhẹ, hợp ngày nóng và những buổi đi chơi xa.'),
    (N'Áo polo', N'Áo polo cotton piqué', N'100% cotton piqué',
        N'Polo vải piqué đứng form, cổ dệt gân, gọn gàng mà vẫn thoải mái.'),
    (N'Áo thun', N'Áo thun cổ tròn basic', N'100% cotton 250gsm',
        N'Áo thun cotton dày dặn, cổ bo chắc, mặc riêng hay lót trong áo khoác đều đẹp.'),
    (N'Quần chino', N'Quần chino dáng regular', N'98% cotton, 2% spandex',
        N'Chino dáng regular, co giãn nhẹ, ống vừa phải, dễ phối với sơ mi, polo hay áo thun.'),
    (N'Quần jeans', N'Quần jeans slim xanh đậm', N'99% cotton denim, 1% spandex',
        N'Jeans slim màu xanh đậm, wash nhẹ, dáng gọn nhưng không bó.'),
    (N'Áo khoác', N'Áo blazer dáng suông', N'Polyester pha viscose',
        N'Blazer không lót dáng suông, vai mềm, khoác ngoài sơ mi hay áo thun đều chỉn chu.'),
    (N'Giày', N'Giày loafer da bò', N'Da bò nguyên tấm, đế cao su',
        N'Loafer da bò trơn, đế cao su êm, đi cùng quần âu hoặc chino xắn gấu.'),
    (N'Đồng hồ', N'Đồng hồ mặt gỗ dây da', N'Vỏ gỗ mun, dây da bò',
        N'Vỏ và mặt gỗ mun, dây da đen. Hợp sơ mi sáng màu và bộ vest tối.'),
    (N'Phụ kiện cài áo', N'Khuy măng sét bạc', N'Hợp kim mạ bạc',
        N'Khuy măng sét bạc tạo hình gấu, điểm nhấn kim loại nhỏ cho sơ mi cổ tay gập.'),
    (N'Kính mát', N'Kính mát gọng gỗ', N'Gọng gỗ, tròng phân cực',
        N'Gọng gỗ vuông, tròng tráng gương phân cực. Nhẹ và ấm tay.'),
    (N'Kính mát', N'Kính mát clubmaster', N'Gọng acetate và kim loại, tròng phân cực',
        N'Viền trên đậm, gọng kim loại dưới. Kiểu cổ điển không lỗi thời.'),
    (N'Cà vạt', N'Cà vạt hoa văn kèm ghim cài', N'Lụa pha polyester',
        N'Cà vạt lụa hoa văn tông hồng khói, kèm ghim cài ve áo hình hoa hồng.');

INSERT INTO dbo.Product (CategoryID, ProductName, Description, Brand, Material, Status)
SELECT pc.CategoryID, p.ProductName, p.Description, N'MENSFORM', p.Material, 'Active'
FROM @Products p
JOIN dbo.ProductCategory pc ON pc.CategoryName = p.CategoryName
WHERE NOT EXISTS (SELECT 1 FROM dbo.Product x WHERE x.ProductName = p.ProductName AND x.Brand = N'MENSFORM');

DECLARE @Variants TABLE (ProductName nvarchar(200), SKU varchar(50), Color nvarchar(50), Size nvarchar(20), Price decimal(18,2));
INSERT INTO @Variants VALUES
    (N'Áo sơ mi Oxford dài tay','MF-OXF-WHT-M',N'Trắng','M',450000),(N'Áo sơ mi Oxford dài tay','MF-OXF-WHT-L',N'Trắng','L',450000),(N'Áo sơ mi Oxford dài tay','MF-OXF-WHT-XL',N'Trắng','XL',450000),
    (N'Áo sơ mi Oxford dài tay','MF-OXF-LBL-M',N'Xanh nhạt','M',450000),(N'Áo sơ mi Oxford dài tay','MF-OXF-LBL-L',N'Xanh nhạt','L',450000),(N'Áo sơ mi Oxford dài tay','MF-OXF-LBL-XL',N'Xanh nhạt','XL',450000),
    (N'Áo sơ mi linen cổ bẻ','MF-LIN-BEI-M',N'Be','M',490000),(N'Áo sơ mi linen cổ bẻ','MF-LIN-BEI-L',N'Be','L',490000),(N'Áo sơ mi linen cổ bẻ','MF-LIN-BEI-XL',N'Be','XL',490000),
    (N'Áo sơ mi linen cổ bẻ','MF-LIN-NVY-M',N'Navy','M',490000),(N'Áo sơ mi linen cổ bẻ','MF-LIN-NVY-L',N'Navy','L',490000),(N'Áo sơ mi linen cổ bẻ','MF-LIN-NVY-XL',N'Navy','XL',490000),
    (N'Áo polo cotton piqué','MF-POL-NVY-M',N'Navy','M',390000),(N'Áo polo cotton piqué','MF-POL-NVY-L',N'Navy','L',390000),(N'Áo polo cotton piqué','MF-POL-NVY-XL',N'Navy','XL',390000),
    (N'Áo polo cotton piqué','MF-POL-WHT-M',N'Trắng','M',390000),(N'Áo polo cotton piqué','MF-POL-WHT-L',N'Trắng','L',390000),(N'Áo polo cotton piqué','MF-POL-WHT-XL',N'Trắng','XL',390000),
    (N'Áo thun cổ tròn basic','MF-TEE-WHT-M',N'Trắng','M',250000),(N'Áo thun cổ tròn basic','MF-TEE-WHT-L',N'Trắng','L',250000),(N'Áo thun cổ tròn basic','MF-TEE-WHT-XL',N'Trắng','XL',250000),
    (N'Áo thun cổ tròn basic','MF-TEE-BLK-M',N'Đen','M',250000),(N'Áo thun cổ tròn basic','MF-TEE-BLK-L',N'Đen','L',250000),(N'Áo thun cổ tròn basic','MF-TEE-BLK-XL',N'Đen','XL',250000),
    (N'Quần chino dáng regular','MF-CHN-BEI-30',N'Be','30',520000),(N'Quần chino dáng regular','MF-CHN-BEI-31',N'Be','31',520000),(N'Quần chino dáng regular','MF-CHN-BEI-32',N'Be','32',520000),
    (N'Quần chino dáng regular','MF-CHN-OLV-30',N'Xanh rêu','30',520000),(N'Quần chino dáng regular','MF-CHN-OLV-31',N'Xanh rêu','31',520000),(N'Quần chino dáng regular','MF-CHN-OLV-32',N'Xanh rêu','32',520000),
    (N'Quần jeans slim xanh đậm','MF-JNS-IND-30',N'Xanh đậm','30',650000),(N'Quần jeans slim xanh đậm','MF-JNS-IND-31',N'Xanh đậm','31',650000),(N'Quần jeans slim xanh đậm','MF-JNS-IND-32',N'Xanh đậm','32',650000),
    (N'Áo blazer dáng suông','MF-BLZ-NVY-M',N'Navy','M',1490000),(N'Áo blazer dáng suông','MF-BLZ-NVY-L',N'Navy','L',1490000),(N'Áo blazer dáng suông','MF-BLZ-NVY-XL',N'Navy','XL',1490000),
    (N'Áo blazer dáng suông','MF-BLZ-BEI-M',N'Be','M',1490000),(N'Áo blazer dáng suông','MF-BLZ-BEI-L',N'Be','L',1490000),(N'Áo blazer dáng suông','MF-BLZ-BEI-XL',N'Be','XL',1490000),
    (N'Giày loafer da bò','MF-LOF-BRN-39',N'Nâu','39',1190000),(N'Giày loafer da bò','MF-LOF-BRN-40',N'Nâu','40',1190000),(N'Giày loafer da bò','MF-LOF-BRN-41',N'Nâu','41',1190000),(N'Giày loafer da bò','MF-LOF-BRN-42',N'Nâu','42',1190000),(N'Giày loafer da bò','MF-LOF-BRN-43',N'Nâu','43',1190000),
    (N'Giày loafer da bò','MF-LOF-BLK-39',N'Đen','39',1190000),(N'Giày loafer da bò','MF-LOF-BLK-40',N'Đen','40',1190000),(N'Giày loafer da bò','MF-LOF-BLK-41',N'Đen','41',1190000),(N'Giày loafer da bò','MF-LOF-BLK-42',N'Đen','42',1190000),(N'Giày loafer da bò','MF-LOF-BLK-43',N'Đen','43',1190000),
    (N'Đồng hồ mặt gỗ dây da','MF-WWD-EBN-42',N'Gỗ mun','42mm',1290000),
    (N'Khuy măng sét bạc','MF-CUF-SLV',N'Bạc','Free Size',350000),
    (N'Kính mát gọng gỗ','MF-SGW-WOD',N'Nâu gỗ','Free Size',690000),
    (N'Kính mát clubmaster','MF-SGC-BLK',N'Đen viền bạc','Free Size',650000),
    (N'Cà vạt hoa văn kèm ghim cài','MF-TIE-FLR',N'Hồng khói','7cm',420000);

INSERT INTO dbo.ProductVariant (ProductID, SKU, Color, Size, Price, Status, Quantity)
SELECT p.ProductID, v.SKU, v.Color, v.Size, v.Price, 'Active', 10
FROM @Variants v
JOIN dbo.Product p ON p.ProductName = v.ProductName AND p.Brand = N'MENSFORM'
WHERE NOT EXISTS (SELECT 1 FROM dbo.ProductVariant x WHERE x.SKU = v.SKU);

-- Store collections. Pieces reference products by name; colors must match variant colors.
DECLARE @Staff int = (SELECT TOP 1 StaffID FROM dbo.Staff WHERE Role = 'Admin' AND Status = 'Active' ORDER BY StaffID);
IF @Staff IS NULL THROW 50001, 'No active Admin staff to own the collections.', 1;

DECLARE @Sets TABLE (Title nvarchar(120), Style varchar(20), Description nvarchar(1000), Notes nvarchar(1000));
INSERT INTO @Sets VALUES
    (N'Sơ mi trắng, blazer navy', 'office', N'Bộ đi làm chuẩn mực: sơ mi Oxford trắng, quần âu xám và blazer navy, điểm thêm cà vạt chấm bi.', N'Cài hết cúc sơ mi khi đeo cà vạt. Bỏ cà vạt và mở một cúc khi gặp đối tác thân quen.'),
    (N'Len be, quần âu xám', 'office', N'Áo len be thay cho vest những ngày mát trời, vẫn gọn gàng nơi công sở.', N'Thắt lưng và giày cùng tông nâu để bộ đồ liền mạch.'),
    (N'Polo navy, chino be', 'everyday', N'Polo navy và chino be: hai món dễ mặc nhất tủ đồ, đi cùng sneaker trắng.', N'Xắn gấu chino một lần để lộ cổ chân khi đi sneaker.'),
    (N'Thun trắng, jeans đậm', 'everyday', N'Áo thun trắng, jeans xanh đậm và đồng hồ mặt gỗ cho ngày thường thoải mái.', N'Sơ vin áo thun và dùng thắt lưng đen cho gọn dáng.'),
    (N'Linen và da lộn', 'weekend', N'Sơ mi linen be, chino xanh rêu và áo khoác da lộn nâu cho chuyến đi cuối tuần.', N'Mở hai cúc sơ mi linen, đeo kính gọng gỗ cho tông đất đồng bộ.'),
    (N'Thun đen, jeans, clubmaster', 'weekend', N'Áo thun đen, jeans đậm, khoác da lộn đen và kính clubmaster.', N'Giữ cả set tông tối, để sneaker trắng làm điểm sáng.'),
    (N'Mặt bàn phụ kiện', 'evening', N'Set từ ảnh phụ kiện trang chủ: sơ mi trắng, blazer navy, cà vạt hoa văn và khuy măng sét bạc.', N'Ghim hoa hồng cài ve áo trái, khuy măng sét chọn sơ mi cổ tay gập.'),
    (N'Len đen tối giản', 'evening', N'Áo len đen, quần âu xám và loafer đen cho buổi hẹn tối.', N'Đồng hồ mặt gỗ là điểm nhấn duy nhất, không cần thêm phụ kiện.');

INSERT INTO dbo.OutfitCollection (Title, Description, StylingNotes, Style, Status, CreatedBy, UpdatedBy)
SELECT s.Title, s.Description, s.Notes, s.Style, 'Published', @Staff, @Staff
FROM @Sets s
WHERE NOT EXISTS (SELECT 1 FROM dbo.OutfitCollection o WHERE o.Title = s.Title);

DECLARE @Pieces TABLE (Title nvarchar(120), ProductName nvarchar(200), Color nvarchar(100), Role varchar(12), SortOrder int);
INSERT INTO @Pieces VALUES
    (N'Sơ mi trắng, blazer navy', N'Áo sơ mi Oxford dài tay', N'Trắng', 'TOP', 0),
    (N'Sơ mi trắng, blazer navy', N'Quần âu dáng slim', N'Xám', 'BOTTOM', 1),
    (N'Sơ mi trắng, blazer navy', N'Áo blazer dáng suông', N'Navy', 'LAYER', 2),
    (N'Sơ mi trắng, blazer navy', N'Giày loafer da bò', N'Đen', 'SHOES', 3),
    (N'Sơ mi trắng, blazer navy', N'Cà vạt lụa bản nhỏ 6cm', N'Navy chấm bi', 'ACCESSORY', 4),
    (N'Sơ mi trắng, blazer navy', N'Đồng hồ dây da Classic 40mm', N'Đen', 'ACCESSORY', 5),
    (N'Len be, quần âu xám', N'Áo len cổ tròn dệt mịn', N'Be', 'TOP', 0),
    (N'Len be, quần âu xám', N'Quần âu dáng slim', N'Xám', 'BOTTOM', 1),
    (N'Len be, quần âu xám', N'Giày loafer da bò', N'Nâu', 'SHOES', 2),
    (N'Len be, quần âu xám', N'Thắt lưng da bò khóa kim bản 3cm', N'Nâu', 'ACCESSORY', 3),
    (N'Len be, quần âu xám', N'Đồng hồ dây da Classic 40mm', N'Nâu', 'ACCESSORY', 4),
    (N'Polo navy, chino be', N'Áo polo cotton piqué', N'Navy', 'TOP', 0),
    (N'Polo navy, chino be', N'Quần chino dáng regular', N'Be', 'BOTTOM', 1),
    (N'Polo navy, chino be', N'Giày sneaker da trắng', N'Trắng', 'SHOES', 2),
    (N'Polo navy, chino be', N'Kính mát gọng kim loại phân cực', N'Gọng vàng', 'ACCESSORY', 3),
    (N'Thun trắng, jeans đậm', N'Áo thun cổ tròn basic', N'Trắng', 'TOP', 0),
    (N'Thun trắng, jeans đậm', N'Quần jeans slim xanh đậm', N'Xanh đậm', 'BOTTOM', 1),
    (N'Thun trắng, jeans đậm', N'Giày sneaker da trắng', N'Trắng', 'SHOES', 2),
    (N'Thun trắng, jeans đậm', N'Thắt lưng da bò khóa kim bản 3cm', N'Đen', 'ACCESSORY', 3),
    (N'Thun trắng, jeans đậm', N'Đồng hồ mặt gỗ dây da', N'Gỗ mun', 'ACCESSORY', 4),
    (N'Linen và da lộn', N'Áo sơ mi linen cổ bẻ', N'Be', 'TOP', 0),
    (N'Linen và da lộn', N'Quần chino dáng regular', N'Xanh rêu', 'BOTTOM', 1),
    (N'Linen và da lộn', N'Áo khoác da lộn dáng suông', N'Nâu', 'LAYER', 2),
    (N'Linen và da lộn', N'Giày loafer da bò', N'Nâu', 'SHOES', 3),
    (N'Linen và da lộn', N'Kính mát gọng gỗ', N'Nâu gỗ', 'ACCESSORY', 4),
    (N'Linen và da lộn', N'Túi đeo chéo da bò', N'Nâu', 'ACCESSORY', 5),
    (N'Thun đen, jeans, clubmaster', N'Áo thun cổ tròn basic', N'Đen', 'TOP', 0),
    (N'Thun đen, jeans, clubmaster', N'Quần jeans slim xanh đậm', N'Xanh đậm', 'BOTTOM', 1),
    (N'Thun đen, jeans, clubmaster', N'Áo khoác da lộn dáng suông', N'Đen', 'LAYER', 2),
    (N'Thun đen, jeans, clubmaster', N'Giày sneaker da trắng', N'Trắng', 'SHOES', 3),
    (N'Thun đen, jeans, clubmaster', N'Kính mát clubmaster', N'Đen viền bạc', 'ACCESSORY', 4),
    (N'Mặt bàn phụ kiện', N'Áo sơ mi Oxford dài tay', N'Trắng', 'TOP', 0),
    (N'Mặt bàn phụ kiện', N'Quần âu dáng slim', N'Xám', 'BOTTOM', 1),
    (N'Mặt bàn phụ kiện', N'Áo blazer dáng suông', N'Navy', 'LAYER', 2),
    (N'Mặt bàn phụ kiện', N'Giày loafer da bò', N'Nâu', 'SHOES', 3),
    (N'Mặt bàn phụ kiện', N'Cà vạt hoa văn kèm ghim cài', N'Hồng khói', 'ACCESSORY', 4),
    (N'Mặt bàn phụ kiện', N'Khuy măng sét bạc', N'Bạc', 'ACCESSORY', 5),
    (N'Len đen tối giản', N'Áo len cổ tròn dệt mịn', N'Đen', 'TOP', 0),
    (N'Len đen tối giản', N'Quần âu dáng slim', N'Xám', 'BOTTOM', 1),
    (N'Len đen tối giản', N'Giày loafer da bò', N'Đen', 'SHOES', 2),
    (N'Len đen tối giản', N'Đồng hồ mặt gỗ dây da', N'Gỗ mun', 'ACCESSORY', 3);

INSERT INTO dbo.OutfitCollectionItem (CollectionID, ProductID, Color, Role, SortOrder)
SELECT o.CollectionID, p.ProductID, x.Color, x.Role, x.SortOrder
FROM @Pieces x
JOIN dbo.OutfitCollection o ON o.Title = x.Title
JOIN dbo.Product p ON p.ProductName = x.ProductName AND p.Brand = N'MENSFORM' AND p.Status = 'Active'
WHERE NOT EXISTS (SELECT 1 FROM dbo.OutfitCollectionItem i WHERE i.CollectionID = o.CollectionID AND i.ProductID = p.ProductID);

-- every piece must have resolved to a product with that color
IF EXISTS (SELECT 1 FROM @Pieces x
        WHERE NOT EXISTS (SELECT 1 FROM dbo.OutfitCollection o
            JOIN dbo.OutfitCollectionItem i ON i.CollectionID = o.CollectionID AND i.SortOrder = x.SortOrder
            JOIN dbo.ProductVariant v ON v.ProductID = i.ProductID AND v.Color = i.Color AND v.Status = 'Active'
            WHERE o.Title = x.Title AND i.Color = x.Color))
    THROW 50002, 'Some collection pieces did not match a product/color.', 1;

COMMIT TRANSACTION;
