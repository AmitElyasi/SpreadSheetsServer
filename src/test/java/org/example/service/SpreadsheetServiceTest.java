package org.example.service;

import org.example.model.Cell;
import org.example.model.Column;
import org.example.model.Sheet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the SpreadsheetService class.
 * These tests focus on the business logic without involving HTTP or controllers.
 */
public class SpreadsheetServiceTest {

    private SpreadsheetService spreadsheetService;

    @BeforeEach
    public void setUp() {
        spreadsheetService = new SpreadsheetService();
    }

    @Test
    public void testCreateSheet() {
        List<Column> columns = Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int")
        );

        Sheet sheet = spreadsheetService.createSheet(columns);

        assertNotNull(sheet);
        assertNotNull(sheet.getId());
        assertEquals(2, sheet.getColumns().size());
        assertEquals("A", sheet.getColumns().get(0).getName());
        assertEquals("string", sheet.getColumns().get(0).getType());
    }

    @Test
    public void testCreateSheetWithCustomId() {
        List<Column> columns = Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int")
        );
        String customId = "custom-id-123";

        Sheet sheet = spreadsheetService.createSheetWithId(customId, columns);

        assertNotNull(sheet);
        assertEquals(customId, sheet.getId());
        assertEquals(2, sheet.getColumns().size());
    }

    @Test
    public void testCreateSheetWithDuplicateId() {
        List<Column> columns = Arrays.asList(
                new Column("A", "string")
        );
        String customId = "duplicate-id-123";

        // Create first sheet
        spreadsheetService.createSheetWithId(customId, columns);

        // Try to create second sheet with same ID
        assertThrows(IllegalArgumentException.class, () -> {
            spreadsheetService.createSheetWithId(customId, columns);
        });
    }

    @Test
    public void testGetSheetById() {
        List<Column> columns = Arrays.asList(
                new Column("A", "string")
        );

        Sheet createdSheet = spreadsheetService.createSheet(columns);
        Sheet retrievedSheet = spreadsheetService.getSheet(createdSheet.getId());

        assertNotNull(retrievedSheet);
        assertEquals(createdSheet.getId(), retrievedSheet.getId());
    }

    @Test
    public void testSetCellValue() {
        // Create sheet
        List<Column> columns = Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int"),
                new Column("C", "boolean")
        );
        Sheet sheet = spreadsheetService.createSheet(columns);
        String sheetId = sheet.getId();

        // Set values
        Cell stringCell = spreadsheetService.setCellValue(sheetId, "A", 1, "test value");
        Cell intCell = spreadsheetService.setCellValue(sheetId, "B", 1, 42);
        Cell boolCell = spreadsheetService.setCellValue(sheetId, "C", 1, true);

        // Verify values
        assertEquals("test value", stringCell.getValue());
        assertEquals(42, intCell.getValue());
        assertEquals(true, boolCell.getValue());

        // Verify sheet contains cells
        Sheet updatedSheet = spreadsheetService.getSheet(sheetId);
        assertEquals("test value", updatedSheet.getCell("A", 1).getValue());
        assertEquals(42, updatedSheet.getCell("B", 1).getValue());
        assertEquals(true, updatedSheet.getCell("C", 1).getValue());
    }

    @Test
    public void testSetCellValueTypeMismatch() {
        // Create sheet
        List<Column> columns = Arrays.asList(
                new Column("A", "int")
        );
        Sheet sheet = spreadsheetService.createSheet(columns);
        String sheetId = sheet.getId();

        // Try to set string value in int column
        assertThrows(IllegalArgumentException.class, () -> {
            spreadsheetService.setCellValue(sheetId, "A", 1, "not an integer");
        });
    }

    @Test
    public void testLookupFunction() {
        // Create sheet
        List<Column> columns = Arrays.asList(
                new Column("A", "string"),
                new Column("B", "string")
        );
        Sheet sheet = spreadsheetService.createSheet(columns);
        String sheetId = sheet.getId();

        // Set original value
        spreadsheetService.setCellValue(sheetId, "A", 1, "original value");

        // Set lookup function
        Cell lookupCell = spreadsheetService.setCellValue(sheetId, "B", 1, "lookup(A,1)");

        // Verify lookup value is set
        assertEquals("original value", lookupCell.getValue());

        // Update original value
        spreadsheetService.setCellValue(sheetId, "A", 1, "updated value");

        // Verify lookup value was updated
        Sheet updatedSheet = spreadsheetService.getSheet(sheetId);
        assertEquals("updated value", updatedSheet.getCell("B", 1).getValue());
    }

    @Test
    public void testLookupTypeMismatch() {
        // Create sheet
        List<Column> columns = Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int")
        );
        Sheet sheet = spreadsheetService.createSheet(columns);
        String sheetId = sheet.getId();

        // Set original value
        spreadsheetService.setCellValue(sheetId, "A", 1, "string value");

        // Try to set lookup from string to int column
        assertThrows(IllegalArgumentException.class, () -> {
            spreadsheetService.setCellValue(sheetId, "B", 1, "lookup(A,1)");
        });
    }

    @Test
    public void testCycleDetection() {
        // Create sheet
        List<Column> columns = Arrays.asList(
                new Column("A", "string"),
                new Column("B", "string"),
                new Column("C", "string")
        );
        Sheet sheet = spreadsheetService.createSheet(columns);
        String sheetId = sheet.getId();

        // Set up C -> A -> B chain
        spreadsheetService.setCellValue(sheetId, "C", 1, "lookup(A,1)");
        spreadsheetService.setCellValue(sheetId, "A", 1, "lookup(B,1)");

        // Try to create cycle with B -> C
        assertThrows(IllegalArgumentException.class, () -> {
            spreadsheetService.setCellValue(sheetId, "B", 1, "lookup(C,1)");
        });
    }

    @Test
    public void testSelfReferenceCycleDetection() {
        // Create sheet
        List<Column> columns = Arrays.asList(
                new Column("A", "string")
        );
        Sheet sheet = spreadsheetService.createSheet(columns);
        String sheetId = sheet.getId();

        // Try to create self-reference
        assertThrows(IllegalArgumentException.class, () -> {
            spreadsheetService.setCellValue(sheetId, "A", 1, "lookup(A,1)");
        });
    }

    @Test
    public void testNonExistentSheet() {
        assertThrows(IllegalArgumentException.class, () -> {
            spreadsheetService.setCellValue("non-existent-id", "A", 1, "test");
        });
    }

    @Test
    public void testNonExistentColumn() {
        // Create sheet
        List<Column> columns = Arrays.asList(
                new Column("A", "string")
        );
        Sheet sheet = spreadsheetService.createSheet(columns);
        String sheetId = sheet.getId();

        // Try to set value in non-existent column
        assertThrows(IllegalArgumentException.class, () -> {
            spreadsheetService.setCellValue(sheetId, "Z", 1, "test");
        });
    }

    @Test
    public void testConvertSheetToCsv() {
        // Create a test sheet with columns
        List<Column> columns = Arrays.asList(
                new Column("A", "string"),
                new Column("B", "int"),
                new Column("C", "boolean")
        );
        Sheet sheet = spreadsheetService.createSheet(columns);
        String sheetId = sheet.getId();

        // Add some cell values
        spreadsheetService.setCellValue(sheetId, "A", 1, "Hello");
        spreadsheetService.setCellValue(sheetId, "B", 1, 42);
        spreadsheetService.setCellValue(sheetId, "C", 1, true);

        spreadsheetService.setCellValue(sheetId, "A", 3, "World");
        spreadsheetService.setCellValue(sheetId, "B", 3, 99);

        // Test with comma in string value
        spreadsheetService.setCellValue(sheetId, "A", 5, "Hello, World");

        // Get the CSV conversion
        String csv = spreadsheetService.convertSheetToCsv(sheet);

        // Assert expected CSV format
        String[] lines = csv.split("\n");

        // Should have headers + 3 data rows
        assertEquals(4, lines.length);

        // Check header row - now should include "Row" as first column
        assertEquals("Row,A,B,C", lines[0]);

        // Data rows should be sorted by row index and include row number as first column
        assertEquals("1,Hello,42,true", lines[1]);
        assertEquals("3,World,99,", lines[2]);
        assertTrue(lines[3].startsWith("5,\"Hello, World\""));
    }
}
