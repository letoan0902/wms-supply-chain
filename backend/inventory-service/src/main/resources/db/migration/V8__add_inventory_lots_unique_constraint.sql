-- V8__add_inventory_lots_unique_constraint.sql: Bổ sung ràng buộc duy nhất (company_id, lot_number, bin_location_id, product_id) cho bảng inventory_lots (WMS-10.2 / WMS-10.3)
-- Dữ liệu cũ có thể đã có nhiều dòng trùng khóa này: gộp về dòng có id nhỏ nhất (cộng dồn tồn và giữ chỗ,
-- giữ trạng thái khóa nếu có dòng nào bị khóa) rồi xóa các dòng thừa, để lệnh thêm ràng buộc không bị lỗi.

-- 1. Cộng dồn số lượng của các dòng trùng vào dòng giữ lại
UPDATE inventory_lots l
    JOIN (SELECT MIN(id)                AS keep_id,
                 SUM(quantity)          AS total_quantity,
                 SUM(reserved_quantity) AS total_reserved,
                 MAX(is_locked)         AS any_locked,
                 MIN(exp_date)          AS earliest_exp,
                 MIN(mfg_date)          AS earliest_mfg
          FROM inventory_lots
          GROUP BY company_id, lot_number, bin_location_id, product_id
          HAVING COUNT(*) > 1) d ON l.id = d.keep_id
SET l.quantity          = d.total_quantity,
    l.reserved_quantity = d.total_reserved,
    l.is_locked         = d.any_locked,
    l.exp_date          = COALESCE(l.exp_date, d.earliest_exp),
    l.mfg_date          = COALESCE(l.mfg_date, d.earliest_mfg);

-- 2. Xóa các dòng trùng còn lại (giữ dòng có id nhỏ nhất)
DELETE l
FROM inventory_lots l
    JOIN inventory_lots k
        ON k.company_id = l.company_id
       AND k.lot_number = l.lot_number
       AND k.bin_location_id = l.bin_location_id
       AND k.product_id = l.product_id
       AND k.id < l.id;

-- 3. Thêm ràng buộc duy nhất, bắt đầu bằng company_id theo quy ước đa doanh nghiệp (MULTI_TENANT.md)
ALTER TABLE inventory_lots
    ADD CONSTRAINT uk_inventory_lots_company_lot_bin_product UNIQUE (company_id, lot_number, bin_location_id, product_id);
