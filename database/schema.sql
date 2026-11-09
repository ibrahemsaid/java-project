CREATE DATABASE IF NOT EXISTS `inventory_management`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

ALTER DATABASE `inventory_management`
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE `inventory_management`;

CREATE TABLE `users` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `username` VARCHAR(64) NOT NULL,
    `password_hash` VARCHAR(255) NOT NULL,
    `role` VARCHAR(32) NOT NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_users_username` UNIQUE (`username`)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `categories` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(100) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_categories_name` UNIQUE (`name`)
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `products` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `name` VARCHAR(200) NOT NULL,
    `code` VARCHAR(64) NOT NULL,
    `category_id` BIGINT UNSIGNED NOT NULL,
    `purchase_price` DECIMAL(12, 2) NOT NULL,
    `selling_price` DECIMAL(12, 2) NOT NULL,
    `quantity` INT NOT NULL DEFAULT 0,
    `minimum_stock` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_products_code` UNIQUE (`code`),
    CONSTRAINT `chk_products_purchase_price` CHECK (`purchase_price` >= 0),
    CONSTRAINT `chk_products_selling_price` CHECK (`selling_price` >= 0),
    CONSTRAINT `chk_products_quantity` CHECK (`quantity` >= 0),
    CONSTRAINT `chk_products_minimum_stock` CHECK (`minimum_stock` >= 0),
    INDEX `idx_products_category_id` (`category_id`),
    CONSTRAINT `fk_products_category`
        FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sales` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `sale_date` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `total_amount` DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_sales_total_amount` CHECK (`total_amount` >= 0),
    INDEX `idx_sales_user_id` (`user_id`),
    INDEX `idx_sales_sale_date` (`sale_date`),
    CONSTRAINT `fk_sales_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `sale_items` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `sale_id` BIGINT UNSIGNED NOT NULL,
    `product_id` BIGINT UNSIGNED NOT NULL,
    `quantity` INT NOT NULL,
    `unit_price` DECIMAL(12, 2) NOT NULL,
    `subtotal` DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_sale_items_quantity` CHECK (`quantity` > 0),
    CONSTRAINT `chk_sale_items_unit_price` CHECK (`unit_price` >= 0),
    CONSTRAINT `chk_sale_items_subtotal` CHECK (`subtotal` >= 0),
    CONSTRAINT `chk_sale_items_subtotal_matches`
        CHECK (`subtotal` = ROUND(`quantity` * `unit_price`, 2)),
    INDEX `idx_sale_items_sale_id` (`sale_id`),
    INDEX `idx_sale_items_product_id` (`product_id`),
    CONSTRAINT `fk_sale_items_sale`
        FOREIGN KEY (`sale_id`) REFERENCES `sales` (`id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT `fk_sale_items_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `stock_movements` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    `product_id` BIGINT UNSIGNED NOT NULL,
    `user_id` BIGINT UNSIGNED NOT NULL,
    `movement_type` VARCHAR(20) NOT NULL,
    `quantity_change` INT NOT NULL,
    `quantity_before` INT NOT NULL,
    `quantity_after` INT NOT NULL,
    `reason` VARCHAR(255) NOT NULL,
    `reference_sale_id` BIGINT UNSIGNED NULL,
    `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `chk_stock_movements_type`
        CHECK (`movement_type` IN ('RECEIPT', 'ISSUE', 'ADJUSTMENT', 'SALE', 'RETURN')),
    CONSTRAINT `chk_stock_movements_change` CHECK (`quantity_change` <> 0),
    CONSTRAINT `chk_stock_movements_direction` CHECK (
        (`movement_type` IN ('RECEIPT', 'RETURN') AND `quantity_change` > 0)
        OR (`movement_type` IN ('ISSUE', 'SALE') AND `quantity_change` < 0)
        OR (`movement_type` = 'ADJUSTMENT' AND `quantity_change` <> 0)
    ),
    CONSTRAINT `chk_stock_movements_before` CHECK (`quantity_before` >= 0),
    CONSTRAINT `chk_stock_movements_after` CHECK (`quantity_after` >= 0),
    CONSTRAINT `chk_stock_movements_balance`
        CHECK (`quantity_after` = `quantity_before` + `quantity_change`),
    INDEX `idx_stock_movements_product_created` (`product_id`, `created_at`),
    INDEX `idx_stock_movements_user_created` (`user_id`, `created_at`),
    INDEX `idx_stock_movements_reference_sale_id` (`reference_sale_id`),
    CONSTRAINT `fk_stock_movements_product`
        FOREIGN KEY (`product_id`) REFERENCES `products` (`id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT `fk_stock_movements_user`
        FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT,
    CONSTRAINT `fk_stock_movements_reference_sale`
        FOREIGN KEY (`reference_sale_id`) REFERENCES `sales` (`id`)
        ON UPDATE CASCADE
        ON DELETE RESTRICT
) ENGINE=InnoDB
  DEFAULT CHARACTER SET=utf8mb4
  COLLATE=utf8mb4_0900_ai_ci;
