package org.example.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.Main;
import org.example.model.Column;
import org.example.model.Sheet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * True integration tests that use an actual HTTP client against a running server instance.
 */
public class SpreadsheetServerIntegrationTest {

    private static final int SERVER_PORT = 8080;
    private static final String BASE_URL = "http://localhost:" + SERVER_PORT + "/api";
    private static ConfigurableApplicationContext context;
    private static final TestRestTemplate restTemplate = new TestRestTemplate();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final HttpHeaders headers = new HttpHeaders();

    @BeforeAll
    public static void setUp() {
        // Start the server
        context = SpringApplication.run(Main.class);

        // Set up headers for JSON communication
        headers.setContentType(MediaType.APPLICATION_JSON);
    }

    @AfterAll
    public static void tearDown() {
        // Shut down the server after tests
        if (context != null) {
            context.close();
        }
    }

    @Test
    public void testCreateAndRetrieveSheet() throws JsonProcessingException {
        // Create a sheet with columns
        Sheet sheetRequest = new Sheet();
        sheetRequest.setColumns(Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int"),
                new Column("C", "boolean")
        ));

        // Send request to create sheet
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(sheetRequest), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/sheets",
                HttpMethod.POST,
                request,
                String.class
        );

        // Verify response
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<String, String> responseMap = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        String sheetId = responseMap.get("id");
        assertNotNull(sheetId);

        // Retrieve the sheet (will return CSV)
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                BASE_URL + "/sheets/" + sheetId,
                String.class
        );

        // Verify CSV response
        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals(MediaType.parseMediaType("text/csv"), getResponse.getHeaders().getContentType());

        String csvContent = getResponse.getBody();
        assertNotNull(csvContent);
        assertTrue(csvContent.startsWith("Row,A,B,C"));

        // Basic validation that we got CSV with expected structure
        String[] lines = csvContent.split("\n");
        assertTrue(lines.length >= 1, "CSV should have at least a header row");
        assertEquals("Row,A,B,C", lines[0].trim(), "CSV header should match columns");
    }

    @Test
    public void testSetCellValueAndLookupFunction() throws JsonProcessingException {
        // Create a sheet
        Sheet sheetRequest = new Sheet();
        sheetRequest.setColumns(Arrays.asList(
                new Column("A", "string"),
                new Column("B", "string")
        ));

        // Send request to create sheet
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(sheetRequest), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/sheets",
                HttpMethod.POST,
                request,
                String.class
        );

        Map<String, String> responseMap = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        String sheetId = responseMap.get("id");

        // Set a value in cell A1
        Map<String, Object> cellValue = new HashMap<>();
        cellValue.put("value", "Hello World");

        HttpEntity<String> setCellRequest = new HttpEntity<>(objectMapper.writeValueAsString(cellValue), headers);
        ResponseEntity<String> setCellResponse = restTemplate.exchange(
                BASE_URL + "/sheets/" + sheetId + "/cells/A/1",
                HttpMethod.PUT,
                setCellRequest,
                String.class
        );

        assertEquals(HttpStatus.OK, setCellResponse.getStatusCode());

        // Set a lookup function in B1 that references A1
        Map<String, Object> lookupValue = new HashMap<>();
        lookupValue.put("value", "lookup(A,1)");

        HttpEntity<String> setLookupRequest = new HttpEntity<>(objectMapper.writeValueAsString(lookupValue), headers);
        ResponseEntity<String> setLookupResponse = restTemplate.exchange(
                BASE_URL + "/sheets/" + sheetId + "/cells/B/1",
                HttpMethod.PUT,
                setLookupRequest,
                String.class
        );

        assertEquals(HttpStatus.OK, setLookupResponse.getStatusCode());
        JsonNode lookupNode = objectMapper.readTree(setLookupResponse.getBody());
        assertEquals("Hello World", lookupNode.get("value").asText());

        // Update the referenced cell
        Map<String, Object> updatedValue = new HashMap<>();
        updatedValue.put("value", "Updated Value");

        HttpEntity<String> updateRequest = new HttpEntity<>(objectMapper.writeValueAsString(updatedValue), headers);
        restTemplate.exchange(
                BASE_URL + "/sheets/" + sheetId + "/cells/A/1",
                HttpMethod.PUT,
                updateRequest,
                String.class
        );

        // Get the sheet and verify the lookup value was updated via CSV response
        ResponseEntity<String> getResponse = restTemplate.getForEntity(
                BASE_URL + "/sheets/" + sheetId,
                String.class
        );

        assertEquals(HttpStatus.OK, getResponse.getStatusCode());
        assertEquals(MediaType.parseMediaType("text/csv"), getResponse.getHeaders().getContentType());

        String csvContent = getResponse.getBody();
        assertNotNull(csvContent);

        // Parse CSV content to verify values
        String[] lines = csvContent.split("\n");
        assertTrue(lines.length >= 2, "CSV should have at least a header row and one data row");

        // Find the row with index 1
        String dataLine = null;
        for (int i = 1; i < lines.length; i++) {
            if (lines[i].startsWith("1,")) {
                dataLine = lines[i];
                break;
            }
        }

        assertNotNull(dataLine, "Data row with index 1 should exist");
        String[] values = dataLine.split(",");
        assertEquals("1", values[0], "First column should be the row number");
        assertEquals("Updated Value", values[1], "Value in A1 should be 'Updated Value'");
        assertEquals("Updated Value", values[2], "Value in B1 should be 'Updated Value' due to the lookup function");
    }

    @Test
    public void testCreateSheetWithCustomId() throws JsonProcessingException {
        // Create a sheet with a custom ID
        Sheet sheetRequest = new Sheet();
        String customId = "custom-id-" + System.currentTimeMillis();
        sheetRequest.setId(customId);
        sheetRequest.setColumns(Arrays.asList(
                new Column("A", "string")
        ));

        // Send request to create sheet
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(sheetRequest), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/sheets",
                HttpMethod.POST,
                request,
                String.class
        );

        // Verify custom ID was used
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        Map<String, String> responseMap = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        assertEquals(customId, responseMap.get("id"));

        // Try to create another sheet with the same ID (should fail)
        ResponseEntity<String> duplicateResponse = restTemplate.exchange(
                BASE_URL + "/sheets",
                HttpMethod.POST,
                request,
                String.class
        );

        assertEquals(HttpStatus.CONFLICT, duplicateResponse.getStatusCode());
    }

    @Test
    public void testCycleDetection() throws JsonProcessingException {
        // Create a sheet
        Sheet sheetRequest = new Sheet();
        sheetRequest.setColumns(Arrays.asList(
                new Column("A", "string"),
                new Column("B", "string")
        ));

        // Send request to create sheet
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(sheetRequest), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/sheets",
                HttpMethod.POST,
                request,
                String.class
        );

        Map<String, String> responseMap = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        String sheetId = responseMap.get("id");

        // Set a lookup function in B1 that references A1
        Map<String, Object> lookupValueB = new HashMap<>();
        lookupValueB.put("value", "lookup(A,1)");

        HttpEntity<String> setLookupRequest = new HttpEntity<>(objectMapper.writeValueAsString(lookupValueB), headers);
        restTemplate.exchange(
                BASE_URL + "/sheets/" + sheetId + "/cells/B/1",
                HttpMethod.PUT,
                setLookupRequest,
                String.class
        );

        // Try to create a cycle by making A1 reference B1
        Map<String, Object> cyclicLookup = new HashMap<>();
        cyclicLookup.put("value", "lookup(B,1)");

        HttpEntity<String> cyclicRequest = new HttpEntity<>(objectMapper.writeValueAsString(cyclicLookup), headers);
        ResponseEntity<String> cyclicResponse = restTemplate.exchange(
                BASE_URL + "/sheets/" + sheetId + "/cells/A/1",
                HttpMethod.PUT,
                cyclicRequest,
                String.class
        );

        // Should return bad request due to cycle
        assertEquals(HttpStatus.BAD_REQUEST, cyclicResponse.getStatusCode());
        JsonNode errorNode = objectMapper.readTree(cyclicResponse.getBody());
        assertTrue(errorNode.has("error"));
        assertTrue(errorNode.get("error").asText().contains("Cycle"));
    }

    @Test
    public void testTypeMismatch() throws JsonProcessingException {
        // Create a sheet with different column types
        Sheet sheetRequest = new Sheet();
        sheetRequest.setColumns(Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int")
        ));

        // Send request to create sheet
        HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(sheetRequest), headers);
        ResponseEntity<String> response = restTemplate.exchange(
                BASE_URL + "/sheets",
                HttpMethod.POST,
                request,
                String.class
        );

        Map<String, String> responseMap = objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        String sheetId = responseMap.get("id");

        // Try to set a string value in an integer column
        Map<String, Object> invalidValue = new HashMap<>();
        invalidValue.put("value", "not an integer");

        HttpEntity<String> invalidRequest = new HttpEntity<>(objectMapper.writeValueAsString(invalidValue), headers);
        ResponseEntity<String> invalidResponse = restTemplate.exchange(
                BASE_URL + "/sheets/" + sheetId + "/cells/B/1",
                HttpMethod.PUT,
                invalidRequest,
                String.class
        );

        // Should return bad request due to type mismatch
        assertEquals(HttpStatus.BAD_REQUEST, invalidResponse.getStatusCode());
    }
}
