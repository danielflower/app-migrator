package com.danielflower.apprunner.migrator;

import io.muserver.*;
import okhttp3.*;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static com.danielflower.apprunner.migrator.ClientUtils.call;
import static com.danielflower.apprunner.migrator.ClientUtils.request;
import static io.muserver.Mutils.urlEncode;
import static okhttp3.internal.Util.EMPTY_REQUEST;

class EmigrateHandler implements RouteHandler {
    private static final Logger log = LoggerFactory.getLogger(EmigrateHandler.class);
    private final OkHttpClient client;
    private final File tempDir;

    public EmigrateHandler(OkHttpClient client, File tempDir) {
        this.client = client;
        this.tempDir = tempDir;
    }

    @Override
    public void handle(MuRequest req, MuResponse resp, Map<String, String> pathParams) throws Exception {
        URI routerUrl = URI.create(req.formValue("routerUrl"));
        String appName = req.formValue("appName");
        String runnerID = req.formValue("currentRunnerID");

        resp.contentType("text/plain; charset=UTF-8");
        resp.sendChunk(spaces(512, '*') + "\n");
        resp.sendChunk("Going to move " + appName + " from the instance it is currently on to another one.\n");
        resp.sendChunk(spaces(512, '*') + "\n\n");
        // sending at least 1k helps ensure browsers start rendering immediately

        Boolean wasRunning = null;
        File appData = null;

        try {
            String getAppUrl = routerUrl.resolve("/api/v1/apps/") + urlEncode(appName);
            URI appUri = URI.create(getAppUrl + "/");

            write(resp, "Getting app info from " + getAppUrl);
            JSONObject appInfo;
            try (Response getResponse = call(request().url(getAppUrl))) {
                appInfo = new JSONObject(write(resp, getResponse));
            }
            wasRunning = appInfo.getString("availableStatus").equals("Running");


            if (wasRunning) {
                write(resp, "Stopping app...");
                try (Response stopResp = call(request().put(EMPTY_REQUEST).url(appUri.resolve("stop").toString()))) {
                    write(resp, stopResp);
                }
            } else {
                write(resp, "No need to stop app because it is not running");
            }

            appData = new File(tempDir, "app-data-" + UUID.randomUUID() + ".zip");
            write(resp, "Saving data to " + appData.getCanonicalPath());

            try (Response dataResp = call(request().url(appUri.resolve("data").toString()))) {
                ResponseBody fileBody = dataResp.body();
                if (fileBody == null || dataResp.code() != 200) {
                    resp.sendChunk("ERROR: The data for this app cannot be downloaded. Note that data downloading was introduced in app-runner 1.6.0.");
                    return;
                }
                try (FileOutputStream fos = new FileOutputStream(appData)) {
                    Mutils.copy(fileBody.byteStream(), fos, 8192);
                }
            }


            write(resp, "Deleting app from app runner");
            try (Response delResp = call(request().delete().url(getAppUrl))) {
                write(resp, delResp);
            }

            String gitUrl = appInfo.getString("gitUrl");
            write(resp, "Creating " + appName + " with URL " + gitUrl + " and asking to not use runner " + runnerID);
            try (Response createResp = call(
                request()
                    .url(routerUrl.resolve("/api/v1/apps").toString())
                    .post(
                        new FormBody.Builder()
                            .add("appName", appName)
                            .add("gitUrl", gitUrl)
                            .build())
                    .header("Content-Type", "application/json")
                    .header("X-Excluded-Runner", runnerID))) {
                write(resp, createResp);
            }

            write(resp, "Deleting data dir on target server in case it is there from a previous migration");
            try (Response delResp = call(request().delete().url(appUri.resolve("data").toString()))) {
                write(resp, delResp);
            }

            write(resp, "Posting app data which is " + appData.length() + " bytes");
            try (Response dataResp = call(request()
                .url(appUri.resolve("data").toString())
                .post(RequestBody.create(MediaType.parse("application/zip"), appData))
            )) {
                write(resp, dataResp);
            }



            write(resp, "Deploying app");
            try (Response deployResp = call(request().post(EMPTY_REQUEST).url(appUri.resolve("deploy").toString()));
                 Reader reader = deployResp.body().charStream()) {

                char[] buf = new char[128];
                int read;
                while ( (read = reader.read(buf)) > -1) {
                    resp.sendChunk(new String(buf, 0, read));
                }

            }

            write(resp, "****************************** EMIGRATION COMPLETE ******************************");

        } catch (Exception e) {
            String errorID = "ERR-" + UUID.randomUUID();
            log.error("Migration exception error id " + errorID, e);
            write(resp, "Exception while emigrating " + appName + " on " + routerUrl + " with error ID " + errorID + " - " + e.getMessage());

            if (wasRunning != null && wasRunning) {
                write(resp, "Note that app was stopped during the migration so you may wish to manually restart it.");
            }

            write(resp, "=========== ERROR ===========");
        } finally {
            if (appData != null && appData.isFile()) {
                boolean deleted = appData.delete();
                log.info("Result of deleting " + appData.getCanonicalPath() + " - " + deleted);
            }
        }
    }

    private String write(MuResponse muResponse, Response clientResp) throws IOException {
        write(muResponse, " >> Status code " + clientResp.code());
        ResponseBody responseBody = clientResp.body();
        String body = responseBody == null ? "" : responseBody.string();
        write(muResponse, " >> Body: " + body);
        return body;
    }

    private void write(MuResponse resp, String s) {
        log.info(s);
        String prefix = "[" + Instant.now() + "] ";
        String indentation = spaces(prefix.length(), ' ');
        resp.sendChunk(prefix + s.replace("\n", "\n" + indentation) + "\n");
    }

    private String spaces(int length, char c) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
