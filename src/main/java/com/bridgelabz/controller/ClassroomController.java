package com.bridgelabz.controller;

import com.bridgelabz.service.GoogleClassroomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClassroomController {

    @Autowired
    private GoogleClassroomService classroomService;

    @Autowired
    private OAuth2AuthorizedClientService clientService;

    @GetMapping("/classroom/all-student-submissions")
    public ResponseEntity<?> getAllCourseWiseCSVs(OAuth2AuthenticationToken authToken) {
        OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
                authToken.getAuthorizedClientRegistrationId(),
                authToken.getName());

        if (client == null || client.getAccessToken() == null) {
            return ResponseEntity.status(401).body("No valid access token found.");
        }

        try {
            classroomService.exportAllCoursesData(client.getAccessToken().getTokenValue());
            return ResponseEntity.ok("All Sheets exported successfully!"); // Koti
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error: " + e.getMessage());
        }

    }
}


//package com.bridgelabz.controller;
//
//import com.bridgelabz.service.GoogleClassroomService;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
//import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
//import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.List;
//
//@RestController
//@RequestMapping("/classroom")
//public class ClassroomController {
//
//    @Autowired
//    private GoogleClassroomService classroomService;
//
//    @Autowired
//    private OAuth2AuthorizedClientService clientService;
//
//    private String getAccessToken(OAuth2AuthenticationToken authToken) {
//        OAuth2AuthorizedClient client = clientService.loadAuthorizedClient(
//                authToken.getAuthorizedClientRegistrationId(),
//                authToken.getName());
//
//        if (client == null || client.getAccessToken() == null) {
//            throw new RuntimeException("No valid access token found.");
//        }
//        return client.getAccessToken().getTokenValue();
//    }
//
//    @GetMapping("/all-student-submissions")
//    public ResponseEntity<?> exportAllBatches(OAuth2AuthenticationToken authToken) {
//        try {
//            String token = getAccessToken(authToken);
//            List<String> urls = classroomService.exportCoursesDataByBatch(token, null);
//            return ResponseEntity.ok("Sheets exported successfully! URLs: " + urls);
//        } catch (Exception e)
//        {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body("Error: " + e.getMessage());
//        }
//    }
//
//
//    @GetMapping("/b1-submissions")
//    public ResponseEntity<?> exportB1Batch(OAuth2AuthenticationToken authToken) {
//        return exportBatch(authToken, "B1");
//    }
//
//    @GetMapping("/b2-submissions")
//    public ResponseEntity<?> exportB2Batch(OAuth2AuthenticationToken authToken) {
//        return exportBatch(authToken, "B2");
//    }
//
//    @GetMapping("/b3-submissions")
//    public ResponseEntity<?> exportB3Batch(OAuth2AuthenticationToken authToken) {
//        return exportBatch(authToken, "B3");
//    }
//
//    @GetMapping("/b4-submissions")
//    public ResponseEntity<?> exportB4Batch(OAuth2AuthenticationToken authToken) {
//        return exportBatch(authToken, "B4");
//    }
//
//    private ResponseEntity<?> exportBatch(OAuth2AuthenticationToken authToken, String batch) {
//        try {
//            String token = getAccessToken(authToken);
//            List<String> urls = classroomService.exportCoursesDataByBatch(token, batch);
//            if (urls.isEmpty()) {
//                return ResponseEntity.status(404).body("No data found for batch: " + batch);
//            }
//            return ResponseEntity.ok("Sheets exported successfully for " + batch + "! URLs: " + urls);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return ResponseEntity.status(500).body("Error: " + e.getMessage());
//        }
//    }
//}
