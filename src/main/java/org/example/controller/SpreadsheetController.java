package org.example.controller;

import lombok.extern.slf4j.Slf4j;
import org.example.model.Cell;
import org.example.model.Sheet;
import org.example.service.SpreadsheetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
@Slf4j
public class SpreadsheetController {

    private final SpreadsheetService spreadsheetService;

    @Autowired
    public SpreadsheetController(SpreadsheetService spreadsheetService) {
        this.spreadsheetService = spreadsheetService;
    }

    /**
     * Create a new sheet with the given schema
     * POST /api/sheets
     */
    @PostMapping("/sheets")
    public ResponseEntity<Map<String, String>> createSheet(@RequestBody Sheet sheetRequest) {
        try {
            log.info("Received request to create a new sheet");
            log.debug("Sheet request: {}", sheetRequest);

            Sheet sheet;
            if (sheetRequest.getId() != null && !sheetRequest.getId().isEmpty()) {
                // Check if a sheet with the provided ID already exists
                log.debug("Custom sheet ID provided: {}", sheetRequest.getId());
                Sheet existingSheet = spreadsheetService.getSheet(sheetRequest.getId());
                if (existingSheet != null) {
                    log.warn("Attempt to create sheet with duplicate ID: {}", sheetRequest.getId());
                    return ResponseEntity.status(HttpStatus.CONFLICT)
                            .body(Map.of("error", "Sheet with ID " + sheetRequest.getId() + " already exists"));
                }
                // Create sheet with the custom ID
                sheet = spreadsheetService.createSheetWithId(sheetRequest.getId(), sheetRequest.getColumns());
                log.info("Created new sheet with custom ID: {}", sheet.getId());
            } else {
                // Create sheet with an auto-generated ID
                sheet = spreadsheetService.createSheet(sheetRequest.getColumns());
                log.info("Created new sheet with auto-generated ID: {}", sheet.getId());
            }
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of("id", sheet.getId()));
        } catch (Exception e) {
            log.error("Failed to create sheet", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get a sheet by ID
     * GET /api/sheets/{sheetId}
     * Supports CSV format responses and JSON error responses
     */
    @GetMapping(
        value = "/sheets/{sheetId}",
        produces = {"text/csv", "application/json"}
    )
    public ResponseEntity<?> getSheet(@PathVariable String sheetId) {
        try {
            log.info("Received request to get sheet with ID: {}", sheetId);

            Sheet sheet = spreadsheetService.getSheet(sheetId);
            if (sheet == null) {
                log.warn("Sheet not found with ID: {}", sheetId);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of("error", "Sheet not found with id: " + sheetId));
            }

            log.info("Returning sheet in CSV format");
            String csvData = spreadsheetService.convertSheetToCsv(sheet);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sheet-" + sheetId + ".csv\"")
                    .body(csvData);

        } catch (Exception e) {
            log.error("Error retrieving sheet with ID: {}", sheetId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Set a cell value in a sheet
     * PUT /api/sheets/{sheetId}/cells/{columnName}/{rowIndex}
     */
    @PutMapping("/sheets/{sheetId}/cells/{columnName}/{rowIndex}")
    public ResponseEntity<?> setCellValue(
            @PathVariable String sheetId,
            @PathVariable String columnName,
            @PathVariable int rowIndex,
            @RequestBody Map<String, Object> requestBody) {

        try {
            log.info("Received request to set cell value for sheet: {}, column: {}, row: {}",
                    sheetId, columnName, rowIndex);

            if (!requestBody.containsKey("value")) {
                log.warn("Request body missing 'value' field");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Value is required"));
            }

            Object value = requestBody.get("value");
            log.debug("Setting cell value: {}", value);

            Cell cell = spreadsheetService.setCellValue(sheetId, columnName, rowIndex, value);
            log.info("Cell value set successfully");
            return ResponseEntity.ok(cell);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request to set cell value", e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error setting cell value", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }
}

