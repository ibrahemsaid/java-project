# Inventory Management System

Inventory Management System is a JavaFX desktop application. It is an
in-progress university project, not a website.

## Technology

- Java 21
- Maven Wrapper (Maven 3.10.0; no separate Maven installation required)
- JavaFX 21.0.6, FXML, and CSS
- MySQL 8.4 with JDBC (MySQL Connector/J 9.2.0)

## Architecture and project structure

The application follows a layered structure: JavaFX views and controllers
handle the UI, services contain business rules, DAOs perform JDBC persistence,
and models represent application data. Shared database and password utilities
are in `util`.

- `src/main/java/com/inventory/App.java` — JavaFX application entry point
- Under `src/main/java/com/inventory/`:
  - `controller/` — login and main-window controllers
  - `service/` — user, product, and category business logic
  - `dao/` — user, product, and category database access
  - `model/` — user, product, and category data models
  - `util/` — database connection and password hashing utilities
- `src/main/resources/com/inventory/view/` — FXML screens
- `src/main/resources/com/inventory/css/` — application stylesheets
- `database/` — MySQL schema and optional sample catalog data
- `config/` — example JDBC configuration

## Current functionality

Sign-in and initial administrator setup are implemented. Product and category
models, services, and database access are present, but there are no product or
category screens yet. The dashboard displays placeholder cards without live
statistics. The Sales & Invoices, Stock Movements, Users, and Reports
navigation items are placeholders; those workflows and reports are not
implemented.

## Windows setup

Prerequisites: Git, a Java 21 JDK, and MySQL Server 8.4 with the `mysql`
command-line client available on `PATH`. Run the following commands from the
project root.

1. Create the database and tables from **Command Prompt (`cmd.exe`)** using a
   MySQL account with permission to create databases and tables:

   ```cmd
   mysql -u YOUR_MYSQL_USER -p < database/schema.sql
   ```

   Enter the MySQL password when prompted. The script creates the
   `inventory_management` database and its tables.

2. Optionally add fictional category and product records to the new database:

   ```cmd
   mysql -u YOUR_MYSQL_USER -p < database/sample-data.sql
   ```

   Run this only once against a fresh database. It uses fixed IDs and a second
   import will fail with duplicate-key errors. It does not create user accounts.

3. In PowerShell, copy the example configuration and edit the local copy:

   ```powershell
   Copy-Item config\db.properties.example config\db.properties
   ```

   Set `db.url`, `db.username`, and `db.password` in `config\db.properties` to
   match your local MySQL setup. Keep your credentials private; do not commit
   this local file.

4. Compile the project:

   ```powershell
   .\mvnw.cmd clean compile
   ```

5. Create the initial administrator before signing in. The schema must already
   be installed, `config\db.properties` must contain valid connection
   settings, and the `users` table must be empty. `InitialAdminSetup` requires
   an interactive terminal console; it prompts for a username and password and
   creates an `ADMIN` account with a hashed password.

   After compiling, generate the runtime dependency classpath and run the
   utility directly from PowerShell (or another terminal that provides a Java
   console):

   ```powershell
   .\mvnw.cmd dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"
   $classpath = "target\classes;" + (Get-Content target\classpath.txt -Raw).Trim()
   java -cp $classpath com.inventory.InitialAdminSetup
   ```

6. Launch the application:

   ```powershell
   .\mvnw.cmd javafx:run
   ```

## Project reference

[GitHub repository](https://github.com/ibrahemsaid/java-project)
