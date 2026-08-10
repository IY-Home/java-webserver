package com.youfuns.webserver;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import com.youfuns.logger.SimpleLogger;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.*;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.function.Consumer;

public class WebServerSecure extends WebServer {
    private boolean initialized = false;

    public WebServerSecure(int port) {
        super(port);
    }

    public WebServerSecure(int port, SimpleLogger logger) {
        super(port, logger);
    }

    public WebServerSecure(InetSocketAddress address) {
        super(address);
    }

    public WebServerSecure(InetSocketAddress address, SimpleLogger logger) {
        super(address, logger);
    }

    public static void generateSelfSigned(String alias, String keystorePath, String password, String dname) {
        // === VALIDATE INPUTS ===

        // 1. Validate alias: alphanumeric, underscore, hyphen only
        if (!alias.matches("^[a-zA-Z0-9_\\-]+$")) {
            throw new IllegalArgumentException("Invalid alias. Use only letters, numbers, underscore, and hyphen.");
        }

        // 2. Validate keystorePath: safe characters, no command injection, no traversal
        if (!keystorePath.matches("^[a-zA-Z0-9_\\-./]+$") || keystorePath.contains("../")) {
            throw new IllegalArgumentException("Invalid keystore path. Use only letters, numbers, underscore, hyphen, dot, and slash.");
        }

        // 3. Validate password: safe characters, no spaces, no special shell chars
        if (!password.matches("^[a-zA-Z0-9_\\-!@#%^&*()+=]+$")) {
            throw new IllegalArgumentException("Invalid password. Use only alphanumeric and common safe symbols.");
        }

        // 4. Validate distinguished name format: exactly "CN=..., OU=..., O=..., L=..., ST=..., C=..."
        if (!dname.matches("^CN=[a-zA-Z0-9_.\\-]+,\\s*OU=[a-zA-Z0-9_.\\-]+,\\s*O=[a-zA-Z0-9_.\\-]+,\\s*L=[a-zA-Z0-9_.\\-]+,\\s*ST=[a-zA-Z0-9_.\\-]+,\\s*C=[A-Z]{2}$")) {
            throw new IllegalArgumentException("Invalid distinguished name. Format: CN=..., OU=..., O=..., L=..., ST=..., C=XX (where XX is a 2-letter country code)");
        }

        // 5. Blacklist dangerous shell characters (extra safety)
        String[] inputs = {alias, keystorePath, password, dname};
        for (String input : inputs) {
            if (input.matches(".*[;&|`$(){}<>].*")) {
                throw new IllegalArgumentException("Input contains forbidden shell characters: " + input);
            }
        }

        File keystoreFile = new File(keystorePath);
        File parentDir = keystoreFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            if (!parentDir.mkdirs()) {
                throw new RuntimeException("Failed to create directory: " + parentDir);
            }
            System.out.println("Created directory: " + parentDir);
        }

        deleteAlias(keystorePath, alias, password);

        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-keystore", keystorePath,
                "-storetype", "PKCS12",
                "-alias", alias,
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "365",
                "-storepass", password,
                "-keypass", password,
                "-dname", dname
        );

        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                    System.out.println("KEYTOOL: " + line);  // Print in real-time
                }
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                System.err.println("Keytool error output:");
                System.err.println(output.toString());
                throw new RuntimeException("keytool failed with code: " + exitCode + "\nOutput: " + output);
            }


        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("keytool failed", e);
        }
    }

    public static void deleteAlias(String keystorePath, String alias, String password) {
        ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-delete",
                "-keystore", keystorePath,
                "-storetype", "PKCS12",
                "-alias", alias,
                "-storepass", password
        );

        try {
            Process process = pb.start();
            process.waitFor();
        } catch (IOException | InterruptedException _) {
        }
    }

    @Override
    protected HttpServer createServer(InetSocketAddress address, int backlog) {
        try {
            return HttpsServer.create(address, 0);
        } catch (IOException e) {
            logger.log(WebServer.class, "HttpServer start failed. Encountered " + e.getClass().getSimpleName() + ": " + e.getMessage(), SimpleLogger.Level.ERROR);
            throw new RuntimeException("Failed to create HttpServer", e);
        }
    }

    public WebServerSecure setupHttps(String keystorePassword, String keystorePath, Consumer<HttpsParameters> configurations) {
        try {
            // Load keystore (contains your certificate)
            char[] keystorePass = keystorePassword.toCharArray();
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(new FileInputStream(keystorePath), keystorePass);

            // Initialize KeyManagerFactory
            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(keystore, keystorePass);

            // Create SSLContext
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(kmf.getKeyManagers(), null, null);

            // Apply to server
            ((HttpsServer) server).setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    // Optional: configure SSL parameters
                    configurations.accept(params);
                }
            });
        } catch (Exception e) {
            logger.log(this.getClass(), "Encountered " + e.getClass().getSimpleName() + " while setting up HTTPS Keystore: " + e.getMessage(), SimpleLogger.Level.ERROR);
            throw new RuntimeException("Failed to configure HttpsServer", e);
        }
        logger.log(this.getClass(), "Https set up successfully", SimpleLogger.Level.INFO);
        initialized = true;
        return this;
    }

    public WebServerSecure setupHttps(String keystorePassword, String keystorePath) {
        return setupHttps(keystorePassword, keystorePath, params -> {
        });
    }

    @Override
    public WebServer start() {
        if (!initialized) {
            logger.log(this.getClass(), "HTTPS not initialized", SimpleLogger.Level.ERROR);
            throw new IllegalStateException("HTTPS not initialized");
        }
        return (WebServer) super.start();
    }
}
