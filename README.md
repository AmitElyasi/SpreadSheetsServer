# SpreadSheets Server

A HTTP server that manages spreadsheets (in-memory only) with lookup functionality and type validation.

## Features

- Create sheets with custom schemas and column types (string, int, boolean, double)
- Set and retrieve cell values with type validation
- Support for lookup functions that reference other cells
- Cycle detection in cell references
- CSV export functionality with row numbers
- Comprehensive logging

## Requirements

- Java 17 or higher
- Maven 3.6 or higher (Optional for building)

## Building the Application (Optional)

To build the application, run:

```bash
mvn clean package
```

This will create an executable JAR file in the `target` directory.

## Running the Server

There are several ways to run the application:

### Using the JAR file

```bash
java -jar target/SpreadSheetsServer-1.0-SNAPSHOT.jar
```
### Using Maven

```bash
mvn spring-boot:run
```

### Production Mode (Minimal Console Output)

```bash
java -jar target/SpreadSheetsServer-1.0-SNAPSHOT.jar --spring.profiles.active=prod
```

By default, the server starts on port 8080. You can change this in the `application.properties` file.

## API Documentation

### Create a New Sheet

Creates a new spreadsheet with the specified column schema.

```
POST /api/sheets
```

Request body example:
```json
{
  "columns": [
    {"name": "A", "type": "string"},
    {"name": "B", "type": "int"},
    {"name": "C", "type": "boolean"},
    {"name": "D", "type": "double"}
  ]
}
```

You can also specify a custom sheet ID:
```json
{
  "id": "my-custom-sheet",
  "columns": [
    {"name": "A", "type": "string"},
    {"name": "B", "type": "int"}
  ]
}
```

Response:
```json
{
  "id": "your-sheet-id"
}
```

### Get a Sheet

Retrieves a sheet by its ID. Returns CSV format.

```
GET /api/sheets/{sheetId}
```

Example CSV output:
```
Row,A,B,C
1,Hello,42,true
3,World,99,
5,"Hello, World",,
```

### Set a Cell Value

Sets a value in a specific cell of the sheet. The value is validated against the column's type.

```
PUT /api/sheets/{sheetId}/cells/{columnName}/{rowIndex}
```

Request body example:
```json
{
  "value": "Hello World"
}
```

For numbers:
```json
{
  "value": 42
}
```

For boolean:
```json
{
  "value": true
}
```

For lookup function:
```json
{
  "value": "lookup(A,10)"
}
```

## Lookup Function

The `lookup` function references another cell's value. For example, `lookup(A,10)` references the value in column A, row 10.

Type validation ensures that the referenced cell's type matches the current cell's type. Cycles in cell references are not allowed and will be detected.

Example:
```
A1 -> "Hello"
B1 -> lookup(A,1) // B1 will have the value "Hello"
```

If A1 is updated, B1 will automatically update to reflect the new value.

## Logs

Logs are written to both console and file:
- Console logs show INFO level and above by default
- File logs include all levels (DEBUG and above)
- Error logs are separately captured in their own file

Log files are stored in the `logs` directory:
- Main log: `logs/spreadsheet-server.log`
- Error log: `logs/spreadsheet-server-errors.log`

## Testing

The application includes comprehensive tests:
- Unit tests for both service and controller layers
- Integration tests with a real HTTP client

To run all tests:
```bash
mvn test
```

## Postman Collection

A Postman collection is included in `spreadsheet-api-postman.json` for easy testing of the API.
