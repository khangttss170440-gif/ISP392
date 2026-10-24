-- Replace the storefront catalog with the MENSFORM accessory collection.
-- Old products are hidden (Status = 'Inactive'), not deleted, so order history stays intact.
-- Backup taken before running: MenWearStore_before_accessories_20261024.bak
-- Run: sqlcmd -S localhost -E -d MenWearStore -C -f 65001 -i 20261024_accessory_catalog.sql
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

UPDATE dbo.Product SET Status = 'Inactive', UpdatedAt = GETDATE() WHERE Status = 'Active' AND ISNULL(Brand, N'') <> N'MENSFORM';

DECLARE @Categories TABLE (CategoryName nvarchar(100), Description nvarchar(500));
INSERT INTO @Categories VALUES
    (N'Đồng hồ', N'Đồng hồ nam dây da, mặt tối giản'),
    (N'Thắt lưng', N'Thắt lưng da bò khóa kim loại'),
    (N'Ví & bao thẻ', N'Ví da và bao thẻ gọn nhẹ'),
    (N'Túi da', N'Túi đeo chéo và túi da nam'),
    (N'Kính mát', N'Kính mát nam chống tia UV'),
    (N'Cà vạt', N'Cà vạt lụa bản nhỏ');

INSERT INTO dbo.ProductCategory (CategoryName, Description, Status)
SELECT c.CategoryName, c.Description, 'Active'
FROM @Categories c
WHERE NOT EXISTS (SELECT 1 FROM dbo.ProductCategory pc WHERE pc.CategoryName = c.CategoryName);

DECLARE @Products TABLE (CategoryName nvarchar(100), ProductName nvarchar(200), Material nvarchar(100), Description nvarchar(1000));
INSERT INTO @Products VALUES
    (N'Đồng hồ', N'Đồng hồ dây da Classic 40mm', N'Thép không gỉ, dây da bò',
        N'Mặt tròn 40mm tối giản, vỏ thép không gỉ, dây da bò mềm. Hợp đi làm và dự tiệc.'),
    (N'Thắt lưng', N'Thắt lưng da bò khóa kim bản 3cm', N'Da bò thật',
        N'Da bò thật bản 3cm, khóa kim hợp kim mạ bạc, đường may viền chắc chắn.'),
    (N'Ví & bao thẻ', N'Ví da bò gập đôi', N'Da bò thật',
        N'Ví gập đôi mỏng gọn, 6 ngăn thẻ, 1 ngăn tiền, da bò thuộc mềm.'),
    (N'Túi da', N'Túi đeo chéo da bò', N'Da bò thật',
        N'Túi đeo chéo vừa máy tính bảng 11 inch, quai chỉnh độ dài, khóa kim loại.'),
    (N'Kính mát', N'Kính mát gọng kim loại phân cực', N'Kim loại, tròng phân cực',
        N'Gọng kim loại mảnh, tròng phân cực chống tia UV400, kèm hộp và khăn lau.'),
    (N'Cà vạt', N'Cà vạt lụa bản nhỏ 6cm', N'Lụa pha',
        N'Cà vạt bản 6cm hiện đại, chất lụa pha mềm, giữ nút thắt gọn.');

INSERT INTO dbo.Product (CategoryID, ProductName, Description, Brand, Material, Status)
SELECT pc.CategoryID, p.ProductName, p.Description, N'MENSFORM', p.Material, 'Active'
FROM @Products p
JOIN dbo.ProductCategory pc ON pc.CategoryName = p.CategoryName
WHERE NOT EXISTS (SELECT 1 FROM dbo.Product x WHERE x.ProductName = p.ProductName AND x.Brand = N'MENSFORM');

DECLARE @Variants TABLE (ProductName nvarchar(200), SKU varchar(50), Color nvarchar(50), Size nvarchar(20), Price decimal(18,2));
INSERT INTO @Variants VALUES
    (N'Đồng hồ dây da Classic 40mm', 'MF-WAT-BRN-40', N'Nâu', N'40mm', 1590000),
    (N'Đồng hồ dây da Classic 40mm', 'MF-WAT-BLK-40', N'Đen', N'40mm', 1590000),
    (N'Thắt lưng da bò khóa kim bản 3cm', 'MF-BLT-BRN-100', N'Nâu', N'100cm', 390000),
    (N'Thắt lưng da bò khóa kim bản 3cm', 'MF-BLT-BRN-110', N'Nâu', N'110cm', 390000),
    (N'Thắt lưng da bò khóa kim bản 3cm', 'MF-BLT-BRN-120', N'Nâu', N'120cm', 390000),
    (N'Thắt lưng da bò khóa kim bản 3cm', 'MF-BLT-BLK-100', N'Đen', N'100cm', 390000),
    (N'Thắt lưng da bò khóa kim bản 3cm', 'MF-BLT-BLK-110', N'Đen', N'110cm', 390000),
    (N'Thắt lưng da bò khóa kim bản 3cm', 'MF-BLT-BLK-120', N'Đen', N'120cm', 390000),
    (N'Ví da bò gập đôi', 'MF-WAL-BRN', N'Nâu', N'Free Size', 450000),
    (N'Ví da bò gập đôi', 'MF-WAL-BLK', N'Đen', N'Free Size', 450000),
    (N'Túi đeo chéo da bò', 'MF-BAG-BRN', N'Nâu', N'Free Size', 1690000),
    (N'Túi đeo chéo da bò', 'MF-BAG-BLK', N'Đen', N'Free Size', 1690000),
    (N'Kính mát gọng kim loại phân cực', 'MF-SUN-GLD', N'Gọng vàng', N'Free Size', 590000),
    (N'Kính mát gọng kim loại phân cực', 'MF-SUN-BLK', N'Gọng đen', N'Free Size', 590000),
    (N'Cà vạt lụa bản nhỏ 6cm', 'MF-TIE-NVY', N'Navy chấm bi', N'6cm', 290000),
    (N'Cà vạt lụa bản nhỏ 6cm', 'MF-TIE-BRN', N'Nâu sọc', N'6cm', 290000);

INSERT INTO dbo.ProductVariant (ProductID, SKU, Color, Size, Price, Status, Quantity)
SELECT p.ProductID, v.SKU, v.Color, v.Size, v.Price, 'Active', 10
FROM @Variants v
JOIN dbo.Product p ON p.ProductName = v.ProductName AND p.Brand = N'MENSFORM'
WHERE NOT EXISTS (SELECT 1 FROM dbo.ProductVariant x WHERE x.SKU = v.SKU);

COMMIT TRANSACTION;
