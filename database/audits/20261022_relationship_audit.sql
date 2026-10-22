-- MenWearStore / SQL Server 2014+: read-only relationship audit.
-- Run in SSMS with MenWearStore selected. No application rows are modified.
SET NOCOUNT ON;
SET LOCK_TIMEOUT 5000;

-- 1. Existing business tables and declared relationships.
SELECT N'Summary' AS AuditSection,
       (SELECT COUNT(*) FROM sys.tables WHERE is_ms_shipped = 0 AND name <> N'sysdiagrams') AS BusinessTables,
       (SELECT COUNT(*) FROM sys.foreign_keys WHERE is_ms_shipped = 0) AS ForeignKeys,
       (SELECT COUNT(*) FROM sys.foreign_keys WHERE is_ms_shipped = 0 AND is_disabled = 1) AS DisabledForeignKeys,
       (SELECT COUNT(*) FROM sys.foreign_keys WHERE is_ms_shipped = 0 AND is_not_trusted = 1) AS UntrustedForeignKeys;

SELECT N'TableRelationships' AS AuditSection, s.name AS SchemaName, t.name AS TableName,
       (SELECT COUNT(*) FROM sys.foreign_keys fk WHERE fk.parent_object_id = t.object_id) AS OutboundForeignKeys,
       (SELECT COUNT(*) FROM sys.foreign_keys fk WHERE fk.referenced_object_id = t.object_id) AS InboundForeignKeys
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
WHERE t.is_ms_shipped = 0 AND t.name <> N'sysdiagrams'
ORDER BY s.name, t.name;

SELECT N'ForeignKeyDefinitions' AS AuditSection, fk.name AS ConstraintName,
       OBJECT_NAME(fk.parent_object_id) AS ChildTable, pc.name AS ChildColumn,
       OBJECT_NAME(fk.referenced_object_id) AS ParentTable, rc.name AS ParentColumn,
       pc.is_nullable AS NullableReference, fk.is_disabled AS Disabled, fk.is_not_trusted AS Untrusted
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fc ON fc.constraint_object_id = fk.object_id
JOIN sys.columns pc ON pc.object_id = fc.parent_object_id AND pc.column_id = fc.parent_column_id
JOIN sys.columns rc ON rc.object_id = fc.referenced_object_id AND rc.column_id = fc.referenced_column_id
WHERE fk.is_ms_shipped = 0
ORDER BY ChildTable, ConstraintName, fc.constraint_column_id;

-- 2. Check every FK against actual data, including disabled/untrusted FKs.
-- Optional NULL references are valid and deliberately excluded from orphans.
DECLARE @auditSql nvarchar(max) = N'';
SELECT @auditSql = @auditSql +
    CASE WHEN LEN(@auditSql) = 0 THEN N'' ELSE N' UNION ALL ' END +
    N'SELECT N''ForeignKeyOrphans'' AS AuditSection, N''' + REPLACE(fk.name, N'''', N'''''') +
    N''' AS ConstraintName, N''' + REPLACE(OBJECT_NAME(fk.parent_object_id), N'''', N'''''') +
    N''' AS ChildTable, N''' + REPLACE(OBJECT_NAME(fk.referenced_object_id), N'''', N'''''') +
    N''' AS ParentTable, COUNT_BIG(*) AS OrphanRows FROM ' +
    QUOTENAME(OBJECT_SCHEMA_NAME(fk.parent_object_id)) + N'.' + QUOTENAME(OBJECT_NAME(fk.parent_object_id)) + N' c WHERE ' +
    STUFF((SELECT N' AND c.' + QUOTENAME(pc.name) + N' IS NOT NULL'
           FROM sys.foreign_key_columns fc
           JOIN sys.columns pc ON pc.object_id = fc.parent_object_id AND pc.column_id = fc.parent_column_id
           WHERE fc.constraint_object_id = fk.object_id
           ORDER BY fc.constraint_column_id
           FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 5, N'') +
    N' AND NOT EXISTS (SELECT 1 FROM ' +
    QUOTENAME(OBJECT_SCHEMA_NAME(fk.referenced_object_id)) + N'.' + QUOTENAME(OBJECT_NAME(fk.referenced_object_id)) + N' p WHERE ' +
    STUFF((SELECT N' AND p.' + QUOTENAME(rc.name) + N' = c.' + QUOTENAME(pc.name)
           FROM sys.foreign_key_columns fc
           JOIN sys.columns pc ON pc.object_id = fc.parent_object_id AND pc.column_id = fc.parent_column_id
           JOIN sys.columns rc ON rc.object_id = fc.referenced_object_id AND rc.column_id = fc.referenced_column_id
           WHERE fc.constraint_object_id = fk.object_id
           ORDER BY fc.constraint_column_id
           FOR XML PATH(''), TYPE).value('.', 'nvarchar(max)'), 1, 5, N'') + N')'
FROM sys.foreign_keys fk
WHERE fk.is_ms_shipped = 0
ORDER BY fk.name;
IF LEN(@auditSql) > 0 EXEC sys.sp_executesql @auditSql;

-- 3. ID-named columns which are neither a PK nor a declared FK.
-- A returned column is a design candidate, not proof that a FK is required.
SELECT N'IdentifierColumnsWithoutForeignKeys' AS AuditSection,
       s.name AS SchemaName, t.name AS TableName, c.name AS ColumnName, c.is_nullable AS Nullable
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
JOIN sys.columns c ON c.object_id = t.object_id
WHERE t.is_ms_shipped = 0 AND t.name <> N'sysdiagrams'
  AND RIGHT(c.name, 2) = N'ID'
  AND NOT EXISTS (SELECT 1 FROM sys.foreign_key_columns fc
                  WHERE fc.parent_object_id = c.object_id AND fc.parent_column_id = c.column_id)
  AND NOT EXISTS (SELECT 1 FROM sys.indexes i
                  JOIN sys.index_columns ic ON ic.object_id = i.object_id AND ic.index_id = i.index_id
                  WHERE i.object_id = c.object_id AND i.is_primary_key = 1 AND ic.column_id = c.column_id)
ORDER BY t.name, c.name;

-- 4. Cross-table ownership/business links which separate FKs cannot enforce.
-- Results contain only IDs; no customer addresses, emails or passwords.
SELECT N'LogicalRelationshipIssues' AS AuditSection,
       N'RequestCustomerDoesNotOwnOrder' AS CheckName, r.RequestID AS RecordID, r.OrderID AS RelatedID
FROM dbo.CustomerOrderRequest r JOIN dbo.SalesOrder o ON o.OrderID = r.OrderID
WHERE r.CustomerID <> o.CustomerID
UNION ALL
SELECT N'LogicalRelationshipIssues', N'RequestDetailDoesNotBelongToRequestOrder', d.RequestDetailID, d.OrderDetailID
FROM dbo.CustomerOrderRequestDetail d
JOIN dbo.CustomerOrderRequest r ON r.RequestID = d.RequestID
JOIN dbo.OrderDetail od ON od.OrderDetailID = d.OrderDetailID
WHERE od.OrderID <> r.OrderID
UNION ALL
SELECT N'LogicalRelationshipIssues', N'ReviewCustomerDoesNotOwnOrder', r.ReviewID, r.OrderID
FROM dbo.ProductReview r JOIN dbo.SalesOrder o ON o.OrderID = r.OrderID
WHERE r.CustomerID <> o.CustomerID
UNION ALL
SELECT N'LogicalRelationshipIssues', N'ReviewProductNotInOrder', r.ReviewID, r.ProductID
FROM dbo.ProductReview r
WHERE NOT EXISTS (SELECT 1 FROM dbo.OrderDetail d
                  JOIN dbo.ProductVariant v ON v.VariantID = d.VariantID
                  WHERE d.OrderID = r.OrderID AND v.ProductID = r.ProductID)
UNION ALL
SELECT N'LogicalRelationshipIssues', N'NotificationCustomerDoesNotOwnOrder', n.NotificationID, n.OrderID
FROM dbo.CustomerNotification n JOIN dbo.SalesOrder o ON o.OrderID = n.OrderID
WHERE n.CustomerID <> o.CustomerID
UNION ALL
SELECT N'LogicalRelationshipIssues', N'TicketCustomerDoesNotOwnOrder', t.TicketID, t.OrderID
FROM dbo.SupportTicket t JOIN dbo.SalesOrder o ON o.OrderID = t.OrderID
WHERE t.CustomerID <> o.CustomerID
UNION ALL
SELECT N'LogicalRelationshipIssues', N'MessageCustomerDoesNotOwnTicket', m.MessageID, m.TicketID
FROM dbo.SupportMessage m JOIN dbo.SupportTicket t ON t.TicketID = m.TicketID
WHERE m.SenderType = 'Customer' AND m.SenderCustomerID <> t.CustomerID
UNION ALL
SELECT N'LogicalRelationshipIssues', N'WarehouseIssueVariantNotInOrder', w.IssueID, w.OrderID
FROM dbo.WarehouseIssue w
WHERE w.OrderID IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM dbo.OrderDetail d WHERE d.OrderID = w.OrderID AND d.VariantID = w.VariantID)
ORDER BY CheckName, RecordID;

-- 5. Parent records without children. These may be normal/new/unused records.
SELECT N'ParentRecordsWithoutChildren' AS AuditSection, N'ProductWithoutVariant' AS CheckName, COUNT_BIG(*) AS RecordCount
FROM dbo.Product p WHERE NOT EXISTS (SELECT 1 FROM dbo.ProductVariant v WHERE v.ProductID = p.ProductID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'CategoryWithoutProduct', COUNT_BIG(*)
FROM dbo.ProductCategory c WHERE NOT EXISTS (SELECT 1 FROM dbo.Product p WHERE p.CategoryID = c.CategoryID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'CustomerWithoutAddress', COUNT_BIG(*)
FROM dbo.Customer c WHERE NOT EXISTS (SELECT 1 FROM dbo.CustomerAddress a WHERE a.CustomerID = c.CustomerID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'CustomerWithoutOrder', COUNT_BIG(*)
FROM dbo.Customer c WHERE NOT EXISTS (SELECT 1 FROM dbo.SalesOrder o WHERE o.CustomerID = c.CustomerID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'OrderWithoutDetail', COUNT_BIG(*)
FROM dbo.SalesOrder o WHERE NOT EXISTS (SELECT 1 FROM dbo.OrderDetail d WHERE d.OrderID = o.OrderID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'OrderWithoutPayment', COUNT_BIG(*)
FROM dbo.SalesOrder o WHERE NOT EXISTS (SELECT 1 FROM dbo.Payment p WHERE p.OrderID = o.OrderID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'OrderWithoutShipment', COUNT_BIG(*)
FROM dbo.SalesOrder o WHERE NOT EXISTS (SELECT 1 FROM dbo.Shipment s WHERE s.OrderID = o.OrderID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'PaymentWithoutTransaction', COUNT_BIG(*)
FROM dbo.Payment p WHERE NOT EXISTS (SELECT 1 FROM dbo.PaymentTransaction t WHERE t.PaymentID = p.PaymentID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'TicketWithoutMessage', COUNT_BIG(*)
FROM dbo.SupportTicket t WHERE NOT EXISTS (SELECT 1 FROM dbo.SupportMessage m WHERE m.TicketID = t.TicketID)
UNION ALL
SELECT N'ParentRecordsWithoutChildren', N'PromotionNeverUsed', COUNT_BIG(*)
FROM dbo.Promotion p WHERE NOT EXISTS (SELECT 1 FROM dbo.SalesOrder o WHERE o.PromotionID = p.PromotionID);

SELECT N'OrdersMissingRequiredDetails' AS AuditSection, o.OrderID, o.OrderStatus,
       CASE WHEN NOT EXISTS (SELECT 1 FROM dbo.OrderDetail d WHERE d.OrderID = o.OrderID) THEN 1 ELSE 0 END AS MissingDetails,
       CASE WHEN NOT EXISTS (SELECT 1 FROM dbo.Payment p WHERE p.OrderID = o.OrderID) THEN 1 ELSE 0 END AS MissingPayment,
       CASE WHEN NOT EXISTS (SELECT 1 FROM dbo.Shipment s WHERE s.OrderID = o.OrderID) THEN 1 ELSE 0 END AS MissingShipment
FROM dbo.SalesOrder o
WHERE NOT EXISTS (SELECT 1 FROM dbo.OrderDetail d WHERE d.OrderID = o.OrderID)
   OR NOT EXISTS (SELECT 1 FROM dbo.Payment p WHERE p.OrderID = o.OrderID)
   OR NOT EXISTS (SELECT 1 FROM dbo.Shipment s WHERE s.OrderID = o.OrderID)
ORDER BY o.OrderID;

SELECT N'PaymentsWithoutTransactionByMethod' AS AuditSection,
       p.PaymentMethod, p.PaymentStatus, COUNT_BIG(*) AS RecordCount
FROM dbo.Payment p
WHERE NOT EXISTS (SELECT 1 FROM dbo.PaymentTransaction t WHERE t.PaymentID = p.PaymentID)
GROUP BY p.PaymentMethod, p.PaymentStatus
ORDER BY p.PaymentMethod, p.PaymentStatus;

-- 6. Optional associations are not orphan data: NULL means no relationship.
SELECT N'OptionalReferences' AS AuditSection,
       N'SalesOrder.StaffID' AS ColumnName, COUNT_BIG(*) AS NullRows
FROM dbo.SalesOrder WHERE StaffID IS NULL
UNION ALL
SELECT N'OptionalReferences', N'SalesOrder.PromotionID', COUNT_BIG(*)
FROM dbo.SalesOrder WHERE PromotionID IS NULL
UNION ALL
SELECT N'OptionalReferences', N'WarehouseIssue.OrderID', COUNT_BIG(*)
FROM dbo.WarehouseIssue WHERE OrderID IS NULL
UNION ALL
SELECT N'OptionalReferences', N'CustomerNotification.OrderID', COUNT_BIG(*)
FROM dbo.CustomerNotification WHERE OrderID IS NULL;
