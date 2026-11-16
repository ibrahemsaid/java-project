# Database scripts

These scripts target MySQL 8.4 and the `inventory_management` database. They
define the initial single-warehouse schema and a small fictional catalog only.
They do not connect to MySQL automatically.

## Schema and relationships

`schema.sql` creates the `inventory_management` database if it does not exist,
then creates the six tables shown in the ERD: `users`, `categories`,
`products`, `sales`, `sale_items`, and `stock_movements`. It does not drop,
truncate, or alter existing tables.

- A category has many products.
- A user records many sales and stock movements.
- A sale contains many sale items; each sale item refers to one product.
- A product can appear in many sale items and stock movements.
- A stock movement may optionally reference a sale.

Foreign keys restrict deletes of referenced rows and cascade key updates.
Prices use `DECIMAL(12,2)`. Checks enforce non-negative prices and stock,
positive sale-item quantities, and consistent stock movement directions and
balances.

Table creation is intentionally not `IF NOT EXISTS`: running the script against
an already initialized schema stops when it encounters an existing table rather
than silently accepting a possibly different structure. Inspect or back up an
existing database before applying schema changes.

## Requirements

- MySQL 8.4 server
- MySQL command-line client (`mysql`) on `PATH`
- A MySQL account allowed to create the database and tables

## JDBC application configuration

Copy `config/db.properties.example` to `config/db.properties` in the project
root, then edit the local copy with your own MySQL username and password. The
application reads its JDBC settings from that file. Never commit
`config/db.properties`; it is ignored by Git.

## Run

From the project root in Command Prompt (`cmd.exe`), execute the schema script
first. The MySQL account must be able to create databases and tables:

```shell
mysql -h localhost -u YOUR_MYSQL_USER -p < database/schema.sql
```

The client prompts for the password. In MySQL Workbench, open and execute
`database/schema.sql` in a query tab while connected to the server; the script
creates and selects `inventory_management` itself.

Then, optionally load the fictional categories and products into the new,
otherwise-empty schema:

```shell
mysql -u YOUR_MYSQL_USER -p < database/sample-data.sql
```

The client prompts for the password. Do not put credentials in these scripts or
in source control. The sample-data script uses fixed IDs and is intended to be
run once against a fresh schema; running it again will encounter duplicate-key
errors.

No sample users are inserted. Authentication accounts must be created by the
application using an appropriate password-hashing implementation; never store
plain-text passwords.

The sample script inserts only catalog rows. Sales and their line items, stock
updates, and stock movement records must be written together by the application
in a single database transaction.
