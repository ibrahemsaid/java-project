module com.inventory.inventorymanagement {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;
    requires mysql.connector.j;

    exports com.inventory;
    opens com.inventory.controller to javafx.fxml;
}
