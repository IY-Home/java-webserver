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

Clone the source code to your project (no prebuilt `JAR`s are provided).

Then import the WebServer and interfaces:

```java
import com.youfuns.webserver.WebServer;
import com.youfuns.webserver.interfaces.*;
```


## Basic Server

```java
class DemoClass {
  void demo() {
    server.on("status", "OK");
  }
}
```

### Using custom `InetSocketAddress`

```java
import java.net.InetSocketAddress;

// Bind ONLY to localhost (127.0.0.1)
InetSocketAddress address = new InetSocketAddress("127.0.0.1", 8080);
WebServer myServer = new WebServer(address, your_logger);
```

### Server operations

```java
server.start(); // starts the server
server.stop(); // stops the server
server.stop(10); // stops the server with up to 10 seconds to wait until exchanges have finished
server.restart(); // restarts the server
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
    Logger.log("Caught " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
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

***Note:** You can have multiple hook and tails, and they run at the order that they are added.
Even if the main handler was not run due to a hook returning false,
tails will still run, allowing you to close database connections or log timing.
Tails also return a boolean to halt the execution of subsequent tails.*

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
- `1` if the file was successfully saved

Note that savePath is the exact file path, including file name. If you want to do custom operations on a successfully
fetched file instead of saving it, pass:

```java
getAndSaveAt(String filename, String[] extensions, FileAction<UploadedFile> fileAction) 
```

It still returns the codes, but instead of saveFileAt it performs your action.
Note that `FileAction` is a `FunctionalInterface` that declares throwing `IOException` so you don't have to catch it.
The action is only executed if the validation is passed (code = 1).

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

## HTTPS Support

***Note:** This feature is very basic and for development or prototyping only. Do not use this in commercial HTTPS
servers.*

### Basic HTTPS Server

```java
import com.youfuns.webserver.WebServerSecure;

// Generate a self-signed certificate (for development)
WebServerSecure.generateSelfSigned("myapp","./https/keystore.p12","changeit",
                                           "CN=localhost, OU=Dev, O=MyCompany, L=NYC, ST=NY, C=US");

// Create and start HTTPS server
new

WebServerSecure(8443)
    .

setupHttps("changeit","./https/keystore.p12")
    .

on("/status","Running with HTTPS")
    .

start();
```

### HTTPS with Custom SSL Configuration

```java
import com.sun.net.httpserver.HttpsParameters;

new WebServerSecure(8443)
    .

setupHttps("changeit","./https/keystore.p12",params ->{
        // Custom SSL parameters (optional)
        params.

setNeedClientAuth(false);
        params.

setWantClientAuth(false);
    })
            .

on("/secure",exchange ->{
        exchange.

sendResponse("Secure connection established!");
    })
            .

start();
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
WebServerSecure.

deleteAlias("./https/keystore.p12","myapp","changeit");
```

### Using a Custom Keystore

```java
// Use an existing PKCS12 keystore (e.g., from Let's Encrypt or a CA)
new WebServerSecure(443)
    .

setupHttps("your_password","/path/to/your/keystore.p12")
    .

on("/",exchange ->exchange.

sendResponse("Using real HTTPS certificate"))
        .

start();
```

## Adding endpoints from source file over-the-air

The framework provides a way to upload `.java` source code of classes that implement `ExchangeHandler` or
`DynamicExchangeHandler`.

***Warning:** This feature is **highly dangerous** as it allows arbitrary code execution and is designed ONLY for
hot-loading in development. Disable in production or allow only to authenticated users.
To disable, delete the `com.youfuns.webserver.hotloading` package from your project or simply do not import it.*

### Clarification

- `ExchangeHandler`: one abstract method, `void handle(Exchange exchange)`.
- `DynamicExchangeHandler`: one abstract method, `void handle(String[] urlParams, Exchange exchange)`.

### Method to call

Call the static method

```java
com.youfuns.webserver.hotloading.HotUtils.loadEndpoint(WebServer webServer, String filePath, String classFolder, String endpoint)
``` 

to load a class from source file and add it as an endpoint.

Returns a `com.youfuns.webserver.hotloading.Result` record:
`boolean success, short code, String message`.

### Return codes

In the `Result` record, the `code` field is the result code.
You are recommended to just deliver the formatted `message` field, but here are the codes for reference:

- `-1` if file is an invalid Java class
- `-2` if the Java compiler was not found (common with using raw JRE instead of JDK)
- `-3` if the Java compiler failed to compile the code
- `-4` if an IOException was encountered during compilation
- `-5` if an Exception was encountered during class loading
- `-6` if the loaded class is null
- `-7` if the class does not implement `ExchangeHandler` or `DynamicExchangeHandler`
- `-8` if `NoSuchMethodException | java.lang.InstantiationException | java.lang.IllegalAccessException |
  java.lang.reflect.InvocationTargetException | ClassCastException` was encountered during class instantiation
- `1` if successful

### Example

#### `./templates/index.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <title>File Upload</title>
  <style>
    body { font-family: sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; }
    .success { color: green; }
    .error { color: red; }
    .code-block { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; }
  </style>
</head>
<body>
<h2>Upload a Java Handler</h2>
<form method="POST" action="/upload-handler" enctype="multipart/form-data">
  <div style="margin-bottom: 10px;">
    <label for="endpoint">Endpoint Path (e.g., /api/hello):</label>
    <input type="text" name="endpoint" id="endpoint" placeholder="/api/hello" required
           style="width: 100%; padding: 8px;">
  </div>
  <div style="margin-bottom: 10px;">
    <label for="file">Java File (.java):</label>
    <input type="file" name="handler" id="file" accept=".java" required>
  </div>
  <button type="submit">Upload & Register</button>
</form>

<div id="result"></div>


<script>
  document.querySelector('form[action="/upload-handler"]').onsubmit = async (e) => {
      e.preventDefault();
      const form = e.target;
      const formData = new FormData(form);
      const resultDiv = document.getElementById('result');

      try {
          const res = await fetch(form.action, { method: 'POST', body: formData });
          const data = await res.json();

          if (Object.hasOwn(data, 'error')) {
              resultDiv.innerHTML = `<p class="error"> ${data.error}</p>`;
          }

          if (data.success) {
              resultDiv.innerHTML = `<p class="success"> ${data.message}</p>`;
          } else {
              resultDiv.innerHTML = `<p class="error"> ${data.message}</p>`;
          }
      } catch (err) {
          resultDiv.innerHTML = `<p class="error"> Error: ${err.message}</p>`;
      }
  };
</script>
</body>
</html>
```

#### `HotLoadingTest.java`

```java
import com.youfuns.webserver.WebServer;
import com.youfuns.webserver.hotloading.HotUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class HotLoadingTest {
  private static final String HANDLER_DIR = "./handlers";
  private static final String CLASSES_DIR = "./classes";

  static {
    try {
      Files.createDirectories(Paths.get(HANDLER_DIR));
      Files.createDirectories(Paths.get(CLASSES_DIR));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void main(String[] args) {
    WebServer webServer = new WebServer(8080);

    // ===== Homepage =====
    webServer.serveFile("/", "./templates/index.html")
            .on("/upload-handler", "POST", exchange -> {
              if (!exchange.isMultipartRequest()) {
                exchange.sendBadRequestResponse("Expected multipart/form-data");
                return;
              }

              // Get endpoint from form field
              String endpoint = exchange.getFormField("endpoint");
              if (endpoint == null || endpoint.trim().isEmpty()) {
                exchange.sendBadRequestResponse("Endpoint path is required");
                return;
              }
              endpoint = endpoint.trim();
              if (!endpoint.startsWith("/")) {
                endpoint = "/" + endpoint;
              }

              // Get the Java file
              if (!exchange.hasFile("handler")) {
                exchange.sendBadRequestResponse("No Java file uploaded");
                return;
              }

              String finalEndpoint = endpoint;
              short code = exchange.getAndSaveAt("handler", new String[]{"java"}, file -> {
                String sourcePath = exchange.saveFileIn(file, HANDLER_DIR);
                exchange.sendJsonResponse(HotUtils.loadEndpoint(webServer, sourcePath, CLASSES_DIR, finalEndpoint));
              });
              switch (code) {
                case -1 -> exchange.sendBadRequestResponse("Expected multipart/form-data");
                case -2 -> exchange.sendBadRequestResponse("No config uploaded");
                case -3 -> exchange.sendBadRequestResponse("Only JSON files allowed");
                case 1 ->
                        exchange.sendResponse("The file was uploaded successfully, but an unknown error prevented the endpoint registration.");
              }
            });
    webServer.start();
  }

}
```

#### `MyHandler.java` (uploaded)

```java
import com.youfuns.webserver.interfaces.Exchange;
import com.youfuns.webserver.interfaces.ExchangeHandler;

import java.io.IOException;
import java.util.Map;

public class MyHandler implements ExchangeHandler {
  @Override
  public void handle(Exchange exchange) throws IOException {
    String name = exchange.getQueryParameter("name", "World");
    exchange.sendJsonResponse(Map.of(
            "message", "Hello, " + name + "!",
            "timestamp", System.currentTimeMillis(),
            "from", "dynamic handler"
    ));
  }
}
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
              int result = exchange.getAndSaveAt("config", new String[]{"json"}, file -> {
                String savedPath = exchange.saveFileSafe(file, "./config", false);
                myServer.serveFile("/config", savedPath);
              });

              switch (result) {
                case -1 -> exchange.sendBadRequestResponse("Expected multipart/form-data");
                case -2 -> exchange.sendBadRequestResponse("No config uploaded");
                case -3 -> exchange.sendBadRequestResponse("Only JSON files allowed");
                case 1 -> exchange.sendResponse("Uploaded successfully!");
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
                Logger.log("Caught " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
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
