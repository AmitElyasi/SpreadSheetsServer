package org.example.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.model.Cell;
import org.example.model.Column;
import org.example.model.Sheet;
import org.example.service.SpreadsheetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.Map;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for the SpreadsheetController class.
 * These tests focus on the controller layer, mocking the service layer.
 */
@ExtendWith(MockitoExtension.class)
public class SpreadsheetControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SpreadsheetService spreadsheetService;

    @InjectMocks
    private SpreadsheetController controller;

    private ObjectMapper objectMapper;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
        objectMapper = new ObjectMapper();
    }

    @Test
    public void testCreateSheet() throws Exception {
        // Prepare test data
        Sheet requestSheet = new Sheet();
        requestSheet.setColumns(Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int")
        ));

        Sheet createdSheet = new Sheet("sheet-123", requestSheet.getColumns());

        // Mock service behavior
        when(spreadsheetService.createSheet(anyList())).thenReturn(createdSheet);

        // Perform request and validate
        mockMvc.perform(post("/api/sheets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestSheet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("sheet-123")));

        // Verify service was called
        verify(spreadsheetService).createSheet(anyList());
    }

    @Test
    public void testCreateSheetWithCustomId() throws Exception {
        // Prepare test data
        Sheet requestSheet = new Sheet();
        requestSheet.setId("custom-id");
        requestSheet.setColumns(Arrays.asList(
                new Column("A", "string")
        ));

        Sheet createdSheet = new Sheet("custom-id", requestSheet.getColumns());

        // Mock service behaviors
        when(spreadsheetService.getSheet("custom-id")).thenReturn(null); // Sheet doesn't exist yet
        when(spreadsheetService.createSheetWithId(eq("custom-id"), anyList())).thenReturn(createdSheet);

        // Perform request and validate
        mockMvc.perform(post("/api/sheets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestSheet)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", is("custom-id")));

        // Verify service methods were called
        verify(spreadsheetService).getSheet("custom-id");
        verify(spreadsheetService).createSheetWithId(eq("custom-id"), anyList());
    }

    @Test
    public void testCreateSheetWithDuplicateId() throws Exception {
        // Prepare test data
        Sheet requestSheet = new Sheet();
        requestSheet.setId("existing-id");
        requestSheet.setColumns(Arrays.asList(
                new Column("A", "string")
        ));

        Sheet existingSheet = new Sheet("existing-id", requestSheet.getColumns());

        // Mock service behavior - sheet already exists
        when(spreadsheetService.getSheet("existing-id")).thenReturn(existingSheet);

        // Perform request and validate
        mockMvc.perform(post("/api/sheets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestSheet)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").exists());

        // Verify only getSheet was called, not createSheetWithId
        verify(spreadsheetService).getSheet("existing-id");
        verify(spreadsheetService, never()).createSheetWithId(anyString(), anyList());
    }

    @Test
    public void testCreateSheetWithServiceException() throws Exception {
        // Prepare test data
        Sheet requestSheet = new Sheet();
        requestSheet.setColumns(Arrays.asList(
                new Column("A", "string")
        ));

        // Mock service throwing exception
        when(spreadsheetService.createSheet(anyList())).thenThrow(new IllegalArgumentException("Invalid column type"));

        // Perform request and validate
        mockMvc.perform(post("/api/sheets")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestSheet)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Invalid column type")));
    }

    @Test
    public void testGetSheetById() throws Exception {
        // Prepare test data
        Sheet sheet = new Sheet("sheet-123", Arrays.asList(
                new Column("A", "string")
        ));
        String csvContent = "Row,A\n"; // Empty sheet with header row

        // Mock service behavior
        when(spreadsheetService.getSheet("sheet-123")).thenReturn(sheet);
        when(spreadsheetService.convertSheetToCsv(sheet)).thenReturn(csvContent);

        // Perform request and validate
        mockMvc.perform(get("/api/sheets/sheet-123"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"sheet-sheet-123.csv\""))
                .andExpect(content().string(csvContent));

        // Verify service methods were called
        verify(spreadsheetService).getSheet("sheet-123");
        verify(spreadsheetService).convertSheetToCsv(sheet);
    }

    @Test
    public void testGetNonExistentSheet() throws Exception {
        // Mock service behavior - explicitly set up to return null for non-existent sheet
        when(spreadsheetService.getSheet("non-existent")).thenReturn(null);

        // Perform request and validate based on the actual behavior
        mockMvc.perform(get("/api/sheets/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());

        // Verify the service method was called
        verify(spreadsheetService).getSheet("non-existent");
    }

    @Test
    public void testSetCellValue() throws Exception {
        // Prepare test data
        Cell cell = new Cell("A", 1, "test value");
        Map<String, Object> requestBody = Map.of("value", "test value");

        // Mock service behavior
        when(spreadsheetService.setCellValue(eq("sheet-123"), eq("A"), eq(1), any())).thenReturn(cell);

        // Perform request and validate
        mockMvc.perform(put("/api/sheets/sheet-123/cells/A/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.column", is("A")))
                .andExpect(jsonPath("$.row", is(1)))
                .andExpect(jsonPath("$.value", is("test value")));
    }

    @Test
    public void testSetCellValueMissingValue() throws Exception {
        // Prepare request with missing value
        Map<String, Object> requestBody = Map.of("otherField", "some value");

        // Perform request and validate
        mockMvc.perform(put("/api/sheets/sheet-123/cells/A/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // Verify service was never called
        verify(spreadsheetService, never()).setCellValue(anyString(), anyString(), anyInt(), any());
    }

    @Test
    public void testSetCellValueWithIllegalArgument() throws Exception {
        // Prepare test data
        Map<String, Object> requestBody = Map.of("value", "test value");

        // Mock service throwing exception
        when(spreadsheetService.setCellValue(eq("sheet-123"), eq("A"), eq(1), any()))
                .thenThrow(new IllegalArgumentException("Column not found: A"));

        // Perform request and validate
        mockMvc.perform(put("/api/sheets/sheet-123/cells/A/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Column not found: A")));
    }

    @Test
    public void testSetCellValueWithRuntimeException() throws Exception {
        // Prepare test data
        Map<String, Object> requestBody = Map.of("value", "test value");

        // Mock service throwing exception
        when(spreadsheetService.setCellValue(eq("sheet-123"), eq("A"), eq(1), any()))
                .thenThrow(new RuntimeException("Unexpected error"));

        // Perform request and validate
        mockMvc.perform(put("/api/sheets/sheet-123/cells/A/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error", is("Unexpected error")));
    }

    @Test
    public void testGetSheetAsCsv() throws Exception {
        // Prepare test data
        Sheet sheet = new Sheet("sheet-123", Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int")
        ));

        // Updated CSV content to include Row column
        String csvContent = "Row,A,B\n1,Value1,42\n";

        // Mock service behavior
        when(spreadsheetService.getSheet("sheet-123")).thenReturn(sheet);
        when(spreadsheetService.convertSheetToCsv(sheet)).thenReturn(csvContent);

        // Perform request with Accept header for CSV
        mockMvc.perform(get("/api/sheets/sheet-123")
                .header("Accept", "text/csv"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"sheet-sheet-123.csv\""))
                .andExpect(content().string(csvContent));

        // Verify service methods were called
        verify(spreadsheetService).getSheet("sheet-123");
        verify(spreadsheetService).convertSheetToCsv(sheet);
    }
}
