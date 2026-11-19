package com.springaimld;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Scanner;

/** Menu-driven JDBC console application for managing ecommerce orders. */
public class OrderManagementApp {
    private static final String URL = "jdbc:mysql://localhost:3306/ecommerce_db?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "root";
    private static final String[] VALID_STATUSES = {"Pending", "Shipped", "Delivered", "Cancelled"};

    public static void main(String[] args) {
        try (Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
             Scanner scanner = new Scanner(System.in)) {
            createTableIfNeeded(connection);
            System.out.println("Connected to ecommerce_db.\n");
            if (isTableEmpty(connection)) {
                System.out.print("No orders found. Load 3 sample orders now? (y/N): ");
                if (scanner.nextLine().trim().equalsIgnoreCase("y")) insertSamples(connection);
            }
            runMenu(connection, scanner);
        } catch (SQLException e) {
            System.err.println("Database connection/setup failed: " + e.getMessage());
            System.err.println("Check that MySQL is running and root/root credentials are correct.");
        }
    }

    private static void createTableIfNeeded(Connection connection) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS Orders (" +
                "order_id INT PRIMARY KEY, customer_name VARCHAR(100), product_name VARCHAR(100), " +
                "quantity INT, unit_price DOUBLE, status VARCHAR(30))";
        try (Statement statement = connection.createStatement()) { statement.executeUpdate(sql); }
    }

    private static boolean isTableEmpty(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM Orders")) {
            return result.next() && result.getInt(1) == 0;
        }
    }

    private static void runMenu(Connection connection, Scanner scanner) {
        boolean running = true;
        while (running) {
            System.out.println("\n========== E-Commerce Order Management ==========");
            System.out.println("1. Insert order");
            System.out.println("2. Filter orders by status (quantity > 2)");
            System.out.println("3. Increase order quantity");
            System.out.println("4. Update order status");
            System.out.println("5. Show total order value and delete cancelled order");
            System.out.println("6. Exit");
            int choice = readInt(scanner, "Choose an option: ");
            try {
                switch (choice) {
                    case 1 -> insertOrder(connection, scanner);
                    case 2 -> filterOrders(connection, scanner);
                    case 3 -> increaseQuantity(connection, scanner);
                    case 4 -> updateStatus(connection, scanner);
                    case 5 -> totalAndDelete(connection, scanner);
                    case 6 -> { running = false; System.out.println("Goodbye."); }
                    default -> System.out.println("Choose a number from 1 to 6.");
                }
            } catch (SQLException e) {
                System.out.println("Database operation failed: " + e.getMessage());
            }
        }
    }

    private static void insertOrder(Connection connection, Scanner scanner) throws SQLException {
        Order order = promptOrder(scanner);
        insertOrder(connection, order);
    }

    private static Order promptOrder(Scanner scanner) {
        int id = readInt(scanner, "Order ID: ");
        String customer = readText(scanner, "Customer name: ");
        String product = readText(scanner, "Product name: ");
        int quantity = readPositiveInt(scanner, "Quantity (> 0): ");
        double price = readPositiveDouble(scanner, "Unit price (> 0): ");
        String status = promptStatus(scanner);
        return new Order(id, customer, product, quantity, price, status);
    }

    private static void insertOrder(Connection connection, Order order) throws SQLException {
        String sql = "INSERT INTO Orders (order_id, customer_name, product_name, quantity, unit_price, status) VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindOrder(statement, order);
            statement.executeUpdate();
            System.out.println("Order inserted successfully: " + order);
        } catch (SQLIntegrityConstraintViolationException e) {
            System.out.println("Order ID " + order.getOrderId() + " already exists. Choose a unique order ID.");
        }
    }

    private static void insertSamples(Connection connection) throws SQLException {
        Order[] samples = {
                new Order(1001, "Aarav Sharma", "Wireless Mouse", 3, 799.00, "Pending"),
                new Order(1002, "Maya Patel", "USB-C Hub", 2, 1499.00, "Shipped"),
                new Order(1003, "Rohan Das", "Mechanical Keyboard", 4, 3299.00, "Cancelled")
        };
        for (Order order : samples) insertOrder(connection, order);
    }

    private static void bindOrder(PreparedStatement statement, Order order) throws SQLException {
        statement.setInt(1, order.getOrderId());
        statement.setString(2, order.getCustomerName());
        statement.setString(3, order.getProductName());
        statement.setInt(4, order.getQuantity());
        statement.setDouble(5, order.getUnitPrice());
        statement.setString(6, order.getStatus());
    }

    private static void filterOrders(Connection connection, Scanner scanner) throws SQLException {
        String status = readText(scanner, "Status to search for: ");
        String sql = "SELECT order_id, customer_name, product_name, quantity, unit_price, status FROM Orders WHERE status = ? AND quantity > 2 ORDER BY unit_price DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            try (ResultSet results = statement.executeQuery()) {
                int count = 0;
                while (results.next()) { System.out.println(toOrder(results)); count++; }
                if (count == 0) System.out.println("No matching orders found.");
            }
        }
    }

    private static void increaseQuantity(Connection connection, Scanner scanner) throws SQLException {
        int id = readInt(scanner, "Order ID: ");
        int additional = readPositiveInt(scanner, "Additional quantity (> 0): ");
        String sql = "UPDATE Orders SET quantity = quantity + ? WHERE order_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, additional);
            statement.setInt(2, id);
            if (statement.executeUpdate() == 0) System.out.println("No order exists with ID " + id + ".");
            else System.out.println("Quantity increased successfully for order " + id + ".");
        }
    }

    private static void updateStatus(Connection connection, Scanner scanner) throws SQLException {
        int id = readInt(scanner, "Order ID: ");
        String status = promptStatus(scanner);
        try (PreparedStatement statement = connection.prepareStatement("UPDATE Orders SET status = ? WHERE order_id = ?")) {
            statement.setString(1, status);
            statement.setInt(2, id);
            if (statement.executeUpdate() == 0) {
                System.out.println("No order exists with ID " + id + ".");
                return;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT order_id, customer_name, product_name, quantity, unit_price, status FROM Orders WHERE order_id = ?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) System.out.println("Updated order: " + toOrder(result));
            }
        }
    }

    private static void totalAndDelete(Connection connection, Scanner scanner) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT SUM(quantity * unit_price) FROM Orders")) {
            result.next();
            double total = result.getDouble(1);
            System.out.printf(Locale.US, "Total value of all orders: $%,.2f%n", total);
        }
        int id = readInt(scanner, "Order ID to delete (only Cancelled orders can be deleted): ");
        String sql = "DELETE FROM Orders WHERE order_id = ? AND status = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, id);
            statement.setString(2, "Cancelled");
            if (statement.executeUpdate() > 0) {
                System.out.println("Cancelled order " + id + " deleted.");
                return;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT status FROM Orders WHERE order_id = ?")) {
            statement.setInt(1, id);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) System.out.println("Order " + id + " does not exist; nothing was deleted.");
                else System.out.println("Order " + id + " has status '" + result.getString("status") + "'; only Cancelled orders can be deleted.");
            }
        }
    }

    private static Order toOrder(ResultSet result) throws SQLException {
        return new Order(result.getInt("order_id"), result.getString("customer_name"),
                result.getString("product_name"), result.getInt("quantity"),
                result.getDouble("unit_price"), result.getString("status"));
    }

    private static String promptStatus(Scanner scanner) {
        while (true) {
            String input = readText(scanner, "Status (Pending, Shipped, Delivered, Cancelled): ");
            for (String valid : VALID_STATUSES) if (valid.equalsIgnoreCase(input)) return valid;
            System.out.println("Invalid status. Enter Pending, Shipped, Delivered, or Cancelled.");
        }
    }

    private static String readText(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This value cannot be empty.");
        }
    }

    private static int readInt(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try { return Integer.parseInt(scanner.nextLine().trim()); }
            catch (NumberFormatException e) { System.out.println("Enter a valid whole number."); }
        }
    }

    private static int readPositiveInt(Scanner scanner, String prompt) {
        while (true) {
            int value = readInt(scanner, prompt);
            if (value > 0) return value;
            System.out.println("Value must be greater than zero.");
        }
    }

    private static double readPositiveDouble(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                double value = Double.parseDouble(scanner.nextLine().trim());
                if (Double.isFinite(value) && value > 0) return value;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a valid amount greater than zero.");
        }
    }
}
