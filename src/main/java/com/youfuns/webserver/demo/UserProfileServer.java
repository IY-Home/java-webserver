package com.youfuns.webserver.demo;

import com.youfuns.logger.ConsoleLogger;
import com.youfuns.logger.LoggerManager;
import com.youfuns.logger.SimpleLogger;
import com.youfuns.webserver.JwtService;
import com.youfuns.webserver.TemplateEngine;
import com.youfuns.webserver.WebServer;
import com.youfuns.webserver.interfaces.Exchange;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public class UserProfileServer {

    // In-memory user database — in a true application, you would save to a database
    private static final List<User> users = new CopyOnWriteArrayList<>();
    private static final String UPLOAD_DIR = "./userServiceDemo/user_avatars";

    // Pre-made admin user
    // Note: this demo stores passwords in plain text. In a true application, passwords must be hashed.
    private static final User ADMIN_USER = new User(
            "Admin",
            "admin@system.com",
            "admin",
            "admin",
            null
    );

    public static void main(String[] args) {
        // Initialize JWT
        JwtService.setSecretKey("your-secret-key-change-this-in-production"); // In a true application, get this from environment variables
        JwtService.setExpiration(3600); // 1 hour

        // Add admin user to the list
        users.add(ADMIN_USER);

        ConsoleLogger logger = new ConsoleLogger();
        logger.setLogLevel(SimpleLogger.Level.DEBUG);

        var server = WebServer.create(8080, logger);

        server.ensureExists(UPLOAD_DIR)
        .on("/", exchange ->
                        exchange.redirect("/login"))
        // Login page
        .on("/login", exchange -> {
            TemplateEngine engine = TemplateEngine.fromFile("./userServiceDemo/templates/login.html");
            String error = exchange.getQueryParameter("error", null);
            if (error != null) {
                engine.replace("error", error).replace("show_error", "show");
                engine.replace("show_error_display", "block");
            } else {
                engine.replace("show_error", "");
                engine.replace("show_error_display", "none");
            }
            exchange.formatHTML();
            exchange.send(engine.getTemplate());
        })

        // Handle login
        .on("/login", "POST", exchange -> {
            String email = exchange.getFormField("email");
            String password = exchange.getFormField("password");

            if (email == null || email.trim().isEmpty() ||
                    password == null || password.trim().isEmpty()) {
                exchange.redirect("/login?error=Username+and+password+required");
                return;
            }

            // Find user by email (name field)
            User user = findUserByEmail(email);

            if (user == null) {
                exchange.redirect("/login?error=Invalid+credentials");
                return;
            }

            // Check password
            if (!user.getPassword().equals(password)) {
                exchange.redirect("/login?error=Invalid+credentials");
                return;
            }

            // Generate JWT token
            String token = JwtService.generateToken(email);

            // Set cookie with JWT
            exchange.addCookie("jwt", token, 3600); // 1 hour

            // Redirect to admin page
            exchange.redirect("/admin");
        })

        // Logout
        .on("/logout", exchange -> {
            exchange.removeCookie("jwt");
            exchange.redirect("/login");
        })

        // Home page - User registration form
        .on("/register", exchange -> {
            TemplateEngine engine = TemplateEngine.fromFile("./userServiceDemo/templates/register.html");
            exchange.formatHTML();
            exchange.send(engine.getTemplate());
        })

        // Handle form submission
        .on("/register", "POST", exchange -> {
            if (!exchange.isMultipartRequest()) {
                exchange.sendBadRequest("Expected multipart/form-data");
                return;
            }

            // Get form fields
            String name = exchange.getFormField("name");
            String email = exchange.getFormField("email");
            String phone = exchange.getFormField("phone");
            String password = exchange.getFormField("password");
            boolean isAdmin = exchange.getFormField("admin") != null;

            // Validate required fields
            if (name == null || name.trim().isEmpty() ||
                    email == null || email.trim().isEmpty() ||
                    phone == null || phone.trim().isEmpty() ||
                    password == null || password.trim().isEmpty()) {
                exchange.sendBadRequest("All fields are required");
                return;
            }

            // Check if username already exists
            if (findUserByEmail(name) != null) {
                exchange.sendBadRequest("Username already exists");
                return;
            }

            // Handle avatar upload
            AtomicReference<String> avatarPath = new AtomicReference<>();
            if (exchange.hasFile("avatar")) {
                int result = exchange.getAndSaveAt(
                        "avatar",
                        new String[]{"png", "jpg", "jpeg", "gif"},
                        file -> {
                            // Generate unique filename
                            avatarPath.set(exchange.saveFileSafe(file, UPLOAD_DIR, false));
                        }
                );

                if (result != 0) {
                    String error = switch (result) {
                        case -1 -> "Not multipart request";
                        case -2 -> "No avatar file uploaded";
                        case -3 -> "Only PNG, JPG, JPEG, GIF allowed";
                        case -4 -> "Invalid file name detected";
                        default -> "Upload failed";
                    };
                    exchange.redirect("/login?error=" + Exchange.urlEncode(error) + " — register again");
                    return;
                }
            }

            // Create and save user
            User user = new User(name, email, phone, password, avatarPath.get(), isAdmin);
            users.add(user);

            exchange.redirect("/login");
        })

        // Admin page - Show all users (protected - admin only)
        .on("/admin", exchange -> {
            // Check authentication
            String token = exchange.getCookie("jwt");
            if (token == null || !JwtService.validateToken(token)) {
                exchange.redirect("/login");
                return;
            }

            // Check if user is admin
            String username = JwtService.extractSubject(token);
            User currentUser = findUserByEmail(username);

            if (currentUser == null || !currentUser.isAdmin()) {
                exchange.redirect("/login?error=User+is+not+admin");
                return;
            }

            TemplateEngine engine = TemplateEngine.fromFile("./userServiceDemo/templates/admin.html");

            // Build user list HTML
            StringBuilder userListHtml = new StringBuilder();

            if (users.isEmpty()) {
                userListHtml.append("<p class='text-muted'>No users registered yet.</p>");
            } else {
                userListHtml.append("<div class='row'>");
                for (User user : users) {
                    userListHtml.append(generateUserCard(user));
                }
                userListHtml.append("</div>");
            }

            // Get success message from query parameter
            String successMsg = exchange.getQueryParameter("success", "");

            engine.replace("users", userListHtml.toString())
                    .replace("count", String.valueOf(users.size()))
                    .replace("admin_name", currentUser.getName());

            exchange.formatHTML();
            exchange.send(engine.getTemplate());
        })

        // Serve uploaded avatars
        .serveStatic("/avatars", UPLOAD_DIR)

        // 404 handler
        .onNotFound(exchange -> {
            exchange.redirect("/login?error=" + Exchange.urlEncode("Page not found: " + exchange.getRequestPath()));
        })

        // Exception handler
        .onException((exchange, exception) -> {
            LoggerManager.INSTANCE.getLogger().log(UserProfileServer.class, "An exception was encountered: ", SimpleLogger.Level.ERROR, exception);
            exchange.redirect("/login?error=" + Exchange.urlEncode("An error occurred: " + exception.getMessage()));
        })

                // Start server
        .start();
        System.out.println("Server running at http://localhost:8080");
        System.out.println("Login: http://localhost:8080/login");
        System.out.println("Register: http://localhost:8080/register");
        System.out.println("Admin: http://localhost:8080/admin (admin only)");
        System.out.println("Admin credentials: email=admin@system.com, password=admin");
    }

    private static String generateUserCard(User user) {
        String avatarImg = user.getAvatarPath() != null ?
                "/avatars/" + user.getAvatarPath().substring(user.getAvatarPath().lastIndexOf('/') + 1) :
                "/avatars/default.png";

        return String.format("""
                        <div class="col-md-4 mb-4">
                            <div class="card">
                                <img src="%s" class="card-img-top" alt="%s's avatar" style="height: 200px; object-fit: cover;">
                                <div class="card-body">
                                    <h5 class="card-title">%s</h5>
                                    <p class="card-text">
                                        <strong>Email:</strong> %s<br>
                                        <strong>Phone:</strong> %s
                                    </p>
                                </div>
                            </div>
                        </div>
                        """,
                avatarImg,
                user.getName(),
                user.getName(),
                user.getEmail(),
                user.getPhone()
        );
    }

    private static User findUserByEmail(String email) {
        for (User user : users) {
            if (user.getEmail().equalsIgnoreCase(email)) {
                return user;
            }
        }
        return null;
    }

    // User model
    static class User {
        private final String name;
        private final String email;
        private final String phone;
        private final String password;
        private final String avatarPath;
        private final boolean isAdmin;

        public User(String name, String email, String phone, String password, String avatarPath) {
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.password = password;
            this.avatarPath = avatarPath;
            this.isAdmin = true;
        }

        private User(String name, String email, String phone, String password, String avatarPath, boolean isAdmin) {
            this.name = name;
            this.email = email;
            this.phone = phone;
            this.password = password;
            this.avatarPath = avatarPath;
            this.isAdmin = isAdmin;
        }

        public String getName() {
            return name;
        }

        public String getEmail() {
            return email;
        }

        public String getPhone() {
            return phone;
        }

        public String getPassword() {
            return password;
        }

        public String getAvatarPath() {
            return avatarPath;
        }

        public boolean isAdmin() {
            return isAdmin;
        }
    }
}