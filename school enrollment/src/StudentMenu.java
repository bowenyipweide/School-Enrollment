import java.util.List;
import java.util.ArrayList;

/**
 * CLI menu for the Student role.
 *
 * <p><b>Responsibilities</b></p>
 * <ul>
 *   <li>Navigate student actions (browse/apply/accept/appeal/password change).</li>
 *   <li>Apply per-user persistent list preferences (filter/sort) via {@link PreferencesStore}.</li>
 *   <li>Delegate business rules to {@link PortalService} and lookups to {@link AuthService}.</li>
 * </ul>
 *
 * <p><b>SOLID notes</b></p>
 * <ul>
 *   <li><b>ISP/DIP:</b> depends on the narrow {@link PreferencesStore} abstraction rather than a concrete CSV utility.</li>
 *   <li><b>SRP:</b> keeps UI flow separate from domain logic (which lives in services/entities).</li>
 * </ul>
 *
 * @since v1.3
 * @version v1.35
 */
public class StudentMenu {
    private final AuthService auth;
    private final SimpleInput in;
    private final PortalService portal = PortalService.getInstance();

    /** Preferences provider (abstraction for ISP/DIP). */
    private final PreferencesStore prefs = PreferenceService.getInstance();

    /** Logical view name for student internships list (used as prefs key). */
    private static final String VIEW_STUDENT_INTERNSHIPS = "student_internships";

    /**
     * Construct a StudentMenu with required collaborators.
     *
     * @param auth authentication/user directory service
     * @param in   console input helper
     */
    public StudentMenu(AuthService auth, SimpleInput in) {
        this.auth = auth;
        this.in = in;
    }

    /**
     * Main loop for student actions.
     *
     * @param me the logged-in student
     */
    public void run(Student me) {
        while (true) {
            System.out.println();
            System.out.println("-- Student Dashboard --");
            System.out.printf("Welcome, %s (Major: %s, Year: %d)%n",
                    me.getName(), me.getMajor(), me.getYear());

            printStudentMenuTable();

            int choice = in.readInt("Choose option: ");
            switch (choice) {
                case 1 -> listOpen(me);            // filter/sort UI
                case 2 -> apply(me);
                case 3 -> listMyApplications(me);
                case 4 -> acceptOffer(me);
                case 5 -> appealUnsuccessful(me);  // appeal flow
                case 6 -> {
                    if (changePassword(me)) {
                        System.out.println("Password changed. Logging out...");
                        System.out.println("Please login again with your new password.");
                        return;
                    }
                }
                case 0 -> {
                    System.out.println("Logging out...");
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    /** Prints the student main-menu as a table. */
    private void printStudentMenuTable() {
        String[] header = {"Option", "Description"};
        List<String[]> rows = TablePrinter.list(
            TablePrinter.row("1", "View internships (eligible; includes FILLED)"),
            TablePrinter.row("2", "Apply to an internship (by ID)"),
            TablePrinter.row("3", "View my applications"),
            TablePrinter.row("4", "Accept a SUCCESSFUL offer"),
            TablePrinter.row("5", "Appeal for UNSUCCESSFUL Applications (Only Once Per Application)"),
            TablePrinter.row("6", "Change password"),
            TablePrinter.row("0", "Logout")
        );
        TablePrinter.printMenu("Student Menu", header, rows);
    }

    /* ----------------------------- Actions ------------------------------ */

    /**
     * Shows internships that the student is eligible for (including filled, for awareness),
     * and provides a loop to configure persistent Filter/Keyword/Sort preferences.
     *
     * @param me the logged-in student
     */
    private void listOpen(Student me) {
        while (true) {
            // Base dataset = eligible for the student (includes FILLED for awareness)
            List<InternshipOpportunity> base = portal.listOpenForStudent(me, true);

            // Load or create this user's preferences for this view (via abstraction)
            ListPreferences p = prefs.get(me, VIEW_STUDENT_INTERNSHIPS);

            // Header: current prefs
            System.out.println();
            System.out.println("-- Internships (Eligible) --");
            System.out.printf("Current Filtered Column: %s%n", p.getFilterColumn().label);
            System.out.printf("Current Filtered Keyword: %s%n", p.getFilterKeyword() == null ? "None" : p.getFilterKeyword());
            System.out.printf("Current Sorted Column: %s%n%n", p.getSortColumn().label);

            // Apply prefs and render table (Title/Company + Total/Empty)
            List<InternshipOpportunity> list = portal.applyOppPrefs(base, p);
            String[] header = {"ID","Title","Company","Level","Total","Empty","Opening Date","Closing Date","Status"};
            List<String[]> rows = new ArrayList<>();
            for (InternshipOpportunity o : list) {
                int total = o.getSlots();
                int empty = Math.max(total - o.getConfirmed(), 0);
                rows.add(TablePrinter.row(
                    o.getId(),
                    safe(o.getTitle()),
                    safe(o.getCompanyName()),
                    safe(o.getLevel()),
                    String.valueOf(total),
                    String.valueOf(empty),
                    safe(o.getOpeningDate()),
                    safe(o.getClosingDate()),
                    safe(o.getStatus())
                ));
            }

            if (rows.isEmpty()) {
                System.out.println("No internships are available right now.");
            } else {
                TablePrinter.printMenu("Internships (Filter/Sort applied)", header, rows);
            }

            // Controls per requirement (now includes Total/Empty Slots)
            System.out.println("Options: 1) Specify Filtering Column  2) Specify Filtering Keywords  3) Specify Sorting Column  9) Clear Preferences  0) Back");
            int choice = in.readInt("Enter choice: ");
            if (choice == 0) return;

            if (choice == 9) {
                p.setFilterColumn(Column.NONE);
                p.setFilterKeyword(null);
                p.setSortColumn(Column.NONE);
                prefs.put(me, VIEW_STUDENT_INTERNSHIPS, p);
                continue;
            }

            if (choice == 1) {
                Column col = askColumn();               // fixed mapping (supports 7–8)
                p.setFilterColumn(col);
                if (col == Column.NONE) p.setFilterKeyword(null);
                prefs.put(me, VIEW_STUDENT_INTERNSHIPS, p);
                continue;
            }

            if (choice == 2) {
                if (p.getFilterColumn() == Column.NONE) {
                    System.out.println("Please choose a Filtering Column first.");
                    continue;
                }
                // Only show keywords that exist in the student's eligible base set
                List<String> kws = portal.uniqueKeywordsOpp(base, p.getFilterColumn());
                pickKeyword(kws, p);
                prefs.put(me, VIEW_STUDENT_INTERNSHIPS, p);
                continue;
            }

            if (choice == 3) {
                Column col = askColumn();               // fixed mapping (supports 7–8)
                p.setSortColumn(col); // ascending by design
                prefs.put(me, VIEW_STUDENT_INTERNSHIPS, p);
            }
        }
    }

    /**
     * Apply to an opportunity by ID, handling validation errors from the service.
     *
     * @param me the logged-in student
     */
    private void apply(Student me) {
        String id = in.readNonEmpty("Enter Opportunity ID to apply: ");
        try {
            portal.applyTo(me, id);
            System.out.println("Applied successfully. Status is now PENDING.");
        } catch (Exception e) {
            System.out.println("Cannot apply: " + e.getMessage());
        }
    }

    /**
     * List all applications for the student, showing company-rep details and appeal metadata.
     *
     * @param me the logged-in student
     */
    private void listMyApplications(Student me) {
        List<Application> apps = portal.listApplicationsByStudent(me.getStudentId());
        if (apps.isEmpty()) {
            System.out.println("\nYou have no applications yet.");
            return;
        }

        String[] header = {
            "OppID", "Status", "AppliedAt",
            "CompanyRep Name", "CompanyRep Email", "Company", "CompanyRep Position",
            "AppealCount", "AppealMessage"
        };
        List<String[]> rows = new ArrayList<>();

        for (Application a : apps) {
            InternshipOpportunity opp = portal.findOpp(a.getOpportunityId());
            String company  = opp != null ? opp.getCompanyName() : "-";
            String repEmail = (opp != null) ? opp.getCreatorEmail() : null;

            CompanyRep rep = (repEmail != null) ? auth.findCompanyRepByEmail(repEmail) : null;
            String repName = (rep != null) ? rep.getName() : "-";
            String repPos  = (rep != null) ? rep.getPosition() : "-";
            String repMail = (rep != null) ? rep.getEmail() : (repEmail == null ? "-" : repEmail);

            rows.add(TablePrinter.row(
                a.getOpportunityId(),
                a.getStatus(),
                a.getAppliedAtIso(),
                repName,
                repMail,
                company,
                repPos,
                String.valueOf(a.getAppealCount()),
                safe(a.getAppealMessage())
            ));
        }

        System.out.println();
        TablePrinter.printMenu("My Applications (with Company Rep details + Appeals)", header, rows);
    }

    /**
     * Accept a SUCCESSFUL offer by opportunity ID.
     *
     * @param me the logged-in student
     */
    private void acceptOffer(Student me) {
        String id = in.readNonEmpty("Enter Opportunity ID to ACCEPT (must be SUCCESSFUL): ");
        try {
            portal.acceptOffer(me.getStudentId(), id);
            System.out.println("Offer accepted. Other PENDING applications withdrawn automatically.");
        } catch (Exception e) {
            System.out.println("Cannot accept: " + e.getMessage());
        }
    }

    // ========================== NEW: Appeals Flow ==========================

    /**
     * Lets the student appeal exactly one UNSUCCESSFUL application (with AppealCount == 0),
     * only if the student has no ACCEPTED offers.
     *
     * @param me the logged-in student
     */
    private void appealUnsuccessful(Student me) {
        if (!portal.studentCanAppeal(me.getStudentId())) {
            System.out.println("Invalid. You are not eligible to appeal for any applications.");
            return;
        }

        List<Application> eligible = portal.listAppealableApplications(me.getStudentId());
        if (eligible.isEmpty()) {
            System.out.println("Invalid. You are not eligible to appeal for any applications.");
            return;
        }

        // Show eligible applications for selection
        String[] header = {"#", "OppID", "Company", "Title", "Status", "AppealCount"};
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < eligible.size(); i++) {
            Application a = eligible.get(i);
            InternshipOpportunity opp = portal.findOpp(a.getOpportunityId());
            rows.add(TablePrinter.row(
                String.valueOf(i + 1),
                a.getOpportunityId(),
                opp == null ? "-" : safe(opp.getCompanyName()),
                opp == null ? "-" : safe(opp.getTitle()),
                a.getStatus(),
                String.valueOf(a.getAppealCount())
            ));
        }
        TablePrinter.printMenu("Eligible Applications for Appeal", header, rows);

        int pick = in.readInt("Select an application (#) to appeal, or 0 to cancel: ");
        if (pick == 0) return;
        if (pick < 1 || pick > eligible.size()) {
            System.out.println("Invalid selection.");
            return;
        }

        Application chosen = eligible.get(pick - 1);
        String msg;
        while (true) {
            msg = in.readNonEmpty("Enter your appeal message (1–100 words): ");
            int wc = wordCount(msg);
            if (wc >= 1 && wc <= 100) break;
            System.out.println("Message must be between 1 and 100 words. You wrote " + wc + " words. Please try again.");
        }

        try {
            portal.appealApplication(me.getStudentId(), chosen.getOpportunityId(), msg);
            System.out.println("Appeal submitted. Status updated to APPEALING.");
        } catch (Exception e) {
            System.out.println("Failed to submit appeal: " + e.getMessage());
        }
    }

    /**
     * Change the student's password. Returns {@code true} when successful so the caller
     * can force a logout (re-authentication).
     *
     * @param me the logged-in student
     * @return {@code true} if password was changed; {@code false} otherwise
     */
    private boolean changePassword(Student me) {
        String oldP = in.readNonEmpty("Old password: ");
        String newP = in.readNonEmpty("New password: ");
        boolean ok = auth.changePassword(me.getStudentId(), oldP, newP);
        if (!ok) {
            System.out.println("Password change failed (old password incorrect?).");
            return false;
        }
        return true;
    }

    /* --------------------------- Helpers (UI) --------------------------- */

    /**
     * Prompt the user to choose a filter/sort column.
     * Supports extended columns: {@code TOTAL_SLOTS}, {@code EMPTY_SLOTS}.
     *
     * @return the selected {@link Column} (or {@link Column#NONE})
     */
    private Column askColumn() {
        System.out.println("Filter/Sort Columns:");
        System.out.println("1.Title  2.Company  3.Level  4.Opening Date  5.Closing Date  6.Status  7.Total Slots  8.Empty Slots  9.None");
        int c = in.readInt("Select: ");
        switch (c) {
            case 1:  return Column.TITLE;
            case 2:  return Column.COMPANY;
            case 3:  return Column.LEVEL;
            case 4:  return Column.OPENING_DATE;
            case 5:  return Column.CLOSING_DATE;
            case 6:  return Column.STATUS;
            case 7:  return Column.TOTAL_SLOTS;
            case 8:  return Column.EMPTY_SLOTS;
            case 9:  return Column.NONE;
            default:
                System.out.println("Invalid choice. Keeping previous selection.");
                return Column.NONE;
        }
    }

    /**
     * Prompt the user to pick a keyword from a list and assign it into the given preferences.
     *
     * @param kws list of available keywords (non-null)
     * @param p   preferences to mutate
     */
    private void pickKeyword(List<String> kws, ListPreferences p) {
        if (kws == null || kws.isEmpty()) {
            System.out.println("No keywords available for this column.");
            return;
        }
        for (int i = 0; i < kws.size(); i++) {
            System.out.printf("%d) %s%n", i + 1, kws.get(i));
        }
        int k = in.readInt("Pick keyword #: ");
        if (k >= 1 && k <= kws.size()) {
            p.setFilterKeyword(kws.get(k - 1));
        }
    }

    /** Return a placeholder dash for blank/NULL strings (for table display). */
    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    /** Count words in a string by whitespace separation. */
    private int wordCount(String s) {
        if (s == null) return 0;
        String t = s.trim();
        if (t.isEmpty()) return 0;
        return t.split("\\s+").length;
    }
}
