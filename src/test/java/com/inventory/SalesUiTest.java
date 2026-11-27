package com.inventory;

import com.inventory.controller.MainController;
import com.inventory.controller.SalesController;
import com.inventory.dao.CategoryDao;
import com.inventory.dao.ProductDao;
import com.inventory.dao.SaleDao;
import com.inventory.dao.SaleItemDao;
import com.inventory.dao.StockMovementDao;
import com.inventory.model.Product;
import com.inventory.model.Sale;
import com.inventory.model.SaleItem;
import com.inventory.service.CategoryService;
import com.inventory.service.ProductService;
import com.inventory.service.SaleService;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

public final class SalesUiTest {
    private static final long TEST_TIMEOUT_SECONDS = 10;

    private SalesUiTest() {
    }

    public static void main(String[] args) throws Exception {
        CountDownLatch startupCompleted = new CountDownLatch(1);
        AtomicReference<Parent> rootReference = new AtomicReference<>();
        AtomicReference<MainPageFixture> pageFixtureReference = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicInteger saleServiceCalls = new AtomicInteger();

        Platform.startup(() -> {
            try {
                MainPageFixture pageFixture = loadSalesPage(saleServiceCalls);
                pageFixtureReference.set(pageFixture);
                Parent mainRoot = pageFixture.mainRoot();
                Button salesButton = (Button) mainRoot.lookup("#salesButton");
                require(salesButton != null, "Main view provides the Sales navigation button.");
                salesButton.fire();
                VBox contentArea = getContentArea(mainRoot);
                Parent salesPage = (Parent) contentArea.getChildren().get(0);
                rootReference.set(salesPage);
                require(
                        salesPage.lookup("#productTable") instanceof TableView<?>,
                        "Sales navigation loads the Sales page.");
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                startupCompleted.countDown();
            }
        });

        try {
            if (!startupCompleted.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("Sales UI startup timed out.");
            }
            rethrowFailure(failure.get(), "Sales UI startup failed.");

            Parent root = rootReference.get();
            awaitFxCondition(
                    () -> tableSize(root, "#productTable") == 2
                            && tableSize(root, "#invoiceTable") == 2,
                    "Sales page did not load products and invoice history.");
            runOnFxAndWait(() -> {
                selectInvoice(root, 42);
                return null;
            });
            awaitFxCondition(
                    () -> tableSize(root, "#invoiceItemsTable") == 2,
                    "Selected invoice details did not load.");
            verifyInvoiceSelectionRace(root, pageFixtureReference.get());

            runOnFxAndWait(() -> {
                verifyInvoiceSnapshots(root);
                verifyProductSearchAndCart(root);
                verifyNavigationLock(pageFixtureReference.get());
                require(
                        saleServiceCalls.get() == 0,
                        "UI checks do not submit a sale to the database.");
                return null;
            });
            verifyCheckoutLifecycle();
        } finally {
            MainPageFixture fixture = pageFixtureReference.get();
            if (fixture != null) {
                fixture.releaseSlowInvoiceLookup().countDown();
            }
            Platform.exit();
        }

        System.out.println("Sales UI checks passed.");
    }

    private static MainPageFixture loadSalesPage(AtomicInteger saleServiceCalls)
            throws IOException {
        List<Product> products = List.of(
                product(1, "Desk Lamp", "DL-01", "12.50", 5),
                product(2, "Office Chair", "CH-02", "99.00", 3),
                product(3, "Discontinued Item", "OLD-03", "4.00", 0));
        Sale invoice41 = new Sale(
                41,
                7,
                LocalDateTime.of(2026, 11, 27, 10, 30),
                new BigDecimal("25.00"));
        Sale invoice42 = new Sale(
                42,
                7,
                LocalDateTime.of(2026, 11, 28, 11, 30),
                new BigDecimal("25.00"));
        List<SaleItem> invoice41Lines = List.of(new SaleItem(
                3, 41, 3, "Older invoice snapshot", "OLD-41",
                1, new BigDecimal("8.00"), new BigDecimal("8.00")));
        List<SaleItem> invoiceLines = List.of(
                new SaleItem(
                        1, 42, 1, "Desk Lamp - Historical", "OLD-DL",
                        2, new BigDecimal("12.50"), new BigDecimal("25.00")),
                new SaleItem(
                        2, 42, 2, null, null,
                        1, new BigDecimal("0.00"), new BigDecimal("0.00")));
        CountDownLatch slowInvoiceLookupStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowInvoiceLookup = new CountDownLatch(1);
        CountDownLatch slowInvoiceLookupFinished = new CountDownLatch(1);

        ProductDao productDao = new ProductDao();
        SaleDao saleDao = new SaleDao() {
            @Override
            public List<Sale> findAll() {
                return List.of(invoice41, invoice42);
            }
        };
        SaleItemDao saleItemDao = new SaleItemDao() {
            @Override
            public List<SaleItem> findBySaleId(long saleId) throws SQLException {
                if (saleId == invoice41.getId()) {
                    slowInvoiceLookupStarted.countDown();
                    try {
                        if (!releaseSlowInvoiceLookup.await(
                                TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new SQLException("Timed out waiting to release invoice lookup.");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Invoice lookup was interrupted.", exception);
                    } finally {
                        slowInvoiceLookupFinished.countDown();
                    }
                    return invoice41Lines;
                }
                return saleId == invoice42.getId() ? invoiceLines : List.of();
            }
        };
        StockMovementDao stockMovementDao = new StockMovementDao();
        ProductService productService = new ProductService(
                productDao, new CategoryDao(), stockMovementDao) {
            @Override
            public List<Product> getAllProducts() {
                return products;
            }
        };
        SaleService saleService = new SaleService(
                productDao, saleDao, saleItemDao, stockMovementDao) {
            @Override
            public Sale createSale(
                    long authenticatedUserId,
                    java.util.Collection<RequestedSaleItem> requestedItems)
                    throws SQLException {
                saleServiceCalls.incrementAndGet();
                throw new AssertionError("The UI test must not submit a sale.");
            }
        };

        FXMLLoader loader = new FXMLLoader(
                SalesUiTest.class.getResource("/com/inventory/view/main-view.fxml"));
        CategoryService categoryService = new CategoryService(new CategoryDao());
        loader.setControllerFactory(controllerType -> {
            if (controllerType == MainController.class) {
                return new MainController(
                        categoryService,
                        productService,
                        saleService,
                        saleDao,
                        saleItemDao);
            }
            throw new IllegalStateException(
                    "No controller factory is configured for "
                            + controllerType.getName());
        });
        Parent root = loader.load();
        MainController mainController = loader.getController();
        mainController.initializeAuthenticatedUser(7, "cashier", "USER");
        root.setUserData(mainController);
        return new MainPageFixture(
                root,
                slowInvoiceLookupStarted,
                releaseSlowInvoiceLookup,
                slowInvoiceLookupFinished);
    }

    private static VBox getContentArea(Parent root) throws ReflectiveOperationException {
        if (!(root.getUserData() instanceof MainController controller)) {
            throw new AssertionError("Main controller was not initialized.");
        }
        Field field = MainController.class.getDeclaredField("contentArea");
        field.setAccessible(true);
        return (VBox) field.get(controller);
    }

    private static void verifyInvoiceSnapshots(Parent root) {
        TableView<SaleItem> itemTable = typedTable(root, "#invoiceItemsTable");
        require(itemTable.getItems().size() == 2, "Invoice line items load from the saved invoice.");
        TableColumn<SaleItem, String> nameColumn = typedColumn(itemTable, 0);
        TableColumn<SaleItem, String> codeColumn = typedColumn(itemTable, 1);
        TableColumn<SaleItem, String> priceColumn = typedColumn(itemTable, 2);
        TableColumn<SaleItem, String> subtotalColumn = typedColumn(itemTable, 4);
        require(
                "Desk Lamp - Historical".equals(nameColumn.getCellData(itemTable.getItems().get(0))),
                "Invoice uses the saved historical product name.");
        require(
                "OLD-DL".equals(codeColumn.getCellData(itemTable.getItems().get(0))),
                "Invoice uses the saved historical product code.");
        require(
                "12.50".equals(priceColumn.getCellData(itemTable.getItems().get(0)))
                        && "25.00".equals(subtotalColumn.getCellData(itemTable.getItems().get(0))),
                "Invoice displays saved historical prices and subtotals.");
        require(
                "Unavailable".equals(nameColumn.getCellData(itemTable.getItems().get(1)))
                        && "Unavailable".equals(
                                codeColumn.getCellData(itemTable.getItems().get(1))),
                "Legacy null snapshot values display a safe fallback.");
        Label selectedTotal = (Label) root.lookup("#selectedInvoiceTotalLabel");
        require("25.00".equals(selectedTotal.getText()), "Selected invoice total is displayed.");
    }

    private static void verifyInvoiceSelectionRace(Parent root, MainPageFixture fixture)
            throws Exception {
        runOnFxAndWait(() -> {
            selectInvoice(root, 41);
            require(tableSize(root, "#invoiceItemsTable") == 0,
                    "Changing invoices clears the previous details immediately.");
            require("Loading invoice details...".equals(
                            ((Label) root.lookup("#invoiceStatusLabel")).getText()),
                    "A loading state is shown while invoice lines load.");
            return null;
        });
        require(fixture.slowInvoiceLookupStarted().await(
                        TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "The delayed older invoice lookup did not start.");

        runOnFxAndWait(() -> {
            selectInvoice(root, 42);
            require(tableSize(root, "#invoiceItemsTable") == 0,
                    "Selecting a newer invoice clears rows during loading.");
            return null;
        });
        awaitFxCondition(
                () -> tableSize(root, "#invoiceItemsTable") == 2
                        && firstInvoiceItemName(root).equals("Desk Lamp - Historical"),
                "The newer invoice details did not load.");

        fixture.releaseSlowInvoiceLookup().countDown();
        require(fixture.slowInvoiceLookupFinished().await(
                        TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "The delayed invoice lookup did not finish.");
        Thread.sleep(150);
        runOnFxAndWait(() -> {
            require(tableSize(root, "#invoiceItemsTable") == 2
                            && firstInvoiceItemName(root).equals("Desk Lamp - Historical"),
                    "A slower older invoice response replaced the newer details.");
            return null;
        });
    }

    private static void verifyNavigationLock(MainPageFixture fixture)
            throws ReflectiveOperationException {
        Parent mainRoot = fixture.mainRoot();
        MainController controller = (MainController) mainRoot.getUserData();
        Method setCheckoutInProgress =
                MainController.class.getDeclaredMethod(
                        "setSalesCheckoutInProgress", boolean.class);
        setCheckoutInProgress.setAccessible(true);

        Button dashboardButton = (Button) mainRoot.lookup("#dashboardButton");
        Button salesButton = (Button) mainRoot.lookup("#salesButton");
        Button logoutButton = (Button) mainRoot.lookup("#logoutButton");
        VBox contentArea = getContentArea(mainRoot);
        Node salesPage = contentArea.getChildren().get(0);

        setCheckoutInProgress.invoke(controller, true);
        require(dashboardButton.isDisabled() && salesButton.isDisabled()
                        && logoutButton.isDisabled(),
                "Navigation and logout are disabled during checkout.");
        dashboardButton.fire();
        require(contentArea.getChildren().get(0) == salesPage,
                "Navigation cannot leave the Sales page during checkout.");

        setCheckoutInProgress.invoke(controller, false);
        require(!dashboardButton.isDisabled() && !salesButton.isDisabled()
                        && !logoutButton.isDisabled(),
                "Navigation is restored after checkout finishes.");
    }

    private static void verifyCheckoutLifecycle() throws Exception {
        CheckoutFixture fixture = runOnFxAndWait(SalesUiTest::loadCheckoutPage);
        Parent root = fixture.root();
        awaitFxCondition(
                () -> tableSize(root, "#productTable") == 1
                        && "0 invoices".equals(
                                ((Label) root.lookup("#invoiceStatusLabel")).getText()),
                "Checkout test page did not finish its initial refresh.");

        runOnFxAndWait(() -> {
            TableView<Product> products = typedTable(root, "#productTable");
            products.getSelectionModel().select(0);
            ((TextField) root.lookup("#productQuantityField")).setText("2");
            ((Button) root.lookup("#addToCartButton")).fire();
            ((Button) root.lookup("#checkoutButton")).fire();
            require(fixture.serviceCalls().get() == 0,
                    "Cancelling checkout does not call SaleService.");
            require(tableSize(root, "#cartTable") == 1,
                    "Cancelling checkout preserves the cart.");
            return null;
        });

        fixture.confirmCheckout().set(true);
        runOnFxAndWait(() -> {
            ((Button) root.lookup("#checkoutButton")).fire();
            return null;
        });
        require(fixture.firstSaleStarted().await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS),
                "The first confirmed checkout did not reach SaleService.");
        runOnFxAndWait(() -> {
            require(fixture.navigationLocked().get(),
                    "Navigation is locked while SaleService is running.");
            require(((Button) root.lookup("#checkoutButton")).isDisabled(),
                    "Checkout cannot be submitted twice while in progress.");
            return null;
        });
        fixture.releaseFirstSale().countDown();

        awaitFxCondition(
                () -> fixture.serviceCalls().get() == 1
                        && ((Label) root.lookup("#checkoutStatusLabel")).getText()
                                .contains("could not complete")
                        && tableSize(root, "#cartTable") == 1
                        && !fixture.navigationLocked().get(),
                "A failed sale did not preserve the cart and restore navigation.");

        awaitFxCondition(
                () -> !((Button) root.lookup("#checkoutButton")).isDisabled(),
                "Checkout did not become available after the product refresh.");
        runOnFxAndWait(() -> {
            ((Button) root.lookup("#checkoutButton")).fire();
            return null;
        });
        awaitFxCondition(
                () -> fixture.serviceCalls().get() == 2
                        && ((Label) root.lookup("#checkoutStatusLabel")).getText()
                                .contains("selling price changed for product #8")
                        && tableSize(root, "#cartTable") == 1,
                "A changed confirmed price did not preserve the cart and request review.");

        awaitFxCondition(
                () -> !((Button) root.lookup("#checkoutButton")).isDisabled(),
                "Checkout did not become available after refreshing the changed price.");
        awaitFxCondition(
                () -> "27.00".equals(((Label) root.lookup("#cartTotalLabel")).getText()),
                "The refreshed product price was not applied to the cart.");
        runOnFxAndWait(() -> {
            ((Button) root.lookup("#checkoutButton")).fire();
            return null;
        });
        awaitFxCondition(
                () -> fixture.serviceCalls().get() == 3
                        && ((Label) root.lookup("#checkoutStatusLabel")).getText()
                                .contains("Sale #77 completed")
                        && tableSize(root, "#cartTable") == 0,
                "A successful sale was not recorded as completed.");
        awaitFxCondition(
                () -> ((Label) root.lookup("#invoiceStatusLabel")).getText()
                        .contains("Sale #77 was completed, but invoice details could not be refreshed")
                        && ((Label) root.lookup("#invoiceStatusLabel")).getText()
                                .contains("Do not submit it again"),
                "A post-commit refresh error was not distinguished from checkout failure.");

        require(!fixture.navigationLocked().get(),
                "Navigation is restored after the sale transaction completes.");
        require(fixture.lastUserId().get() == 7,
                "Checkout passes the authenticated user ID to SaleService.");
        List<SaleService.RequestedSaleItem> requestedItems = fixture.lastRequest().get();
        require(requestedItems.size() == 1
                        && requestedItems.get(0).productId() == 8
                        && requestedItems.get(0).quantity() == 2
                        && requestedItems.get(0).expectedUnitPrice()
                                .compareTo(new BigDecimal("13.50")) == 0,
                "Checkout passes the confirmed displayed price and quantity.");
        require("0.00".equals(((Label) root.lookup("#cartTotalLabel")).getText()),
                "The cart clears only after SaleService returns a committed sale.");
    }

    private static CheckoutFixture loadCheckoutPage() throws IOException {
        ProductDao productDao = new ProductDao();
        SaleDao saleDao = new SaleDao() {
            private final AtomicInteger historyReads = new AtomicInteger();

            @Override
            public List<Sale> findAll() throws SQLException {
                if (historyReads.incrementAndGet() == 1) {
                    return List.of();
                }
                throw new SQLException("Injected invoice refresh failure.");
            }
        };
        SaleItemDao saleItemDao = new SaleItemDao();
        StockMovementDao stockMovementDao = new StockMovementDao();
        AtomicBoolean priceChanged = new AtomicBoolean();
        ProductService productService = new ProductService(
                productDao, new CategoryDao(), stockMovementDao) {
            @Override
            public List<Product> getAllProducts() {
                return List.of(product(
                        8,
                        "Checkout Product",
                        "CP-08",
                        priceChanged.get() ? "13.50" : "12.50",
                        5));
            }
        };
        AtomicInteger serviceCalls = new AtomicInteger();
        AtomicReference<Long> lastUserId = new AtomicReference<>();
        AtomicReference<List<SaleService.RequestedSaleItem>> lastRequest =
                new AtomicReference<>(List.of());
        CountDownLatch firstSaleStarted = new CountDownLatch(1);
        CountDownLatch releaseFirstSale = new CountDownLatch(1);
        AtomicBoolean confirmCheckout = new AtomicBoolean();
        AtomicBoolean navigationLocked = new AtomicBoolean();
        SaleService saleService = new SaleService(
                productDao, saleDao, saleItemDao, stockMovementDao) {
            @Override
            public Sale createSale(
                    long authenticatedUserId,
                    java.util.Collection<RequestedSaleItem> requestedItems)
                    throws SQLException {
                int callNumber = serviceCalls.incrementAndGet();
                lastUserId.set(authenticatedUserId);
                lastRequest.set(List.copyOf(requestedItems));
                if (callNumber == 1) {
                    firstSaleStarted.countDown();
                    try {
                        if (!releaseFirstSale.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                            throw new SQLException("Timed out waiting to release checkout.");
                        }
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new SQLException("Checkout was interrupted.", exception);
                    }
                    throw new SQLException("Injected checkout failure.");
                }
                if (callNumber == 2) {
                    priceChanged.set(true);
                    throw new SaleService.PriceChangedException(
                            8, new BigDecimal("12.50"), new BigDecimal("13.50"));
                }
                return new Sale(
                        77,
                        authenticatedUserId,
                        LocalDateTime.of(2026, 11, 29, 12, 0),
                        new BigDecimal("27.00"));
            }
        };
        SalesController controller = new TestSalesController(
                productService,
                saleService,
                saleDao,
                saleItemDao,
                7,
                confirmCheckout,
                navigationLocked);
        FXMLLoader loader = new FXMLLoader(
                SalesUiTest.class.getResource("/com/inventory/view/sales-view.fxml"));
        loader.setControllerFactory(controllerType -> {
            if (controllerType == SalesController.class) {
                return controller;
            }
            throw new IllegalStateException(
                    "No controller factory is configured for "
                            + controllerType.getName());
        });
        Parent root = loader.load();
        root.setUserData(controller);
        return new CheckoutFixture(
                root,
                serviceCalls,
                lastUserId,
                lastRequest,
                firstSaleStarted,
                releaseFirstSale,
                confirmCheckout,
                navigationLocked);
    }

    private static void selectInvoice(Parent root, long invoiceId) {
        TableView<Sale> invoiceTable = typedTable(root, "#invoiceTable");
        Sale selected = invoiceTable.getItems().stream()
                .filter(sale -> sale.getId() == invoiceId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Invoice " + invoiceId + " was not found."));
        invoiceTable.getSelectionModel().select(selected);
    }

    private static String firstInvoiceItemName(Parent root) {
        TableView<SaleItem> items = typedTable(root, "#invoiceItemsTable");
        return items.getItems().isEmpty() ? "" : items.getItems().get(0).getProductName();
    }

    private static void verifyProductSearchAndCart(Parent root) {
        TableView<Product> productTable = typedTable(root, "#productTable");
        TextField searchField = (TextField) root.lookup("#productSearchField");
        TextField productQuantityField = (TextField) root.lookup("#productQuantityField");
        Button addButton = (Button) root.lookup("#addToCartButton");
        TableView<?> cartTable = table(root, "#cartTable");
        TextField cartQuantityField = (TextField) root.lookup("#cartQuantityField");
        Button updateButton = (Button) root.lookup("#updateCartButton");
        Button removeButton = (Button) root.lookup("#removeCartItemButton");
        Button checkoutButton = (Button) root.lookup("#checkoutButton");
        Label totalLabel = (Label) root.lookup("#cartTotalLabel");
        Label productStatus = (Label) root.lookup("#productStatusLabel");

        require(productTable.getItems().size() == 2, "Out-of-stock products are not selectable.");
        searchField.setText("office");
        require(
                productTable.getItems().size() == 1
                        && "Office Chair".equals(productTable.getItems().get(0).getName()),
                "Product search filters by product name.");
        searchField.setText("dl-01");
        require(
                productTable.getItems().size() == 1
                        && "Desk Lamp".equals(productTable.getItems().get(0).getName()),
                "Product search filters by product code.");

        searchField.clear();
        Product deskLamp = productTable.getItems().stream()
                .filter(product -> product.getId() == 1)
                .findFirst()
                .orElseThrow();
        productTable.getSelectionModel().select(deskLamp);
        productQuantityField.setText("2");
        addButton.fire();
        require(cartTable.getItems().size() == 1, "A selected product is added to the cart.");
        require("25.00".equals(totalLabel.getText()), "Cart total uses decimal arithmetic.");

        productQuantityField.setText("1");
        addButton.fire();
        require(
                cartTable.getItems().size() == 1,
                "Adding an existing product updates its cart row instead of duplicating it.");
        require("37.50".equals(totalLabel.getText()), "Duplicate quantity updates the total.");

        productQuantityField.setText("0");
        addButton.fire();
        require(
                cartTable.getItems().size() == 1
                        && productStatus.getText().contains("greater than zero"),
                "Zero quantity is rejected without changing the cart.");
        productQuantityField.setText("3");
        addButton.fire();
        require(
                cartTable.getItems().size() == 1
                        && "37.50".equals(totalLabel.getText())
                        && productStatus.getText().contains("available stock"),
                "Adding more than available stock is rejected.");

        cartTable.getSelectionModel().select(0);
        cartQuantityField.setText("0");
        updateButton.fire();
        require(
                "37.50".equals(totalLabel.getText()),
                "Invalid cart quantity updates leave the existing cart unchanged.");
        cartQuantityField.setText("4");
        updateButton.fire();
        require("50.00".equals(totalLabel.getText()), "Valid cart quantity updates recalculate.");

        removeButton.fire();
        require(cartTable.getItems().isEmpty(), "Selected cart items can be removed.");
        require("0.00".equals(totalLabel.getText()), "Removing the last item resets the total.");
        require(checkoutButton.isDisabled(), "Checkout is disabled for an empty cart.");
        checkoutButton.fire();
    }

    private static Product product(
            long id, String name, String code, String sellingPrice, int quantity) {
        return new Product(
                id,
                name,
                code,
                1,
                new BigDecimal("1.00"),
                new BigDecimal(sellingPrice),
                quantity,
                0);
    }

    private static void awaitFxCondition(BooleanSupplier condition, String failureMessage)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TEST_TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            AtomicReference<Boolean> matches = new AtomicReference<>(false);
            CountDownLatch checked = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    matches.set(condition.getAsBoolean());
                } finally {
                    checked.countDown();
                }
            });
            if (!checked.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("JavaFX condition check timed out.");
            }
            if (matches.get()) {
                return;
            }
            Thread.sleep(20);
        }
        throw new AssertionError(failureMessage);
    }

    private static <T> T runOnFxAndWait(ThrowingSupplier<T> action) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(action.get());
            } catch (Throwable exception) {
                failure.set(exception);
            } finally {
                completed.countDown();
            }
        });
        if (!completed.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new AssertionError("JavaFX UI checks timed out.");
        }
        rethrowFailure(failure.get(), "Sales UI checks failed.");
        return result.get();
    }

    private static void rethrowFailure(Throwable failure, String message) {
        if (failure != null) {
            throw new AssertionError(message, failure);
        }
    }

    private static int tableSize(Parent root, String selector) {
        TableView<?> table = table(root, selector);
        return table == null ? -1 : table.getItems().size();
    }

    private static TableView<?> table(Parent root, String selector) {
        return root.lookup(selector) instanceof TableView<?> table ? table : null;
    }

    @SuppressWarnings("unchecked")
    private static <T> TableView<T> typedTable(Parent root, String selector) {
        Object node = root.lookup(selector);
        if (!(node instanceof TableView<?>)) {
            throw new AssertionError("Expected a TableView at " + selector + ".");
        }
        return (TableView<T>) node;
    }

    @SuppressWarnings("unchecked")
    private static <S, T> TableColumn<S, T> typedColumn(TableView<S> table, int index) {
        Object column = table.getColumns().get(index);
        if (!(column instanceof TableColumn<?, ?>)) {
            throw new AssertionError("Expected a TableColumn at index " + index + ".");
        }
        return (TableColumn<S, T>) column;
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private record MainPageFixture(
            Parent mainRoot,
            CountDownLatch slowInvoiceLookupStarted,
            CountDownLatch releaseSlowInvoiceLookup,
            CountDownLatch slowInvoiceLookupFinished) {
    }

    private record CheckoutFixture(
            Parent root,
            AtomicInteger serviceCalls,
            AtomicReference<Long> lastUserId,
            AtomicReference<List<SaleService.RequestedSaleItem>> lastRequest,
            CountDownLatch firstSaleStarted,
            CountDownLatch releaseFirstSale,
            AtomicBoolean confirmCheckout,
            AtomicBoolean navigationLocked) {
    }

    private static final class TestSalesController extends SalesController {
        private final AtomicBoolean confirmCheckout;

        private TestSalesController(
                ProductService productService,
                SaleService saleService,
                SaleDao saleDao,
                SaleItemDao saleItemDao,
                long authenticatedUserId,
                AtomicBoolean confirmCheckout,
                AtomicBoolean navigationLocked) {
            super(
                    productService,
                    saleService,
                    saleDao,
                    saleItemDao,
                    authenticatedUserId,
                    navigationLocked::set);
            this.confirmCheckout = confirmCheckout;
        }

        @Override
        protected boolean confirmCheckout(BigDecimal total, int itemCount) {
            return confirmCheckout.get();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
