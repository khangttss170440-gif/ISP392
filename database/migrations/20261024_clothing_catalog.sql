-- Add MENSFORM clothing (jacket, sweater, trousers, sneaker) next to the accessory collection.
-- Old empty categories 1-8 are hidden (Status = 'Inactive'), not deleted.
-- Backup taken before running: MenWearStore_before_clothing_20261024.bak
-- Run: sqlcmd -S localhost -E -d MenWearStore -C -f 65001 -i 20261024_clothing_catalog.sql
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

UPDATE dbo.ProductCategory SET Status = 'Inactive' WHERE CategoryID BETWEEN 1 AND 8;

DECLARE @Categories TABLE (CategoryName nvarchar(100), Description nvarchar(500));
INSERT INTO @Categories VALUES
    (N'Áo khoác', N'Áo khoác nam dáng suông'),
    (N'Áo len', N'Áo len nam mặc đi làm và cuối tuần'),
    (N'Quần âu', N'Quần âu nam dáng slim'),
    (N'Giày', N'Giày nam da và sneaker');

INSERT INTO dbo.ProductCategory (CategoryName, Description, Status)
SELECT c.CategoryName, c.Description, 'Active'
FROM @Categories c
WHERE NOT EXISTS (SELECT 1 FROM dbo.ProductCategory pc WHERE pc.CategoryName = c.CategoryName);

DECLARE @Products TABLE (CategoryName nvarchar(100), ProductName nvarchar(200), Material nvarchar(100), Description nvarchar(1000));
INSERT INTO @Products VALUES
    (N'Áo khoác', N'Áo khoác da lộn dáng suông', N'Da lộn (suede)',
        N'Áo khoác da lộn dáng suông, cổ bẻ, hai túi ốp. Khoác ngoài sơ mi hoặc áo len đều hợp.'),
    (N'Áo len', N'Áo len cổ tròn dệt mịn', N'55% acrylic, 45% cotton',
        N'Áo len cổ tròn dệt mịn, giữ form tốt, mặc riêng hoặc lồng sơ mi đi làm.'),
    (N'Quần âu', N'Quần âu dáng slim', N'Polyester, rayon, spandex',
        N'Quần âu dáng slim, co giãn nhẹ, ly ủi sẵn, hợp đi làm và tiệc tối.'),
    (N'Giày', N'Giày sneaker da trắng', N'Da bò, đế cao su',
        N'Sneaker da trắng tối giản, đế cao su êm, dễ phối với quần âu lẫn jeans.');

INSERT INTO dbo.Product (CategoryID, ProductName, Description, Brand, Material, Status)
SELECT pc.CategoryID, p.ProductName, p.Description, N'MENSFORM', p.Material, 'Active'
FROM @Products p
JOIN dbo.ProductCategory pc ON pc.CategoryName = p.CategoryName
WHERE NOT EXISTS (SELECT 1 FROM dbo.Product x WHERE x.ProductName = p.ProductName AND x.Brand = N'MENSFORM');

DECLARE @Variants TABLE (ProductName nvarchar(200), SKU varchar(50), Color nvarchar(50), Size nvarchar(20), Price decimal(18,2));
INSERT INTO @Variants VALUES
    (N'Áo khoác da lộn dáng suông', 'MF-JKT-BRN-M', N'Nâu', N'M', 1890000),
    (N'Áo khoác da lộn dáng suông', 'MF-JKT-BRN-L', N'Nâu', N'L', 1890000),
    (N'Áo khoác da lộn dáng suông', 'MF-JKT-BRN-XL', N'Nâu', N'XL', 1890000),
    (N'Áo khoác da lộn dáng suông', 'MF-JKT-BLK-M', N'Đen', N'M', 1890000),
    (N'Áo khoác da lộn dáng suông', 'MF-JKT-BLK-L', N'Đen', N'L', 1890000),
    (N'Áo khoác da lộn dáng suông', 'MF-JKT-BLK-XL', N'Đen', N'XL', 1890000),
    (N'Áo len cổ tròn dệt mịn', 'MF-SWT-BLK-M', N'Đen', N'M', 549000),
    (N'Áo len cổ tròn dệt mịn', 'MF-SWT-BLK-L', N'Đen', N'L', 549000),
    (N'Áo len cổ tròn dệt mịn', 'MF-SWT-BLK-XL', N'Đen', N'XL', 549000),
    (N'Áo len cổ tròn dệt mịn', 'MF-SWT-BEI-M', N'Be', N'M', 549000),
    (N'Áo len cổ tròn dệt mịn', 'MF-SWT-BEI-L', N'Be', N'L', 549000),
    (N'Áo len cổ tròn dệt mịn', 'MF-SWT-BEI-XL', N'Be', N'XL', 549000),
    (N'Quần âu dáng slim', 'MF-TRS-GRY-30', N'Xám', N'30', 590000),
    (N'Quần âu dáng slim', 'MF-TRS-GRY-31', N'Xám', N'31', 590000),
    (N'Quần âu dáng slim', 'MF-TRS-GRY-32', N'Xám', N'32', 590000),
    (N'Quần âu dáng slim', 'MF-TRS-BEI-30', N'Be', N'30', 590000),
    (N'Quần âu dáng slim', 'MF-TRS-BEI-31', N'Be', N'31', 590000),
    (N'Quần âu dáng slim', 'MF-TRS-BEI-32', N'Be', N'32', 590000),
    (N'Giày sneaker da trắng', 'MF-SNK-WHT-39', N'Trắng', N'39', 990000),
    (N'Giày sneaker da trắng', 'MF-SNK-WHT-40', N'Trắng', N'40', 990000),
    (N'Giày sneaker da trắng', 'MF-SNK-WHT-41', N'Trắng', N'41', 990000),
    (N'Giày sneaker da trắng', 'MF-SNK-WHT-42', N'Trắng', N'42', 990000),
    (N'Giày sneaker da trắng', 'MF-SNK-WHT-43', N'Trắng', N'43', 990000);

INSERT INTO dbo.ProductVariant (ProductID, SKU, Color, Size, Price, Status, Quantity)
SELECT p.ProductID, v.SKU, v.Color, v.Size, v.Price, 'Active', 10
FROM @Variants v
JOIN dbo.Product p ON p.ProductName = v.ProductName AND p.Brand = N'MENSFORM'
WHERE NOT EXISTS (SELECT 1 FROM dbo.ProductVariant x WHERE x.SKU = v.SKU);

COMMIT TRANSACTION;
