ALTER TABLE `sale_items`
    ADD COLUMN `product_name` VARCHAR(200) NULL AFTER `product_id`,
    ADD COLUMN `product_code` VARCHAR(64) NULL AFTER `product_name`;
