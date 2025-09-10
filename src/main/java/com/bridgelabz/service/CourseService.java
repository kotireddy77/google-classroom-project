package com.bridgelabz.service;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.ArrayList;
import java.util.List;

@Service
public class CourseService {

    private final ObjectMapper mapper = new ObjectMapper();

    public List<JsonNode> getAllCourses(String token) throws Exception {
        WebClient client = buildClient(token);

        String coursesJson = client.get()
                .uri("/v1/courses")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        JsonNode root = mapper.readTree(coursesJson).path("courses");
        List<JsonNode> list = new ArrayList<>();
        root.forEach(list::add);
        return list;
    }

    private WebClient buildClient(String token) {
        return WebClient.builder()
                .baseUrl("https://classroom.googleapis.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .build();
    }
}
