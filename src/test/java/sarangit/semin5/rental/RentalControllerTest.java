package sarangit.semin5.rental;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

class RentalControllerTest {
    @Test
    void exportUsesSearchAndKeepsUnitPriceNumeric() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(
                Map.of("source_row", 1, "item_name", "프린터", "unit_price", 34000),
                Map.of("source_row", 2, "item_name", "전화기", "unit_price", 20000)));
        RentalController controller = new RentalController(jdbc);
        var response = controller.exportInUse("프린터");
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("SRGH%20%EB%8B%A4%ED%95%9C%EC%98%A4%EC%97%90%EC%9D%B4%20%EC%9E%84%EB%8C%80%ED%94%84%EB%A6%B0%ED%84%B0.xlsx");

        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(
                response.getBody()))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(0).getCell(2).getStringCellValue()).isEqualTo("MAC");
            assertThat(sheet.getRow(0).getCell(4).getStringCellValue()).isEqualTo("규격");
            assertThat(sheet.getRow(0).getCell(5).getStringCellValue()).isEqualTo("단가");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("프린터");
            assertThat(sheet.getRow(1).getCell(5).getNumericCellValue()).isEqualTo(34000);
            assertThat(sheet.getRow(2).getCell(3).getStringCellValue()).isEqualTo("총 1대");
            assertThat(sheet.getRow(2).getCell(5).getNumericCellValue()).isEqualTo(34000);
            for (var row : sheet) {
                for (var cell : row) {
                    assertThat(workbook.getFontAt(cell.getCellStyle().getFontIndex()).getFontName())
                            .isEqualTo("맑은 고딕");
                }
            }
        }
    }

    @Test
    void inUseListUsesEnglishTableAndVisibleColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("source_row", 3, "item_name", "Printer")));
        RentalController controller = new RentalController(jdbc);

        assertThat(controller.list("in-use").getBody()).hasSize(1);
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("FROM in_use", "item_name", "unit_price")
                .doesNotContain("mac_address", "contract_date");
    }

    @Test
    void recoveredDetailReadsAllSourceColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(3))).thenReturn(List.of(Map.of("source_row", 3, "mac_address", "AA:BB")));
        RentalController controller = new RentalController(jdbc);

        assertThat(controller.detail("recovered", 3).getBody()).containsEntry("mac_address", "AA:BB");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture(), eq(3));
        assertThat(sql.getValue()).contains("FROM recovered", "mac_address", "contract_date");
    }

    @Test
    void recoveredListMatchesInUseVisibleColumns() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString())).thenReturn(List.of(Map.of("source_row", 3)));
        RentalController controller = new RentalController(jdbc);

        controller.list("recovered");
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc).queryForList(sql.capture());
        assertThat(sql.getValue()).contains("location", "location_detail", "FROM recovered")
                .doesNotContain("contract_date");
    }

    @Test
    void moveMissingRowDoesNotWriteEitherTable() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForList(anyString(), eq(42))).thenReturn(List.of());
        RentalController controller = new RentalController(jdbc);

        assertThatThrownBy(() -> controller.move("in-use", 42))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404");
    }
}
