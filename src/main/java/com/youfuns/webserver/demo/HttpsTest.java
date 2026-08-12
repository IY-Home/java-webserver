package com.youfuns.webserver.demo;

import com.youfuns.logger.LoggerManager;
import com.youfuns.webserver.WebServerSecure;

public class HttpsTest {
    public static void main(String[] args) {
        WebServerSecure.generateSelfSigned("test", "./https/keystore.p12", "changeit", "CN=myapp.com, OU=Dev, O=MyCompany, L=NYC, ST=NY, C=US");
        new WebServerSecure(8443, LoggerManager.INSTANCE.getLogger())
                .setupHttps("changeit", "./https/keystore.p12")
                .on("/status", "Running with HTTPS")
                .start();
    }

}
