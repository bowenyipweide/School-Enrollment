/**
 * Columns that can be displayed, filtered, and sorted in list views across the app.
 * <p>
 * Each enum value carries a human-readable {@link #label} used by menus and table printers.
 * This enum is also serialized/deserialized in user preference storage (CSV) via
 * {@link #toCsv()} and {@link #fromCsv(String)}.
 *
 * <h3>Typical usage</h3>
 * <ul>
 *   <li>Menus present {@link #label} to users for filter/sort choices.</li>
 *   <li>{@code PreferenceService} stores the selected column using {@link #toCsv()}.</li>
 *   <li>On load, the column is restored with {@link #fromCsv(String)} (tolerant to spaces and synonyms).</li>
 * </ul>
 *
 * <p><b>Version notes:</b> {@link #PENDING_APPEAL} added in v1.35 to support the appeals workflow.</p>
 *
 * @since v1.3
 */
public enum Column {
    /** No column selected (used to represent "no filter" and "no sort"). */
    NONE("None"),

    /** Internship identifier (unique ID). */
    ID("ID"),

    /** Internship title. */
    TITLE("Title"),

    /** Company name. */
    COMPANY("Company"),

    /** Internship level (e.g., BASIC/INTERMEDIATE/ADVANCED). */
    LEVEL("Level"),

    /** Preferred major; blank means ANY. */
    PREF_MAJOR("PrefMajor"),

    /** Opening date (string as provided/stored). */
    OPENING_DATE("Opening Date"),

    /** Closing date (string as provided/stored). */
    CLOSING_DATE("Closing Date"),

    /** Opportunity status (e.g., PENDING/APPROVED/REJECTED). */
    STATUS("Status"),

    /** Total number of slots for an opportunity. */
    TOTAL_SLOTS("Total Slots"),

    /** Remaining (empty) slots = total - confirmed (never negative). */
    EMPTY_SLOTS("Empty Slots"),

    /** Count of applications awaiting company decision (PENDING). */
    PENDING_COMPANY("Pending Company Approval"),

    /** Count of SUCCESSFUL applications awaiting student acceptance. */
    PENDING_STUDENT("Pending Student Acceptance"),

    /** Count of APPEALING applications awaiting company decision. */
    PENDING_APPEAL("Pending Appeal Approval"); // NEW in v1.35

    /** Human-readable label used in menus and tables. */
    public final String label;

    Column(String l) {
        this.label = l;
    }

    /**
     * Legacy numeric mapping used by older menus (1..6).
     * <p>
     * Newer menus should present options directly from {@link #label} instead of numeric mapping.
     *
     * @param i user choice (1..6)
     * @return the corresponding column, or {@link #NONE} if out of range
     */
    public static Column fromUserChoice(int i) {
        switch (i) {
            case 1: return TITLE;
            case 2: return COMPANY;
            case 3: return LEVEL;
            case 4: return OPENING_DATE;
            case 5: return CLOSING_DATE;
            case 6: return STATUS;
            default: return NONE;
        }
    }

    /**
     * Parses a column name from CSV storage or user input. Tolerates case, spaces,
     * and common synonyms (e.g., {@code "PREFMAJOR"}, {@code "OPENING DATE"}).
     *
     * @param s raw string (may be {@code null} or blank)
     * @return resolved {@link Column}, or {@link #NONE} if unrecognized
     */
    public static Column fromCsv(String s) {
        if (s == null || s.isBlank()) return NONE;
        s = s.trim().toUpperCase();
        switch (s) {
            case "ID": return ID;
            case "TITLE": return TITLE;
            case "COMPANY": return COMPANY;
            case "LEVEL": return LEVEL;
            case "PREF_MAJOR":
            case "PREFMAJOR":
            case "PREFERRED_MAJOR":
                return PREF_MAJOR;
            case "OPENING_DATE":
            case "OPENING DATE":
                return OPENING_DATE;
            case "CLOSING_DATE":
            case "CLOSING DATE":
                return CLOSING_DATE;
            case "STATUS": return STATUS;
            case "TOTAL_SLOTS":
            case "TOTAL SLOTS":
                return TOTAL_SLOTS;
            case "EMPTY_SLOTS":
            case "EMPTY SLOTS":
                return EMPTY_SLOTS;
            case "PENDING_COMPANY":
            case "PENDING COMPANY APPROVAL":
                return PENDING_COMPANY;
            case "PENDING_STUDENT":
            case "PENDING STUDENT ACCEPTANCE":
                return PENDING_STUDENT;
            case "PENDING_APPEAL":
            case "PENDING APPEAL":
            case "PENDING APPEAL APPROVAL":
                return PENDING_APPEAL;
            case "NONE": return NONE;
            default:
                try {
                    // Fallback: convert spaces to underscores and try enum name
                    return Column.valueOf(s.replace(' ', '_'));
                } catch (Exception e) {
                    return NONE;
                }
        }
    }

    /**
     * Serializes the column name for CSV storage.
     *
     * @return {@code ""} for {@link #NONE}, otherwise the enum constant name (e.g., {@code "TITLE"})
     */
    public String toCsv() {
        return this == NONE ? "" : this.name();
    }
}
