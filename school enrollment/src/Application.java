/**
 * Domain model representing a student's application to a specific internship opportunity.
 * <p>
 * Identity is the pair {@code (studentId, opportunityId)}. Status is stored as an
 * uppercase string and must be one of:
 * <ul>
 *   <li>{@code PENDING}</li>
 *   <li>{@code SUCCESSFUL}</li>
 *   <li>{@code UNSUCCESSFUL}</li>
 *   <li>{@code ACCEPTED}</li>
 *   <li>{@code APPEALING}</li>
 * </ul>
 *
 * <h3>Lifecycle (typical)</h3>
 * PENDING → (company decision) → SUCCESSFUL | UNSUCCESSFUL<br>
 * SUCCESSFUL → (student accepts) → ACCEPTED<br>
 * UNSUCCESSFUL → (student appeals, if eligible) → APPEALING → (company decision) → SUCCESSFUL | UNSUCCESSFUL
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code studentId} and {@code opportunityId} are non-null identifiers.</li>
 *   <li>{@code status} is normalized to UPPERCASE; convenience checks are case-insensitive.</li>
 *   <li>{@code appliedAtIso} is an ISO-8601 timestamp string (e.g., from {@link java.time.OffsetDateTime}).</li>
 *   <li>{@code appealCount} is always {@code >= 0}.</li>
 *   <li>{@code appealMessage} is never {@code null} (empty string if unspecified).</li>
 * </ul>
 *
 * <p>
 * Status transitions and eligibility rules (e.g., one appeal per application) are enforced
 * by higher-level services (e.g., {@code PortalService}). This class only models state.
 *
 * @since v1.3 (appeal fields added in v1.3); used in v1.35
 */
public class Application {

    /** Student identifier (login/student ID). */
    private String studentId;

    /** Internship opportunity identifier. */
    private String opportunityId;

    /**
     * Application status in UPPERCASE.
     * One of: PENDING, SUCCESSFUL, UNSUCCESSFUL, ACCEPTED, APPEALING.
     */
    private String status;

    /**
     * ISO-8601 timestamp string for when the application was created
     * (e.g., {@code OffsetDateTime.now().toString()}).
     */
    private String appliedAtIso;

    /** Number of appeals filed for this application (never negative). */
    private int appealCount;

    /** Latest appeal message provided by the student (never {@code null}). */
    private String appealMessage;

    /**
     * Full constructor.
     *
     * @param studentId     the applicant's student ID (non-null)
     * @param opportunityId the opportunity ID being applied to (non-null)
     * @param status        initial status; if {@code null}, defaults to {@code "PENDING"}; stored uppercase
     * @param appliedAtIso  ISO-8601 timestamp string (e.g., from {@code OffsetDateTime#now()})
     * @param appealCount   number of appeals; negative values are coerced to {@code 0}
     * @param appealMessage appeal message; if {@code null}, stored as empty string
     */
    public Application(String studentId, String opportunityId, String status, String appliedAtIso,
                       int appealCount, String appealMessage) {
        this.studentId = studentId;
        this.opportunityId = opportunityId;
        this.status = status == null ? "PENDING" : status.toUpperCase();
        this.appliedAtIso = appliedAtIso;
        this.appealCount = Math.max(appealCount, 0);
        this.appealMessage = (appealMessage == null) ? "" : appealMessage;
    }

    /**
     * Factory for a new {@code PENDING} application with the current timestamp.
     *
     * @param studentId     the applicant's student ID
     * @param opportunityId the opportunity ID being applied to
     * @return a new {@code Application} with status {@code PENDING}, {@code appealCount=0}, empty {@code appealMessage}
     */
    public static Application newPending(String studentId, String opportunityId) {
        String now = java.time.OffsetDateTime.now().toString();
        return new Application(studentId, opportunityId, "PENDING", now, 0, "");
    }

    // --- getters

    /** @return the student ID associated with this application */
    public String getStudentId() { return studentId; }

    /** @return the opportunity ID associated with this application */
    public String getOpportunityId() { return opportunityId; }

    /**
     * @return the current status (UPPERCASE):
     * {@code PENDING}, {@code SUCCESSFUL}, {@code UNSUCCESSFUL}, {@code ACCEPTED}, or {@code APPEALING}
     */
    public String getStatus() { return status; }

    /**
     * @return ISO-8601 timestamp string indicating when the application was created
     */
    public String getAppliedAtIso() { return appliedAtIso; }

    /** @return the number of appeals filed (never negative) */
    public int getAppealCount() { return appealCount; }

    /** @return the latest appeal message (never {@code null}; may be empty) */
    public String getAppealMessage() { return appealMessage; }

    // --- setters

    /**
     * Sets the status. {@code null} will clear the status to {@code null}; non-null values are uppercased.
     * Domain services are responsible for validating allowed transitions.
     *
     * @param s new status value (case-insensitive)
     */
    public void setStatus(String s) { this.status = (s == null ? null : s.toUpperCase()); }

    /**
     * Sets the appeal count; negative values are coerced to {@code 0}.
     *
     * @param c new appeal count
     */
    public void setAppealCount(int c) { this.appealCount = Math.max(c, 0); }

    /**
     * Sets the appeal message; {@code null} is stored as empty string.
     *
     * @param m new appeal message (may be empty)
     */
    public void setAppealMessage(String m) { this.appealMessage = (m == null) ? "" : m; }

    // --- convenience (case-insensitive checks)

    /** @return {@code true} if {@code status == PENDING} (case-insensitive) */
    public boolean isPending() { return "PENDING".equalsIgnoreCase(status); }

    /** @return {@code true} if {@code status == SUCCESSFUL} (case-insensitive) */
    public boolean isSuccessful() { return "SUCCESSFUL".equalsIgnoreCase(status); }

    /** @return {@code true} if {@code status == UNSUCCESSFUL} (case-insensitive) */
    public boolean isUnsuccessful() { return "UNSUCCESSFUL".equalsIgnoreCase(status); }

    /** @return {@code true} if {@code status == ACCEPTED} (case-insensitive) */
    public boolean isAccepted() { return "ACCEPTED".equalsIgnoreCase(status); }

    /** @return {@code true} if {@code status == APPEALING} (case-insensitive) */
    public boolean isAppealing() { return "APPEALING".equalsIgnoreCase(status); }
}
