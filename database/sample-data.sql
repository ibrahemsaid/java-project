USE `inventory_management`;

-- Fictional development catalog data only. No user accounts or credentials
-- are seeded; create users later with a real password-hashing implementation.
INSERT INTO `categories` (`id`, `name`) VALUES
    (1, 'Office Supplies'),
    (2, 'Computer Accessories'),
    (3, 'Packaging Materials');

INSERT INTO `products` (
    `id`, `name`, `code`, `category_id`,
    `purchase_price`, `selling_price`, `quantity`, `minimum_stock`
) VALUES
    (1, 'A4 Copy Paper (500 sheets)', 'OFF-PAPER-A4-500', 1, 4.25, 6.50, 80, 15),
    (2, 'Blue Ballpoint Pen', 'OFF-PEN-BLUE', 1, 0.18, 0.45, 240, 40),
    (3, 'Wired USB Keyboard', 'ACC-KEYBOARD-USB', 2, 8.75, 12.99, 18, 5),
    (4, 'Small Shipping Box', 'PKG-BOX-SMALL', 3, 0.62, 1.10, 55, 10);
