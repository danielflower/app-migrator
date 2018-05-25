package com.danielflower.apprunner.migrator;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ClientUtils {

    public static final OkHttpClient client;
    public static X509TrustManager veryTrustingTrustManager = veryTrustingTrustManager();

    static {
        Logger.getLogger(OkHttpClient.class.getName()).setLevel(Level.FINE);
        client = newClient().build();
    }

    public static OkHttpClient.Builder newClient() {
        boolean isDebug = ManagementFactory.getRuntimeMXBean().getInputArguments().toString().contains("jdwp");
        return new OkHttpClient.Builder()
            .hostnameVerifier((hostname, session) -> true)
            .readTimeout(isDebug ? 120 : 10, TimeUnit.SECONDS)
            .sslSocketFactory(sslContextForTesting(veryTrustingTrustManager).getSocketFactory(), veryTrustingTrustManager);
    }



    public static Request.Builder request() {
        return new Request.Builder();
    }

    public static Response call(Request.Builder request) {
        try {
            return client.newCall(request.build()).execute();
        } catch (IOException e) {
            throw new RuntimeException("Error while calling " + request, e);
        }
    }

    public static SSLContext sslContextForTesting(TrustManager trustManager) {
        try {
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, new TrustManager[]{trustManager}, null);
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Cannot set up test SSLContext", e);
        }
    }

    public static X509TrustManager veryTrustingTrustManager() {
        return new X509TrustManager() {
            public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {
            }

            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                return new java.security.cert.X509Certificate[]{};
            }
        };
    }
}
