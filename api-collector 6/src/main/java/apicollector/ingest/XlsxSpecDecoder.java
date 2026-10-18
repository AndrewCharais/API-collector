package apicollector.ingest;

import org.apache.poi.ss.usermodel.*;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the first sheet of an .xlsx workbook into rows of strings, then reuses the
 * already-tested {@link TabularImporter#fromRows}. POI only does the decode; column
 * detection (method/path, or combined "GET /path") lives in TabularImporter and is shared
 * with the CSV path.
 */
public final class XlsxSpecDecoder {
    private final TabularImporter tabular = new TabularImporter();

    public List<ImportedEndpoint> decode(InputStream xlsx, String source) throws Exception {
        try (Workbook wb = WorkbookFactory.create(xlsx)) {
            Sheet sheet = wb.getNumberOfSheets() > 0 ? wb.getSheetAt(0) : null;
            List<List<String>> rows = new ArrayList<>();
            if (sheet != null) {
                DataFormatter fmt = new DataFormatter();
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    int last = row.getLastCellNum();
                    for (int c = 0; c < last; c++) {
                        Cell cell = row.getCell(c, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        cells.add(cell == null ? "" : fmt.formatCellValue(cell).trim());
                    }
                    rows.add(cells);
                }
            }
            return tabular.fromRows(rows, source);
        }
    }
}
