import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * CSV loader/saver for the three <b>user directory</b> files in the project root:
 *
 * <ul>
 *   <li>{@code sample_student_list.csv}</li>
 *   <li>{@code sample_staff_list.csv}</li>
 *   <li>{@code sample_company_representative_list.csv}</li>
 * </ul>
 *
 * <p><b>Scope</b>: This utility is intentionally limited to user CSVs.
 * Opportunities and applications are handled by {@link OppAppCsv}, and per-user list
 * preferences are handled by {@link PreferenceService}. Keeping these concerns separate
 * adheres to SRP/ISP and reduces coupling.</p>
 *
 * <p><b>Behavior</b>:</p>
 * <ul>
 *   <li>Columns are matched by header names (case-insensitive; extra spaces ignored).</li>
 *   <li>Missing optional columns are tolerated (e.g., {@code Password}, {@code Approved}).</li>
 *   <li>If a file is missing, it is created with a normalized header.</li>
 * </ul>
 *
 * <p><b>SOLID notes</b>:</p>
 * <ul>
 *   <li><b>SRP</b>: Only user-directory CSV responsibilities live here.</li>
 *   <li><b>ISP</b>: Menus/services depend only on what they need—this class provides
 *       minimal, role-specific loaders/writers rather than a “fat” everything-CSV utility.</li>
 * </ul>
 *
 * @since v1.35
 */
public class CsvUtil {

    /** Students CSV filename (project root). */
    public static final String STUDENT_CSV = "sample_student_list.csv";
    /** Staff CSV filename (project root). */
    public static final String STAFF_CSV   = "sample_staff_list.csv";
    /** Company reps CSV filename (project root). */
    public static final String REPS_CSV    = "sample_company_representative_list.csv";

    /* ============================= Students ============================= */

    /**
     * Read all students from {@link #STUDENT_CSV}.
     * <p>Creates the file with a normalized header if it does not exist.</p>
     *
     * @return list of {@link Student} records (never {@code null})
     * @throws IOException if the file cannot be created or read
     */
    public static List<Student> readStudents() throws IOException {
        ensureFileWithHeader(
                STUDENT_CSV,
                "StudentID,Name,Major,Year,Email,Password"
        );
        List<Student> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(STUDENT_CSV), StandardCharsets.UTF_8)) {
            String headerLine = br.readLine(); // header
            if (headerLine == null) return out;

            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> idx = toHeaderIndex(headers);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = splitCsvLine(line);

                String id   = get(parts, idx, "studentid");
                String name = get(parts, idx, "name");
                String major= get(parts, idx, "major");
                String yearS= get(parts, idx, "year");
                String email= get(parts, idx, "email");
                String pass = get(parts, idx, "password"); // optional

                if (id == null || name == null) continue;
                int year = safeInt(yearS, 1);

                out.add(new Student(id, name, major != null ? major : "", year, email != null ? email : "", pass));
            }
        }
        return out;
    }

    /**
     * Write the given students to {@link #STUDENT_CSV} with a normalized header.
     *
     * @param students students to persist
     * @throws IOException if the file cannot be written
     */
    public static void writeStudents(List<Student> students) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(STUDENT_CSV), StandardCharsets.UTF_8)) {
            // normalized header
            bw.write(joinCsv("StudentID", "Name", "Major", "Year", "Email", "Password"));
            bw.newLine();
            for (Student s : students) {
                bw.write(joinCsv(
                        nz(s.getStudentId()),
                        nz(s.getName()),
                        nz(s.getMajor()),
                        Integer.toString(s.getYear()),
                        nz(s.getEmail()),
                        nz(s.getPassword())
                ));
                bw.newLine();
            }
        }
    }

    /* ============================== Staff ============================== */

    /**
     * Read all staff from {@link #STAFF_CSV}.
     * <p>Creates the file with a normalized header if it does not exist.</p>
     *
     * @return list of {@link Staff} records (never {@code null})
     * @throws IOException if the file cannot be created or read
     */
    public static List<Staff> readStaff() throws IOException {
        ensureFileWithHeader(
                STAFF_CSV,
                "StaffID,Name,Department,Email,Password"
        );
        List<Staff> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(STAFF_CSV), StandardCharsets.UTF_8)) {
            String headerLine = br.readLine();
            if (headerLine == null) return out;

            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> idx = toHeaderIndex(headers);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = splitCsvLine(line);

                String id   = get(parts, idx, "staffid");
                String name = get(parts, idx, "name");
                String dept = get(parts, idx, "department");
                String email= get(parts, idx, "email");
                String pass = get(parts, idx, "password"); // optional

                if (id == null || name == null) continue;
                out.add(new Staff(id, name, dept != null ? dept : "", email != null ? email : "", pass));
            }
        }
        return out;
    }

    /**
     * Write the given staff to {@link #STAFF_CSV} with a normalized header.
     *
     * @param staff staff list to persist
     * @throws IOException if the file cannot be written
     */
    public static void writeStaff(List<Staff> staff) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(STAFF_CSV), StandardCharsets.UTF_8)) {
            bw.write(joinCsv("StaffID", "Name", "Department", "Email", "Password"));
            bw.newLine();
            for (Staff s : staff) {
                bw.write(joinCsv(
                        nz(s.getStaffId()),
                        nz(s.getName()),
                        nz(s.getDepartment()),
                        nz(s.getEmail()),
                        nz(s.getPassword())
                ));
                bw.newLine();
            }
        }
    }

    /* ========================= Company Reps ============================ */

    /**
     * Read all company representatives from {@link #REPS_CSV}.
     * <p>Creates the file with a normalized header if it does not exist.</p>
     *
     * @return list of {@link CompanyRep} records (never {@code null})
     * @throws IOException if the file cannot be created or read
     */
    public static List<CompanyRep> readCompanyReps() throws IOException {
        ensureFileWithHeader(
                REPS_CSV,
                "Email,Name,Company,Department,Position,Approved,Password"
        );
        List<CompanyRep> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(REPS_CSV), StandardCharsets.UTF_8)) {
            String headerLine = br.readLine();
            if (headerLine == null) return out;

            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> idx = toHeaderIndex(headers);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = splitCsvLine(line);

                String email = get(parts, idx, "email");
                String name  = get(parts, idx, "name");
                String comp  = get(parts, idx, "company");
                String dept  = get(parts, idx, "department");
                String pos   = get(parts, idx, "position");
                String apprS = get(parts, idx, "approved"); // optional
                String pass  = get(parts, idx, "password"); // optional

                if (email == null || name == null) continue;
                boolean approved = safeBool(apprS, false);

                out.add(new CompanyRep(
                        email,
                        name,
                        comp != null ? comp : "",
                        dept != null ? dept : "",
                        pos != null ? pos : "",
                        approved,
                        pass
                ));
            }
        }
        return out;
    }

    /**
     * Write the given representatives to {@link #REPS_CSV} with a normalized header.
     *
     * @param reps representative list to persist
     * @throws IOException if the file cannot be written
     */
    public static void writeCompanyReps(List<CompanyRep> reps) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(REPS_CSV), StandardCharsets.UTF_8)) {
            bw.write(joinCsv("Email", "Name", "Company", "Department", "Position", "Approved", "Password"));
            bw.newLine();
            for (CompanyRep r : reps) {
                bw.write(joinCsv(
                        nz(r.getEmail()),
                        nz(r.getName()),
                        nz(r.getCompany()),
                        nz(r.getDepartment()),
                        nz(r.getPosition()),
                        Boolean.toString(r.isApproved()),
                        nz(r.getPassword())
                ));
                bw.newLine();
            }
        }
    }

    /* ============================ Helpers ============================== */

    /**
     * Create the given file with the provided header line if it does not already exist.
     *
     * @param filename file path (project root)
     * @param header   header line to write
     * @throws IOException if the file cannot be created
     */
    private static void ensureFileWithHeader(String filename, String header) throws IOException {
        Path p = Path.of(filename);
        if (!Files.exists(p)) {
            try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                bw.write(header);
                bw.newLine();
            }
        }
    }

    /**
     * Split a CSV line into cells, supporting quotes and doubled-quote escapes.
     * <p>No external libraries; minimal RFC4180-like behavior.</p>
     *
     * @param line raw CSV line
     * @return cells array (never {@code null})
     */
    private static String[] splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '\"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '\"') {
                        // Escaped quote
                        cur.append('\"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    cur.append(c);
                }
            } else {
                if (c == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else if (c == '\"') {
                    inQuotes = true;
                } else {
                    cur.append(c);
                }
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    /**
     * Join fields into a CSV line, quoting fields that contain commas, quotes, or newlines.
     * Quotes are escaped by doubling them.
     *
     * @param fields ordered fields to join (may contain {@code null})
     * @return a single CSV line string
     */
    private static String joinCsv(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            String f = fields[i] == null ? "" : fields[i];
            boolean needsQuote = f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r");
            if (needsQuote) {
                sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(f);
            }
            if (i + 1 < fields.length) sb.append(',');
        }
        return sb.toString();
    }

    /**
     * Build a header-name-to-index map with case-insensitive keys (spaces removed).
     *
     * @param headers header row cells
     * @return map from normalized header name to column index
     */
    private static Map<String, Integer> toHeaderIndex(String[] headers) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String key = headers[i] == null ? "" : headers[i].toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            m.put(key, i);
        }
        return m;
    }

    /**
     * Safely fetch a field from a row by normalized header name.
     *
     * @param parts row cells
     * @param idx   header index map (from {@link #toHeaderIndex(String[])})
     * @param keyLower normalized header name (lowercase, spaces removed)
     * @return trimmed string or {@code null} if missing/out-of-bounds
     */
    private static String get(String[] parts, Map<String, Integer> idx, String keyLower) {
        Integer i = idx.get(keyLower);
        if (i == null || i < 0 || i >= parts.length) return null;
        String v = parts[i];
        return v != null ? v.trim() : null;
    }

    /**
     * Parse an integer with a default fallback.
     *
     * @param s   source string
     * @param def fallback value on failure
     * @return parsed integer or {@code def}
     */
    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    /**
     * Parse a boolean with a permissive set of truthy/falsey tokens.
     * Truthy: {@code true, 1, yes, y}. Falsey: {@code false, 0, no, n}.
     *
     * @param s   source string
     * @param def fallback value when unknown
     * @return parsed boolean or {@code def}
     */
    private static boolean safeBool(String s, boolean def) {
        if (s == null) return def;
        String t = s.trim().toLowerCase(Locale.ROOT);
        if (t.equals("true") || t.equals("1") || t.equals("yes") || t.equals("y")) return true;
        if (t.equals("false") || t.equals("0") || t.equals("no") || t.equals("n")) return false;
        return def;
    }

    /**
     * Null-to-empty helper for consistent CSV writing.
     *
     * @param s input string
     * @return {@code ""} if {@code s} is {@code null}; otherwise {@code s}
     */
    private static String nz(String s) { return s == null ? "" : s; }
}
