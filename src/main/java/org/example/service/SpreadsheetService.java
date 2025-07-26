package org.example.service;

import lombok.extern.slf4j.Slf4j;
import org.example.model.Cell;
import org.example.model.Column;
import org.example.model.Sheet;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Slf4j
public class SpreadsheetService {

    // In-memory storage for sheets
    private final Map<String, Sheet> sheets = new ConcurrentHashMap<>();

    /**
     * Create a new sheet with the given columns
     */
    public Sheet createSheet(List<Column> columns) {
        log.info("Creating new sheet with auto-generated ID");
        String sheetId = UUID.randomUUID().toString();
        Sheet sheet = new Sheet(sheetId, columns);
        sheets.put(sheetId, sheet);
        log.debug("Created sheet with ID: {}, columns: {}", sheetId, columns.size());
        return sheet;
    }

    /**
     * Create a new sheet with the given columns and a custom ID
     */
    public Sheet createSheetWithId(String sheetId, List<Column> columns) {
        log.info("Creating new sheet with custom ID: {}", sheetId);
        if (sheets.containsKey(sheetId)) {
            log.warn("Attempt to create sheet with existing ID: {}", sheetId);
            throw new IllegalArgumentException("Sheet with ID " + sheetId + " already exists");
        }
        Sheet sheet = new Sheet(sheetId, columns);
        sheets.put(sheetId, sheet);
        log.debug("Created sheet with custom ID: {}, columns: {}", sheetId, columns.size());
        return sheet;
    }

    /**
     * Get a sheet by ID
     */
    public Sheet getSheet(String sheetId) {
        log.debug("Getting sheet with ID: {}", sheetId);
        Sheet sheet = sheets.get(sheetId);
        if (sheet == null) {
            log.debug("Sheet not found with ID: {}", sheetId);
        } else {
            log.debug("Found sheet with ID: {}, columns: {}", sheetId, sheet.getColumns().size());
        }
        return sheet;
    }

    /**
     * Set a cell value in a sheet
     */
    public Cell setCellValue(String sheetId, String columnName, int rowIndex, Object value) {
        log.info("Setting cell value in sheet: {}, column: {}, row: {}", sheetId, columnName, rowIndex);

        Sheet sheet = getSheet(sheetId);
        if (sheet == null) {
            log.warn("Sheet not found with ID: {}", sheetId);
            throw new IllegalArgumentException("Sheet not found with id: " + sheetId);
        }

        Column column = sheet.getColumnByName(columnName);
        if (column == null) {
            log.warn("Column not found: {} in sheet: {}", columnName, sheetId);
            throw new IllegalArgumentException("Column not found: " + columnName);
        }

        Cell cell = new Cell(columnName, rowIndex, value);
        log.debug("Processing cell value type: {}", value != null ? value.getClass().getSimpleName() : "null");

        // Check if value is a lookup function
        if (value instanceof String && ((String) value).startsWith("lookup(")) {
            log.debug("Processing lookup function: {}", value);
            processLookupFunction(sheet, cell, (String) value);
        } else {
            // Validate the type of the value against the column type
            log.debug("Validating value type against column type: {}", column.getType());
            validateValueType(column, value);
        }

        // Check for cycles before adding the cell
        if (cell.isLookupCell()) {
            log.debug("Checking for reference cycles");
            checkForCycles(sheet, cell, new HashSet<>());
        }

        // Add or update the cell
        sheet.addCell(cell);
        log.info("Cell value set successfully");

        // Update dependent cells if any
        log.debug("Updating dependent cells");
        updateDependentCells(sheet, columnName, rowIndex, new HashSet<>());

        return cell;
    }

    /**
     * Process a lookup function in a cell value
     */
    private void processLookupFunction(Sheet sheet, Cell cell, String lookupFunction) {
        log.debug("Processing lookup function: {}", lookupFunction);

        // Extract column name and row index from lookup function
        // Regex to match lookup(columnName, rowIndex)
        Pattern pattern = Pattern.compile("lookup\\(\\s*([A-Za-z]+)\\s*,\\s*(\\d+)\\s*\\)");
        Matcher matcher = pattern.matcher(lookupFunction);

        if (!matcher.matches()) {
            log.warn("Invalid lookup function format: {}", lookupFunction);
            throw new IllegalArgumentException("Invalid lookup function format: " + lookupFunction);
        }

        String referencedColumn = matcher.group(1);
        int referencedRow = Integer.parseInt(matcher.group(2));
        log.debug("Lookup references column: {}, row: {}", referencedColumn, referencedRow);

        // Check if referenced column exists
        Column referencedColDef = sheet.getColumnByName(referencedColumn);
        if (referencedColDef == null) {
            log.warn("Referenced column not found: {}", referencedColumn);
            throw new IllegalArgumentException("Referenced column not found: " + referencedColumn);
        }

        // Get the column definition of the current cell
        Column currentColDef = sheet.getColumnByName(cell.getColumn());

        // Check if the types are compatible
        if (!isTypeCompatible(referencedColDef.getType(), currentColDef.getType())) {
            log.warn("Type mismatch: Cannot set lookup from {} to {}",
                    referencedColDef.getType(), currentColDef.getType());
            throw new IllegalArgumentException(
                "Type mismatch: Cannot set lookup from " + referencedColDef.getType() +
                " to " + currentColDef.getType());
        }

        // Get the referenced cell value
        Cell referencedCell = sheet.getCell(referencedColumn, referencedRow);
        Object referencedValue = referencedCell != null ? referencedCell.getValue() : null;
        log.debug("Referenced cell value: {}", referencedValue);

        // Set the lookup function and the referenced value
        cell.setLookupFunction(lookupFunction);
        cell.setReferencedColumn(referencedColumn);
        cell.setReferencedRow(referencedRow);
        cell.setValue(referencedValue);
        log.debug("Lookup function processed successfully");
    }

    /**
     * Check if two column types are compatible
     */
    private boolean isTypeCompatible(String sourceType, String targetType) {
        boolean compatible = sourceType.equals(targetType);
        log.trace("Type compatibility check: {} -> {} = {}", sourceType, targetType, compatible);
        return compatible;
    }

    /**
     * Validate that the value matches the column type
     */
    private void validateValueType(Column column, Object value) {
        log.debug("Validating value type for column: {}, type: {}", column.getName(), column.getType());

        if (value == null) {
            log.debug("Null value is allowed for any type");
            return; // Null values are allowed
        }

        switch (column.getType().toLowerCase()) {
            case "boolean":
                if (!(value instanceof Boolean)) {
                    log.warn("Type validation failed: expected boolean, got {}", value.getClass().getSimpleName());
                    throw new IllegalArgumentException("Expected boolean value for column: " + column.getName());
                }
                break;
            case "int":
                if (value instanceof Number) {
                    // Convert to Integer if needed
                    if (!(value instanceof Integer)) {
                        try {
                            int intValue = ((Number) value).intValue();
                            // Check if the conversion loses precision
                            if (intValue != ((Number) value).doubleValue()) {
                                log.warn("Type validation failed: value {} cannot be converted to int without loss of precision", value);
                                throw new IllegalArgumentException("Expected integer value for column: " + column.getName());
                            }
                        } catch (ClassCastException e) {
                            log.warn("Type validation failed: cannot cast {} to int", value.getClass().getSimpleName());
                            throw new IllegalArgumentException("Expected integer value for column: " + column.getName());
                        }
                    }
                } else if (value instanceof String) {
                    try {
                        Integer.parseInt((String) value);
                    } catch (NumberFormatException e) {
                        log.warn("Type validation failed: string value '{}' cannot be parsed as int", value);
                        throw new IllegalArgumentException("Expected integer value for column: " + column.getName());
                    }
                } else {
                    log.warn("Type validation failed: expected int, got {}", value.getClass().getSimpleName());
                    throw new IllegalArgumentException("Expected integer value for column: " + column.getName());
                }
                break;
            case "double":
                if (value instanceof Number) {
                    // Already a number, no validation needed
                    log.trace("Value {} is already a number, validation passed", value);
                } else if (value instanceof String) {
                    try {
                        Double.parseDouble((String) value);
                        log.trace("String value '{}' can be parsed as double, validation passed", value);
                    } catch (NumberFormatException e) {
                        log.warn("Type validation failed: string value '{}' cannot be parsed as double", value);
                        throw new IllegalArgumentException("Expected double value for column: " + column.getName());
                    }
                } else {
                    log.warn("Type validation failed: expected double, got {}", value.getClass().getSimpleName());
                    throw new IllegalArgumentException("Expected double value for column: " + column.getName());
                }
                break;
            case "string":
                // All values can be represented as strings
                log.trace("Value can be represented as string, validation passed");
                break;
            default:
                log.warn("Unsupported column type: {}", column.getType());
                throw new IllegalArgumentException("Unsupported column type: " + column.getType());
        }

        log.debug("Type validation successful for column: {}", column.getName());
    }

    /**
     * Check for cycles in cell references
     * This method detects direct and indirect cycles in cell references
     */
    private void checkForCycles(Sheet sheet, Cell newCell, Set<String> visited) {
        log.debug("Checking for cycles starting from cell: {},{}", newCell.getColumn(), newCell.getRow());

        // First check if this cell would create a self-reference
        if (newCell.isLookupCell() &&
            newCell.getColumn().equals(newCell.getReferencedColumn()) &&
            newCell.getRow() == newCell.getReferencedRow()) {
            log.warn("Self-reference cycle detected in cell: {},{}", newCell.getColumn(), newCell.getRow());
            throw new IllegalArgumentException("Cycle detected in cell references: self-reference");
        }

        // Start with the target cell itself
        String cellKey = sheet.generateCellKey(newCell.getColumn(), newCell.getRow());

        // Now check if this cell is part of a longer cycle
        if (newCell.isLookupCell()) {
            // Start a new set for tracking visited cells
            Set<String> pathVisited = new HashSet<>();
            pathVisited.add(cellKey); // Mark the starting cell as visited

            log.debug("Checking for cycles in reference path");
            // Check if adding this cell would create a cycle
            checkPathForCycles(sheet, newCell.getReferencedColumn(), newCell.getReferencedRow(),
                              newCell.getColumn(), newCell.getRow(), pathVisited);
        }

        log.debug("No cycles detected");
    }

    /**
     * Helper method to check for cycles in a path of cell references
     */
    private void checkPathForCycles(Sheet sheet, String currentCol, int currentRow,
                                   String targetCol, int targetRow, Set<String> pathVisited) {
        log.trace("Checking path node: {},{} (target: {},{})", currentCol, currentRow, targetCol, targetRow);

        // Check if we've reached the target cell, which would mean we have a cycle
        if (currentCol.equals(targetCol) && currentRow == targetRow) {
            log.warn("Cycle detected in reference path");
            throw new IllegalArgumentException("Cycle detected in cell references");
        }

        // Get the current cell
        Cell currentCell = sheet.getCell(currentCol, currentRow);

        // If we reached a cell that isn't a lookup or doesn't exist, we've reached the end of the path
        if (currentCell == null || !currentCell.isLookupCell()) {
            log.trace("Path ended at {},{} (not a lookup cell or doesn't exist)", currentCol, currentRow);
            return;
        }

        // Mark this cell as visited in our path
        String cellKey = sheet.generateCellKey(currentCol, currentRow);
        if (pathVisited.contains(cellKey)) {
            // We've already visited this cell in our current path, which means there's a cycle,
            // but it doesn't involve our target cell
            log.trace("Already visited cell {},{} in this path (different cycle)", currentCol, currentRow);
            return;
        }

        pathVisited.add(cellKey);
        log.trace("Added {},{} to visited path", currentCol, currentRow);

        // Continue following the path
        checkPathForCycles(sheet, currentCell.getReferencedColumn(), currentCell.getReferencedRow(),
                          targetCol, targetRow, pathVisited);
    }

    /**
     * Update all cells that reference a specific cell
     */
    private void updateDependentCells(Sheet sheet, String columnName, int rowIndex, Set<String> visited) {
        log.debug("Updating dependent cells for {},{}", columnName, rowIndex);

        String cellKey = sheet.generateCellKey(columnName, rowIndex);

        // If we've already visited this cell in this update chain, stop to prevent infinite recursion
        if (visited.contains(cellKey)) {
            log.trace("Cell {},{} already visited, stopping recursive update", columnName, rowIndex);
            return;
        }

        // Mark this cell as visited
        visited.add(cellKey);

        // Find all cells that reference this cell
        for (Cell cell : sheet.getCells().values()) {
            if (cell.isLookupCell() &&
                columnName.equals(cell.getReferencedColumn()) &&
                rowIndex == cell.getReferencedRow()) {

                log.debug("Found dependent cell: {},{}", cell.getColumn(), cell.getRow());

                // Update the value from the referenced cell
                Cell referencedCell = sheet.getCell(columnName, rowIndex);
                if (referencedCell != null) {
                    Object oldValue = cell.getValue();
                    cell.setValue(referencedCell.getValue());
                    log.debug("Updated cell {},{} value from: {} to: {}",
                            cell.getColumn(), cell.getRow(), oldValue, cell.getValue());

                    // Recursively update cells that depend on this cell
                    updateDependentCells(sheet, cell.getColumn(), cell.getRow(), visited);
                }
            }
        }

        log.debug("Finished updating dependent cells for {},{}", columnName, rowIndex);
    }

    /**
     * Converts a sheet to CSV format with columns as headers and data sorted by row
     * First column in CSV will be the row number
     * @param sheet The sheet to convert
     * @return String representation of the sheet in CSV format
     */
    public String convertSheetToCsv(Sheet sheet) {
        log.debug("Converting sheet to CSV format: {}", sheet.getId());
        StringBuilder csv = new StringBuilder();

        // Add "Row" as first column header, followed by sheet column headers
        csv.append("Row,");
        List<Column> columns = sheet.getColumns();
        for (int i = 0; i < columns.size(); i++) {
            csv.append(columns.get(i).getName());
            if (i < columns.size() - 1) {
                csv.append(",");
            }
        }
        csv.append("\n");

        // Find all unique row indices and sort them
        Set<Integer> rowIndices = new TreeSet<>();
        for (Cell cell : sheet.getCells().values()) {
            rowIndices.add(cell.getRow());
        }

        // Add data rows, sorted by row index
        for (Integer rowIndex : rowIndices) {
            // Add row number as first column
            csv.append(rowIndex).append(",");

            // Add data for each column
            for (int i = 0; i < columns.size(); i++) {
                String columnName = columns.get(i).getName();
                Cell cell = sheet.getCell(columnName, rowIndex);

                if (cell != null) {
                    Object value = cell.getValue();
                    if (value != null) {
                        // Handle string values that might contain commas - quote them
                        if (value instanceof String && ((String) value).contains(",")) {
                            csv.append("\"").append(value).append("\"");
                        } else {
                            csv.append(value);
                        }
                    }
                }

                if (i < columns.size() - 1) {
                    csv.append(",");
                }
            }
            csv.append("\n");
        }

        log.debug("CSV conversion complete");
        return csv.toString();
    }
}
