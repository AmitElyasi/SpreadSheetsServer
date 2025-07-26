package org.example.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Sheet {
    private String id;
    private List<Column> columns;
    private Map<String, Cell> cells;

    public Sheet() {
        this.columns = new ArrayList<>();
        this.cells = new HashMap<>();
    }

    public Sheet(String id, List<Column> columns) {
        this.id = id;
        this.columns = columns;
        this.cells = new HashMap<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Column> getColumns() {
        return columns;
    }

    public void setColumns(List<Column> columns) {
        this.columns = columns;
    }

    public Map<String, Cell> getCells() {
        return cells;
    }

    public void setCells(Map<String, Cell> cells) {
        this.cells = cells;
    }

    public void addCell(Cell cell) {
        String cellKey = generateCellKey(cell.getColumn(), cell.getRow());
        cells.put(cellKey, cell);
    }

    public Cell getCell(String columnName, int rowIndex) {
        String cellKey = generateCellKey(columnName, rowIndex);
        return cells.get(cellKey);
    }

    public String generateCellKey(String columnName, int rowIndex) {
        return columnName + "," + rowIndex;
    }

    public Column getColumnByName(String columnName) {
        return columns.stream()
                .filter(column -> column.getName().equals(columnName))
                .findFirst()
                .orElse(null);
    }
}
