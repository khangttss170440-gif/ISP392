/* Preserve existing payment methods and allow checkout to persist VNPAY. */
IF EXISTS (
    SELECT 1
    FROM sys.check_constraints
    WHERE parent_object_id = OBJECT_ID(N'dbo.Payment')
      AND name = N'CK_Payment_Method'
)
BEGIN
    ALTER TABLE dbo.Payment
        DROP CONSTRAINT CK_Payment_Method;
END;
GO

ALTER TABLE dbo.Payment WITH CHECK
    ADD CONSTRAINT CK_Payment_Method
    CHECK (PaymentMethod IN (N'COD', N'EWallet', N'BankTransfer', N'Cash', N'VNPAY'));
GO

ALTER TABLE dbo.Payment
    CHECK CONSTRAINT CK_Payment_Method;
GO
