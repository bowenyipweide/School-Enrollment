import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Central domain service for Internship Hub business rules.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Own in-memory collections of {@link InternshipOpportunity} and {@link Application}.</li>
 *   <li>Load/save opportunities &amp; applications via {@link OppAppCsv} (CSV persistence).</li>
 *   <li>Expose student/rep/staff-facing use cases: browsing &amp; applying, offer acceptance,
 *       appeal flow, representative decisions, staff approvals, and universal list utilities
 *       (filter/sort/keyword extraction) based on {@link Column}.</li>
 * </ul>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li><b>Singleton:</b> {@link #getInstance()} provides a single shared service instance.</li>
 *   <li><b>Separation of concerns:</b> User data is handled by {@code AuthService}, while CSV
 *       I/O for opportunities/applications is handled here strictly via {@link OppAppCsv}
 *       (supports ISP/DIP in v1.35).</li>
 *   <li><b>Appeals:</b> v1.35 introduces APPEALING status and counters/messages.</li>
 * </ul>
 *
 * @since v1.3
 * @version v1.35
 */
public class PortalService {

    /* ----------------------------- Singleton ---------------------------- */
    private static final PortalService INSTANCE = new PortalService();

    /**
     * Returns the single {@code PortalService} instance.
     *
     * @return singleton instance
     */
    public static PortalService getInstance() { return INSTANCE; }

    private PortalService() { reload(); }

    /* ----------------------- Persistent “database” ---------------------- */
    private final List<InternshipOpportunity> opportunities = new ArrayList<>();
    private final List<Application> applications = new ArrayList<>();

    /* =============================== I/O ================================ */

    /**
     * Reloads in-memory state from CSV files:
     * <ul>
     *   <li>{@code opportunities.csv}</li>
     *   <li>{@code applications.csv}</li>
     * </ul>
     * Any prior in-memory lists are cleared first.
     */
    public final void reload() {
        opportunities.clear();
        applications.clear();
        try { opportunities.addAll(OppAppCsv.readOpportunities()); }
        catch (IOException e) { System.err.println("Failed to read opportunities.csv: " + e.getMessage()); }
        try { applications.addAll(OppAppCsv.readApplications()); }
        catch (IOException e) { System.err.println("Failed to read applications.csv: " + e.getMessage()); }
    }

    /** Persists opportunities to {@code opportunities.csv}. */
    private void persistOpps() {
        try { OppAppCsv.writeOpportunities(opportunities); }
        catch (IOException e) { System.err.println("Failed to write opportunities.csv: " + e.getMessage()); }
    }

    /** Persists applications to {@code applications.csv}. */
    private void persistApps() {
        try { OppAppCsv.writeApplications(applications); }
        catch (IOException e) { System.err.println("Failed to write applications.csv: " + e.getMessage()); }
    }

    /** Persists both opportunities and applications. */
    private void persistAll() { persistOpps(); persistApps(); }

    /* =============================== Student ============================ */

    /**
     * Returns APPROVED, currently open, eligible internships for the student.
     * Excludes FILLED by default.
     *
     * @param s the student
     * @return list of opportunities the student can see (FILLED excluded)
     */
    public List<InternshipOpportunity> listOpenForStudent(Student s) {
        return listOpenForStudent(s, false);
    }

    /**
     * Returns APPROVED &amp; currently open internships for the student, with an option
     * to include already FILLED postings (for awareness).
     *
     * @param s             the student
     * @param includeFilled whether to include FILLED postings
     * @return list of opportunities visible to the student
     */
    public List<InternshipOpportunity> listOpenForStudent(Student s, boolean includeFilled) {
        return opportunities.stream()
                .filter(o -> o.isApproved() || (includeFilled && o.isFilled()))
                .filter(this::isWithinWindowNow)
                .filter(o -> isEligible(s, o))
                .filter(o -> includeFilled || o.getConfirmed() < o.getSlots())
                .sorted(
                    Comparator
                        .comparing((InternshipOpportunity o) -> o.getConfirmed() >= o.getSlots())
                        .thenComparing(InternshipOpportunity::getCompanyName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(InternshipOpportunity::getTitle, String.CASE_INSENSITIVE_ORDER)
                )
                .collect(Collectors.toList());
    }

    /**
     * Returns true when today's date is between the opportunity's opening and closing dates.
     * Falls back to {@link InternshipOpportunity#isOpenNow()} if parsing fails.
     */
    private boolean isWithinWindowNow(InternshipOpportunity o) {
        LocalDate open  = parseDateFlexible(o.getOpeningDate());
        LocalDate close = parseDateFlexible(o.getClosingDate());
        if (open == null || close == null) return o.isOpenNow();
        LocalDate today = LocalDate.now();
        return !today.isBefore(open) && !today.isAfter(close);
    }

    /** Parses ISO (yyyy-MM-dd), d/M/uuuu, d-M-uuuu, dd/MM/uuuu formats; returns {@code null} if none match. */
    private LocalDate parseDateFlexible(String s) {
        if (s == null || s.isBlank()) return null;
        try { return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("d/M/uuuu")); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("d-M-uuuu")); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.parse(s, DateTimeFormatter.ofPattern("dd/MM/uuuu")); } catch (DateTimeParseException ignored) {}
        return null;
    }

    /**
     * Eligibility rule:
     * <ul>
     *   <li>If {@code preferredMajor} is set, it must match the student's major (case-insensitive).</li>
     *   <li>Year 1–2 students can only apply to BASIC level internships.</li>
     * </ul>
     */
    private boolean isEligible(Student s, InternshipOpportunity o) {
        if (s == null || o == null) return false;
        if (!o.getPreferredMajor().isBlank() && !o.getPreferredMajor().equalsIgnoreCase(s.getMajor())) return false;
        if (s.getYear() <= 2 && !"BASIC".equalsIgnoreCase(o.getLevel())) return false;
        return true;
    }

    /** @return true if the student already has an ACCEPTED application. */
    private boolean hasAcceptedOffer(String studentId) {
        return applications.stream()
                .anyMatch(a -> a.getStudentId().equals(studentId)
                             && "ACCEPTED".equalsIgnoreCase(a.getStatus()));
    }

    /**
     * Submits a new application for the given student and opportunity.
     * <p>Validations:</p>
     * <ul>
     *   <li>Student must not already have an ACCEPTED offer.</li>
     *   <li>Opportunity must exist and be open now.</li>
     *   <li>Major/year constraints enforced (see {@link #isEligible}).</li>
     *   <li>No duplicate application for the same student &amp; opportunity.</li>
     *   <li>At most 3 PENDING applications per student.</li>
     * </ul>
     * On success, a new {@code PENDING} {@link Application} is added and persisted.
     *
     * @throws IllegalStateException    if business rules are violated
     * @throws IllegalArgumentException if the opportunity cannot be found
     */
    public void applyTo(Student s, String opportunityId) {
        if (hasAcceptedOffer(s.getStudentId()))
            throw new IllegalStateException("Unable to apply for new internship opportunities since you have accepted other internship opportunity.");

        InternshipOpportunity o = findOpp(opportunityId);
        if (o == null) throw new IllegalArgumentException("Opportunity not found.");
        if (!o.isOpenNow()) throw new IllegalStateException("Opportunity is not open for applications.");
        if (!o.getPreferredMajor().isBlank() && !o.getPreferredMajor().equalsIgnoreCase(s.getMajor()))
            throw new IllegalStateException("Preferred major mismatch.");
        if (s.getYear() <= 2 && !"BASIC".equalsIgnoreCase(o.getLevel()))
            throw new IllegalStateException("Year 1-2 may only apply to BASIC internships.");

        boolean dup = applications.stream()
                .anyMatch(a -> a.getStudentId().equals(s.getStudentId()) && a.getOpportunityId().equals(o.getId()));
        if (dup) throw new IllegalStateException("You have already applied to this opportunity.");

        long pending = applications.stream()
                .filter(a -> a.getStudentId().equals(s.getStudentId()))
                .filter(Application::isPending)
                .count();
        if (pending >= 3) throw new IllegalStateException("You already have 3 pending applications.");

        applications.add(Application.newPending(s.getStudentId(), o.getId()));
        persistApps();
    }

    /**
     * Lists all applications made by the given student.
     *
     * @param studentId student identifier
     * @return list of {@link Application}
     */
    public List<Application> listApplicationsByStudent(String studentId) {
        return applications.stream()
                .filter(a -> a.getStudentId().equals(studentId))
                .collect(Collectors.toList());
    }

    /**
     * Accepts a SUCCESSFUL offer for the given student/opportunity.
     * <p>Effects:</p>
     * <ul>
     *   <li>Target application becomes {@code ACCEPTED}.</li>
     *   <li>Opportunity's confirmed count increments; flips to FILLED if now full.</li>
     *   <li>All other PENDING/SUCCESSFUL applications for the student become {@code UNSUCCESSFUL}.</li>
     * </ul>
     *
     * @throws IllegalStateException    when rules are violated (already accepted, not SUCCESSFUL, no slots)
     * @throws IllegalArgumentException when the application/opportunity does not exist
     */
    public void acceptOffer(String studentId, String opportunityId) {
        if (hasAcceptedOffer(studentId))
            throw new IllegalStateException("You have already accepted an offer.");

        Application target = applications.stream()
                .filter(a -> a.getStudentId().equals(studentId) && a.getOpportunityId().equals(opportunityId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such application."));

        if (!target.isSuccessful())
            throw new IllegalStateException("Offer is not SUCCESSFUL.");

        InternshipOpportunity opp = findOpp(opportunityId);
        if (opp == null) throw new IllegalStateException("Opportunity missing.");

        if (opp.getConfirmed() >= opp.getSlots())
            throw new IllegalStateException("No remaining slots for this opportunity.");

        // Accept: set ACCEPTED on all duplicate app records for same student/opportunity
        for (Application a : applications) {
            if (a.getStudentId().equals(studentId) && Objects.equals(a.getOpportunityId(), opportunityId)) {
                a.setStatus("ACCEPTED");
            }
        }

        opp.increaseConfirmed(); // flips to FILLED if confirmed == slots

        for (Application a : applications) {
            if (a.getStudentId().equals(studentId)
                    && !Objects.equals(a.getOpportunityId(), opportunityId)
                    && (a.isPending() || a.isSuccessful())) {
                a.setStatus("UNSUCCESSFUL");
            }
        }

        persistAll();
    }

    /* ========================== Appeal (Student) ========================= */

    /** Word-count helper (tokens split by whitespace). */
    private int wordCount(String s){
        if (s == null) return 0;
        String t = s.trim();
        if (t.isEmpty()) return 0;
        return t.split("\\s+").length;
    }

    /**
     * @return true if the student has at least one {@code UNSUCCESSFUL} application with
     *         {@code AppealCount==0} and the student has no {@code ACCEPTED} offer.
     */
    public boolean studentCanAppeal(String studentId){
        if (hasAcceptedOffer(studentId)) return false;
        return applications.stream().anyMatch(a ->
                a.getStudentId().equals(studentId)
             && a.isUnsuccessful()
             && a.getAppealCount() == 0
        );
    }

    /**
     * Returns all {@code UNSUCCESSFUL} and not-yet-appealed applications for UI selection.
     *
     * @param studentId student identifier
     * @return list of eligible applications for appeal
     */
    public List<Application> listAppealableApplications(String studentId){
        if (hasAcceptedOffer(studentId)) return Collections.emptyList();
        return applications.stream()
                .filter(a -> a.getStudentId().equals(studentId)
                        && a.isUnsuccessful()
                        && a.getAppealCount() == 0)
                .collect(Collectors.toList());
    }

    /**
     * Files a single appeal for the student's own {@code UNSUCCESSFUL} application.
     * <p>Rules:</p>
     * <ul>
     *   <li>Student must have no {@code ACCEPTED} offer.</li>
     *   <li>Target app must be {@code UNSUCCESSFUL} with {@code AppealCount==0}.</li>
     *   <li>Appeal message must be 1–100 words.</li>
     * </ul>
     * <p>Effects: sets appeal message, increments counter, status becomes {@code APPEALING}.</p>
     *
     * @throws IllegalStateException    when a rule is violated
     * @throws IllegalArgumentException if the application cannot be found
     */
    public void appealApplication(String studentId, String opportunityId, String appealMessage){
        if (hasAcceptedOffer(studentId))
            throw new IllegalStateException("Invalid. You are not eligible to appeal for any applications.");

        Application app = applications.stream()
                .filter(a -> a.getStudentId().equals(studentId) && a.getOpportunityId().equals(opportunityId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No such application."));

        if (!app.isUnsuccessful())
            throw new IllegalStateException("Only UNSUCCESSFUL applications can be appealed.");
        if (app.getAppealCount() > 0)
            throw new IllegalStateException("You may only appeal once for this application.");

        int wc = wordCount(appealMessage);
        if (wc < 1 || wc > 100)
            throw new IllegalArgumentException("Appeal message must be between 1 and 100 words.");

        app.setAppealMessage(appealMessage == null ? "" : appealMessage.trim());
        app.setAppealCount(app.getAppealCount() + 1);
        app.setStatus("APPEALING");
        persistApps();
    }

    /* ============================ Company Rep =========================== */

    /**
     * Checks if the representative may create more opportunities (limit = 5).
     *
     * @param repEmail representative's email
     * @return {@code true} if total owned opportunities &lt; 5
     */
    public boolean canCreateMore(String repEmail) {
        long total = opportunities.stream()
                .filter(o -> o.getCreatorEmail().equalsIgnoreCase(repEmail))
                .count();
        return total < 5;
    }

    /** Generates next numeric ID by scanning existing numeric IDs; falls back to "1" if none. */
    private String nextOpportunityId() {
        long max = 0L;
        for (InternshipOpportunity o : opportunities) {
            String id = o.getId();
            if (id != null && id.matches("\\d+")) {
                try {
                    long v = Long.parseLong(id);
                    if (v > max) max = v;
                } catch (NumberFormatException ignored) {}
            }
        }
        return Long.toString(max + 1);
    }

    /**
     * Creates a new {@code PENDING} opportunity owned by the given representative.
     *
     * @throws IllegalStateException if rep not approved or has reached the limit of 5
     * @throws IllegalArgumentException if slots is not in 1..10
     */
    public InternshipOpportunity createOpportunity(CompanyRep rep,
                                                   String title,
                                                   String description,
                                                   String level,
                                                   String preferredMajor,
                                                   String openingDate,
                                                   String closingDate,
                                                   int slots) {
        if (!rep.isApproved()) throw new IllegalStateException("Your account is not approved yet.");
        if (!canCreateMore(rep.getEmail())) throw new IllegalStateException("Max 5 opportunities per representative reached.");
        if (slots < 1 || slots > 10) throw new IllegalArgumentException("Slots must be 1..10.");

        String newId = nextOpportunityId();
        InternshipOpportunity o = new InternshipOpportunity(
                newId,
                rep.getEmail(),
                rep.getCompany(),
                title,
                description,
                level,
                preferredMajor,
                openingDate,
                closingDate,
                slots,
                0,
                "PENDING"
        );
        opportunities.add(o);
        persistOpps();
        return o;
    }

    /**
     * Lists opportunities owned by a representative, sorted by Status (A→Z) then numeric ID ascending.
     */
    public List<InternshipOpportunity> listOpportunitiesByRep(String repEmail) {
        return opportunities.stream()
                .filter(o -> o.getCreatorEmail().equalsIgnoreCase(repEmail))
                .sorted(
                        Comparator.comparing(InternshipOpportunity::getStatus, String.CASE_INSENSITIVE_ORDER)
                                  .thenComparing(o -> {
                                      String id = o.getId();
                                      if (id != null && id.matches("\\d+")) {
                                          try { return Long.parseLong(id); }
                                          catch (NumberFormatException ignored) {}
                                      }
                                      return Long.MAX_VALUE;
                                  })
                )
                .collect(Collectors.toList());
    }

    /**
     * Lists all applications for a representative-owned opportunity.
     *
     * @throws IllegalArgumentException if the opportunity does not exist
     * @throws IllegalStateException    if the opportunity is not owned by the representative
     */
    public List<Application> listApplicationsForOpportunity(String repEmail, String opportunityId) {
        InternshipOpportunity opp = findOpp(opportunityId);
        if (opp == null) throw new IllegalArgumentException("Opportunity not found.");
        if (!opp.getCreatorEmail().equalsIgnoreCase(repEmail))
            throw new IllegalStateException("Not your opportunity.");

        return applications.stream()
                .filter(a -> a.getOpportunityId().equals(opportunityId))
                .collect(Collectors.toList());
    }

    /**
     * Lists only {@code APPEALING} applications for a representative-owned opportunity.
     *
     * @throws IllegalArgumentException if the opportunity does not exist
     * @throws IllegalStateException    if the opportunity is not owned by the representative
     */
    public List<Application> listAppealsForOpportunity(String repEmail, String opportunityId) {
        InternshipOpportunity opp = findOpp(opportunityId);
        if (opp == null) throw new IllegalArgumentException("Opportunity not found.");
        if (!opp.getCreatorEmail().equalsIgnoreCase(repEmail))
            throw new IllegalStateException("Not your opportunity.");

        return applications.stream()
                .filter(a -> a.getOpportunityId().equals(opportunityId))
                .filter(a -> "APPEALING".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Representative decision for a student's application.
     * <p>If the application is {@code APPEALING}, delegates to {@link #setAppealDecision(String, String, String, boolean)}
     * to ensure appeal metadata is updated.</p>
     *
     * @throws IllegalArgumentException if app/opp not found
     * @throws IllegalStateException    if not owner, not APPROVED, or opp is filled
     */
    public void setApplicationDecision(String repEmail, String opportunityId, String studentId, boolean approve) {
        InternshipOpportunity opp = findOpp(opportunityId);
        if (opp == null) throw new IllegalArgumentException("Opportunity not found.");
        if (!opp.getCreatorEmail().equalsIgnoreCase(repEmail))
            throw new IllegalStateException("Not your opportunity.");
        if (!"APPROVED".equalsIgnoreCase(opp.getStatus()))
            throw new IllegalStateException("Opportunity must be APPROVED by Staff first.");
        if (opp.isFilled())
            throw new IllegalStateException("All slots are filled.");

        Application app = applications.stream()
                .filter(a -> a.getOpportunityId().equals(opportunityId) && a.getStudentId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));

        // NEW: if this is an appeal, handle via appeal path so AppealMessage is updated.
        if (app.isAppealing()) {
            setAppealDecision(repEmail, opportunityId, studentId, approve);
            return;
        }

        // Original (non-appeal) path
        app.setStatus(approve ? "SUCCESSFUL" : "UNSUCCESSFUL");
        persistApps();
    }

    /**
     * Representative decision for an {@code APPEALING} application.
     * <ul>
     *   <li>Approve  → {@code SUCCESSFUL}, sets AppealMessage = "APPEAL APPROVED"</li>
     *   <li>Reject   → {@code UNSUCCESSFUL}, sets AppealMessage = "APPEAL REJECTED"</li>
     * </ul>
     *
     * @throws IllegalArgumentException if app/opp not found
     * @throws IllegalStateException    if not owner, not APPROVED, opp is filled, or app not APPEALING
     */
    public void setAppealDecision(String repEmail, String opportunityId, String studentId, boolean approve) {
        InternshipOpportunity opp = findOpp(opportunityId);
        if (opp == null) throw new IllegalArgumentException("Opportunity not found.");
        if (!opp.getCreatorEmail().equalsIgnoreCase(repEmail))
            throw new IllegalStateException("Not your opportunity.");
        if (!"APPROVED".equalsIgnoreCase(opp.getStatus()))
            throw new IllegalStateException("Opportunity must be APPROVED by Staff first.");
        if (opp.isFilled())
            throw new IllegalStateException("All slots are filled.");

        Application app = applications.stream()
                .filter(a -> a.getOpportunityId().equals(opportunityId) && a.getStudentId().equals(studentId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Application not found."));

        if (!"APPEALING".equalsIgnoreCase(app.getStatus()))
            throw new IllegalStateException("This application is not in APPEALING status.");

        if (approve) {
            app.setStatus("SUCCESSFUL");
            app.setAppealMessage("APPEAL APPROVED");
        } else {
            app.setStatus("UNSUCCESSFUL");
            app.setAppealMessage("APPEAL REJECTED");
        }

        persistApps();
    }

    /* --------------------------- Edit / Delete --------------------------- */

    /**
     * Ensures the representative owns the opportunity and that it is editable
     * ({@code PENDING} or {@code REJECTED}). Returns the object if valid.
     *
     * @throws IllegalArgumentException if the opportunity does not exist
     * @throws IllegalStateException    if not owned or not in an editable status
     */
    public InternshipOpportunity requireEditableOpportunity(String repEmail, String opportunityId) {
        InternshipOpportunity o = findOpp(opportunityId);
        if (o == null) throw new IllegalArgumentException("Opportunity not found.");
        if (!o.getCreatorEmail().equalsIgnoreCase(repEmail))
            throw new IllegalStateException("Not your opportunity.");
        String status = o.getStatus();
        if (!"PENDING".equalsIgnoreCase(status) && !"REJECTED".equalsIgnoreCase(status))
            throw new IllegalStateException("Only PENDING or REJECTED opportunities can be edited.");
        return o;
    }

    /**
     * Updates fields for an owned, editable opportunity.
     * <p>Constraints: slots ∈ [max(confirmed), 10]; preferredMajor stored as "" for ANY.</p>
     *
     * @throws IllegalArgumentException if slot constraints are violated
     * @throws IllegalStateException    if not editable/owned
     */
    public void updateOpportunity(String repEmail,
                                  String id,
                                  String title,
                                  String description,
                                  String level,
                                  String preferredMajor,
                                  String openingDate,
                                  String closingDate,
                                  int slots) {
        InternshipOpportunity o = requireEditableOpportunity(repEmail, id);

        if (slots < o.getConfirmed() || slots < 1 || slots > 10)
            throw new IllegalArgumentException("Slots must be between max(confirmed," + o.getConfirmed() + ") and 10.");

        o.setTitle(title);
        o.setDescription(description);
        o.setLevel(level);
        o.setPreferredMajor(preferredMajor == null ? "" : preferredMajor);
        o.setOpeningDate(openingDate);
        o.setClosingDate(closingDate);
        o.setSlots(slots);

        persistOpps();
    }

    /**
     * Deletes an owned opportunity if and only if there are:
     * <ul>
     *   <li>No applications referencing it, and</li>
     *   <li>No confirmed acceptances.</li>
     * </ul>
     *
     * @throws IllegalArgumentException if the opportunity does not exist
     * @throws IllegalStateException    if not owned or deletion rules are not met
     */
    public void deleteOpportunity(String repEmail, String opportunityId) {
        InternshipOpportunity o = findOpp(opportunityId);
        if (o == null) throw new IllegalArgumentException("Opportunity not found.");
        if (!o.getCreatorEmail().equalsIgnoreCase(repEmail))
            throw new IllegalStateException("Not your opportunity.");

        long appCount = applications.stream()
                .filter(a -> a.getOpportunityId().equals(opportunityId))
                .count();
        if (appCount > 0) throw new IllegalStateException("Cannot delete: there are applications for this opportunity.");
        if (o.getConfirmed() > 0) throw new IllegalStateException("Cannot delete: there are confirmed students for this opportunity.");

        opportunities.remove(o);
        persistOpps();
    }

    /* ================================ Staff ============================= */

    /**
     * Returns all {@code PENDING} opportunities (for Staff review).
     */
    public List<InternshipOpportunity> listPendingOpportunities() {
        return opportunities.stream()
                .filter(o -> "PENDING".equalsIgnoreCase(o.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Sets the approval status of an opportunity to APPROVED/REJECTED.
     *
     * @param opportunityId opportunity identifier
     * @param approve       {@code true} → APPROVED, {@code false} → REJECTED
     * @return {@code true} if the opportunity exists and was updated
     */
    public boolean setOpportunityApproval(String opportunityId, boolean approve) {
        InternshipOpportunity o = findOpp(opportunityId);
        if (o == null) return false;
        o.setStatus(approve ? "APPROVED" : "REJECTED");
        persistOpps();
        return true;
    }

    /* ===================== Universal list utilities ===================== */

    /** Computes empty slots as {@code max(slots - confirmed, 0)}. */
    private int emptySlots(InternshipOpportunity o) {
        return Math.max(o.getSlots() - o.getConfirmed(), 0);
    }

    /** Safe long parser for numeric sorts; returns {@link Long#MIN_VALUE} if not numeric. */
    private long parseLongSafe(String s){
        if (s == null || s.isBlank()) return Long.MIN_VALUE;
        try { return Long.parseLong(s.trim()); } catch (Exception e){ return Long.MIN_VALUE; }
    }

    /**
     * Returns a string value for the given {@link Column} from an opportunity.
     * This is what filtering compares against (case-insensitive).
     *
     * @param o opportunity
     * @param c column
     * @return comparable string value or {@code null} for {@code NONE/unknown}
     */
    public String valueFor(InternshipOpportunity o, Column c) {
        if (o == null || c == null) return null;
        switch (c) {
            case ID:            return o.getId();
            case TITLE:         return o.getTitle();
            case COMPANY:       return o.getCompanyName();
            case LEVEL:         return o.getLevel();
            case PREF_MAJOR:    return o.getPreferredMajor().isBlank() ? "ANY" : o.getPreferredMajor();
            case OPENING_DATE:  return o.getOpeningDate();
            case CLOSING_DATE:  return o.getClosingDate();
            case STATUS:        return o.getStatus();
            case TOTAL_SLOTS:   return String.valueOf(o.getSlots());
            case EMPTY_SLOTS:   return String.valueOf(emptySlots(o));
            case PENDING_COMPANY:
                return String.valueOf(countApplications(o.getId(), "PENDING"));
            case PENDING_STUDENT:
                return String.valueOf(countApplications(o.getId(), "SUCCESSFUL"));
            case PENDING_APPEAL:
                return String.valueOf(countApplications(o.getId(), "APPEALING"));
            default:           return null;
        }
    }

    /** Robust date parser for sorting; returns {@link LocalDate#MIN} when unknown. */
    private LocalDate parseDateForSort(String s) {
        if (s == null || s.isBlank()) return LocalDate.MIN;
        String[] fmts = {"yyyy-MM-dd","dd/MM/uuuu","d/M/uuuu","dd-MM-uuuu","d-M-uuuu"};
        for (String f : fmts) {
            try { return LocalDate.parse(s.trim(), DateTimeFormatter.ofPattern(f)); }
            catch (Exception ignored) {}
        }
        // Lastly, try ISO just in case
        try { return LocalDate.parse(s.trim()); } catch (Exception ignored) {}
        return LocalDate.MIN;
    }

    /**
     * Returns a comparator for the given {@link Column}. For {@code NONE}, returns a no-op comparator.
     */
    public Comparator<InternshipOpportunity> comparatorFor(Column c) {
        if (c == null || c == Column.NONE) return (a, b) -> 0;
        switch (c) {
            case ID:
                return Comparator.comparingLong(o -> parseLongSafe(o.getId()));
            case TITLE:
                return Comparator.comparing(InternshipOpportunity::getTitle, String.CASE_INSENSITIVE_ORDER);
            case COMPANY:
                return Comparator.comparing(InternshipOpportunity::getCompanyName, String.CASE_INSENSITIVE_ORDER);
            case LEVEL:
                return Comparator.comparing(InternshipOpportunity::getLevel, String.CASE_INSENSITIVE_ORDER);
            case PREF_MAJOR:
                return Comparator.comparing(
                    o -> o.getPreferredMajor().isBlank() ? "ANY" : o.getPreferredMajor(),
                    String.CASE_INSENSITIVE_ORDER
                );
            case OPENING_DATE:
                return Comparator.comparing(o -> parseDateForSort(o.getOpeningDate()));
            case CLOSING_DATE:
                return Comparator.comparing(o -> parseDateForSort(o.getClosingDate()));
            case STATUS:
                return Comparator.comparing(InternshipOpportunity::getStatus, String.CASE_INSENSITIVE_ORDER);
            case TOTAL_SLOTS:
                return Comparator.comparingInt(InternshipOpportunity::getSlots);
            case EMPTY_SLOTS:
                return Comparator.comparingInt(this::emptySlots);
            case PENDING_COMPANY:
                return Comparator.comparingInt(o -> countApplications(o.getId(), "PENDING"));
            case PENDING_STUDENT:
                return Comparator.comparingInt(o -> countApplications(o.getId(), "SUCCESSFUL"));
            case PENDING_APPEAL:
                return Comparator.comparingInt(o -> countApplications(o.getId(), "APPEALING"));
            default:
                return (a, b) -> 0;
        }
    }

    /**
     * Applies an equality filter by column + keyword (case-insensitive).
     * If column is {@code NONE} or keyword blank, returns a shallow copy of {@code base}.
     */
    public List<InternshipOpportunity> applyFilter(List<InternshipOpportunity> base, Column col, String keyword) {
        if (base == null) return Collections.emptyList();
        if (col == null || col == Column.NONE || keyword == null || keyword.isBlank()) return new ArrayList<>(base);
        String kw = keyword.trim();
        return base.stream()
                .filter(o -> {
                    String v = valueFor(o, col);
                    return v != null && v.equalsIgnoreCase(kw);
                })
                .collect(Collectors.toList());
    }

    /**
     * Applies ascending sort by the given column. If column is {@code NONE}, returns a copy unchanged.
     */
    public List<InternshipOpportunity> applySort(List<InternshipOpportunity> base, Column col) {
        if (base == null) return Collections.emptyList();
        if (col == null || col == Column.NONE) return new ArrayList<>(base);
        return base.stream().sorted(comparatorFor(col)).collect(Collectors.toList());
    }

    /**
     * Applies both filter and sort using a {@link ListPreferences} object (ascending sort).
     *
     * @param base base list
     * @param p    preferences (filter column/keyword and sort column)
     * @return filtered and sorted list
     */
    public List<InternshipOpportunity> applyOppPrefs(List<InternshipOpportunity> base, ListPreferences p) {
        if (p == null) return new ArrayList<>(base);
        return applySort(applyFilter(base, p.getFilterColumn(), p.getFilterKeyword()), p.getSortColumn());
    }

    /**
     * Collects distinct, display-ready keywords for a column across a base list.
     * Numeric-like columns (ID, slots, pending counts) are sorted numerically ascending.
     *
     * @param base base list
     * @param col  column to extract from
     * @return distinct keyword list for menu pickers
     */
    public List<String> uniqueKeywordsOpp(List<InternshipOpportunity> base, Column col) {
        if (base == null || col == null || col == Column.NONE) return Collections.emptyList();

        switch (col) {
            case ID:
            case TOTAL_SLOTS:
            case EMPTY_SLOTS:
            case PENDING_COMPANY:
            case PENDING_STUDENT:
            case PENDING_APPEAL:
                return base.stream()
                        .map(o -> valueFor(o, col))
                        .filter(v -> v != null && !v.isBlank())
                        .map(String::trim)
                        .map(v -> {
                            try { return Long.parseLong(v); } catch (Exception e) { return Long.MIN_VALUE; }
                        })
                        .filter(v -> v != Long.MIN_VALUE)
                        .distinct()
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.toList());
            default:
                return base.stream()
                        .map(o -> valueFor(o, col))
                        .filter(v -> v != null && !v.isBlank())
                        .map(String::trim)
                        .map(v -> (col == Column.LEVEL || col == Column.STATUS) ? v.toUpperCase() : v)
                        .distinct()
                        .sorted(String.CASE_INSENSITIVE_ORDER)
                        .collect(Collectors.toList());
        }
    }

    /**
     * Convenience for students: returns unique keywords from the student's eligible set (includes FILLED).
     */
    public List<String> uniqueKeywordsFor(Student s, Column col) {
        return uniqueKeywordsOpp(listOpenForStudent(s, true), col);
    }

    /* =============================== Helpers ============================ */

    /**
     * Finds an opportunity by ID.
     *
     * @param id opportunity id
     * @return opportunity or {@code null} if not found
     */
    public InternshipOpportunity findOpp(String id) {
        if (id == null) return null;
        for (InternshipOpportunity o : opportunities) {
            if (o.getId().equals(id)) return o;
        }
        return null;
    }

    /**
     * Counts all applications for the given opportunity.
     *
     * @param opportunityId opportunity id
     * @return number of applications
     */
    public int countApplications(String opportunityId) {
        return (int) applications.stream()
                .filter(a -> opportunityId.equals(a.getOpportunityId()))
                .count();
    }

    /**
     * Counts applications for the given opportunity matching a specific status (case-insensitive).
     *
     * @param opportunityId           opportunity id
     * @param statusEqualsIgnoreCase  status to match (nullable; if null, counts all)
     * @return matching application count
     */
    public int countApplications(String opportunityId, String statusEqualsIgnoreCase) {
        return (int) applications.stream()
                .filter(a -> opportunityId.equals(a.getOpportunityId()))
                .filter(a -> statusEqualsIgnoreCase == null
                        || statusEqualsIgnoreCase.equalsIgnoreCase(a.getStatus()))
                .count();
    }

    /**
     * Returns a defensive copy of the in-memory opportunities list.
     */
    public List<InternshipOpportunity> allOpportunities() { return new ArrayList<>(opportunities); }

    /**
     * Returns a defensive copy of the in-memory applications list.
     */
    public List<Application> allApplications() { return new ArrayList<>(applications); }
}
