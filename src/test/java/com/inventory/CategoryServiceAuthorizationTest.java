package com.inventory;

import com.inventory.dao.CategoryDao;
import com.inventory.model.Category;
import com.inventory.service.CategoryService;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class CategoryServiceAuthorizationTest {
    private static final String ADMIN_ROLE = "ADMIN";
    private static final String REGULAR_ROLE = "USER";

    private CategoryServiceAuthorizationTest() {
    }

    public static void main(String[] args) throws SQLException {
        InMemoryCategoryDao dao = new InMemoryCategoryDao();
        CategoryService service = new CategoryService(dao);

        Category category = service.createCategory(
                ADMIN_ROLE, new Category("  Office  "));
        require("Office".equals(category.getName()), "Category names are trimmed.");
        require(category.getId() > 0, "Admin can create a category.");
        require(
                service.getAllCategories().size() == 1,
                "Read-only category retrieval remains available.");

        expectExact(
                IllegalArgumentException.class,
                () -> service.createCategory(ADMIN_ROLE, new Category(null)),
                "reject null name");
        expectExact(
                IllegalArgumentException.class,
                () -> service.createCategory(ADMIN_ROLE, new Category(" \t ")),
                "reject whitespace-only name");
        expectExact(
                IllegalArgumentException.class,
                () -> service.createCategory(ADMIN_ROLE, new Category("x".repeat(101))),
                "reject name longer than 100 characters");

        Category maximumLength = service.createCategory(
                ADMIN_ROLE, new Category("x".repeat(100)));
        require(maximumLength.getName().codePointCount(
                        0, maximumLength.getName().length()) == 100,
                "A 100-character name is accepted.");

        expectExact(
                IllegalArgumentException.class,
                () -> service.createCategory(ADMIN_ROLE, new Category("office")),
                "reject duplicate name using database-style case-insensitive lookup");

        Category secondCategory = service.createCategory(
                ADMIN_ROLE, new Category("Hardware"));
        int updateCallsBeforeExactNoOp = dao.updateCallCount;
        require(
                service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), "Office"))
                        == CategoryService.UpdateResult.UNCHANGED,
                "An identical name returns UNCHANGED.");
        require(
                dao.updateCallCount == updateCallsBeforeExactNoOp,
                "An identical name does not call DAO update.");

        int updateCallsBeforeCaseNoOp = dao.updateCallCount;
        require(
                service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), "office"))
                        == CategoryService.UpdateResult.UNCHANGED,
                "A case-equivalent name returns UNCHANGED.");
        require(
                dao.updateCallCount == updateCallsBeforeCaseNoOp,
                "A case-equivalent no-op does not call DAO update.");

        require(
                service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), "\u2003Office\u2003"))
                        == CategoryService.UpdateResult.UNCHANGED,
                "A name differing only by surrounding whitespace returns UNCHANGED.");
        require(
                "Office".equals(service.getCategoryById(category.getId()).orElseThrow().getName()),
                "A case-equivalent no-op preserves the stored spelling.");

        int updateCallsBeforeRealUpdate = dao.updateCallCount;
        require(
                service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), "Office Supplies"))
                        == CategoryService.UpdateResult.UPDATED,
                "A changed name returns UPDATED.");
        require(
                dao.updateCallCount == updateCallsBeforeRealUpdate + 1,
                "A real update invokes DAO update.");

        int updateCallsBeforeDuplicate = dao.updateCallCount;
        expectExact(
                IllegalArgumentException.class,
                () -> service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), "hardware")),
                "reject update to another category's name");
        require(
                dao.updateCallCount == updateCallsBeforeDuplicate,
                "A duplicate name is rejected before DAO update.");
        expectExact(
                IllegalStateException.class,
                () -> service.updateCategory(
                        ADMIN_ROLE, new Category(Long.MAX_VALUE, "Missing")),
                "reject update to missing category");
        expectExact(
                IllegalArgumentException.class,
                () -> service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), null)),
                "reject null update name");
        expectExact(
                IllegalArgumentException.class,
                () -> service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), " \t ")),
                "reject blank update name");
        expectExact(
                IllegalArgumentException.class,
                () -> service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), "x".repeat(101))),
                "reject update name longer than 100 characters");
        expectExact(
                IllegalArgumentException.class,
                () -> service.updateCategory(ADMIN_ROLE, null),
                "reject null category update");
        expectExact(
                IllegalArgumentException.class,
                () -> service.updateCategory(
                        ADMIN_ROLE, new Category(0, "Invalid ID")),
                "reject update with non-positive ID");
        expectExact(
                IllegalStateException.class,
                () -> service.deleteCategory(ADMIN_ROLE, Long.MAX_VALUE),
                "reject delete of missing category");

        int writesBeforeUnauthorizedCalls = dao.writeCount;
        expectExact(
                SecurityException.class,
                () -> service.createCategory(REGULAR_ROLE, new Category("Denied create")),
                "deny regular-user create");
        expectExact(
                SecurityException.class,
                () -> service.updateCategory(
                        REGULAR_ROLE, new Category(category.getId(), "Denied update")),
                "deny regular-user update");
        expectExact(
                SecurityException.class,
                () -> service.deleteCategory(REGULAR_ROLE, category.getId()),
                "deny regular-user delete");
        require(
                dao.writeCount == writesBeforeUnauthorizedCalls,
                "Unauthorized mutations do not reach the DAO.");

        dao.nextSaveFailure = new SQLException("simulated duplicate race", "23000", 1062);
        expectExact(
                IllegalArgumentException.class,
                () -> service.createCategory(ADMIN_ROLE, new Category("Concurrent duplicate")),
                "translate duplicate-key race");

        dao.nextSaveFailure = new SQLException("unrelated database failure", "HY000", 9999);
        expectSqlError(
                () -> service.createCategory(ADMIN_ROLE, new Category("Unrelated failure")),
                9999,
                "propagate unrelated database failures");

        dao.nextUpdateFailure = new SQLException(
                "simulated update duplicate race", "23000", 1062);
        expectExact(
                IllegalArgumentException.class,
                () -> service.updateCategory(
                        ADMIN_ROLE, new Category(category.getId(), "Concurrent update duplicate")),
                "translate duplicate-key update race");

        dao.referencedCategoryId = category.getId();
        expectExact(
                CategoryService.CategoryInUseException.class,
                () -> service.deleteCategory(ADMIN_ROLE, category.getId()),
                "translate referenced-category restriction");
        dao.referencedCategoryId = 0;

        require(
                service.deleteCategory(ADMIN_ROLE, maximumLength.getId()),
                "Admin can delete an unused category.");
        dao.deleteBeforeNextUpdateId = secondCategory.getId();
        expectExact(
                IllegalStateException.class,
                () -> service.updateCategory(
                        ADMIN_ROLE, new Category(secondCategory.getId(), "Hardware Revised")),
                "reject an update when the category is deleted concurrently");

        System.out.println("CategoryService authorization and validation checks passed.");
    }

    private static void expectExact(
            Class<? extends RuntimeException> expectedType,
            CheckedOperation operation,
            String checkName) throws SQLException {
        try {
            operation.run();
        } catch (SQLException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            if (exception.getClass() != expectedType) {
                throw new AssertionError(
                        checkName + " threw " + exception.getClass().getName()
                                + " instead of " + expectedType.getName(),
                        exception);
            }
            System.out.println("PASS: " + checkName);
            return;
        }
        throw new AssertionError(checkName + " did not throw " + expectedType.getName());
    }

    private static void expectSqlError(
            CheckedOperation operation, int expectedCode, String checkName) throws SQLException {
        try {
            operation.run();
        } catch (SQLException exception) {
            if (exception.getErrorCode() != expectedCode) {
                throw new AssertionError(
                        checkName + " propagated SQL error code " + exception.getErrorCode());
            }
            System.out.println("PASS: " + checkName);
            return;
        }
        throw new AssertionError(checkName + " did not throw SQLException");
    }

    private static void require(boolean condition, String checkName) {
        if (!condition) {
            throw new AssertionError(checkName);
        }
        System.out.println("PASS: " + checkName);
    }

    @FunctionalInterface
    private interface CheckedOperation {
        Object run() throws SQLException;
    }

    private static final class InMemoryCategoryDao extends CategoryDao {
        private final Map<Long, Category> categories = new LinkedHashMap<>();
        private long nextId = 1;
        private int writeCount;
        private int updateCallCount;
        private SQLException nextSaveFailure;
        private SQLException nextUpdateFailure;
        private long referencedCategoryId;
        private long deleteBeforeNextUpdateId;

        @Override
        public List<Category> findAll() {
            List<Category> result = new ArrayList<>();
            for (Category category : categories.values()) {
                result.add(copy(category));
            }
            return result;
        }

        @Override
        public Optional<Category> findById(long id) {
            return Optional.ofNullable(categories.get(id)).map(this::copy);
        }

        @Override
        public Optional<Category> findByName(String name) {
            return categories.values().stream()
                    .filter(category -> category.getName().equalsIgnoreCase(name))
                    .findFirst()
                    .map(this::copy);
        }

        @Override
        public Category save(Category category) throws SQLException {
            writeCount++;
            if (nextSaveFailure != null) {
                SQLException failure = nextSaveFailure;
                nextSaveFailure = null;
                throw failure;
            }
            if (findByName(category.getName()).isPresent()) {
                throw new SQLException("Duplicate category name.", "23000", 1062);
            }
            category.setId(nextId++);
            categories.put(category.getId(), copy(category));
            return category;
        }

        @Override
        public boolean update(Category category) throws SQLException {
            updateCallCount++;
            writeCount++;
            if (nextUpdateFailure != null) {
                SQLException failure = nextUpdateFailure;
                nextUpdateFailure = null;
                throw failure;
            }
            if (category.getId() == deleteBeforeNextUpdateId) {
                categories.remove(category.getId());
                deleteBeforeNextUpdateId = 0;
                return false;
            }
            if (!categories.containsKey(category.getId())) {
                return false;
            }
            Optional<Category> duplicate = findByName(category.getName());
            if (duplicate.isPresent() && duplicate.get().getId() != category.getId()) {
                throw new SQLException("Duplicate category name.", "23000", 1062);
            }
            categories.put(category.getId(), copy(category));
            return true;
        }

        @Override
        public boolean deleteById(long id) throws SQLException {
            writeCount++;
            if (id == referencedCategoryId) {
                throw new SQLException("Referenced row.", "23000", 1451);
            }
            return categories.remove(id) != null;
        }

        private Category copy(Category source) {
            return new Category(source.getId(), source.getName());
        }
    }
}
