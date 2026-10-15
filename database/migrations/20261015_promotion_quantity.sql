SET XACT_ABORT ON;
BEGIN TRANSACTION;

IF COL_LENGTH('dbo.Promotion', 'Quantity') IS NULL
BEGIN
    ALTER TABLE dbo.Promotion ADD Quantity int NOT NULL
        CONSTRAINT DF_Promotion_Quantity DEFAULT (0) WITH VALUES;
END;

IF NOT EXISTS (SELECT 1 FROM sys.check_constraints
               WHERE parent_object_id = OBJECT_ID('dbo.Promotion')
                 AND name = 'CK_Promotion_Quantity_Nonnegative')
BEGIN
    EXEC(N'ALTER TABLE dbo.Promotion WITH CHECK ADD CONSTRAINT CK_Promotion_Quantity_Nonnegative
        CHECK (Quantity >= 0)');
END;

COMMIT TRANSACTION;
