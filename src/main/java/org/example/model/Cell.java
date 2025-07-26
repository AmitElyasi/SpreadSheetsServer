package org.example.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Cell {
    private String column;
    private int row;
    private Object value;

    @JsonIgnore
    private String lookupFunction;

    @JsonIgnore
    private String referencedColumn;

    @JsonIgnore
    private Integer referencedRow;

    public Cell() {
    }

    public Cell(String column, int row, Object value) {
        this.column = column;
        this.row = row;
        this.value = value;
    }

    public String getColumn() {
        return column;
    }

    public void setColumn(String column) {
        this.column = column;
    }

    public int getRow() {
        return row;
    }

    public void setRow(int row) {
        this.row = row;
    }

    public Object getValue() {
        return value;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public String getLookupFunction() {
        return lookupFunction;
    }

    public void setLookupFunction(String lookupFunction) {
        this.lookupFunction = lookupFunction;
    }

    public String getReferencedColumn() {
        return referencedColumn;
    }

    public void setReferencedColumn(String referencedColumn) {
        this.referencedColumn = referencedColumn;
    }

    public Integer getReferencedRow() {
        return referencedRow;
    }

    public void setReferencedRow(Integer referencedRow) {
        this.referencedRow = referencedRow;
    }

    @JsonIgnore
    public boolean isLookupCell() {
        return lookupFunction != null && !lookupFunction.isEmpty();
    }
}
