//package com.bridgelabz.service;
//import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
//import com.google.api.client.http.javanet.NetHttpTransport;
//import com.google.api.client.json.jackson2.JacksonFactory;
//import com.google.api.services.drive.Drive;
//import com.google.api.services.drive.model.FileList;
//import com.google.api.services.sheets.v4.Sheets;
//import com.google.api.services.sheets.v4.model.*;
//import org.springframework.stereotype.Service;
//
//import java.io.IOException;
//import java.security.GeneralSecurityException;
//import java.util.*;
//import java.util.logging.Logger;
//
//@Service
//public class GoogleSheetsDriveService {
//
//    private static final Logger logger = Logger.getLogger("GoogleSheetsDriveService");
//    private static final String APPLICATION_NAME = "Google Classroom CSV Exporter";
//    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
//
//    private int totalSubmitted = 0;
//    private int totalNotSubmitted = 0;
//
//    public void resetTotals() {
//        totalSubmitted = 0;
//        totalNotSubmitted = 0;
//    }
//
//    public int getTotalSubmitted() {
//        return totalSubmitted;
//    }
//
//    public int getTotalNotSubmitted() {
//        return totalNotSubmitted;
//    }
//
//    private String sanitizeSheetName(String sheetName) {
//        return sheetName.replaceAll("[^a-zA-Z0-9_]", "_");
//    }
//
//    private void updateSheetData(Sheets sheetsService, String spreadsheetId, String sheetName, String range, List<List<Object>> data) throws IOException {
//        ValueRange body = new ValueRange().setValues(data);
//        UpdateValuesResponse result = sheetsService.spreadsheets().values()
//                .update(spreadsheetId, sheetName + "!" + range, body)
//                .setValueInputOption("RAW")
//                .execute();
//        logger.info("Update result: " + result.getUpdatedCells() + " cells updated in sheet: " + sheetName);
//    }
//
//    public String exportToGoogleSheetForBatch(
//            String batchName,
//            Map<String, Map<String, AbstractMap.SimpleEntry<String, String>>> courseStudentData,
//            Map<String, List<String>> courseWorkTitles,
//            String accessToken,
//            String driveFolderId
//    ) throws IOException {
//        try {
//            // Reset totals here to avoid carryover between calls
//            resetTotals();
//
//            Sheets sheetsService = getSheetsService(accessToken);
//            Drive driveService = getDriveService(accessToken);
//
//            // ✅ 1. Check if spreadsheet already exists in the given folder
//            String query = String.format(
//                    "name='%s' and '%s' in parents and mimeType='application/vnd.google-apps.spreadsheet'",
//                    batchName, driveFolderId
//            );
//            FileList result = driveService.files().list().setQ(query).execute();
//
//            if (!result.getFiles().isEmpty()) {
//                String existingId = result.getFiles().get(0).getId();
//                logger.info("Spreadsheet already exists for batch: " + batchName + " (ID: " + existingId + ")");
//                return "https://docs.google.com/spreadsheets/d/" + existingId;
//            }
//
//            // ✅ 2. Only create if it doesn't exist
//            logger.info("Creating new spreadsheet with title: " + batchName);
//            Spreadsheet spreadsheet = new Spreadsheet()
//                    .setProperties(new SpreadsheetProperties().setTitle(batchName));
//            spreadsheet = sheetsService.spreadsheets().create(spreadsheet).execute();
//            String spreadsheetId = spreadsheet.getSpreadsheetId();
//            logger.info("Spreadsheet created with ID: " + spreadsheetId);
//
//            // 1. Create sheets for each course (lab)
//            List<Request> sheetRequests = new ArrayList<>();
//            Map<String, String> sanitizedNameMap = new HashMap<>();
//
//            for (String courseName : courseStudentData.keySet()) {
//                String sheetTitle = sanitizeSheetName(courseName);
//                if (sheetTitle.length() > 100) {
//                    sheetTitle = sheetTitle.substring(0, 100);
//                }
//                sanitizedNameMap.put(courseName, sheetTitle);
//
//                AddSheetRequest addSheetRequest = new AddSheetRequest()
//                        .setProperties(new SheetProperties().setTitle(sheetTitle));
//                sheetRequests.add(new Request().setAddSheet(addSheetRequest));
//            }
//
//            if (!sheetRequests.isEmpty()) {
//                BatchUpdateSpreadsheetRequest batchRequest = new BatchUpdateSpreadsheetRequest().setRequests(sheetRequests);
//                sheetsService.spreadsheets().batchUpdate(spreadsheetId, batchRequest).execute();
//                logger.info("Added sheets for each course.");
//            }
//
//            // 2. Prepare batch data updates
//            List<ValueRange> allUpdates = new ArrayList<>();
//
//            for (Map.Entry<String, Map<String, AbstractMap.SimpleEntry<String, String>>> courseEntry : courseStudentData.entrySet()) {
//                String courseName = courseEntry.getKey();
//                Map<String, AbstractMap.SimpleEntry<String, String>> studentRows = courseEntry.getValue();
//                List<String> courseworkTitles = courseWorkTitles.getOrDefault(courseName, Collections.emptyList());
//
//                String sheetTitle = sanitizedNameMap.get(courseName);
//                List<List<Object>> values = new ArrayList<>();
//
//                // Header row
//                List<Object> header = new ArrayList<>();
//                header.add("Name");
//                header.add("Email");
//                header.addAll(courseworkTitles);
//                header.add("Submissions");
//                header.add("Percentage");
//                values.add(header);
//
//                // Student rows
//                for (Map.Entry<String, AbstractMap.SimpleEntry<String, String>> studentEntry : studentRows.entrySet()) {
//                    String studentName = studentEntry.getKey();
//                    String email = studentEntry.getValue().getKey();
//                    String[] statusValues = studentEntry.getValue().getValue().split(",");
//
//                    List<Object> row = new ArrayList<>();
//                    row.add(studentName);
//                    row.add(email);
//
//                    int submissions = 0;
//                    for (String status : statusValues) {
//                        if ("submitted".equalsIgnoreCase(status.trim()) || "turned in".equalsIgnoreCase(status.trim())) {
//                            submissions++;
//                            totalSubmitted++;  // Counting total submissions here
//                        } else {
//                            totalNotSubmitted++;  // Counting total non-submissions here
//                        }
//                    }
//
//                    float percentage = ((float) submissions / statusValues.length) * 100;
//                    row.addAll(Arrays.asList(statusValues));
//                    row.add(submissions);
//                    row.add(String.format("%.2f%%", percentage));
//
//                    values.add(row);
//                }
//
//                // Add this sheet data to batch updates
//                ValueRange courseDataRange = new ValueRange()
//                        .setRange(sheetTitle + "!A1")
//                        .setValues(values);
//                allUpdates.add(courseDataRange);
//            }
//
//            // 3. Add Engagement Report sheet
//            String summarySheetName = "Engagement Report";
//            AddSheetRequest addSummarySheetRequest = new AddSheetRequest()
//                    .setProperties(new SheetProperties().setTitle(summarySheetName));
//            sheetsService.spreadsheets().batchUpdate(spreadsheetId,
//                    new BatchUpdateSpreadsheetRequest().setRequests(Collections.singletonList(
//                            new Request().setAddSheet(addSummarySheetRequest)
//                    ))
//            ).execute();
//            logger.info("Added Engagement Report sheet.");
//
//            // Build Engagement Report Data
//            List<List<Object>> labLevelData = new ArrayList<>();
//            labLevelData.add(Arrays.asList(
//                    "Lab Id", "Total", "# Submitted", "# Not Submitted",
//                    "Upto 25% Submission", "25% - 50% Submission",
//                    "50% - 75% Submission", "75% - 100% Submission"
//            ));
//
//            int batchTotal = 0;
//            int batchSubmitted = 0;
//            int batchNotSubmitted = 0;
//
//            for (Map.Entry<String, Map<String, AbstractMap.SimpleEntry<String, String>>> courseEntry : courseStudentData.entrySet()) {
//                String courseName = courseEntry.getKey();
//                Map<String, AbstractMap.SimpleEntry<String, String>> studentMap = courseEntry.getValue();
//
//                int total = studentMap.size();
//                int submittedCount = 0;
//                int notSubmittedCount = 0;
//                int upTo25 = 0, between25And50 = 0, between50And75 = 0, between75And100 = 0;
//
//                for (Map.Entry<String, AbstractMap.SimpleEntry<String, String>> studentEntry : studentMap.entrySet()) {
//                    String[] statuses = studentEntry.getValue().getValue().split(",");
//                    int individualSubmissions = 0;
//
//                    for (String status : statuses) {
//                        if ("submitted".equalsIgnoreCase(status.trim()) || "turned in".equalsIgnoreCase(status.trim())) {
//                            individualSubmissions++;
//                        }
//                    }
//
//                    float percent = ((float) individualSubmissions / statuses.length) * 100;
//
//                    if (percent <= 25) upTo25++;
//                    else if (percent <= 50) between25And50++;
//                    else if (percent <= 75) between50And75++;
//                    else between75And100++;
//
//                    if (individualSubmissions > 0) submittedCount++;
//                    else notSubmittedCount++;
//                }
//
//                batchTotal += total;
//                batchSubmitted += submittedCount;
//                batchNotSubmitted += notSubmittedCount;
//
//                labLevelData.add(Arrays.asList(
//                        courseName,
//                        total,
//                        submittedCount,
//                        notSubmittedCount,
//                        upTo25,
//                        between25And50,
//                        between50And75,
//                        between75And100
//                ));
//            }
//
//            // Lab-level table starting at A10
//            ValueRange labLevelDataRange = new ValueRange()
//                    .setRange(summarySheetName + "!A10")
//                    .setValues(labLevelData);
//            allUpdates.add(labLevelDataRange);
//
//            // Batch summary starting at K10
//            List<List<Object>> batchSummary = new ArrayList<>();
//            batchSummary.add(Arrays.asList("Batch #", "Total", "# Submitted", "# Not Submitted", "% Submission"));
//
//            float percent = batchTotal > 0 ? ((float) batchSubmitted * 100 / batchTotal) : 0;
//
//            batchSummary.add(Arrays.asList(
//                    "Batch " + batchName.replace("B", ""),
//                    batchTotal,
//                    batchSubmitted,
//                    batchNotSubmitted,
//                    String.format("%.2f%%", percent)
//            ));
//
//            ValueRange batchSummaryRange = new ValueRange()
//                    .setRange(summarySheetName + "!K10")
//                    .setValues(batchSummary);
//            allUpdates.add(batchSummaryRange);
//
//            // 4. Batch update all values in one API call
//            BatchUpdateValuesRequest batchRequestBody = new BatchUpdateValuesRequest()
//                    .setValueInputOption("RAW")
//                    .setData(allUpdates);
//
//            BatchUpdateValuesResponse batchResponse = sheetsService.spreadsheets().values()
//                    .batchUpdate(spreadsheetId, batchRequestBody)
//                    .execute();
//
//            logger.info("Batch update response: " + batchResponse.getTotalUpdatedCells() + " cells updated in total.");
//
//            // Move file to Drive folder
//            driveService.files().update(spreadsheetId, null)
//                    .setAddParents(driveFolderId)
//                    .setFields("id, parents")
//                    .execute();
//            logger.info("Moved spreadsheet to Drive folder: " + driveFolderId);
//
//            return "https://docs.google.com/spreadsheets/d/" + spreadsheetId;
//
//        } catch (GeneralSecurityException e) {
//            logger.severe("Failed to export sheet due to security exception: " + e.getMessage());
//            throw new RuntimeException("Failed to export sheet: " + e.getMessage(), e);
//        }
//    }
//
//    private Sheets getSheetsService(String accessToken) throws GeneralSecurityException, IOException {
//        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
//        return new Sheets.Builder(httpTransport, JSON_FACTORY, request -> {
//            request.getHeaders().setAuthorization("Bearer " + accessToken);
//        }).setApplicationName(APPLICATION_NAME).build();
//    }
//
//    private Drive getDriveService(String accessToken) throws GeneralSecurityException, IOException {
//        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
//        return new Drive.Builder(httpTransport, JSON_FACTORY, request -> {
//            request.getHeaders().setAuthorization("Bearer " + accessToken);
//        }).setApplicationName(APPLICATION_NAME).build();
//    }
//}
//





package com.bridgelabz.service;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.*;
import java.util.logging.Logger;

@Service
public class GoogleSheetsDriveService {

    private static final Logger logger = Logger.getLogger("GoogleSheetsDriveService");
    private static final String APPLICATION_NAME = "Google Classroom CSV Exporter";
    private static final JacksonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();

    private int totalSubmitted = 0;
    private int totalNotSubmitted = 0;

    /**
     * Wrapper for spreadsheet result
     */
    public static class SpreadsheetResult {
        private final String url;
        private final boolean created;

        public SpreadsheetResult(String url, boolean created) {
            this.url = url;
            this.created = created;
        }

        public String getUrl() {
            return url;
        }

        public boolean isCreated() {
            return created;
        }

        @Override
        public String toString() {
            return url;
        }
    }

    public void resetTotals() {
        totalSubmitted = 0;
        totalNotSubmitted = 0;
    }

    public int getTotalSubmitted() {
        return totalSubmitted;
    }

    public int getTotalNotSubmitted() {
        return totalNotSubmitted;
    }

    private String sanitizeSheetName(String sheetName) {
        return sheetName.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    public SpreadsheetResult exportToGoogleSheetForBatch(
            String batchName,
            Map<String, Map<String, AbstractMap.SimpleEntry<String, String>>> courseStudentData,
            Map<String, List<String>> courseWorkTitles,
            String accessToken,
            String driveFolderId
    ) throws IOException {
        try {
            resetTotals();

            Sheets sheetsService = getSheetsService(accessToken);
            Drive driveService = getDriveService(accessToken);

            // ✅ 1. Check if spreadsheet already exists
            String query = String.format(
                    "name='%s' and '%s' in parents and mimeType='application/vnd.google-apps.spreadsheet'",
                    batchName, driveFolderId
            );
            FileList result = driveService.files().list().setQ(query).execute();

            if (!result.getFiles().isEmpty()) {
                String existingId = result.getFiles().get(0).getId();
                logger.info("Spreadsheet already exists for batch: " + batchName + " (ID: " + existingId + ")");
                return new SpreadsheetResult("https://docs.google.com/spreadsheets/d/" + existingId, false);
            }

            // ✅ 2. Create new spreadsheet
            logger.info("Creating new spreadsheet with title: " + batchName);
            Spreadsheet spreadsheet = new Spreadsheet()
                    .setProperties(new SpreadsheetProperties().setTitle(batchName));
            spreadsheet = sheetsService.spreadsheets().create(spreadsheet).execute();
            String spreadsheetId = spreadsheet.getSpreadsheetId();
            logger.info("Spreadsheet created with ID: " + spreadsheetId);

            // ✅ 3. Add sheets for each course
            List<Request> sheetRequests = new ArrayList<>();
            Map<String, String> sanitizedNameMap = new HashMap<>();

            for (String courseName : courseStudentData.keySet()) {
                String sheetTitle = sanitizeSheetName(courseName);
                if (sheetTitle.length() > 100) {
                    sheetTitle = sheetTitle.substring(0, 100);
                }
                sanitizedNameMap.put(courseName, sheetTitle);

                sheetRequests.add(new Request().setAddSheet(
                        new AddSheetRequest().setProperties(new SheetProperties().setTitle(sheetTitle))
                ));
            }

            if (!sheetRequests.isEmpty()) {
                sheetsService.spreadsheets().batchUpdate(
                        spreadsheetId,
                        new BatchUpdateSpreadsheetRequest().setRequests(sheetRequests)
                ).execute();
            }

            // ✅ 4. Prepare batch updates (course data + engagement report)
            List<ValueRange> allUpdates = new ArrayList<>();

            // Course sheets
            for (Map.Entry<String, Map<String, AbstractMap.SimpleEntry<String, String>>> courseEntry : courseStudentData.entrySet()) {
                String courseName = courseEntry.getKey();
                Map<String, AbstractMap.SimpleEntry<String, String>> studentRows = courseEntry.getValue();
                List<String> courseworkTitles = courseWorkTitles.getOrDefault(courseName, Collections.emptyList());

                String sheetTitle = sanitizedNameMap.get(courseName);
                List<List<Object>> values = new ArrayList<>();

                // Header row
                List<Object> header = new ArrayList<>();
                header.add("Name");
                header.add("Email");
                header.addAll(courseworkTitles);
                header.add("Submissions");
                header.add("Percentage");
                values.add(header);

                // Student rows
                for (Map.Entry<String, AbstractMap.SimpleEntry<String, String>> studentEntry : studentRows.entrySet()) {
                    String studentName = studentEntry.getKey();
                    String email = studentEntry.getValue().getKey();
                    String[] statusValues = studentEntry.getValue().getValue().split(",");

                    int submissions = 0;
                    for (String status : statusValues) {
                        if ("submitted".equalsIgnoreCase(status.trim()) || "turned in".equalsIgnoreCase(status.trim())) {
                            submissions++;
                            totalSubmitted++;
                        } else {
                            totalNotSubmitted++;
                        }
                    }

                    float percentage = ((float) submissions / statusValues.length) * 100;

                    List<Object> row = new ArrayList<>();
                    row.add(studentName);
                    row.add(email);
                    row.addAll(Arrays.asList(statusValues));
                    row.add(submissions);
                    row.add(String.format("%.2f%%", percentage));

                    values.add(row);
                }

                allUpdates.add(new ValueRange().setRange(sheetTitle + "!A1").setValues(values));
            }

            // Engagement Report
            String summarySheetName = "Engagement_Report";
            sheetsService.spreadsheets().batchUpdate(spreadsheetId,
                    new BatchUpdateSpreadsheetRequest().setRequests(Collections.singletonList(
                            new Request().setAddSheet(new AddSheetRequest()
                                    .setProperties(new SheetProperties().setTitle(summarySheetName)))
                    ))).execute();

            List<List<Object>> summaryData = new ArrayList<>();
            summaryData.add(Arrays.asList("Batch", "Total Submitted", "Total Not Submitted", "Overall %"));
            float percent = totalSubmitted + totalNotSubmitted > 0
                    ? (float) totalSubmitted * 100 / (totalSubmitted + totalNotSubmitted)
                    : 0;
            summaryData.add(Arrays.asList(batchName, totalSubmitted, totalNotSubmitted, String.format("%.2f%%", percent)));

            allUpdates.add(new ValueRange().setRange(summarySheetName + "!A1").setValues(summaryData));

            // Batch update all values
            sheetsService.spreadsheets().values()
                    .batchUpdate(spreadsheetId, new BatchUpdateValuesRequest()
                            .setValueInputOption("RAW")
                            .setData(allUpdates))
                    .execute();

            // ✅ 5. Move file into Drive folder
            File fileMetadata = new File();
            fileMetadata.setParents(Collections.singletonList(driveFolderId));
            driveService.files().update(spreadsheetId, fileMetadata).setFields("id, parents").execute();

            logger.info("Moved spreadsheet to Drive folder: " + driveFolderId);

            return new SpreadsheetResult("https://docs.google.com/spreadsheets/d/" + spreadsheetId, true);

        } catch (GeneralSecurityException e) {
            logger.severe("Security exception: " + e.getMessage());
            throw new RuntimeException("Failed to export sheet: " + e.getMessage(), e);
        }
    }

    private Sheets getSheetsService(String accessToken) throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new Sheets.Builder(httpTransport, JSON_FACTORY, request ->
                request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }

    private Drive getDriveService(String accessToken) throws GeneralSecurityException, IOException {
        NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        return new Drive.Builder(httpTransport, JSON_FACTORY, request ->
                request.getHeaders().setAuthorization("Bearer " + accessToken))
                .setApplicationName(APPLICATION_NAME)
                .build();
    }
}
