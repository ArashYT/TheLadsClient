package com.thelads.core.client.auth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class MicrosoftAuthenticator {
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE = "service::user.auth.xboxlive.com::MBI_SSL";
    private final HttpClient client = HttpClient.newHttpClient();

    public static class DeviceCodeInfo {
        public String userCode;
        public String verificationUri;
        public String deviceCode;
        public int interval;
        public String message;
    }

    public CompletableFuture<DeviceCodeInfo> requestDeviceCode() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String body = "client_id=" + CLIENT_ID + "&scope=" + SCOPE;
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                DeviceCodeInfo info = new DeviceCodeInfo();
                info.userCode = json.get("user_code").getAsString();
                info.deviceCode = json.get("device_code").getAsString();
                info.verificationUri = json.get("verification_uri").getAsString();
                info.interval = json.get("interval").getAsInt();
                info.message = json.get("message").getAsString();
                return info;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<String> pollForToken(DeviceCodeInfo info, Consumer<String> statusCallback) {
        return CompletableFuture.supplyAsync(() -> {
            String body = "client_id=" + CLIENT_ID + "&grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code=" + info.deviceCode;
            while (true) {
                try {
                    Thread.sleep(info.interval * 1000L);
                    HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create("https://login.microsoftonline.com/consumers/oauth2/v2.0/token"))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                    HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                    JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                    if (json.has("access_token")) {
                        return json.get("access_token").getAsString();
                    } else if (json.has("error")) {
                        String error = json.get("error").getAsString();
                        if (error.equals("authorization_pending")) {
                            statusCallback.accept("Waiting for you to sign in...");
                            continue;
                        } else {
                            throw new RuntimeException("Auth failed: " + error);
                        }
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    public CompletableFuture<String> authXboxLive(String msToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject properties = new JsonObject();
                properties.addProperty("AuthMethod", "RPS");
                properties.addProperty("SiteName", "user.auth.xboxlive.com");
                properties.addProperty("RpsTicket", "d=" + msToken);
                
                JsonObject body = new JsonObject();
                body.add("Properties", properties);
                body.addProperty("RelyingParty", "http://auth.xboxlive.com");
                body.addProperty("TokenType", "JWT");

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://user.auth.xboxlive.com/user/authenticate"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                JsonObject json = JsonParser.parseString(res.body()).getAsJsonObject();
                return json.get("Token").getAsString();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<JsonObject> authXsts(String xblToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject properties = new JsonObject();
                properties.addProperty("SandboxId", "RETAIL");
                JsonArray userTokens = new JsonArray();
                userTokens.add(xblToken);
                properties.add("UserTokens", userTokens);
                
                JsonObject body = new JsonObject();
                body.add("Properties", properties);
                body.addProperty("RelyingParty", "rp://api.minecraftservices.com/");
                body.addProperty("TokenType", "JWT");

                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://xsts.auth.xboxlive.com/xsts/authorize"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                return JsonParser.parseString(res.body()).getAsJsonObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<JsonObject> authMinecraft(String uhs, String xstsToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                JsonObject body = new JsonObject();
                body.addProperty("identityToken", "XBL3.0 x=" + uhs + ";" + xstsToken);
                
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.minecraftservices.com/authentication/login_with_xbox"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                return JsonParser.parseString(res.body()).getAsJsonObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public CompletableFuture<JsonObject> getMinecraftProfile(String mcToken) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.minecraftservices.com/minecraft/profile"))
                    .header("Authorization", "Bearer " + mcToken)
                    .GET()
                    .build();
                HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
                return JsonParser.parseString(res.body()).getAsJsonObject();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
}
