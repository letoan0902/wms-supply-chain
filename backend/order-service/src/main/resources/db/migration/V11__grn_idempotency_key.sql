-- WMS-20.4: khóa chống trùng (Idempotency-Key) do client gửi khi lập phiếu nhập kho.
-- Hai request lập phiếu giống nhau (bấm hai lần, gửi lại sau khi hết thời gian chờ) mang cùng khóa chỉ tạo một phiếu;
-- request sau nhận lại phiếu đầu. Khóa duy nhất trong phạm vi một doanh nghiệp; phiếu cũ và phiếu lập không kèm khóa
-- để NULL (MySQL cho phép nhiều NULL trong ràng buộc UNIQUE).
ALTER TABLE goods_receipt_notes ADD COLUMN idempotency_key VARCHAR(100) NULL;
ALTER TABLE goods_receipt_notes
    ADD CONSTRAINT uk_goods_receipt_notes_company_idempotency UNIQUE (company_id, idempotency_key);
