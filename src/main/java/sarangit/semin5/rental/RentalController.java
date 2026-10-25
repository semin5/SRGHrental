package sarangit.semin5.rental;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class RentalController {
    private static final List<ExportColumn> IN_USE_EXPORT = List.of(
            new ExportColumn("item_name", "품명"), new ExportColumn("ip_address", "IP"),
            new ExportColumn("mac_address", "MAC"), new ExportColumn("model", "모델"),
            new ExportColumn("specification", "규격"), new ExportColumn("unit_price", "단가"),
            new ExportColumn("location", "위치"), new ExportColumn("location_detail", "위치 (세부)"),
            new ExportColumn("remarks", "비고"));
    private static final Map<String, TableSpec> TABLES = Map.of(
            "in-use", new TableSpec("in_use", List.of(
                    "item_name", "ip_address", "mac_address", "model", "specification",
                    "unit_price", "location", "location_detail", "remarks", "contract_date"),
                    List.of("item_name", "ip_address", "model", "specification", "unit_price", "location",
                            "location_detail", "remarks")),
            "recovered", new TableSpec("recovered", List.of(
                    "item_name", "ip_address", "mac_address", "model", "specification",
                    "unit_price", "location", "location_detail", "remarks", "contract_date"),
                    List.of("item_name", "ip_address", "model", "unit_price", "location",
                            "location_detail", "remarks")));
    private final JdbcTemplate jdbc;

    public RentalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private TableSpec table(String tab) {
        TableSpec spec = TABLES.get(tab);
        if (spec == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return spec;
    }

    @GetMapping("/{tab}")
    public ResponseEntity<List<Map<String, Object>>> list(@PathVariable String tab) {
        TableSpec spec = table(tab);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jdbc.queryForList(
                "SELECT source_row, " + String.join(", ", spec.listColumns())
                        + " FROM " + spec.name() + " ORDER BY source_row"));
    }

    @GetMapping(value = "/in-use/export", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> exportInUse(@RequestParam(defaultValue = "") String search) {
        TableSpec spec = table("in-use");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT source_row, "
                + String.join(", ", spec.columns()) + " FROM " + spec.name() + " ORDER BY source_row");
        String needle = search.trim().toLowerCase(Locale.KOREAN);
        if (!needle.isEmpty()) rows = rows.stream().filter(row -> spec.listColumns().stream().anyMatch(column -> {
            Object value = row.get(column);
            return value != null && value.toString().toLowerCase(Locale.KOREAN).contains(needle);
        })).toList();
        try (XSSFWorkbook book = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("사용");
            sheet.createFreezePane(0, 1);
            var headingStyle = book.createCellStyle();
            headingStyle.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            headingStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var headingFont = book.createFont();
            headingFont.setFontName("맑은 고딕");
            headingFont.setBold(true);
            headingStyle.setFont(headingFont);
            var bodyFont = book.createFont();
            bodyFont.setFontName("맑은 고딕");
            CellStyle bodyStyle = book.createCellStyle();
            bodyStyle.setFont(bodyFont);
            CellStyle priceStyle = book.createCellStyle();
            priceStyle.setFont(bodyFont);
            priceStyle.setDataFormat(book.createDataFormat().getFormat("#,##0"));
            Row heading = sheet.createRow(0);
            for (int index = 0; index < IN_USE_EXPORT.size(); index++) {
                var cell = heading.createCell(index);
                cell.setCellValue(IN_USE_EXPORT.get(index).label());
                cell.setCellStyle(headingStyle);
                sheet.setColumnWidth(index, IN_USE_EXPORT.get(index).key().equals("remarks") ? 48 * 256 : 24 * 256);
            }
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row excelRow = sheet.createRow(rowIndex + 1);
                Map<String, Object> data = rows.get(rowIndex);
                for (int columnIndex = 0; columnIndex < IN_USE_EXPORT.size(); columnIndex++) {
                    Object value = data.get(IN_USE_EXPORT.get(columnIndex).key());
                    var cell = excelRow.createCell(columnIndex);
                    if (value instanceof Number number) cell.setCellValue(number.doubleValue());
                    else cell.setCellValue(value == null ? "" : value.toString());
                    cell.setCellStyle(IN_USE_EXPORT.get(columnIndex).key().equals("unit_price")
                            ? priceStyle : bodyStyle);
                }
            }
            Row totalRow = sheet.createRow(rows.size() + 1);
            CellStyle totalStyle = book.createCellStyle();
            var totalFont = book.createFont();
            totalFont.setFontName("맑은 고딕");
            totalFont.setBold(true);
            totalStyle.setFont(totalFont);
            totalStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            totalStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            for (int index = 0; index < IN_USE_EXPORT.size(); index++) totalRow.createCell(index).setCellStyle(totalStyle);
            totalRow.getCell(3).setCellValue("총 " + rows.size() + "대");
            totalRow.getCell(4).setCellValue("단가 합계");
            if (rows.isEmpty()) totalRow.getCell(5).setCellValue(0);
            else totalRow.getCell(5).setCellFormula("SUM(F2:F" + (rows.size() + 1) + ")");
            CellStyle totalAmountStyle = book.createCellStyle();
            totalAmountStyle.cloneStyleFrom(totalStyle);
            totalAmountStyle.setDataFormat(book.createDataFormat().getFormat("#,##0"));
            totalRow.getCell(5).setCellStyle(totalAmountStyle);
            totalRow.getCell(6).setCellValue("원");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll();
            if (!rows.isEmpty()) sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(
                    0, rows.size(), 0, IN_USE_EXPORT.size() - 1));
            book.write(output);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment()
                                    .filename("SRGH 다한오에이 임대프린터.xlsx", StandardCharsets.UTF_8)
                                    .build().toString())
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(output.toByteArray());
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "엑셀 파일을 만들지 못했습니다", exception);
        }
    }

    @GetMapping("/{tab}/{id}")
    public ResponseEntity<Map<String, Object>> detail(@PathVariable String tab, @PathVariable int id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(find(table(tab), id));
    }

    private Map<String, Object> find(TableSpec spec, int id) {
        return jdbc.queryForList("SELECT source_row, " + String.join(", ", spec.columns())
                + " FROM " + spec.name() + " WHERE source_row = ?", id).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Map<String, Object> findListRow(TableSpec spec, int id) {
        return jdbc.queryForList("SELECT source_row, " + String.join(", ", spec.listColumns())
                + " FROM " + spec.name() + " WHERE source_row = ?", id).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Object value(Map<String, Object> values, String column) {
        Object raw = values.get(column);
        if (raw == null || raw.toString().isBlank()) return null;
        if (column.equals("unit_price")) {
            try {
                return Integer.valueOf(raw.toString().trim());
            } catch (NumberFormatException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, column + " must be a number");
            }
        }
        String text = raw.toString();
        if (text.length() > (column.equals("remarks") ? 4000 : 255))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Value is too long");
        return text;
    }

    @PostMapping(value = "/{tab}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> create(@PathVariable String tab, @RequestBody Map<String, Object> values) {
        TableSpec spec = table(tab);
        List<Object> parameters = spec.columns().stream().map(column -> value(values, column)).toList();
        return findListRow(spec, insert(spec, parameters));
    }

    private int insert(TableSpec spec, List<Object> parameters) {
        String columns = String.join(", ", spec.columns());
        String placeholders = String.join(", ", spec.columns().stream().map(ignored -> "?").toList());
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO " + spec.name() + " (" + columns
                    + ") VALUES (" + placeholders + ")", new String[]{"source_row"});
            for (int i = 0; i < parameters.size(); i++) statement.setObject(i + 1, parameters.get(i));
            return statement;
        }, key);
        if (key.getKey() == null) throw new IllegalStateException("Missing generated rental row ID");
        return key.getKey().intValue();
    }

    @PostMapping("/{tab}/{id}/move")
    @Transactional
    public Map<String, Object> move(@PathVariable String tab, @PathVariable int id) {
        TableSpec source = table(tab);
        String destinationTab = tab.equals("in-use") ? "recovered" : "in-use";
        TableSpec destination = table(destinationTab);
        Map<String, Object> row = jdbc.queryForList("SELECT source_row, " + String.join(", ", source.columns())
                + " FROM " + source.name() + " WHERE source_row = ? FOR UPDATE", id).stream()
                .findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        int destinationId = insert(destination, destination.columns().stream().map(row::get).toList());
        if (jdbc.update("DELETE FROM " + source.name() + " WHERE source_row = ?", id) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        return Map.of("destinationTab", destinationTab, "sourceRow", destinationId);
    }

    @PutMapping(value = "/{tab}/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> update(@PathVariable String tab, @PathVariable int id,
                                      @RequestBody Map<String, Object> values) {
        TableSpec spec = table(tab);
        List<Object> parameters = new ArrayList<>();
        for (String column : spec.columns()) parameters.add(value(values, column));
        parameters.add(id);
        String assignments = String.join(", ", spec.columns().stream().map(column -> column + " = ?").toList());
        if (jdbc.update("UPDATE " + spec.name() + " SET " + assignments + " WHERE source_row = ?",
                parameters.toArray()) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        return findListRow(spec, id);
    }

    @DeleteMapping("/{tab}/{id}")
    public void delete(@PathVariable String tab, @PathVariable int id) {
        TableSpec spec = table(tab);
        if (jdbc.update("DELETE FROM " + spec.name() + " WHERE source_row = ?", id) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    private record TableSpec(String name, List<String> columns, List<String> listColumns) {}
    private record ExportColumn(String key, String label) {}
}
