-- Move legacy Customer.Address values into the customer's address book,
-- then remove the denormalized Customer.Address column.
SET XACT_ABORT ON;

BEGIN TRY
    BEGIN TRANSACTION;

    IF OBJECT_ID(N'dbo.Customer', N'U') IS NULL
        THROW 51100, 'dbo.Customer does not exist.', 1;
    IF OBJECT_ID(N'dbo.CustomerAddress', N'U') IS NULL
        THROW 51101, 'dbo.CustomerAddress does not exist.', 1;

    -- Customer.Address allowed longer text than the current address-book field.
    ALTER TABLE dbo.CustomerAddress
        ALTER COLUMN AddressLine NVARCHAR(1000) NOT NULL;
    ALTER TABLE dbo.CustomerAddress
        ALTER COLUMN Phone VARCHAR(20) NULL;
    ALTER TABLE dbo.CustomerAddress
        ALTER COLUMN Province NVARCHAR(200) NULL;

    IF COL_LENGTH(N'dbo.Customer', N'Address') IS NOT NULL
    BEGIN
        INSERT INTO dbo.CustomerAddress
            (CustomerID, RecipientName, Phone, AddressLine,
             Ward, District, Province, IsDefault)
        SELECT c.CustomerID,
               c.FullName,
               NULLIF(LTRIM(RTRIM(c.Phone)), ''),
               LTRIM(RTRIM(c.Address)),
               NULL,
               NULL,
               NULL,
               CASE WHEN EXISTS (
                    SELECT 1 FROM dbo.CustomerAddress existing
                    WHERE existing.CustomerID = c.CustomerID
                      AND existing.IsDefault = 1
               ) THEN 0 ELSE 1 END
        FROM dbo.Customer c
        WHERE NULLIF(LTRIM(RTRIM(c.Address)), '') IS NOT NULL
          AND NOT EXISTS (
                SELECT 1 FROM dbo.CustomerAddress existing
                WHERE existing.CustomerID = c.CustomerID
                  AND existing.AddressLine = LTRIM(RTRIM(c.Address))
          );

        ALTER TABLE dbo.Customer DROP COLUMN Address;
    END;

    COMMIT TRANSACTION;
END TRY
BEGIN CATCH
    IF XACT_STATE() <> 0
        ROLLBACK TRANSACTION;
    THROW;
END CATCH;
