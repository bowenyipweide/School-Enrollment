import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * CSV persistence utility for <b>Internship Opportunities</b> and <b>Applications</b>.
 * <p>
 * This class is intentionally focused on a single responsibility: reading/writing
 * {@code opportunities.csv} and {@code applications.csv}. User records and preference
 * storage are handled by their own persistence components (see v1.35 report: ISP/DIP).
 *
 * <h3>Files (in project root)</h3>
 * <ul>
 *   <li><b>opportunities.csv</b> — header created if missing:
 *   <pre>ID,CreatorEmail,CompanyName,Title,Description,Level,PreferredMajor,OpeningDate,ClosingDate,Slots,Confirmed,Status</pre>
 *   </li>
 *   <li><b>applications.csv</b> — header created if missing (v1.35+):
 *   <pre>StudentID,OpportunityID,Status,AppliedAt,AppealCount,AppealMessage</pre>
 *   </li>
 * </ul>
 *
 * <h3>Model mapping</h3>
 * <ul>
 *   <li>{@link InternshipOpportunity} &harr; rows in {@code opportunities.csv}</li>
 *   <li>{@link Application} &harr; rows in {@code applications.csv} (with appeals fields in v1.35)</li>
 * </ul>
 *
 * <h3>Back-compat</h3>
 * Older {@code applications.csv} without appeal columns are still readable:
 * {@code AppealCount} defaults to {@code 0}, {@code AppealMessage} to {@code ""}.
 *
 * @since v1.3 (initial)
 * @version v1.35 (appeals columns added)
 */
public class OppAppCsv {

    /** Filename for internship opportunities CSV. */
    public static final String OPP_CSV = "opportunities.csv";

    /** Filename for applications CSV (includes AppealCount/AppealMessage since v1.35). */
    public static final String APP_CSV = "applications.csv";

    /* ============================ Opportunities ============================ */

    /**
     * Loads internship opportunities from {@link #OPP_CSV}.
     * <p>If the file is missing, it is created with the correct header and an empty list is returned.</p>
     *
     * @return list of {@link InternshipOpportunity} instances (never {@code null})
     * @throws IOException if an I/O error occurs creating or reading the file
     */
    public static List<InternshipOpportunity> readOpportunities() throws IOException {
        ensureFileWithHeader(OPP_CSV,
                "ID,CreatorEmail,CompanyName,Title,Description,Level,PreferredMajor,OpeningDate,ClosingDate,Slots,Confirmed,Status"
        );
        List<InternshipOpportunity> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(OPP_CSV), StandardCharsets.UTF_8)) {
            String headerLine = br.readLine();
            if (headerLine == null) return out;

            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> idx = toHeaderIndex(headers);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = splitCsvLine(line);

                String id     = get(p, idx, "id");
                String email  = get(p, idx, "creatoremail");
                String comp   = get(p, idx, "companyname");
                String title  = get(p, idx, "title");
                String desc   = get(p, idx, "description");
                String level  = get(p, idx, "level");
                String major  = get(p, idx, "preferredmajor");
                String open   = get(p, idx, "openingdate");
                String close  = get(p, idx, "closingdate");
                int slots     = safeInt(get(p, idx, "slots"), 1);
                int confirmed = safeInt(get(p, idx, "confirmed"), 0);
                String status = get(p, idx, "status");

                // minimal guard against malformed rows
                if (title == null || comp == null || email == null) continue;

                out.add(new InternshipOpportunity(
                        id, email, nz(comp), nz(title), nz(desc),
                        nz(level), nz(major), nz(open), nz(close), slots, confirmed, nz(status)
                ));
            }
        }
        return out;
    }

    /**
     * Writes the given list of opportunities to {@link #OPP_CSV}, overwriting existing content.
     *
     * @param list opportunities to persist (null-safe; null behaves like empty)
     * @throws IOException if an I/O error occurs writing the file
     */
    public static void writeOpportunities(List<InternshipOpportunity> list) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(OPP_CSV), StandardCharsets.UTF_8)) {
            bw.write(joinCsv("ID","CreatorEmail","CompanyName","Title","Description",
                    "Level","PreferredMajor","OpeningDate","ClosingDate","Slots","Confirmed","Status"));
            bw.newLine();
            if (list != null) {
                for (InternshipOpportunity o : list) {
                    bw.write(joinCsv(
                            nz(o.getId()),
                            nz(o.getCreatorEmail()),
                            nz(o.getCompanyName()),
                            nz(o.getTitle()),
                            nz(o.getDescription()),
                            nz(o.getLevel()),
                            nz(o.getPreferredMajor()),
                            nz(o.getOpeningDate()),
                            nz(o.getClosingDate()),
                            Integer.toString(o.getSlots()),
                            Integer.toString(o.getConfirmed()),
                            nz(o.getStatus())
                    ));
                    bw.newLine();
                }
            }
        }
    }

    /* ============================= Applications ============================ */

    /**
     * Loads applications from {@link #APP_CSV}.
     * <p>
     * If the file is missing, it is created with the current (extended) header:
     * {@code StudentID,OpportunityID,Status,AppliedAt,AppealCount,AppealMessage}.
     * Older files without appeals columns are still supported (defaults applied).
     * </p>
     *
     * @return list of {@link Application} (never {@code null})
     * @throws IOException if an I/O error occurs creating or reading the file
     */
    public static List<Application> readApplications() throws IOException {
        // Create file if missing with the NEW header (includes appeal columns).
        ensureFileWithHeader(APP_CSV,
                "StudentID,OpportunityID,Status,AppliedAt,AppealCount,AppealMessage"
        );

        List<Application> out = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(APP_CSV), StandardCharsets.UTF_8)) {
            String headerLine = br.readLine();
            if (headerLine == null) return out;

            String[] headers = splitCsvLine(headerLine);
            Map<String, Integer> idx = toHeaderIndex(headers);

            String line;
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] p = splitCsvLine(line);

                String sid   = get(p, idx, "studentid");
                String oppId = get(p, idx, "opportunityid");
                String st    = get(p, idx, "status");
                String at    = get(p, idx, "appliedat");

                // NEW appeal fields (tolerant to old files with no columns)
                int appealCount      = safeInt(get(p, idx, "appealcount"), 0);
                String appealMessage = nz(get(p, idx, "appealmessage"));

                if (sid == null || oppId == null) continue;

                // Use the extended constructor (studentId, oppId, status, appliedAtIso, appealCount, appealMessage)
                out.add(new Application(sid, oppId, st, at, appealCount, appealMessage));
            }
        }
        return out;
    }

    /**
     * Writes the given list of applications to {@link #APP_CSV}, overwriting existing content.
     * Always emits the extended header (v1.35).
     *
     * @param list applications to persist (null-safe; null behaves like empty)
     * @throws IOException if an I/O error occurs writing the file
     */
    public static void writeApplications(List<Application> list) throws IOException {
        try (BufferedWriter bw = Files.newBufferedWriter(Path.of(APP_CSV), StandardCharsets.UTF_8)) {
            // Always write the extended header going forward
            bw.write(joinCsv("StudentID","OpportunityID","Status","AppliedAt","AppealCount","AppealMessage"));
            bw.newLine();
            if (list != null) {
                for (Application a : list) {
                    bw.write(joinCsv(
                            nz(a.getStudentId()),
                            nz(a.getOpportunityId()),
                            nz(a.getStatus()),
                            nz(a.getAppliedAtIso()),
                            Integer.toString(a.getAppealCount()),
                            nz(a.getAppealMessage())
                    ));
                    bw.newLine();
                }
            }
        }
    }

    /* ================================ Helpers ============================= */

    // Create file with header if missing (does NOT overwrite existing files)
    private static void ensureFileWithHeader(String filename, String header) throws IOException {
        Path p = Path.of(filename);
        if (!Files.exists(p)) {
            try (BufferedWriter bw = Files.newBufferedWriter(p, StandardCharsets.UTF_8)) {
                bw.write(header);
                bw.newLine();
            }
        }
    }

    // CSV split with quote support (simple RFC4180-ish)
    private static String[] splitCsvLine(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"'); i++;
                    } else {
                        inQuotes = false;
                    }
                } else cur.append(c);
            } else {
                if (c == ',') { out.add(cur.toString()); cur.setLength(0); }
                else if (c == '"') { inQuotes = true; }
                else cur.append(c);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }

    // CSV join with quoting for commas, quotes, and newlines
    private static String joinCsv(String... fields) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < fields.length; i++) {
            String f = fields[i] == null ? "" : fields[i];
            boolean needsQuote = f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r");
            if (needsQuote) sb.append('"').append(f.replace("\"", "\"\"")).append('"');
            else sb.append(f);
            if (i + 1 < fields.length) sb.append(',');
        }
        return sb.toString();
    }

    // Build case/space-insensitive header index map
    private static Map<String, Integer> toHeaderIndex(String[] headers) {
        Map<String, Integer> m = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            String key = headers[i] == null ? "" : headers[i].toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
            m.put(key, i);
        }
        return m;
    }

    private static String get(String[] parts, Map<String, Integer> idx, String keyLower) {
        Integer i = idx.get(keyLower);
        if (i == null || i < 0 || i >= parts.length) return null;
        String v = parts[i];
        return v != null ? v.trim() : null;
    }

    private static int safeInt(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private static String nz(String s) { return s == null ? "" : s; }
}
