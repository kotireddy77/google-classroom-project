//package com.bridgelabz.service;
//
//import com.fasterxml.jackson.databind.JsonNode;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.springframework.stereotype.Service;
//import org.springframework.web.reactive.function.client.WebClient;
//
//import java.util.LinkedHashMap;
//import java.util.Map;
//
//@Service
//public class CourseworkService {
//
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    public Map<String, String> getCourseworkTitles(String courseId, String token) throws Exception {
//        WebClient client = buildClient(token);
//        Map<String, String> map = new LinkedHashMap<>();
//        String pageToken = null;
//
//        do {
//            String uri = "/v1/courses/" + courseId + "/courseWork";
//            if (pageToken != null) uri += "?pageToken=" + pageToken;
//
//            String json = client.get().uri(uri).retrieve().bodyToMono(String.class).block();
//            JsonNode root = mapper.readTree(json);
//            pageToken = root.path("nextPageToken").asText(null);
//
//            for (JsonNode cw : root.path("courseWork")) {
//                String id = cw.path("id").asText();
//                String title = cw.path("title").asText().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
//                map.put(id, title + " (ID: " + id + ")");
//                System.out.println(" Coursework Titles Collected: " + title);
//            }
//        } while (pageToken != null);
//
//        return map;
//    }
//
//
//    private WebClient buildClient(String token) {
//        return WebClient.builder()
//                .baseUrl("https://classroom.googleapis.com")
//                .defaultHeader("Authorization", "Bearer " + token)
//                .build();
//    }
//}



package com.bridgelabz.service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class CourseworkService {

    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, String> getCourseworkTitles(String courseId, String token) throws Exception {
        WebClient client = buildClient(token);
        Map<String, String> map = new LinkedHashMap<>();
        String pageToken = null;

        do {
            String uri = "/v1/courses/" + courseId + "/courseWork";
            if (pageToken != null) uri += "?pageToken=" + pageToken;

            // Reactive call with retry, timeout and error handling
            String json = client.get()
                    .uri(uri)
                    .retrieve()
                    .bodyToMono(String.class)
                    .retryWhen(Retry.backoff(3, Duration.ofSeconds(2))
                            .filter(throwable -> throwable instanceof WebClientRequestException))
                    .onErrorResume(WebClientRequestException.class, e -> {
                        System.err.println("Request failed: " + e.getMessage());
                        // Return empty JSON to prevent breaking the loop or your app
                        return Mono.just("{\"courseWork\": []}");
                    })
                    .block();

            JsonNode root = mapper.readTree(json);
            pageToken = root.path("nextPageToken").asText(null);

            for (JsonNode cw : root.path("courseWork")) {
                String id = cw.path("id").asText();
                String title = cw.path("title").asText().replaceAll("[^a-zA-Z0-9_\\- ]", "_");
                map.put(id, title + " (ID: " + id + ")");
                System.out.println(" Coursework Titles Collected: " + title);
            }
        } while (pageToken != null);

        return map;
    }

    private WebClient buildClient(String token) {
        // Configure HttpClient with connect and response timeouts
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10000) // 10 seconds connection timeout
                .doOnConnected(conn ->
                        conn.addHandlerLast(new ReadTimeoutHandler(30, TimeUnit.SECONDS))  // 30 seconds read timeout
                                .addHandlerLast(new WriteTimeoutHandler(30, TimeUnit.SECONDS)) // 30 seconds write timeout
                );

        return WebClient.builder()
                .baseUrl("https://classroom.googleapis.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
