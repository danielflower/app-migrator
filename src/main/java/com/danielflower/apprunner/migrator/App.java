package com.danielflower.apprunner.migrator;

import io.muserver.*;
import io.muserver.handlers.ResourceHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Map;

import static io.muserver.ContextHandlerBuilder.context;
import static io.muserver.MuServerBuilder.httpServer;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        Map<String, String> settings = System.getenv();

        int port = Integer.parseInt(settings.getOrDefault("APP_PORT", "9494"));
        String appName = settings.getOrDefault("APP_NAME", "app-migrator");
        File tempDir = new File(settings.getOrDefault("TEMP", "target/temp"));
        log.info("Temp dir created at " + tempDir.getCanonicalPath() + " - " + tempDir.mkdirs());
        log.info("Starting " + appName + " on port " + port);


        MuServer server = httpServer()
            .withHttpPort(port)
            .addHandler((req, resp) -> {
                log.info("Got request: " + req);
                resp.headers().set(HeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
                return false;
            })
            .addHandler(
                context(appName)
                    .addHandler(ResourceHandler.fileOrClasspath("src/main/resources/web", "/web"))
                    .addHandler(Method.POST, "/api/emigrate", new EmigrateHandler(ClientUtils.client, tempDir))
            )
            .addShutdownHook(true)
            .start();

        log.info("Started server at " + server.uri().resolve("/" + appName));

    }

}
