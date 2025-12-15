package net.clementlevallois.cowo.controller;

import jakarta.json.Json;
import jakarta.json.JsonObjectBuilder;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.openide.util.Exceptions;

public class JobNotifier {

    private final String callbackURL;
    private final String jobId;
    private static final HttpClient SHARED_CLIENT = HttpClient.newHttpClient();

    public JobNotifier(String callbackURL, String jobId) {
        this.callbackURL = callbackURL;
        this.jobId = jobId;
    }

    public void notify(String message, int progress) {
        if (callbackURL == null || callbackURL.isBlank()) {
            return;
        }
        
        JsonObjectBuilder joBuilder = Json.createObjectBuilder();
        joBuilder.add("info", "INTERMEDIARY");
        joBuilder.add("message", message);
        joBuilder.add("function", "cowo");
        joBuilder.add("progress", progress);
        joBuilder.add("jobId", jobId);
        
        send(joBuilder.build().toString());
    }

    private void send(String payload) {
        try {
            URI uri = new URI(callbackURL);
            HttpRequest.BodyPublisher bodyPublisher = HttpRequest.BodyPublishers.ofString(payload);
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(bodyPublisher)
                    .header("Content-Type", "application/json")
                    .uri(uri)
                    .build();

            SHARED_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (URISyntaxException | IOException | InterruptedException ex) {
            System.out.println("Callback failed: " + callbackURL);
            Exceptions.printStackTrace(ex);
        }
    }
}