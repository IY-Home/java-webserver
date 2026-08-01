# WebServer Framework Guide

## Overview

A lightweight web server framework built on Java's built-in HttpServer with a fluent API for defining routes, handling requests, and serving static files.

## Adding to Project

Add these dependencies to your `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>commons-fileupload</groupId>
        <artifactId>commons-fileupload</artifactId>
        <version>1.5</version>
    </dependency>
    <dependency>
        <groupId>commons-io</groupId>
        <artifactId>commons-io</artifactId>
        <version>2.15.1</version>
    </dependency>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.1</version>
    </dependency>
    <dependency>
        <groupId>javax.servlet</groupId>
        <artifactId>javax.servlet-api</artifactId>
        <version>4.0.1</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

Import the JAR file to your project, and add this dependency:
```xml
<dependency>
    <groupId>com.youfuns</groupId>
    <artifactId>webserver</artifactId>
    <version>1.0.0</version>
</dependency>
```

Then import the WebServer and interfaces:
```java
import com.youfuns.webserver.WebServer;
import com.youfuns.webserver.Exchange;
import com.youfuns.webserver.Exchange.UploadedFile; // for file uploading
```
or
```java
import com.youfuns.webserver.*;
```


## Basic Server

```java
new WebServer(8080)
    .start();
```
## Using custom `InetSocketAddress`

```java
import java.net.InetSocketAddress;

// Bind ONLY to localhost (127.0.0.1)
InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
WebServer myServer = new WebServer(address, your_logger);
```

## Defining Basic Endpoints

### GET Endpoint

```java
.on("/hello", "GET", exchange -> {
    exchange.sendResponse("Hello World");
})
```

### POST Endpoint

```java
.on("/hello", "POST", exchange -> {
    exchange.sendResponse("POST received");
})
```

### Default Handler (All Methods)

```java
.on("/hello", exchange -> {
    exchange.sendResponse("Default response");
})
```

### Multiple Methods on Same Path

```java
.on("/api/users", "GET", exchange -> {
    // List users
})
.on("/api/users", "POST", exchange -> {
    // Create user
})
.on("/api/users", exchange -> {
    // Default: Method Not Allowed
    exchange.sendMethodNotAllowedResponse("GET", "POST");
})
```

### Only return text

```java
.on("/status", "OK") // equivalent to .on("/status", exchange -> exchange.sendResponse("OK"))
```

## Dynamic Endpoints with Path Parameters

Use `$` as a placeholder and accept a String[]:

```java
.on("/users/$", (params, exchange) -> {
    String userId = params[0];
    exchange.sendResponse("User ID: " + userId);
})
```

Multiple parameters:

```java
.on("/users/$/posts/$", (params, exchange) -> {
    String userId = params[0];
    String postId = params[1];
    exchange.sendResponse("User: " + userId + ", Post: " + postId);
})
```

## Static File Serving

```java
.serveStatic("/static", "./public")
```

With index file and directory listing:

```java
.serveStatic("/", "./public", true, "index.html")
// on endpoint "/", serve files from "./public", with directory listing
```

Only serving a single file:

```java
.serveFile("/login", "./login.html")

// or directly from the exchange

.on("/login", exchange -> exchange.serveFile("./login.html"))
```

## 404 Handler

```java
.onNotFound(exchange -> {
    exchange.sendResponse(404, "Page not found");
})
```
or
```java
.onNotFoundServe("./public/404.html");
```

## Global Exception Handler

```java
.onException((exchange, exception) -> {
    LoggerManager.quickLog("Caught " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
    exchange.sendErrorResponse("An error occurred.");
})
```

## Adding, modifying, or destroying endpoints after WebServer start

```java
WebServer myServer = new WebServer(8080);
myServer.start();
if ("hello".equals("hello")) {
    myServer.on("/hello", "Hello");
}
myServer.on("/change", exchange -> {
    myServer.on("/change", "New one");
    exchange.sendResponse("Updated");
})
.on("/setLogo", (exchange) -> {
    LoggerManager.quickLog(exchange.getQueryParameters().get("path"));
    try {
        myServer.serveFile("/logo", exchange.getQueryParameter("path", "./logos/default.png"));
    } catch (IllegalArgumentException e) {
        exchange.sendBadRequestResponse("File was not found");
    }
    exchange.sendResponse("Updated successfully");
})
.on("/one_time", exchange -> {
    exchange.sendResponse("One time used!");
    myServer.removeEndpoint("/one_time");
})
.on("/stop", exchange -> {
    exchange.sendResponse("Server stopping...");
    myServer.stop();
});
```



## File Upload

### HTML Form

```html
<form action="/upload" method="post" enctype="multipart/form-data">
    <input type="text" name="username">
    <input type="file" name="file">
    <button type="submit">Upload</button>
</form>
```

### Server Handler Demo

```java
.on("/upload", "POST", exchange -> {
    if (!exchange.isMultipartRequest()) {
        exchange.sendBadRequestResponse("Expected multipart/form-data");
        return;
    }
    
    String username = exchange.getFormField("username");
    
    if (!exchange.hasFile("file")) {
        exchange.sendBadRequestResponse("No file uploaded");
        return;
    }
    
    UploadedFile file = exchange.getFile("file");
    
    // Check file type
    if (exchange.isPNG(file) || exchange.isJPEG(file)) {
        exchange.saveFileIn(file, "./uploads");
        // can also: exchange.saveFileAt(file, aSpecificFilePath)
        exchange.sendResponse("Uploaded: " + file.getFilename());
    } else {
        exchange.sendBadRequestResponse("Only PNG and JPEG allowed");
    }
})
```

### Limiting File Size

```java
webServer.limitUploadSize(int_size_in_bytes)
```

## HTML Form (URL-encoded) 

### HTML Form

```html
<form action="/login" method="post"> <!-- By default application/x-www-form-urlencoded -->
    <input type="text" name="username" placeholder="Username">
    <input type="password" name="password" placeholder="Password">
    <input type="email" name="email" placeholder="Email">
    <button type="submit">Login</button>
</form>
```

### Server Handler Demo

```java
.on("/login", "POST", exchange -> {
    if (!exchange.isFormUrlEncoded()) {
        exchange.sendBadRequestResponse("Expected application/x-www-form-urlencoded");
        return;
    }
    
    String username = exchange.getFormFieldFromUrlEncoded("username", "unknown");
    String password = exchange.getFormFieldFromUrlEncoded("password", "");
    String email = exchange.getFormFieldFromUrlEncoded("email", "");
    
    exchange.sendJsonResponse(Map.of(
        "message", "Login attempt",
        "username", username,
        "email", email,
        "password_length", password.length()
    ));
})
```

## Multiple Hooks/Tails (Request Interceptors)

### Pre-Request Hook

```java
.hook(exchange -> {
    System.out.println("Request: " + exchange.getRequestPath());
    return true; // Continue processing
})
// Start timing in hook
.hook(exchange -> {
    exchange.setAttribute("startTime", System.nanoTime());
    return true;
})
.hook(exchange -> {
    String jwt = exchange.getBearerToken();
    return jwtService.authenticate(jwt); // if false, do not run the main endpoint. Stops subsequent hooks too. Tails are unaffected.
})
```

### Post-Request Tail

```java
.tail(exchange -> {
    System.out.println("Request completed: " + exchange.getRequestPath());
    return true;
})
// End timing in tail
.tail(exchange -> {
    Long startTime = exchange.getAttribute("startTime", Long.class);
    if (startTime != null) {
        long duration = (System.nanoTime() - startTime) / 1_000_000; // milliseconds
        System.out.println("Request to " + exchange.getRequestPath() + 
                            " took " + duration + "ms");
    }
    return true;
})
```

***Note:** You can have multiple hook and tails, and they run at the order that they are added. Even if the main handler was not run due to a hook returning false, tails will still run, allowing you to close database connections or log timing. Tails also return a boolean to halt the execution of subsequent tails.

## Response Methods

### Text Response

```java
exchange.sendResponse("Hello World");
```

### JSON Response

```java
Map<String, Object> data = new HashMap<>();
data.put("message", "Hello");
data.put("status", "ok");
exchange.sendJsonResponse(data);
```

### Status Code with Body

```java
exchange.sendResponse(404, "Not Found");
```

### Common Responses

```java
exchange.sendNotFoundResponse();      // 404
exchange.sendBadRequestResponse("error");  // 400
exchange.sendUnauthorizedResponse();   // 401
exchange.sendForbiddenResponse();      // 403
exchange.sendCreatedResponse();        // 201
exchange.sendNoContentResponse();      // 204
```

### CORS

```java
exchange.enableCors();
exchange.enableCors("https://example.com");
```

## Headers

```java
exchange.addResponseHeader("X-Custom-Header", "value");
```

## URL Query Parameters

```java
String name = exchange.getQueryParameter("name");
String name = exchange.getQueryParameter("name", "default");
int age = exchange.getQueryParameterAsInt("age", 18); // also works for Long, Double, Boolean, returns primitive types
```

## JSON Body Parsing

```java
Map<String, Object> data = exchange.parseBodyAsJsonMap();
User user = exchange.parseBodyAsJson(User.class);
String name = exchange.getJsonParameter(String.class, "name", "Default name"); 
int age = exchange.getJsonParameterAsInt("age", 20); // also works for Long, Double, Boolean, and String, returns primitive types except for String
String password = exchange.getJsonUsername("defaultUsername"); // helper method for JSON login parameter, with getJsonName (looks for JSON parameter "name"), getJsonEmail ("email"), getJsonUsername ("username"), and getJsonPassword ("password")
```

## File Operations

### Save File

```java
exchange.saveFileIn(file, "./uploads");          // Keep original name
exchange.saveFileAt(file, "./uploads/photo.jpg"); // Specific location
String path = exchange.saveFileSafe(file, "./uploads", true); 
// With duplicate handling.
// (UploadedFile uploadedFile, String filePath, boolean preserveOriginalName) -> String savedFilePath
// If preserveOriginalName is false, it generates a random UUID for filename. If preserveOriginalName is true, it saves with original filename, and if duplicate, saves as filename_X.extension where X is the incremented file number.
```

### Check File Types

```java
exchange.isPNG(file)
exchange.isJPEG(file)
exchange.isPDF(file)
exchange.isExtension(file, "jpg")
```

## HTML Templating

***Note: This feature is very basic and for convenience only. Using Thymeleaf for complex templating is recommended.***

### HTML Template File Example

```html
<!DOCTYPE html>
<html>
<head>
    <title>{{title}}</title>
    <style>
        .greeting { color: {{user_color}}; font-size: 20px; }
    </style>
</head>
<body>
    <div class="greeting">
        <h1>Hello, {{name}}!</h1>
        <p>Today is {{date}}</p>
    </div>
</body>
</html>
```

### Usage Example
```java
.on("/page", "GET", exchange -> {
    TemplateEngine engine = TemplateEngine.fromFile("./templates/page.html");
    
    // Get query parameters
    String name = exchange.getQueryParameter("name", "Guest");
    String color = exchange.getQueryParameter("color", "blue");
    
    // Set template variables
    engine.replace("name", name)
          .replace("title", "User Page")
          .replace("user_color", color)
          .replace("date", LocalDateTime.now().toString());
    
    exchange.addResponseHeader("Content-Type", "text/html");
    exchange.sendResponse(engine.getTemplate());
})
```

### Result
```html
<!DOCTYPE html>
<html>
<head>
    <title>User Page</title>
    <style>
        .greeting { color: blue; font-size: 20px; }
    </style>
</head>
<body>
<div class="greeting">
    <h1>Hello, Guest!</h1>
    <p>Today is 2026-07-16T13:29:52.931374</p>
</div>
</body>
```

## Redirects

```java
.on("/login-old", exchange -> {
    exchange.redirectPermanent("/login");
})
.on("/login", exchange -> {
    exchange.redirect("/login-beta");
})
.on("/page/$", (params, exchange) -> {
    switch (params[0]) {
        case "login":
            exchange.redirectPermanent("/login");
            break;
        case "logout":
            exchange.redirectPermanent("/logout");
            break; 
        case "home":
            exchange.redirectPermanent("/homepage");
            break;
        default:
            exchange.redirectPermanent("/404");
            break;
    }
})
```

## Cookies

```java
// Simple cookie (HttpOnly, Path=/)
exchange.addCookie("jwt", token);

// Cookie with expiration (seconds)
exchange.addCookie("jwt", token, 3600);  // 1 hour

// Cookie with custom path
exchange.addCookie("jwt", token, 3600, "/api");

// Cookie with custom path and no expiration
exchange.addCookie("jwt", token, -1, "/");

// Get a specific cookie
String jwt = exchange.getCookie("jwt");

// Get with default value
String jwt = exchange.getCookie("jwt", "default");

// Check if cookie exists
if (exchange.hasCookie("jwt")) {
    // Cookie exists
}

// Get all cookies as a Map
Map<String, String> allCookies = exchange.getCookies();
String token = allCookies.get("token");

// Remove cookie (sets Max-Age=0)
exchange.removeCookie("jwt");

// Remove cookie with specific path
exchange.removeCookie("jwt", "/");
```


## Complete Example

```java
public class Main {
    public static void main(String[] args) throws Exception {
        WebServer myServer = new WebServer(8080);
        myServer
            .on("/api/users", "GET", exchange -> {
                exchange.sendJsonResponse(Map.of("users", "list"));
            })
            .on("/api/users/$", (params, exchange) -> {
                String id = params[0];
                exchange.sendResponse("User: " + id);
            })
            .on("/api/user/update", "POST", exchange -> {
                if (!exchange.isMultipartRequest()) {
                    exchange.sendBadRequestResponse("Expected multipart/form-data");
                    return;
                }
                
                String username = exchange.getFormField("username");
                
                if (!exchange.hasFile("file")) {
                    exchange.sendBadRequestResponse("No file uploaded");
                    return;
                }
                
                UploadedFile file = exchange.getFile("file");
                
                // Check file type
                if (exchange.isPNG(file) || exchange.isJPEG(file)) {
                    exchange.saveFileIn(file, "./uploads");
                    exchange.sendResponse("Uploaded: " + file.getFilename());
                } else {
                    exchange.sendBadRequestResponse("Only PNG and JPEG allowed");
                }
            })
            .on("/api/setConfig", "POST", exchange -> {
                if (!exchange.isMultipartRequest()) {
                    exchange.sendBadRequestResponse("Expected multipart/form-data");
                    return;
                }
                
                if (!exchange.hasFile("config")) {
                    exchange.sendBadRequestResponse("No logo uploaded");
                    return;
                }
                
                UploadedFile file = exchange.getFile("config");

                // Check file type
                if (exchange.isJSON(file)) {
                    exchange.saveFileSafe(file, "./config", false); // Don't overwrite previous configs for debug
                    exchange.sendResponse("Uploaded: " + file.getFilename());
                    myServer.serveFile("/config", "./config");

                } else {
                    exchange.sendBadRequestResponse("Only PNG and JPEG allowed");
                }
            })
            .serveStatic("/", "./public", false, "index.html")
            .onNotFound(exchange -> {
                exchange.sendResponse(404, "Not Found: " + exchange.getRequestPath());
            })
            .hook(exchange -> {
                System.out.println("Request: " + exchange.getRequestPath());
                return true;
            })
            .hook(exchange -> {
                if (exchange.getRequestPath().startsWith("/api")) {
                    return jwtService.authorize(exchange.getBearerToken());
                }
                return true;
            })
            .tail(exchange -> {
                System.out.println("Finished handling response");
                return true;
            })
            .onException((exchange, exception) -> {
                    LoggerManager.quickLog("Caught " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    exchange.sendErrorResponse("An error occurred.");
                })
            .limitUploadSize(50 * 1024 * 1024) // 50 MB
            .start();
    }
}
```

## Directory Structure

```
project/
├── src/
│   └── com/youfuns/webserver/
│       └── (framework files)
├── public/
│   └── index.html
├── uploads/
└── pom.xml
```

## Running

```bash
mvn compile
mvn exec:java -Dexec.mainClass="your.Main"
```

Or from IntelliJ: Run the Main class directly.
