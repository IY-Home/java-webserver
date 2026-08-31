# WebServer Framework Guide

## Overview

A simple, lightweight web server framework built on Java's built-in HttpServer *(or any common web
server)* with a fluent API for defining routes,
handling requests, and serving static files.

***Note:** This framework is very simple and lacks production features, such as rate limiting or authentication. It is
intended for prototyping only. Please do not use it in production applications.*

## Adding to project

Clone the source code to your project (no prebuilt `JAR`s are provided). Run the Maven project from the `pom.xml`.

Then import the WebServer and interfaces:

```java
import com.youfuns.webserver.*;
import com.youfuns.webserver.interfaces.*;
```


## Basic Server

```java
// Helper function
WebServer.create(8080)
        .start();
// Note: the WebServer uses a Builder pattern
WebServer.builder().port(8080).build()
        .start();
```

### Using custom `SimpleLogger`

```java
WebServer.builder().port(8080).logger(new ConsoleLogger(System.out)).build()
        .start();
```

### Using custom `InetSocketAddress`

```java
import java.net.InetSocketAddress;

// Bind ONLY to localhost (127.0.0.1)
InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
        var server = WebServer.builder().port(address).build();
// Note: use 'var' during assignments of WebServer, as the WebServer class has variable generic parameters,
// WebServer<InternalServer, InternalExchange, InternalHandler>
```

### Builder

```java
import com.youfuns.webserver.servers.WebServerType;

var server = WebServer.builder() // get builder
        .port(8080) // int port, or
        .port(new InetSocketAddress("127.0.0.1", 8080)) // InetSocketAddress
        .logger(new ConsoleLogger(System.out)) // SimpleLogger
        .server(WebServerType.SUN_NET_HTTPSERVER) // WebServerType, or
        .server(new NetHttpServer()) // WebServerInterface
        .build() // Returns a WebServer<?, ?, ?>
```

Logger (default `ConsoleLogger`) and server (default `WebServerType.SUN_NET_HTTPSERVER`) are optional, but port is mandatory.
If not set, `build()` throws `IllegalStateException`.

### Server operations

```java
server.start(); // starts the server
server.stop(); // stops the server
server.stop(10); // stops the server with up to 10 seconds to wait until exchanges have finished
server.restart(); // restarts the server
```

## `SimpleLogger` class

This framework includes a basic logger through the `SimpleLogger` interface:

```java
package com.youfuns.logger;

public interface SimpleLogger {
    enum Level {
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    void log(Class<?> clazz, String message, Level level);

    void log(Class<?> clazz, String message, Level level, Throwable t);

    Level getLogLevel();

    void setLogLevel(Level logLevel);
}
```

To use it, import `com.youfuns.logger.*`.

The default implementation is `ConsoleLogger`, which prints to any `java.io.PrintStream`, by default `System.out`.

```java
// Log to console
ConsoleLogger logger = new ConsoleLogger(System.out);

// Log to a file
ConsoleLogger fileLogger = new ConsoleLogger(new java.io.PrintStream("./logs/app.log"));

// Only for ConsoleLogger:

// Set config
logger
    .setShowTimestamp(true)
    .setShowClass(true)
    .setShowLevel(true)
    .setPrefix("> ")
    .setLogLevel(Level.DEBUG)
    .setOutputOn(true);

// Convenience logging methods
logger.debug(this.getClass(), "Debugging");
logger.info(this.getClass(), "Info");
// same for warn and error
```

To get the provided singleton logger as a SimpleLogger, call

```java
com.youfuns.logger.LoggerManager.INSTANCE.getLogger()
```

and to log quickly with the `DEBUG` level, call

```java
com.youfuns.logger.LoggerManager.quickLog(Object caller_to_get_class, String message)
```

## `Exchange` class

The `Exchange<InternalExchange>` class is a wrapper around
`HttpExchange` (or any internal exchange class) that provides various utility methods.
You typically receive it in the `FunctionalInterfaces` for your endpoints, heads/tails, and exception handlers.

To create an `Exchange` manually, and leave the generic parameter for internal Exchange classes as blank, use this constructor:

```java
Exchange<IExchange> exchange = new Exchange(String method, URI requestUri, String protocol, InetSocketAddress remoteAddress,
        Map <String, List <String>> requestHeaderMap, SimpleLogger logger, String body, ExchangeHandlerInterface <IExchange> exchangeInterface_nullable, IExchange wrappedExchange_nullable)
```

and put `Object` as the `IExchange`.
However, with this mock instance, parsing multipart, serving file, and sending response will throw
`UnsupportedOperationException`.

The `Exchange` also lets you store attributes for use between heads, tails, and handlers. Simply use:

```java
exchange.setAttribute("key", value_of_any_type);
exchange.getAttribute("key", Type_Of_Value.class); // returns null if not instance
exchange.getAttribute("key", String.class, "default");
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

### Multiple Methods

```java
.on("/hello", new String[]{"GET", "POST"}, exchange -> {
    exchange.sendResponse("GET or POST received");
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
// on endpoint "/", serve files from "./public", with directory listing true
```

Only serving a single file:

```java
.serveFile("/login", "./login.html")

// or directly from the exchange

.on("/login", exchange -> exchange.serveFile("./login.html"))
```

Serving a file from resource (/src/main/resources):

```java
.serveFileResource("/login","./login.html") // serving /src/main/resources/login.html
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

***Note:** This feature may not be supported by some web servers. If this feature is unsupported, an `UnsupportedOperationException` is thrown.
The default `com.sun.net.HttpServer` supports this feature.
If you need this feature on an unsupported web server, you are recommended to use dynamic endpoints (those with `$` parameters).*

```java
var myServer = WebServer.create(8080);
myServer.start();
if ("hello".equals("hello")) {
    myServer.on("/hello", "Hello");
}
myServer.on("/change", exchange -> {
    myServer.on("/change", "New one");
    exchange.sendResponse("Updated");
})
.on("/setLogo", (exchange) -> {
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

### Helper function to save files

There is a function in Exchange to simplify saving uploaded files:

```java
short getAndSaveAt(String filename, String[] extensions, String savePath) throws IOException
```

Returns:

- `-1` if request is not `multipart/form-data`
- `-2` if the file was not found
- `-3` if the extension does not match the provided extensions (for accepting all extensions, provide this array:
  `["all"]`)
- `-4` if path traversal was detected (if using getAndSaveAt with savePath and not Consumer)
- `0` if the file was successfully saved

Note that savePath is the exact file path, including file name. If you want to do custom operations on a successfully
fetched file instead of saving it, pass:

```java
getAndSaveAt(String filename, String[] extensions, FileAction<UploadedFile> fileAction) 
```

It still returns the codes, but instead of saveFileAt it performs your action.
Note that `FileAction` is a `FunctionalInterface` that declares throwing `IOException` so you don't have to catch it.
The action is only executed if the validation is passed (code = 1).


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

## Multiple Heads/Tails (Request Interceptors)

### Pre-Request Head

```java
.head(exchange -> {
    System.out.println("Request: " + exchange.getRequestPath());
    return true; // Continue processing
})
// Start timing in head
.head(exchange -> {
    exchange.setAttribute("startTime", System.nanoTime());
    return true;
})
.head(exchange -> {
    String jwt = exchange.getBearerToken();
    return jwtService.

authenticate(jwt); // if false, do not run the main endpoint. Stops subsequent heads too. Tails are unaffected.
})
```

### Post-Request Tail

```java
.tail(exchange -> {
    System.out.println("Request completed: " + exchange.getRequestPath());
})
// End timing in tail
.tail(exchange -> {
    Long startTime = exchange.getAttribute("startTime", Long.class);
    if (startTime != null) {
        long duration = (System.nanoTime() - startTime) / 1_000_000; // milliseconds
        System.out.println("Request to " + exchange.getRequestPath() + 
                            " took " + duration + "ms");
    }
})
```

***Note:** You can have multiple head and tails, and they run at the order that they are added.
Only heads return boolean.
Even if the main handler was not run due to a head returning false,
tails will still run, allowing you to close database connections or log timing.*

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

### Helper format functions

```java
exchange.formatHTML(); // equivalent to `addResponseHeader("Content-Type", "text/html; charset=UTF-8");`
exchange.formatJSON();
exchange.formatXML();
exchange.formatPlainText();
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
String path1 = exchange.saveFileIn(file, "./uploads");          // Keep original name
String path2 = exchange.saveFileAt(file, "./uploads/photo.jpg"); // Specific location
String path3 = exchange.saveFileSafe(file, "./uploads", true);
// With duplicate handling.
// Returned path will be "" if file is null.
```

#### Note on saveFileSafe
`(UploadedFile uploadedFile, String filePath, boolean preserveOriginalName): String savedFilePath`

If preserveOriginalName is false, it generates a random UUID for filename. If preserveOriginalName is true,
it saves with original filename, and if duplicate, saves as `filename_X.extension` where X is the incremented file number.

***Note:** The saveFile functions include prevention against common path traversal attacks (e.g. `../../../../etc/passwd`) using the Java `Paths`. 
However, it does NOT prevent absolute paths (e.g. intentionally opening uploads to `C:\`). You are recommended only to save uploaded files in project directories, such as `./public/uploads`.*

### Check File Types

```java
exchange.isPNG(file)
exchange.isJPEG(file)
exchange.isPDF(file)
exchange.isExtension(file, "jpg")
```

### `UploadedFile`

```java
UploadedFile file = exchange.getFile("file");
String fieldName = file.getFieldName();
String filename = file.getFilename();
String contentType = file.getContentType();
String extension = file.getExtension();
byte[] data = file.getData();
long size = file.getSize();
boolean empty = file.isEmpty();
```

### Create directory if not existent

```java
yourWebServer.ensureExists("./myDir");
// or static method (without logging)
WebServer.createIfNotExists("./myDir");
```

Throws `RuntimeException("The directory could not be created: {directory}", IOException)` if directory creation failed.

## HTML Templating

***Note:** This feature is very basic and for convenience only. Using Thymeleaf for complex templating is recommended.*

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

## HTTPS Support *(Not available)*

***Note:** Following our refactoring of WebServer to be generic, HTTPS support, which was bound to `com.sun.net.HttpsServer`, was temporarily removed.
If you need it, please manually implement it or pull from commit `ab0fbbd4`, the last commit before refactoring.
Below is the documentation for the previous version.*

---

***Note:** This feature is very basic and for development or prototyping only. Do not use this in commercial HTTPS
servers.*

### Basic HTTPS Server

```java
import com.youfuns.webserver.WebServerSecure;

// Generate a self-signed certificate (for development)
WebServerSecure.generateSelfSigned("myapp", "./https/keystore.p12", "changeit", "CN=localhost, OU=Dev, O=MyCompany, L=NYC, ST=NY, C=US");

// Create and start HTTPS server
new WebServerSecure(8443)
    .setupHttps("changeit", "./https/keystore.p12")
    .on("/status", "Running with HTTPS")
    .start();
```

### HTTPS with Custom SSL Configuration

```java
import com.sun.net.httpserver.HttpsParameters;

new WebServerSecure(8443)
    .setupHttps("changeit", "./https/keystore.p12", params -> {
        // Custom SSL parameters (optional)
        params.setNeedClientAuth(false);
        params.setWantClientAuth(false);
    })
    .on("/secure", exchange -> {
        exchange.sendResponse("Secure connection established");
    })
    .start();
```

### Generating Certificates Manually

***Note:**  Self-signed certificates should not be used in production. Currently, this framework does not support ACME.*

```java
// Generate a self-signed certificate programmatically
WebServerSecure.generateSelfSigned(
    "myapp",                              // alias
    "./https/keystore.p12",               // keystore path
    "changeit",                           // password
    "CN=myapp.com, OU=Dev, O=MyCompany, L=NYC, ST=NY, C=US"  // distinguished name
);

// Delete an existing alias (if regenerating)
WebServerSecure.deleteAlias("./https/keystore.p12", "myapp", "changeit");
```

### Using a Custom Keystore

```java
// Use an existing PKCS12 keystore (e.g., from Let's Encrypt or a CA)
new WebServerSecure(443)
    .setupHttps("your_password", "/path/to/your/keystore.p12")
    .on("/", exchange -> exchange.sendResponse("Using real HTTPS certificate"))
    .start();
```

---

## JWT utility

`com.youfuns.webserver.JwtService` provides a basic convenient way to generate and validate JWTs (JSON Web Tokens):

```java
import com.youfuns.webserver.JwtService;

JwtService.setSecretKey(String key);
JwtService.setExpiration(long seconds);
JwtService.generateToken(String subject);

boolean isValid = JwtService.validateToken(String token);
String subject = JwtService.extractSubject(String token); // null if invalid
```

## Complete Example

```java
import com.youfuns.webserver.WebServer;

public class Main {
    public static void main(String[] args) throws Exception {
        var myServer = WebServer.create(8080);
        myServer
                .on("/api/users", "GET", exchange -> {
                    exchange.sendJsonResponse(Map.of("users", "list"));
                })
                .on("/api/users/$", (params, exchange) -> {
                    String id = params[0];
                    exchange.sendResponse("User: " + id);
                })
                .on("/api/user/update", "POST", exchange -> {
                    int result = exchange.getAndSaveAt("file", new String[]{"png", "jpg", "jpeg"}, "./uploads/");

                    switch (result) {
                        case -1 -> exchange.sendBadRequestResponse("Expected multipart/form-data");
                        case -2 -> exchange.sendBadRequestResponse("No file uploaded");
                        case -3 -> exchange.sendBadRequestResponse("Only PNG and JPEG allowed");
                        case 0 -> exchange.sendResponse("Uploaded successfully");
                    }
                })
                .on("/api/setConfig", "POST", exchange -> {
                    int result = exchange.getAndSaveAt("config", new String[]{"json"}, file -> {
                        String savedPath = exchange.saveFileSafe(file, "./config", false);
                        myServer.serveFile("/config", savedPath);
                    });

                    switch (result) {
                        case -1 -> exchange.sendBadRequestResponse("Expected multipart/form-data");
                        case -2 -> exchange.sendBadRequestResponse("No config uploaded");
                        case -3 -> exchange.sendBadRequestResponse("Only JSON files allowed");
                        0 ->exchange.sendResponse("Uploaded successfully!");
                    }
                })
                .serveStatic("/", "./public", false, "index.html")
                .onNotFound(exchange -> {
                    exchange.sendResponse(404, "Not Found: " + exchange.getRequestPath());
                })
                .head(exchange -> {
                    System.out.println("Request: " + exchange.getRequestPath());
                    exchange.setAttribute("startTime", System.nanoTime());
                    return true;
                })
                .head(exchange -> {
                    if (exchange.getRequestPath().startsWith("/api")) {
                        String subject = JwtService.extractSubject(exchange.getBearerToken());
                        return (subject != null); // demo
                    }
                    return true;
                })
                .tail(exchange -> {
                    Long startTime = exchange.getAttribute("startTime", Long.class);
                    if (startTime != null) {
                        long duration = (System.nanoTime() - startTime) / 1_000_000; // Convert to milliseconds
                        System.out.println("Request to " + exchange.getRequestPath() + " took " + duration + "ms");
                    }
                })
                .onException((exchange, exception) -> {
                    LoggerManager.quickLog("Caught " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
                    if (exception instanceof IllegalArgumentException) {
                        exchange.sendBadRequestResponse("Bad request: " + exception.getMessage());
                    } else {
                        exchange.sendErrorResponse("An error occurred.");
                    }
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

## Other demonstrations

See `com.youfuns.webserver.demo` for full demonstrations:

- `Basic` for a basic server
- `FileUploadTest` for a file upload demo
- ***(Not available currently)*** `HttpsTest` for HTTPS
- `UserProfileServer` for a full user registration and admin system with JWT
- `Proxy` for an advanced proxy program
- ***(New)*** `TestKotlin` for a Kotlin use of the framework


## Using other web servers

Currently, the framework natively supports `com.sun.net.HttpServer`,
but if you want to plug in your own web server, e.g. Undertow,

please reference `com.youfuns.webserver.servers` and implement the `WebServerInterface<Server, InternalExchange, InternalHandler>` interface.

Then, in the builder, pass
```java
.server(WebServerInterface<?, ?, ?> serverInterface)
```
e.g.
```java
var myUndertowServer = WebServer.builder().port(8080).server(new UndertowServer()).build();
```
