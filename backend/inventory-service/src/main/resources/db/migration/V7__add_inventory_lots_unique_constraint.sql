-- V7__add_inventory_lots_unique_constraint.sql: Bổ sung ràng buộc duy nhất (company_id, lot_number, bin_location_id, product_id) cho bảng inventory_lots (WMS-10.2 / WMS-10.3)

ALTER TABLE inventory_lots
    ADD CONSTRAINT uk_inventory_lots_company_lot_bin_product UNIQUE (company_id, lot_number, bin_location_id, product_id);
