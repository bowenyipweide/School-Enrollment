import java.util.ArrayList;
import java.util.List;

/**
 * Utility for rendering simple ASCII tables in a console/CLI.
 * <p>
 * Designed for menu-style displays in this project. It computes per-column widths
 * from the header and provided rows, then prints a boxed table using {@code |} for
 * column separators and {@code -, =} for borders (with {@code =} under the header).
 * </p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * String[] header = {"Option", "Description"};
 * List<String[]> rows = TablePrinter.list(
 *     TablePrinter.row("1", "Login"),
 *     TablePrinter.row("0", "Exit")
 * );
 * TablePrinter.printMenu("Main Menu", header, rows);
 * }</pre>
 *
 * <h3>Assumptions</h3>
 * <ul>
 *   <li>Output is rendered in a monospaced console.</li>
 *   <li>All rows must have exactly the same number of columns as the header.</li>
 *   <li>Cell contents are printed verbatim (no wrapping or truncation).</li>
 * </ul>
 *
 * @since v1.3
 * @version v1.35
 */
public final class TablePrinter {
    /** Static-only utility class. */
    private TablePrinter() {}

    /**
     * Prints a titled table with borders and aligned columns.
     *
     * @param title  title line printed above the table
     * @param header non-null header cells; must have at least one column
     * @param rows   row data; each row must have exactly {@code header.length} cells;
     *               if {@code null}, treated as empty
     * @throws IllegalArgumentException if header is null/empty or any row has a different column count
     */
    public static void printMenu(String title, String[] header, List<String[]> rows) {
        if (header == null || header.length == 0) {
            throw new IllegalArgumentException("Header must have at least one column.");
        }
        if (rows == null) rows = new ArrayList<>();

        int cols = header.length;
        for (String[] r : rows) {
            if (r == null || r.length != cols) {
                throw new IllegalArgumentException("Every row must have exactly " + cols + " columns.");
            }
        }

        int[] widths = computeWidths(header, rows);
        String top    = line('+', '-', widths);
        String mid    = line('+', '=', widths);
        String bottom = top;

        System.out.println();
        System.out.println("== " + title + " ==");
        System.out.println(top);
        printRow(header, widths);
        System.out.println(mid);
        for (String[] r : rows) {
            printRow(r, widths);
        }
        System.out.println(bottom);
    }

    /**
     * Computes the visual width of each column based on the longest cell
     * (considering both header and rows).
     */
    private static int[] computeWidths(String[] header, List<String[]> rows) {
        int cols = header.length;
        int[] widths = new int[cols];
        for (int c = 0; c < cols; c++) widths[c] = safeLen(header[c]);
        for (String[] r : rows) {
            for (int c = 0; c < cols; c++) widths[c] = Math.max(widths[c], safeLen(r[c]));
        }
        return widths;
    }

    /**
     * Builds a horizontal border line (e.g., +-----+---+) using the given fill char.
     */
    private static String line(char joint, char fill, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append(joint);
        for (int w : widths) {
            sb.append(repeat(fill, w + 2)).append(joint);
        }
        return sb.toString();
    }

    /**
     * Prints one table row with padding based on the precomputed column widths.
     */
    private static void printRow(String[] cells, int[] widths) {
        StringBuilder sb = new StringBuilder();
        sb.append('|');
        for (int c = 0; c < cells.length; c++) {
            sb.append(' ')
              .append(padRight(cells[c], widths[c]))
              .append(' ')
              .append('|');
        }
        System.out.println(sb);
    }

    /**
     * Pads a string on the right with spaces to the requested length.
     */
    private static String padRight(String s, int len) {
        if (s == null) s = "";
        if (s.length() >= len) return s;
        StringBuilder sb = new StringBuilder(len);
        sb.append(s);
        while (sb.length() < len) sb.append(' ');
        return sb.toString();
    }

    /** Null-safe length helper. */
    private static int safeLen(String s) { return s == null ? 0 : s.length(); }

    /** Repeats a character {@code n} times. */
    private static String repeat(char ch, int n) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(ch);
        return sb.toString();
    }

    // ---------------------- Row-building conveniences ----------------------

    /**
     * Convenience to create a single row (array of cells).
     * @param cols cells for the row
     * @return the same array (no copy)
     */
    public static String[] row(String... cols) { return cols; }

    /**
     * Convenience to build a {@link List} of rows for {@link #printMenu}.
     * @param rows rows to include (each must be non-null)
     * @return a mutable {@link ArrayList} containing the rows
     */
    public static List<String[]> list(String[]... rows) {
        List<String[]> l = new ArrayList<>();
        for (String[] r : rows) l.add(r);
        return l;
    }
}
