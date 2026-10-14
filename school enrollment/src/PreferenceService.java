import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Concrete CSV-backed implementation of {@link PreferencesStore} for a CLI session.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Maintain per-user, per-view {@link ListPreferences} in-memory.</li>
 *   <li>Persist/load preferences to/from a simple CSV file (<code>user_prefs.csv</code>).</li>
 *   <li>Provide a narrow API ({@link #get(User, String)} / {@link #put(User, String, ListPreferences)})
 *       to satisfy ISP/DIP when used by menus.</li>
 * </ul>
 *
 * <h3>CSV Layout</h3>
 * Header:
 * <pre>
 * role,userKey,view,filter_col,filter_kw,sort_col
 * </pre>
 * Rows:
 * <pre>
 * STUDENT, U1234567A, student_opps, TITLE, "Data", EMPTY_SLOTS
 * </pre>
 *
 * <h3>Notes</h3>
 * <ul>
 *   <li>Singleton for CLI scope (not thread-safe by design).</li>
 *   <li>Keys are composed as: {@code role|userKey|view} (internal detail, encapsulated).</li>
 *   <li>Requires {@link Column#toCsv()} and {@link Column#fromCsv(String)} to map enum values.</li>
 * </ul>
 *
 * @author
 * @since v1.3
 */
public final class PreferenceService implements PreferencesStore {

    /** Singleton instance for the current CLI process. */
    private static final PreferenceService INSTANCE = new PreferenceService();

    /**
     * Obtain the singleton instance.
     * @return shared {@code PreferenceService}
     */
    public static PreferenceService getInstance() {
        return INSTANCE;
    }

    /** CSV file storing preferences. */
    private final Path file = Paths.get("user_prefs.csv");

    /** In-memory store keyed by "role|userKey|view". */
    private final Map<String, ListPreferences> byKey = new HashMap<>();

    /** Private ctor (singleton); eagerly loads from disk if present. */
    private PreferenceService() {
        load();
    }

    // ---------------------------------------------------------------------
    // Public API (PreferencesStore)  —  ISP/DIP-friendly narrow surface
    // ---------------------------------------------------------------------

    /**
     * Get the preferences for a specific user and view. If none exist yet, a new
     * {@link ListPreferences} instance is created, cached, and returned (never {@code null}).
     *
     * @param u    the user (must not be {@code null})
     * @param view logical view name (e.g., {@code "student_opps"}), must not be {@code null}
     * @return a non-null {@link ListPreferences} snapshot for this user+view
     */
    @Override
    public ListPreferences get(User u, String view) {
        String k = key(roleOf(u), userKeyOf(u), view);
        return byKey.computeIfAbsent(k, kk -> new ListPreferences());
    }

    /**
     * Persist (upsert) the given preferences for a user and view.
     *
     * @param u    the user (must not be {@code null})
     * @param view logical view name (must not be {@code null})
     * @param p    the preferences (if {@code null}, an empty {@link ListPreferences} will be stored)
     */
    @Override
    public void put(User u, String view, ListPreferences p) {
        byKey.put(key(roleOf(u), userKeyOf(u), view), (p == null ? new ListPreferences() : p));
        save();
    }

    // ---------------------------------------------------------------------
    // Internals — keying & persistence (encapsulated)
    // ---------------------------------------------------------------------

    /**
     * Build the internal map key. Uses {@code '|'} which is safe with proper split limit.
     */
    private String key(String role, String userKey, String view) {
        // '|' is safe; we split with "\\|" and a limit later
        return role + "|" + userKey + "|" + view;
    }

    /**
     * Load all preferences from {@link #file} into {@link #byKey}.
     * Missing files are tolerated (created on first save).
     *
     * @implNote Accepts rows with the exact 6 columns under the documented header.
     */
    private void load() {
        byKey.clear();
        if (!Files.exists(file)) return; // create on first save

        List<String[]> rows = readCsvLocal(file);
        if (rows.isEmpty()) return;

        // Expect header: role,userKey,view,filter_col,filter_kw,sort_col
        for (int r = 1; r < rows.size(); r++) {
            String[] row = rows.get(r);
            if (row == null || row.length < 6) continue;

            String role  = safeTrim(row[0]);
            String userK = safeTrim(row[1]);
            String view  = safeTrim(row[2]);
            String fcol  = row[3];
            String fkw   = row[4];
            String scol  = row[5];

            if (role.isEmpty() || userK.isEmpty() || view.isEmpty()) continue;

            ListPreferences p = new ListPreferences();
            p.setFilterColumn(Column.fromCsv(fcol));
            p.setFilterKeyword((fkw == null || fkw.isBlank()) ? null : fkw);
            p.setSortColumn(Column.fromCsv(scol));

            byKey.put(key(role, userK, view), p);
        }
    }

    /**
     * Save all preferences from {@link #byKey} into {@link #file}, overwriting existing content.
     *
     * @implNote Writes RFC4180-ish CSV with quotes where needed. Header is always written.
     */
    private void save() {
        List<String[]> out = new ArrayList<>();
        out.add(new String[]{"role", "userKey", "view", "filter_col", "filter_kw", "sort_col"});

        for (Map.Entry<String, ListPreferences> e : byKey.entrySet()) {
            // Use a split with limit to preserve embedded '|' in the view if any
            String[] parts = e.getKey().split("\\|", 3);
            String role  = parts.length > 0 ? parts[0] : "";
            String userK = parts.length > 1 ? parts[1] : "";
            String view  = parts.length > 2 ? parts[2] : "";

            ListPreferences p = e.getValue();
            out.add(new String[]{
                role,
                userK,
                view,
                (p.getFilterColumn() == null ? "" : p.getFilterColumn().toCsv()),
                (p.getFilterKeyword() == null ? "" : p.getFilterKeyword()),
                (p.getSortColumn() == null ? "" : p.getSortColumn().toCsv())
            });
        }

        writeCsvLocal(file, out);
    }

    // ---------------------------------------------------------------------
    // Role & key helpers
    // ---------------------------------------------------------------------

    /**
     * Map a concrete user subtype to its stable role label for persistence.
     */
    private String roleOf(User u) {
        if (u instanceof Student)    return "STUDENT";
        if (u instanceof Staff)      return "STAFF";
        if (u instanceof CompanyRep) return "REP";
        return "USER";
    }

    /**
     * Extract the stable, unique user key used in the CSV (varies per role).
     */
    private String userKeyOf(User u) {
        if (u instanceof Student)    return ((Student) u).getStudentId();
        if (u instanceof Staff)      return ((Staff) u).getStaffId();
        if (u instanceof CompanyRep) return ((CompanyRep) u).getEmail();
        return u.getEmail(); // fallback for generic User
    }

    private static String safeTrim(String s) {
        return (s == null) ? "" : s.trim();
    }

    // ---------------------------------------------------------------------
    // Minimal CSV helpers (RFC4180-ish): supports quotes and "" escapes.
    // ---------------------------------------------------------------------

    /**
     * Read all CSV lines into a list of string arrays (cells).
     *
     * @param path file path
     * @return rows as {@code List<String[]>} (never {@code null})
     */
    private static List<String[]> readCsvLocal(Path path) {
        List<String[]> out = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                out.add(parseCsvLine(line));
            }
        } catch (Exception e) {
            System.err.println("PreferenceService: failed to read " + path + ": " + e.getMessage());
        }
        return out;
    }

    /**
     * Write all rows to the given path, replacing file contents.
     *
     * @param path target file
     * @param rows rows to write (header + data)
     */
    private static void writeCsvLocal(Path path, List<String[]> rows) {
        try {
            List<String> lines = new ArrayList<>(rows.size());
            for (String[] row : rows) {
                lines.add(joinCsvRow(row));
            }
            Files.write(path, lines, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (Exception e) {
            System.err.println("PreferenceService: failed to write " + path + ": " + e.getMessage());
        }
    }

    /**
     * Parse one CSV line into cells. Supports quoted cells and double-quote escaping.
     */
    private static String[] parseCsvLine(String line) {
        if (line == null) return new String[]{ "" };
        List<String> cells = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '\"') {
                    // double quote inside quotes -> escaped quote
                    if (i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                        sb.append('\"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    sb.append(c);
                }
            } else {
                if (c == ',') {
                    cells.add(sb.toString());
                    sb.setLength(0);
                } else if (c == '\"') {
                    inQuotes = true;
                } else {
                    sb.append(c);
                }
            }
        }
        cells.add(sb.toString());
        return cells.toArray(new String[0]);
    }

    /**
     * Join cells into a CSV line, applying quotes where necessary.
     */
    private static String joinCsvRow(String[] row) {
        if (row == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < row.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escapeCsvCell(row[i]));
        }
        return sb.toString();
    }

    /**
     * Escape one CSV cell (quote if it contains comma, quote, or newlines).
     */
    private static String escapeCsvCell(String v) {
        if (v == null) v = "";
        boolean needQuotes = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r");
        if (!needQuotes) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }
}
