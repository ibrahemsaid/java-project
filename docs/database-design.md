# Initial database design

This design targets MySQL 8.4 for one warehouse. The schema is defined in
[`../database/schema.sql`](../database/schema.sql); optional fictional catalog
rows are in [`../database/sample-data.sql`](../database/sample-data.sql).

## Database-wide choices

- The database and tables use InnoDB, `utf8mb4`, and
  `utf8mb4_0900_ai_ci`. The case-insensitive collation makes unique usernames,
  category names, and product codes compare without regard to case.
- `schema.sql` explicitly alters the database default character set and
  collation after `CREATE DATABASE IF NOT EXISTS`. This also normalizes the
  database default if it already existed with a different collation, such as
  `utf8mb4_unicode_ci`. Each table separately declares the same character set
  and collation, so its defaults are explicit as well.
- Surrogate primary keys use `BIGINT UNSIGNED AUTO_INCREMENT`.
- Money uses `DECIMAL(12,2)` only. Prices and totals are constrained to
  non-negative values.
- Product and stock-balance quantities use signed `INT` with non-negative
  checks; signed integers are needed for recorded stock changes.
- Foreign keys use `ON DELETE RESTRICT` to preserve referenced records and
  `ON UPDATE CASCADE` for key updates.
- Useful foreign-key and lookup indexes are explicitly declared.
- No warehouse table or warehouse identifier is included; this is a
  single-warehouse design.

## Tables

### `users`

Stores application accounts and the user identity referenced by historical
records.

| Column | Definition | Purpose |
|---|---|---|
| `id` | `BIGINT UNSIGNED` primary key | Account identifier. |
| `username` | `VARCHAR(64) NOT NULL`, unique | Unique login name. |
| `password_hash` | `VARCHAR(255) NOT NULL` | Encoded password hash; never plain-text. |
| `role` | `VARCHAR(32) NOT NULL` | Role label; allowed role names are intentionally not fixed yet. |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | Account creation time. |

Deleting a user referenced by a sale or stock movement is restricted, preserving
the recorded actor.

### `categories`

| Column | Definition | Purpose |
|---|---|---|
| `id` | `BIGINT UNSIGNED` primary key | Category identifier. |
| `name` | `VARCHAR(100) NOT NULL`, unique | Unique category name. |

The products foreign key uses `ON DELETE RESTRICT`, so a category cannot be
deleted while products still refer to it.

### `products`

| Column | Definition | Purpose |
|---|---|---|
| `id` | `BIGINT UNSIGNED` primary key | Product identifier. |
| `name` | `VARCHAR(200) NOT NULL` | Display name. |
| `code` | `VARCHAR(64) NOT NULL`, unique | Unique product code/SKU. |
| `category_id` | `BIGINT UNSIGNED NOT NULL`, foreign key | Required category. |
| `purchase_price` | `DECIMAL(12,2) NOT NULL` | Recorded purchase price; non-negative. |
| `selling_price` | `DECIMAL(12,2) NOT NULL` | Current selling price; non-negative. |
| `quantity` | `INT NOT NULL DEFAULT 0` | Current on-hand quantity; non-negative. |
| `minimum_stock` | `INT NOT NULL DEFAULT 0` | Low-stock threshold; non-negative. |

Category deletion is restricted while products exist. Product deletion is
restricted if sale items or stock movements reference the product.

### `sales`

| Column | Definition | Purpose |
|---|---|---|
| `id` | `BIGINT UNSIGNED` primary key | Sale identifier. |
| `user_id` | `BIGINT UNSIGNED NOT NULL`, foreign key | User who recorded the sale. |
| `sale_date` | `DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP` | Sale date and time. |
| `total_amount` | `DECIMAL(12,2) NOT NULL` | Non-negative sale total. |

The user foreign key restricts deleting users with sales. Sales are not
cascade-deleted from their line items or stock movements. The database does not
provide a general soft-delete or void workflow; the application should not
expose casual deletion of completed sales, and database account permissions
should be considered accordingly.

### `sale_items`

| Column | Definition | Purpose |
|---|---|---|
| `id` | `BIGINT UNSIGNED` primary key | Sale-line identifier. |
| `sale_id` | `BIGINT UNSIGNED NOT NULL`, foreign key | Parent sale. |
| `product_id` | `BIGINT UNSIGNED NOT NULL`, foreign key | Product sold. |
| `quantity` | `INT NOT NULL` | Positive quantity sold. |
| `unit_price` | `DECIMAL(12,2) NOT NULL` | Price captured for this line; non-negative. |
| `subtotal` | `DECIMAL(12,2) NOT NULL` | Non-negative line amount, checked against quantity × unit price. |

Both foreign keys restrict deletion to preserve the sale history. There is no
unique constraint on `(sale_id, product_id)`, so a sale may contain more than
one line for the same product. The sale total is not constrained to equal the
sum of its lines; the application must calculate and persist both consistently
in one transaction.

### `stock_movements`

An append-oriented audit record for each change to product stock.

| Column | Definition | Purpose |
|---|---|---|
| `id` | `BIGINT UNSIGNED` primary key | Movement identifier. |
| `product_id` | `BIGINT UNSIGNED NOT NULL`, foreign key | Product whose stock changed. |
| `user_id` | `BIGINT UNSIGNED NOT NULL`, foreign key | User who recorded the change. |
| `movement_type` | `VARCHAR(20) NOT NULL` | Checked against `RECEIPT`, `ISSUE`, `ADJUSTMENT`, `SALE`, or `RETURN`. |
| `quantity_change` | `INT NOT NULL` | Non-zero signed stock delta; receipts/returns are positive and issues/sales are negative. Adjustments may be positive or negative. |
| `quantity_before` | `INT NOT NULL` | Non-negative balance before the change. |
| `quantity_after` | `INT NOT NULL` | Non-negative balance after the change. |
| `reason` | `VARCHAR(255) NOT NULL` | Explanation of the movement. |
| `reference_sale_id` | Nullable `BIGINT UNSIGNED`, foreign key | Optional related sale; the application requires it for a `SALE` movement. |
| `created_at` | `TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP` | Movement creation time. |

Checks enforce the supported movement types, direction of the stock delta, a
non-zero change, and `quantity_after = quantity_before + quantity_change`.
Foreign keys restrict deleting referenced products, users, and sales.
The nullable sale reference is not checked against the movement type in SQL.
The Java service/business logic must require a sale reference whenever it
creates a `SALE` stock movement.

## Relationships and transaction boundary

- One category has many products (`products.category_id`).
- One user can record many sales (`sales.user_id`).
- One sale has many line items (`sale_items.sale_id`).
- One product can appear in many sale lines (`sale_items.product_id`).
- One product can have many stock movements (`stock_movements.product_id`).
- One user can record many stock movements (`stock_movements.user_id`).
- A stock movement may refer to a sale (`stock_movements.reference_sale_id`).

When a sale affects inventory, the future application must save the sale and its
items, update product quantities, and insert corresponding stock movements in
one database transaction. A failure at any step must roll the entire operation
back. SQL scripts here define the schema only; they do not execute transactions
or connect to a server.

## Decisions for future review

- The initial movement-type vocabulary and sign rules should be checked against
  the eventual receiving, issuing, adjustment, sale, and return workflows.
- The database cannot ensure `sales.total_amount` equals the sum of all related
  line subtotals with a row-level check; transaction logic must maintain this
  invariant.
- `DECIMAL(12,2)` and signed `INT` quantities impose maximum representable
  amounts and balances that should be reviewed against expected scale.
- Roles remain free-form strings, and no policy for account deactivation,
  sale voiding, discounts, taxes, or audit retention is selected yet.
- The schema disallows deleting referenced historical rows, but protecting
  even an empty completed sale from deletion requires application policy and
  appropriate database privileges.
