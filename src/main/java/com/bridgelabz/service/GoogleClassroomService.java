package com.bridgelabz.service;

import com.bridgelabz.csvUtiles.CSVWriterUtil;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.AbstractMap.SimpleEntry;
import java.util.logging.Logger;

@Service
public class GoogleClassroomService
{
    @Autowired
    private CourseService courseService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private CourseworkService courseworkService;

    @Autowired
    private SubmissionService submissionService;

    @Autowired
    private GoogleSheetsDriveService sheetExporterService;

    private static final String DRIVE_FOLDER_ID = "1jx6CcqvLxpfpuoS_3VZNlF1npRkOhBRc";
    private static final Logger logger = Logger.getLogger("GoogleClassroomService");

    public List<String> exportAllCoursesData(String accessToken) throws Exception {
        List<String> savedSheetsUrls = new ArrayList<>();

        Map<String, Map<String, Map<String, SimpleEntry<String, String>>>> batchStudentDataMap = new TreeMap<>();
        Map<String, Map<String, List<String>>> batchCourseworkMap = new TreeMap<>();

        List<JsonNode> courses = courseService.getAllCourses(accessToken);

        for (JsonNode course : courses) {
            String courseId = course.path("id").asText();
            String courseName = course.path("name").asText().replaceAll("[^a-zA-Z0-9]", "_");
            String batchKey = getBatchKeyFromCourseName(courseName);

            Map<String, Map<String, String>> studentsData = studentService.getStudents(courseId, accessToken);
            Map<String, String> courseworkMap = courseworkService.getCourseworkTitles(courseId, accessToken);
            List<String> courseworkTitles = new ArrayList<>(courseworkMap.values());

            submissionService.getSubmissions(courseId, accessToken, courseworkMap, studentsData);

            for (Map<String, String> student : studentsData.values()) {
                for (String title : courseworkTitles) {
                    student.putIfAbsent(title, "Missing");
                }
            }

            CSVWriterUtil.writeCSV(courseName, studentsData, courseworkTitles);

            Map<String, SimpleEntry<String, String>> convertedStudentData = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, String>> entry : studentsData.entrySet()) {
                Map<String, String> studentMap = entry.getValue();
                String studentName = studentMap.getOrDefault("name", entry.getKey());
                String email = studentMap.getOrDefault("email", "");

                StringBuilder rowBuilder = new StringBuilder();
                for (String title : courseworkTitles) {
                    rowBuilder.append(",").append(studentMap.getOrDefault(title, "Missing"));
                }

                convertedStudentData.put(studentName, new SimpleEntry<>(email, rowBuilder.substring(1)));
            }

            batchStudentDataMap
                    .computeIfAbsent(batchKey, k -> new LinkedHashMap<>())
                    .put(courseName, convertedStudentData);

            batchCourseworkMap
                    .computeIfAbsent(batchKey, k -> new LinkedHashMap<>())
                    .put(courseName, courseworkTitles);
        }

        for (String batch : batchStudentDataMap.keySet()) {
            sheetExporterService.resetTotals();

            GoogleSheetsDriveService.SpreadsheetResult result =
                    sheetExporterService.exportToGoogleSheetForBatch(
                            batch,
                            batchStudentDataMap.get(batch),
                            batchCourseworkMap.get(batch),
                            accessToken,
                            DRIVE_FOLDER_ID
                    );

            savedSheetsUrls.add(result.getUrl());
            logger.info("Sheet created for batch " + batch + ": " + result.getUrl());
        }

        return savedSheetsUrls;
    }
    private String getBatchKeyFromCourseName(String courseName)
    {
        if (courseName.matches(".*B1P\\d+.*")) return "B1";
        if (courseName.matches(".*B2P\\d+.*")) return "B2";
        if (courseName.matches(".*B3P\\d+.*")) return "B3";
        if (courseName.matches(".*B4P\\d+.*")) return "B4";
        return "Other_Batches";

    }
}
