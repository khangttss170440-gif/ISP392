-- Follow-up to 20261024_accessory_catalog.sql.
-- The product photo for MF-TIE-BRN shows a dotted brown tie, so its colour
-- is renamed from "Nâu sọc" to "Nâu chấm bi" to match the per-colour images.
-- Run with: sqlcmd -S localhost -E -d MenWearStore -C -f 65001 -i 20261024_tie_color.sql
SET NOCOUNT ON;

UPDATE dbo.ProductVariant
SET Color = N'Nâu chấm bi'
WHERE SKU = 'MF-TIE-BRN';
