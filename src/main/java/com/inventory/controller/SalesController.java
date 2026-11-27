package com.inventory.controller;

import com.inventory.dao.SaleDao;
import com.inventory.dao.SaleItemDao;
import com.inventory.model.Product;
import com.inventory.model.Sale;
import com.inventory.model.SaleItem;
import com.inventory.service.ProductService;
import com.inventory.service.SaleService;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SalesController {
    private static final Logger LOGGER = Logger.getLogger(SalesController.class.getName());
    private static final BigDecimal ZERO_MONEY = BigDecimal.ZERO.setScale(2);
    private static final DateTimeFormatter SALE_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ProductService productService;
    private final SaleService saleService;
    private final SaleDao saleDao;
    private final SaleItemDao saleItemDao;
    private final long authenticatedUserId;
    private final Consumer<Boolean> checkoutStateChanged;

    private final ObservableList<Product> availableProducts =
            FXCollections.observableArrayList();
    private final FilteredList<Product> filteredProducts =
            new FilteredList<>(availableProducts, product -> true);
    private final ObservableList<CartLine> cartItems =
            FXCollections.observableArrayList();
    private final ObservableList<Sale> invoices = FXCollections.observableArrayList();
    private final ObservableList<SaleItem> invoiceItems =
            FXCollections.observableArrayList();

    @FXML
    private TextField productSearchField;

    @FXML
    private TextField productQuantityField;

    @FXML
    private TableView<Product> productTable;

    @FXML
    private TableColumn<Product, String> productNameColumn;

    @FXML
    private TableColumn<Product, String> productCodeColumn;

    @FXML
    private TableColumn<Product, String> productPriceColumn;

    @FXML
    private TableColumn<Product, Number> productStockColumn;

    @FXML
    private Button addToCartButton;

    @FXML
    private Button refreshProductsButton;

    @FXML
    private TableView<CartLine> cartTable;

    @FXML
    private TableColumn<CartLine, String> cartNameColumn;

    @FXML
    private TableColumn<CartLine, String> cartCodeColumn;

    @FXML
    private TableColumn<CartLine, String> cartPriceColumn;

    @FXML
    private TableColumn<CartLine, Number> cartQuantityColumn;

    @FXML
    private TableColumn<CartLine, String> cartSubtotalColumn;

    @FXML
    private TextField cartQuantityField;

    @FXML
    private Button updateCartButton;

    @FXML
    private Button removeCartItemButton;

    @FXML
    private Label cartTotalLabel;

    @FXML
    private Button checkoutButton;

    @FXML
    private Label productStatusLabel;

    @FXML
    private Label checkoutStatusLabel;

    @FXML
    private TableView<Sale> invoiceTable;

    @FXML
    private TableColumn<Sale, Number> invoiceIdColumn;

    @FXML
    private TableColumn<Sale, String> invoiceDateColumn;

    @FXML
    private TableColumn<Sale, String> invoiceTotalColumn;

    @FXML
    private Button refreshInvoicesButton;

    @FXML
    private Label invoiceStatusLabel;

    @FXML
    private Label selectedInvoiceTitleLabel;

    @FXML
    private Label selectedInvoiceTotalLabel;

    @FXML
    private TableView<SaleItem> invoiceItemsTable;

    @FXML
    private TableColumn<SaleItem, String> invoiceItemNameColumn;

    @FXML
    private TableColumn<SaleItem, String> invoiceItemCodeColumn;

    @FXML
    private TableColumn<SaleItem, String> invoiceItemPriceColumn;

    @FXML
    private TableColumn<SaleItem, Number> invoiceItemQuantityColumn;

    @FXML
    private TableColumn<SaleItem, String> invoiceItemSubtotalColumn;

    private boolean productLoadInProgress;
    private boolean productRefreshPending;
    private boolean checkoutInProgress;
    private long invoiceRefreshVersion;
    private long invoiceDetailsVersion;
    private Long recentlyCompletedSaleId;
    private Long completedSaleId;

    public SalesController(
            ProductService productService,
            SaleService saleService,
            SaleDao saleDao,
            SaleItemDao saleItemDao,
            long authenticatedUserId,
            Consumer<Boolean> checkoutStateChanged) {
        this.productService = Objects.requireNonNull(productService);
        this.saleService = Objects.requireNonNull(saleService);
        this.saleDao = Objects.requireNonNull(saleDao);
        this.saleItemDao = Objects.requireNonNull(saleItemDao);
        if (authenticatedUserId <= 0) {
            throw new IllegalArgumentException("Authenticated user ID must be positive.");
        }
        this.authenticatedUserId = authenticatedUserId;
        this.checkoutStateChanged =
                Objects.requireNonNull(checkoutStateChanged, "Checkout state listener is required.");
    }

    @FXML
    private void initialize() {
        configureProductTable();
        configureCartTable();
        configureInvoiceTables();

        productTable.setItems(filteredProducts);
        cartTable.setItems(cartItems);
        invoiceTable.setItems(invoices);
        invoiceItemsTable.setItems(invoiceItems);

        productSearchField.textProperty().addListener(
                (observable, previousValue, searchText) -> applyProductFilter(searchText));
        productTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousProduct, selectedProduct) -> updateActionButtons());
        cartTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousLine, selectedLine) -> {
                    cartQuantityField.setText(
                            selectedLine == null ? "" : Integer.toString(selectedLine.quantity()));
                    updateActionButtons();
                });
        invoiceTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, previousSale, selectedSale) -> loadInvoiceDetails(selectedSale));

        updateCartTotal();
        updateActionButtons();
        refreshProducts();
        refreshInvoices(null, null);
    }

    @FXML
    private void handleAddToCart() {
        Product selectedProduct = productTable.getSelectionModel().getSelectedItem();
        if (selectedProduct == null || checkoutInProgress) {
            return;
        }

        OptionalInt requestedQuantity = parseQuantity(productQuantityField.getText());
        if (requestedQuantity.isEmpty()) {
            productStatusLabel.setText("Enter a whole quantity greater than zero.");
            return;
        }
        if (!hasValidSellingPrice(selectedProduct)) {
            productStatusLabel.setText("This product has an invalid selling price.");
            return;
        }

        CartLine existingLine = findCartLine(selectedProduct.getId());
        long combinedQuantity = requestedQuantity.getAsInt()
                + (existingLine == null ? 0L : existingLine.quantity());
        if (combinedQuantity > selectedProduct.getQuantity()) {
            productStatusLabel.setText(
                    "Requested quantity exceeds the available stock for "
                            + selectedProduct.getName() + ".");
            return;
        }

        if (existingLine == null) {
            cartItems.add(new CartLine(selectedProduct, requestedQuantity.getAsInt()));
        } else {
            existingLine.setProduct(selectedProduct);
            existingLine.setQuantity((int) combinedQuantity);
        }
        cartTable.refresh();
        updateCartTotal();
        updateActionButtons();
        productStatusLabel.setText("Product added to the cart.");
        checkoutStatusLabel.setText("");
    }

    @FXML
    private void handleUpdateCartQuantity() {
        CartLine selectedLine = cartTable.getSelectionModel().getSelectedItem();
        if (selectedLine == null || checkoutInProgress) {
            return;
        }

        OptionalInt requestedQuantity = parseQuantity(cartQuantityField.getText());
        if (requestedQuantity.isEmpty()) {
            checkoutStatusLabel.setText("Enter a whole quantity greater than zero.");
            return;
        }
        if (requestedQuantity.getAsInt() > selectedLine.product().getQuantity()) {
            checkoutStatusLabel.setText(
                    "Requested quantity exceeds the available stock for "
                            + selectedLine.product().getName() + ".");
            return;
        }

        selectedLine.setQuantity(requestedQuantity.getAsInt());
        cartTable.refresh();
        updateCartTotal();
        checkoutStatusLabel.setText("");
    }

    @FXML
    private void handleRemoveCartItem() {
        CartLine selectedLine = cartTable.getSelectionModel().getSelectedItem();
        if (selectedLine == null || checkoutInProgress) {
            return;
        }

        cartItems.remove(selectedLine);
        cartQuantityField.clear();
        updateCartTotal();
        updateActionButtons();
        checkoutStatusLabel.setText("");
    }

    @FXML
    private void handleRefreshProducts() {
        refreshProducts();
    }

    @FXML
    private void handleRefreshInvoices() {
        refreshInvoices(null, null);
    }

    @FXML
    private void handleCheckout() {
        if (checkoutInProgress || cartItems.isEmpty()) {
            return;
        }

        Optional<CheckoutSnapshot> snapshot = createCheckoutSnapshot();
        if (snapshot.isEmpty()) {
            checkoutStatusLabel.setText(
                    "A cart product has no valid selling price. Refresh products and review "
                            + "the cart before checkout.");
            refreshProducts();
            return;
        }
        CheckoutSnapshot confirmedCheckout = snapshot.get();
        if (!confirmCheckout(confirmedCheckout.total(), confirmedCheckout.items().size())) {
            return;
        }

        completedSaleId = null;
        setCheckoutInProgress(true);
        checkoutStatusLabel.setText("Checkout is in progress. Please wait for the sale result.");
        updateActionButtons();

        Task<Sale> task = new Task<>() {
            @Override
            protected Sale call() throws SQLException {
                return saleService.createSale(
                        authenticatedUserId, confirmedCheckout.items());
            }
        };
        runTask(
                task,
                this::handleCommittedSale,
                this::handleCheckoutFailure,
                this::handlePostCommitPresentationFailure,
                "sale checkout");
    }

    private void configureProductTable() {
        productNameColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().getName()));
        productCodeColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().getCode()));
        productPriceColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(formatMoney(cell.getValue().getSellingPrice())));
        productStockColumn.setCellValueFactory(
                cell -> new ReadOnlyIntegerWrapper(cell.getValue().getQuantity()));
    }

    private void configureCartTable() {
        cartNameColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().product().getName()));
        cartCodeColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(cell.getValue().product().getCode()));
        cartPriceColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        formatMoney(cell.getValue().product().getSellingPrice())));
        cartQuantityColumn.setCellValueFactory(
                cell -> new ReadOnlyIntegerWrapper(cell.getValue().quantity()));
        cartSubtotalColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(formatMoney(cell.getValue().subtotal())));
    }

    private void configureInvoiceTables() {
        invoiceIdColumn.setCellValueFactory(
                cell -> new ReadOnlyObjectWrapper<Number>(cell.getValue().getId()));
        invoiceDateColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                cell.getValue().getSaleDate() == null
                        ? "Unavailable"
                        : SALE_DATE_FORMAT.format(cell.getValue().getSaleDate())));
        invoiceTotalColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        formatMoney(cell.getValue().getTotalAmount())));

        invoiceItemNameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                snapshotText(cell.getValue().getProductName())));
        invoiceItemCodeColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                snapshotText(cell.getValue().getProductCode())));
        invoiceItemPriceColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        formatMoney(cell.getValue().getUnitPrice())));
        invoiceItemQuantityColumn.setCellValueFactory(
                cell -> new ReadOnlyIntegerWrapper(cell.getValue().getQuantity()));
        invoiceItemSubtotalColumn.setCellValueFactory(
                cell -> new ReadOnlyStringWrapper(
                        formatMoney(cell.getValue().getSubtotal())));
    }

    private void refreshProducts() {
        if (productLoadInProgress) {
            productRefreshPending = true;
            return;
        }

        productLoadInProgress = true;
        refreshProductsButton.setDisable(true);
        productStatusLabel.setText("Loading available products...");
        updateActionButtons();

        Task<List<Product>> task = new Task<>() {
            @Override
            protected List<Product> call() throws SQLException {
                return productService.getAllProducts();
            }
        };
        runTask(task, products -> {
            List<Product> loadedProducts = List.copyOf(products);
            productLoadInProgress = false;
            refreshProductsButton.setDisable(false);
            availableProducts.setAll(
                    loadedProducts.stream().filter(product -> product.getQuantity() > 0).toList());
            updateCartProducts(loadedProducts);
            applyProductFilter(productSearchField.getText());
            productStatusLabel.setText(
                    availableProducts.size() == 1
                            ? "1 available product"
                            : availableProducts.size() + " available products");
            updateCartTotal();
            updateActionButtons();
            runPendingProductRefresh();
        }, failure -> {
            productLoadInProgress = false;
            refreshProductsButton.setDisable(false);
            updateActionButtons();
            LOGGER.log(Level.SEVERE, "Unable to load products for sales.", failure);
            productStatusLabel.setText(completedSaleId == null
                    ? "Unable to load products. Your cart is unchanged; refresh to try again."
                    : "Sale #" + completedSaleId
                            + " was completed, but products could not be refreshed. "
                            + "Use Refresh to try again.");
            runPendingProductRefresh();
        }, "product refresh");
    }

    private void runPendingProductRefresh() {
        if (productRefreshPending) {
            productRefreshPending = false;
            refreshProducts();
        }
    }

    private void refreshInvoices(Long preferredSaleId, Sale committedSale) {
        long requestVersion = ++invoiceRefreshVersion;
        Long selectedSaleId = preferredSaleId;
        if (selectedSaleId == null) {
            Sale selectedSale = invoiceTable.getSelectionModel().getSelectedItem();
            selectedSaleId = selectedSale == null ? null : selectedSale.getId();
        }
        final Long invoiceToSelect = selectedSaleId;
        invoiceStatusLabel.setText("Loading invoice history...");

        Task<List<Sale>> task = new Task<>() {
            @Override
            protected List<Sale> call() throws SQLException {
                return saleDao.findAll();
            }
        };
        runTask(task, sales -> {
            if (requestVersion != invoiceRefreshVersion) {
                return;
            }

            List<Sale> loadedSales = new ArrayList<>(sales);
            if (committedSale != null
                    && loadedSales.stream().noneMatch(
                            sale -> sale.getId() == committedSale.getId())) {
                loadedSales.add(0, committedSale);
            }
            invoices.setAll(loadedSales);

            Sale saleToSelect = invoiceToSelect == null
                    ? null
                    : invoices.stream()
                            .filter(sale -> sale.getId() == invoiceToSelect)
                            .findFirst()
                            .orElse(null);
            if (saleToSelect == null) {
                invoiceTable.getSelectionModel().clearSelection();
                invoiceItems.clear();
                selectedInvoiceTitleLabel.setText("Select an invoice");
                selectedInvoiceTotalLabel.setText("—");
            } else {
                invoiceTable.getSelectionModel().select(saleToSelect);
            }
            invoiceStatusLabel.setText(
                    invoices.size() == 1 ? "1 invoice" : invoices.size() + " invoices");
        }, failure -> {
            if (requestVersion != invoiceRefreshVersion) {
                return;
            }
            LOGGER.log(Level.SEVERE, "Unable to load invoice history.", failure);
            if (committedSale != null) {
                invoiceStatusLabel.setText(
                        "Sale #" + committedSale.getId()
                                + " was completed, but invoice details could not be refreshed. "
                                + "Do not submit it again. Use Refresh invoices to retry.");
            } else {
                invoiceStatusLabel.setText(
                        "Unable to load invoice history. Refresh to try again.");
            }
        }, "invoice history refresh");
    }

    private void loadInvoiceDetails(Sale sale) {
        long requestVersion = ++invoiceDetailsVersion;
        invoiceItems.clear();
        if (sale == null) {
            selectedInvoiceTitleLabel.setText("Select an invoice");
            selectedInvoiceTotalLabel.setText("—");
            return;
        }

        selectedInvoiceTitleLabel.setText("Invoice #" + sale.getId());
        selectedInvoiceTotalLabel.setText(formatMoney(sale.getTotalAmount()));
        invoiceStatusLabel.setText("Loading invoice details...");

        Task<List<SaleItem>> task = new Task<>() {
            @Override
            protected List<SaleItem> call() throws SQLException {
                return saleItemDao.findBySaleId(sale.getId());
            }
        };
        runTask(task, items -> {
            if (requestVersion != invoiceDetailsVersion
                    || !isSelectedInvoice(sale.getId())) {
                return;
            }
            invoiceItems.setAll(items);
            invoiceStatusLabel.setText(
                    items.isEmpty()
                            ? "No line items are available for this invoice."
                            : items.size() == 1
                                    ? "1 invoice line"
                                    : items.size() + " invoice lines");
        }, failure -> {
            if (requestVersion != invoiceDetailsVersion
                    || !isSelectedInvoice(sale.getId())) {
                return;
            }
            invoiceItems.clear();
            LOGGER.log(
                    Level.SEVERE,
                    "Unable to load invoice details for sale " + sale.getId() + ".",
                    failure);
            if (Objects.equals(recentlyCompletedSaleId, sale.getId())) {
                invoiceStatusLabel.setText(
                        "Sale #" + sale.getId()
                                + " was completed, but invoice details could not be refreshed. "
                                + "Do not submit it again. Use Refresh invoices to retry.");
            } else {
                invoiceStatusLabel.setText(
                        "Unable to load invoice details. Refresh invoices to try again.");
            }
        }, "invoice details");
    }

    private void updateCartProducts(List<Product> products) {
        for (CartLine line : cartItems) {
            products.stream()
                    .filter(product -> product.getId() == line.product().getId())
                    .findFirst()
                    .ifPresent(line::setProduct);
        }
        cartTable.refresh();
    }

    private void applyProductFilter(String searchText) {
        String query = searchText == null ? "" : searchText.strip().toLowerCase(Locale.ROOT);
        filteredProducts.setPredicate(product -> product.getQuantity() > 0
                && (query.isEmpty()
                        || containsIgnoreCase(product.getName(), query)
                        || containsIgnoreCase(product.getCode(), query)));
        updateActionButtons();
    }

    private void updateCartTotal() {
        BigDecimal total = cartItems.stream()
                .map(CartLine::subtotal)
                .reduce(ZERO_MONEY, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        cartTotalLabel.setText(formatMoney(total));
    }

    private void updateActionButtons() {
        if (addToCartButton != null) {
            addToCartButton.setDisable(
                    checkoutInProgress
                            || productLoadInProgress
                            || productTable.getSelectionModel().getSelectedItem() == null);
        }
        if (updateCartButton != null) {
            updateCartButton.setDisable(
                    checkoutInProgress
                            || cartTable.getSelectionModel().getSelectedItem() == null);
        }
        if (removeCartItemButton != null) {
            removeCartItemButton.setDisable(
                    checkoutInProgress
                            || cartTable.getSelectionModel().getSelectedItem() == null);
        }
        if (checkoutButton != null) {
            checkoutButton.setDisable(
                    checkoutInProgress || productLoadInProgress || cartItems.isEmpty());
        }
    }

    protected boolean confirmCheckout(BigDecimal total, int itemCount) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm checkout");
        confirmation.setHeaderText("Complete this sale?");
        confirmation.setContentText(
                itemCount + " item(s), total " + formatMoney(total) + ".");
        confirmation.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        return confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private Optional<CheckoutSnapshot> createCheckoutSnapshot() {
        List<SaleService.RequestedSaleItem> items = new ArrayList<>(cartItems.size());
        BigDecimal total = ZERO_MONEY;
        for (CartLine line : cartItems) {
            Product product = line.product();
            if (!hasValidSellingPrice(product)) {
                return Optional.empty();
            }
            BigDecimal displayedPrice =
                    product.getSellingPrice().setScale(2, RoundingMode.HALF_UP);
            items.add(new SaleService.RequestedSaleItem(
                    product.getId(), line.quantity(), displayedPrice));
            total = total.add(
                    displayedPrice.multiply(BigDecimal.valueOf(line.quantity())));
        }
        return Optional.of(new CheckoutSnapshot(items, total.setScale(2, RoundingMode.HALF_UP)));
    }

    private void handleCommittedSale(Sale sale) {
        if (sale == null || sale.getId() <= 0) {
            setCheckoutInProgress(false);
            checkoutStatusLabel.setText(
                    "The sale result could not be verified. Check invoice history before "
                            + "attempting another checkout.");
            return;
        }

        completedSaleId = sale.getId();
        recentlyCompletedSaleId = sale.getId();
        cartItems.clear();
        cartQuantityField.clear();
        updateCartTotal();
        updateActionButtons();
        setCheckoutInProgress(false);
        checkoutStatusLabel.setText(
                "Sale #" + sale.getId() + " completed. Refreshing invoice details...");
        refreshProducts();
        refreshInvoices(sale.getId(), sale);
    }

    private void handleCheckoutFailure(Throwable failure) {
        setCheckoutInProgress(false);
        updateActionButtons();
        checkoutStatusLabel.setText(checkoutFailureMessage(failure));
        if (failure instanceof SQLException) {
            LOGGER.log(Level.SEVERE, "Unable to complete the sale.", failure);
        }
        refreshProducts();
    }

    private void handlePostCommitPresentationFailure(RuntimeException failure) {
        LOGGER.log(
                Level.SEVERE,
                "Sale " + completedSaleId
                        + " was committed, but its screen could not be refreshed.",
                failure);
        setCheckoutInProgress(false);
        updateActionButtons();
        if (completedSaleId == null) {
            checkoutStatusLabel.setText(
                    "The sale result could not be displayed. Verify invoice history before "
                            + "attempting another checkout.");
            return;
        }

        cartItems.clear();
        cartQuantityField.clear();
        updateCartTotal();
        String message = "Sale #" + completedSaleId
                + " was completed, but the page could not be refreshed. "
                + "Do not submit it again; use Refresh invoices to verify the transaction.";
        checkoutStatusLabel.setText(message);
        invoiceStatusLabel.setText(message);
    }

    private void setCheckoutInProgress(boolean inProgress) {
        checkoutInProgress = inProgress;
        updateActionButtons();
        checkoutStateChanged.accept(inProgress);
    }

    private CartLine findCartLine(long productId) {
        return cartItems.stream()
                .filter(line -> line.product().getId() == productId)
                .findFirst()
                .orElse(null);
    }

    private boolean isSelectedInvoice(long saleId) {
        Sale selectedSale = invoiceTable.getSelectionModel().getSelectedItem();
        return selectedSale != null && selectedSale.getId() == saleId;
    }

    private boolean hasValidSellingPrice(Product product) {
        return product.getSellingPrice() != null
                && product.getSellingPrice().signum() >= 0;
    }

    private String checkoutFailureMessage(Throwable failure) {
        if (failure instanceof SaleService.PriceChangedException priceChanged) {
            return "The selling price changed for product #" + priceChanged.getProductId()
                    + " after confirmation. The sale was not completed; refresh products, "
                    + "review the cart, and confirm again.";
        }
        if (failure instanceof IllegalArgumentException
                || failure instanceof IllegalStateException) {
            String message = failure.getMessage();
            if (message != null && message.toLowerCase(Locale.ROOT).contains("stock")) {
                return "Stock changed or is insufficient. Your cart was kept; review the "
                        + "available stock and try again.";
            }
            return "Sale could not be completed: "
                    + (message == null ? "check the cart and try again." : message);
        }
        return "The database could not complete the sale. Your cart was kept; please try again.";
    }

    private OptionalInt parseQuantity(String text) {
        if (text == null || text.isBlank()) {
            return OptionalInt.empty();
        }
        try {
            int quantity = Integer.parseInt(text.strip());
            return quantity > 0 ? OptionalInt.of(quantity) : OptionalInt.empty();
        } catch (NumberFormatException exception) {
            return OptionalInt.empty();
        }
    }

    private String formatMoney(BigDecimal amount) {
        return amount == null
                ? "Unavailable"
                : amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String snapshotText(String value) {
        return value == null || value.isBlank() ? "Unavailable" : value;
    }

    private boolean containsIgnoreCase(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private <T> void runTask(
            Task<T> task,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            String operationName) {
        runTask(
                task,
                onSuccess,
                onFailure,
                exception -> onFailure.accept(exception),
                operationName);
    }

    private <T> void runTask(
            Task<T> task,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            Consumer<RuntimeException> onSuccessFailure,
            String operationName) {
        task.setOnSucceeded(event -> {
            try {
                onSuccess.accept(task.getValue());
            } catch (RuntimeException exception) {
                LOGGER.log(
                        Level.SEVERE,
                        "The " + operationName + " completed, but the UI could not be updated.",
                        exception);
                onSuccessFailure.accept(exception);
            }
        });
        task.setOnFailed(event -> {
            Throwable failure = task.getException();
            if (failure == null) {
                failure = new IllegalStateException("The " + operationName + " failed.");
            }
            onFailure.accept(failure);
        });
        task.setOnCancelled(event ->
                onFailure.accept(new IllegalStateException(
                        "The " + operationName + " was cancelled.")));

        try {
            Thread worker = new Thread(task, "sales-ui-" + operationName.replace(' ', '-'));
            worker.setDaemon(true);
            worker.start();
        } catch (RuntimeException exception) {
            onFailure.accept(exception);
        }
    }

    private record CheckoutSnapshot(
            List<SaleService.RequestedSaleItem> items,
            BigDecimal total) {
        private CheckoutSnapshot {
            items = List.copyOf(items);
        }
    }

    private final class CartLine {
        private Product product;
        private int quantity;

        private CartLine(Product product, int quantity) {
            this.product = product;
            this.quantity = quantity;
        }

        private Product product() {
            return product;
        }

        private void setProduct(Product product) {
            this.product = product;
        }

        private int quantity() {
            return quantity;
        }

        private void setQuantity(int quantity) {
            this.quantity = quantity;
        }

        private BigDecimal subtotal() {
            return product.getSellingPrice()
                    .setScale(2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(quantity))
                    .setScale(2, RoundingMode.HALF_UP);
        }
    }
}
