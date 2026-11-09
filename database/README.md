# Database scripts

These scripts target MySQL 8.4 and the `inventory_management` database. They
define the initial single-warehouse schema and a small fictional catalog only.
They do not connect to MySQL automatically.

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
first:

```shell
mysql -u YOUR_MYSQL_USER -p < database/schema.sql
```

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
