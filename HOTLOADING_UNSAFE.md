# [Unsafe] Adding endpoints from source files over-the-air

The framework provides a basic way to upload `.java` source code of classes that implement `ExchangeHandler` or
`DynamicExchangeHandler`.

***Warning:** This feature is **highly dangerous** as it allows arbitrary code execution and is designed ONLY for
hot-loading in development. This is just an **experiment** and **subject to change**.*

*If you do not trust this, do **not** use this feature at all, not even in development!*

*Disable in production or allow only to authenticated users.
To disable, set `enableHotLoadingUnsafe` to `false`, simply do not import it, or delete
the `com.youfuns.webserver.hotloading` package from your project.*

## Clarification

- `ExchangeHandler`: one abstract method, `void handle(Exchange exchange)`.
- `DynamicExchangeHandler`: one abstract method, `void handle(String[] urlParams, Exchange exchange)`.

## Usage

Enable hot-loading by importing the `HotUtils` class and enabling it:

```java
import com.youfuns.webserver.hotloading.HotUtils;
HotUtils.enableHotLoadingUnsafe(true); // to disable, call this with false
```

Call the static method

```java
HotUtils.loadEndpoint(WebServer webServer, String filePath, String classFolder, String endpoint)
``` 

to load a class from source file and add it as an endpoint.

Returns a `com.youfuns.webserver.hotloading.Result` record:
`boolean success, short code, String message`.

## Return codes

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
- `0` if successful

## Example

***Warning:** Do **NOT** copy and run the following code. They lack proper security and expose dangerous RCE
vulnerabilities. Use them for reference only.*

### `./templates/index.html`

```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>[DANGER] Add endpoint</title>
    <style>
        body { font-family: sans-serif; max-width: 800px; margin: 40px auto; padding: 20px; }
        .success { color: green; }
        .error { color: red; }
        .code-block { background: #f4f4f4; padding: 15px; border-radius: 5px; overflow-x: auto; }
    </style>
</head>
<body>
<h2>Upload a Java Handler</h2>
<h4>WARNING: If you are testing this code copied as-is, DELETE IT IMMEDIATELY!</h4>
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

### `HotLoadingTest.java`

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
        HotUtils.enableHotLoadingUnsafe(true);

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
                        case 0 ->
                                exchange.sendResponse("The file was uploaded successfully, but an unknown error prevented the endpoint registration.");
                    }
                });
        webServer.start();
    }

}
```

### `MyHandler.java` (uploaded)

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

***As said, this is a dangerous feature and should be used with utmost caution.***