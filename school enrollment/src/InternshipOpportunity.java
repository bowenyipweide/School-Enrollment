import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Internship opportunity record mirroring the columns in {@code opportunities.csv}:
 * <pre>
 * ID,CreatorEmail,CompanyName,Title,Description,Level,
 * PreferredMajor,OpeningDate,ClosingDate,Slots,Confirmed,Status
 * </pre>
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li><b>Status</b>: {@code PENDING}, {@code APPROVED}, {@code REJECTED}, {@code FILLED} (stored uppercase).</li>
 *   <li><b>Level</b>: {@code BASIC}, {@code INTERMEDIATE}, {@code ADVANCED} (stored uppercase; default {@code BASIC}).</li>
 *   <li><b>Preferred Major</b>: single string; empty string means ANY (no restriction).</li>
 *   <li><b>Dates</b>: opening/closing accept ISO {@code YYYY-MM-DD} and common local formats {@code DD/MM/YYYY} or {@code DD-MM-YYYY}.</li>
 *   <li><b>Slots</b>: clamped to {@code 1..10}. {@code confirmed} is {@code >= 0}.</li>
 *   <li><b>Auto-fill</b>: when {@code confirmed >= slots}, {@code status} is normalized to {@code FILLED}.</li>
 * </ul>
 *
 * <p>Status transitions and approval policies are enforced by higher-level services
 * (e.g., {@code PortalService}). This class encapsulates the state and basic invariants.</p>
 *
 * @since v1.3
 */
public class InternshipOpportunity {

    /** Unique identifier. If blank/null in constructor, a UUID is generated. */
    private String id;

    /** Email of the owning company representative. */
    private String creatorEmail;

    /** Company name. */
    private String companyName;

    /** Opportunity title. */
    private String title;

    /** Free-text description. */
    private String description;

    /** Level: BASIC / INTERMEDIATE / ADVANCED (stored uppercase; defaults to BASIC). */
    private String level;

    /** Preferred major (uppercase); empty string means ANY. */
    private String preferredMajor;

    /** Opening date string (accepts YYYY-MM-DD, DD/MM/YYYY, or DD-MM-YYYY). */
    private String openingDate;

    /** Closing date string (accepts YYYY-MM-DD, DD/MM/YYYY, or DD-MM-YYYY). */
    private String closingDate;

    /** Total number of slots (clamped to 1..10). */
    private int slots;

    /** Number of accepted applicants (never negative). */
    private int confirmed;

    /** Status: PENDING / APPROVED / REJECTED / FILLED (stored uppercase). */
    private String status;

    /**
     * Full constructor used by CSV loading and service logic.
     * Ensures normalization:
     * <ul>
     *   <li>Generates {@code id} if null/blank.</li>
     *   <li>Uppercases {@code level} (default BASIC) and {@code status} (default PENDING).</li>
     *   <li>Uppercases and trims {@code preferredMajor} (blank means ANY).</li>
     *   <li>Clamps {@code slots} to 1..10.</li>
     *   <li>{@code confirmed} is coerced to {@code >= 0}.</li>
     *   <li>Applies {@link #normalizeFilled()} to align {@code status} with capacity.</li>
     * </ul>
     *
     * @param id             unique identifier (UUID/string). If null/blank, a UUID is generated.
     * @param creatorEmail   company rep email (owner)
     * @param companyName    company name
     * @param title          opportunity title
     * @param description    description text
     * @param level          BASIC/INTERMEDIATE/ADVANCED (case-insensitive; null/blank → BASIC)
     * @param preferredMajor preferred major (null → ""; stored uppercase; "" means ANY)
     * @param openingDate    opening date string (YYYY-MM-DD or DD/MM/YYYY or DD-MM-YYYY)
     * @param closingDate    closing date string (YYYY-MM-DD or DD/MM/YYYY or DD-MM-YYYY)
     * @param slots          total slots (clamped to 1..10)
     * @param confirmed      accepted count (negative coerced to 0)
     * @param status         PENDING/APPROVED/REJECTED/FILLED (case-insensitive; null/blank → PENDING)
     */
    public InternshipOpportunity(String id,
                                 String creatorEmail,
                                 String companyName,
                                 String title,
                                 String description,
                                 String level,
                                 String preferredMajor,
                                 String openingDate,
                                 String closingDate,
                                 int slots,
                                 int confirmed,
                                 String status) {
        this.id = (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
        this.creatorEmail = nz(creatorEmail);
        this.companyName = nz(companyName);
        this.title = nz(title);
        this.description = nz(description);
        this.level = (level == null || level.isBlank()) ? "BASIC" : level.toUpperCase();
        this.preferredMajor = preferredMajor == null ? "" : preferredMajor.trim().toUpperCase();
        this.openingDate = nz(openingDate);
        this.closingDate = nz(closingDate);
        this.slots = Math.max(1, Math.min(10, slots));
        this.confirmed = Math.max(0, confirmed);
        this.status = (status == null || status.isBlank()) ? "PENDING" : status.toUpperCase();
        normalizeFilled();
    }

    /**
     * Factory for a new unapproved posting (defaults to {@code PENDING} with {@code confirmed=0}).
     *
     * @param creatorEmail   owner email
     * @param companyName    company name
     * @param title          opportunity title
     * @param description    description text
     * @param level          BASIC/INTERMEDIATE/ADVANCED
     * @param preferredMajor preferred major (""/null → ANY)
     * @param openingDate    opening date string
     * @param closingDate    closing date string
     * @param slots          total slots (1..10)
     * @return a new {@code InternshipOpportunity} with {@code status=PENDING}
     */
    public static InternshipOpportunity draft(String creatorEmail,
                                              String companyName,
                                              String title,
                                              String description,
                                              String level,
                                              String preferredMajor,
                                              String openingDate,
                                              String closingDate,
                                              int slots) {
        return new InternshipOpportunity(
                null, creatorEmail, companyName, title, description, level, preferredMajor,
                openingDate, closingDate, slots, 0, "PENDING"
        );
    }

    /* ------------------------ Basic getters/setters ------------------------ */

    /** @return unique identifier */
    public String getId() { return id; }

    /** @return owner email (company representative) */
    public String getCreatorEmail() { return creatorEmail; }

    /** Sets owner email. Null becomes empty string. */
    public void setCreatorEmail(String creatorEmail) { this.creatorEmail = nz(creatorEmail); }

    /** @return company name */
    public String getCompanyName() { return companyName; }

    /** Sets company name. Null becomes empty string. */
    public void setCompanyName(String companyName) { this.companyName = nz(companyName); }

    /** @return opportunity title */
    public String getTitle() { return title; }

    /** Sets title. Null becomes empty string. */
    public void setTitle(String title) { this.title = nz(title); }

    /** @return description text */
    public String getDescription() { return description; }

    /** Sets description. Null becomes empty string. */
    public void setDescription(String description) { this.description = nz(description); }

    /** @return level (uppercase BASIC/INTERMEDIATE/ADVANCED) */
    public String getLevel() { return level; }

    /** Sets level; null/blank → BASIC; value stored uppercase. */
    public void setLevel(String level) { this.level = (level == null || level.isBlank()) ? "BASIC" : level.toUpperCase(); }

    /** @return preferred major (uppercase; empty means ANY) */
    public String getPreferredMajor() { return preferredMajor; }

    /** Sets preferred major; null → ""; value stored uppercase; empty means ANY. */
    public void setPreferredMajor(String preferredMajor) { this.preferredMajor = preferredMajor == null ? "" : preferredMajor.trim().toUpperCase(); }

    /** @return opening date string as provided */
    public String getOpeningDate() { return openingDate; }

    /** Sets opening date string; null becomes empty string. */
    public void setOpeningDate(String openingDate) { this.openingDate = nz(openingDate); }

    /** @return closing date string as provided */
    public String getClosingDate() { return closingDate; }

    /** Sets closing date string; null becomes empty string. */
    public void setClosingDate(String closingDate) { this.closingDate = nz(closingDate); }

    /** @return total slots (1..10) */
    public int getSlots() { return slots; }

    /**
     * Sets total slots (clamped to 1..10) and re-evaluates fill status.
     * Invokes {@link #normalizeFilled()}.
     */
    public void setSlots(int slots) { this.slots = Math.max(1, Math.min(10, slots)); normalizeFilled(); }

    /** @return accepted count (never negative) */
    public int getConfirmed() { return confirmed; }

    /**
     * Sets accepted count (coerced to {@code >= 0}) and re-evaluates fill status.
     * Invokes {@link #normalizeFilled()}.
     */
    public void setConfirmed(int confirmed) { this.confirmed = Math.max(0, confirmed); normalizeFilled(); }

    /** @return status (UPPERCASE) */
    public String getStatus() { return status; }

    /**
     * Sets status (stored uppercase; null/blank → PENDING) and re-evaluates fill status.
     * Invokes {@link #normalizeFilled()}.
     */
    public void setStatus(String status) { this.status = (status == null || status.isBlank()) ? "PENDING" : status.toUpperCase(); normalizeFilled(); }

    /* ----------------------------- Helpers -------------------------------- */

    /** @return {@code true} if {@code status == APPROVED} (case-insensitive) */
    public boolean isApproved() { return "APPROVED".equalsIgnoreCase(status); }

    /**
     * @return {@code true} if {@code status == FILLED} or {@code confirmed >= slots}.
     * Keeps logic robust even if external code modified counts directly.
     */
    public boolean isFilled() { return "FILLED".equalsIgnoreCase(status) || confirmed >= slots; }

    /**
     * @return {@code true} if the opportunity is APPROVED, not FILLED, and today's date is within
     *         [openingDate, closingDate] inclusive; returns {@code false} for malformed dates.
     */
    public boolean isOpenNow() {
        if (!isApproved() || isFilled()) return false;
        LocalDate open = parseDate(openingDate);
        LocalDate close = parseDate(closingDate);
        if (open == null || close == null) return false; // malformed => treat as closed
        LocalDate today = LocalDate.now();
        return !(today.isBefore(open) || today.isAfter(close));
    }

    /**
     * Attempts to parse a date string in one of the supported formats.
     * Tries {@code YYYY-MM-DD}, then {@code d/M/uuuu}, then {@code d-M-uuuu}.
     *
     * @param s date string
     * @return parsed {@link LocalDate}, or {@code null} if unparsable
     */
    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        String t = s.trim();
        // Try ISO first
        try { return LocalDate.parse(t); } catch (DateTimeParseException ignored) {}
        // Try d/M/uuuu (e.g., 1/9/2025, 15/10/2025)
        try { return LocalDate.parse(t, DateTimeFormatter.ofPattern("d/M/uuuu")); } catch (DateTimeParseException ignored) {}
        // Try d-M-uuuu
        try { return LocalDate.parse(t, DateTimeFormatter.ofPattern("d-M-uuuu")); } catch (DateTimeParseException ignored) {}
        return null;
    }

    /**
     * Increments {@code confirmed} by 1 and re-evaluates fill status.
     * Invokes {@link #normalizeFilled()}.
     */
    public void increaseConfirmed() { this.confirmed++; normalizeFilled(); }

    /**
     * Normalizes {@code status} to {@code FILLED} if {@code confirmed >= slots}.
     * Called by relevant mutators to keep {@code status} consistent with capacity.
     */
    private void normalizeFilled() {
        if (confirmed >= slots) this.status = "FILLED";
    }

    /** Null-to-empty helper. */
    private static String nz(String s) { return s == null ? "" : s; }

    @Override
    public String toString() {
        return String.format("ID=%s | %s (%s) | level=%s | prefMajor=%s | slots %d/%d | %s..%s | %s",
                id, title, companyName, level, preferredMajor.isBlank() ? "ANY" : preferredMajor,
                confirmed, slots, openingDate, closingDate, status);
    }
}
