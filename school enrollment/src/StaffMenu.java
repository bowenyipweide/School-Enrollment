import java.util.List;
import java.util.ArrayList;

/**
 * CLI menu for the <b>Staff</b> role.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Review & approve/reject company representatives and internship opportunities.</li>
 *   <li>Apply per-user persistent list preferences (filter/sort) via {@link PreferencesStore}.</li>
 *   <li>Delegate business rules to {@link PortalService} and user lookups to {@link AuthService}.</li>
 * </ul>
 * <p>
 * SOLID notes:
 * <ul>
 *   <li><b>ISP/DIP:</b> depends on the narrow {@link PreferencesStore} instead of the concrete service.</li>
 *   <li><b>SRP:</b> keeps UI flow separate from domain rules (services/entities own the rules).</li>
 * </ul>
 */
public class StaffMenu {
    private final AuthService auth;
    private final SimpleInput in;
    private final PortalService portal = PortalService.getInstance();

    /** Preferences provider (abstraction for ISP/DIP). */
    private final PreferencesStore prefs = PreferenceService.getInstance();

    /** Logical view name for staff’s pending-opportunities list (prefs key). */
    private static final String VIEW_STAFF_OPPS = "staff_opps";

    /**
     * Construct a StaffMenu with required collaborators.
     *
     * @param auth authentication/user-directory service
     * @param in   console input helper
     */
    public StaffMenu(AuthService auth, SimpleInput in) {
        this.auth = auth;
        this.in = in;
    }

    /**
     * Main loop for staff actions.
     *
     * @param me the logged-in staff user
     */
    public void run(Staff me) {
        while (true) {
            System.out.println();
            System.out.println("-- Career Centre Staff Dashboard --");
            System.out.printf("Welcome, %s (%s)%n", me.getName(), me.getDepartment());

            printStaffMenuTable();

            int choice = in.readInt("Choose option: ");
            switch (choice) {
                case 1 -> reviewCompanyReps();
                case 2 -> reviewOpportunities(me); // includes filter/sort + approve/reject
                case 3 -> {
                    if (changePassword(me)) {
                        System.out.println("Password changed. Logging out...");
                        System.out.println("Please login again with your new password.");
                        return; // logout after successful change
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

    /** Prints the staff main-menu as a table. */
    private void printStaffMenuTable() {
        String[] header = {"Option", "Description"};
        List<String[]> rows = TablePrinter.list(
            TablePrinter.row("1", "Review company representative registrations"),
            TablePrinter.row("2", "Review internship opportunities (approve/reject)"),
            TablePrinter.row("3", "Change my password"),
            TablePrinter.row("0", "Logout")
        );
        TablePrinter.printMenu("Staff Menu", header, rows);
    }

    /* ----------------------- Company Rep approvals ---------------------- */

    /**
     * Lists pending company-rep registrations and lets staff approve/reject one by email.
     */
    private void reviewCompanyReps() {
        System.out.println("\n-- Pending Company Representatives --");
        List<CompanyRep> pending = auth.listPendingReps();
        if (pending.isEmpty()) {
            System.out.println("No pending registrations.");
            return;
        }

        String[] header = {"Name", "Company", "Department/Position", "LoginID", "Approved"};
        List<String[]> rows = new ArrayList<>();
        for (CompanyRep r : pending) {
            rows.add(TablePrinter.row(
                r.getName(),
                r.getCompany(),
                r.getDepartment() + " / " + r.getPosition(),
                r.getEmail(),
                String.valueOf(r.isApproved())
            ));
        }
        TablePrinter.printMenu("Pending Company Representatives", header, rows);

        String key = in.readNonEmpty("\nEnter rep Email to approve/reject (or 'skip'): ");
        if (key.equalsIgnoreCase("skip")) return;

        boolean approve = in.readYesNo("Approve this representative?");
        boolean ok = auth.setCompanyRepApproval(key, approve);
        System.out.println(ok ? "Updated." : "Could not find that representative.");

        auth.reload(); // refresh view after update
    }

    /* -------------------- Opportunity approval workflow ----------------- */

    /**
     * Review pending internship opportunities with persistent Filter/Sort controls
     * and an inline action to approve/reject a selected opportunity.
     *
     * @param me the logged-in staff user
     */
    private void reviewOpportunities(Staff me) {
        while (true) {
            // Base dataset = PENDING opportunities
            List<InternshipOpportunity> base = portal.listPendingOpportunities();

            // Load/create this staff user's preferences for this view (via abstraction)
            ListPreferences p = prefs.get(me, VIEW_STAFF_OPPS);

            System.out.println("\n-- Staff: Pending Opportunities --");
            System.out.printf("Current Filtered Column: %s%n", p.getFilterColumn().label);
            System.out.printf("Current Filtered Keyword: %s%n", p.getFilterKeyword() == null ? "None" : p.getFilterKeyword());
            System.out.printf("Current Sorted Column: %s%n%n", p.getSortColumn().label);

            // Apply prefs (ascending sort by design)
            List<InternshipOpportunity> list = portal.applyOppPrefs(base, p);

            // Render table (separate Title and Company columns)
            String[] header = {"ID","Title","Company","Level","PrefMajor","Slots","Opening","Closing","Status"};
            List<String[]> rows = new ArrayList<>();
            for (InternshipOpportunity o : list) {
                rows.add(TablePrinter.row(
                    o.getId(),
                    safe(o.getTitle()),
                    safe(o.getCompanyName()),
                    safe(o.getLevel()),
                    o.getPreferredMajor().isBlank() ? "ANY" : o.getPreferredMajor(),
                    String.valueOf(o.getSlots()),
                    safe(o.getOpeningDate()),
                    safe(o.getClosingDate()),
                    safe(o.getStatus())
                ));
            }

            if (rows.isEmpty()) {
                System.out.println("No pending opportunities.");
            } else {
                TablePrinter.printMenu("Pending Internship Opportunities (Filter/Sort applied)", header, rows);
            }

            // Controls (includes Approve/Reject action)
            System.out.println("Options: 1) Specify Filtering Column  2) Specify Filtering Keywords  3) Specify Sorting Column  4) Approve/Reject an Opportunity  9) Clear Preferences  0) Back");
            int choice = in.readInt("Enter choice: ");
            if (choice == 0) return;

            if (choice == 9) {
                p.setFilterColumn(Column.NONE);
                p.setFilterKeyword(null);
                p.setSortColumn(Column.NONE);
                prefs.put(me, VIEW_STAFF_OPPS, p);
                continue;
            }

            if (choice == 1) {
                Column col = askColumn();
                p.setFilterColumn(col);
                if (col == Column.NONE) p.setFilterKeyword(null);
                prefs.put(me, VIEW_STAFF_OPPS, p);
                continue;
            }

            if (choice == 2) {
                if (p.getFilterColumn() == Column.NONE) {
                    System.out.println("Please choose a Filtering Column first.");
                    continue;
                }
                List<String> kws = portal.uniqueKeywordsOpp(base, p.getFilterColumn());
                pickKeyword(kws, p);
                prefs.put(me, VIEW_STAFF_OPPS, p);
                continue;
            }

            if (choice == 3) {
                Column col = askColumn();
                p.setSortColumn(col); // ascending
                prefs.put(me, VIEW_STAFF_OPPS, p);
                continue;
            }

            if (choice == 4) {
                String id = in.readNonEmpty("\nEnter Opportunity ID to approve/reject (or 'skip'): ");
                if (!id.equalsIgnoreCase("skip")) {
                    boolean approve = in.readYesNo("Approve this opportunity?");
                    boolean ok = portal.setOpportunityApproval(id, approve); // keep your existing API
                    System.out.println(ok ? "Updated." : "Opportunity not found.");
                    portal.reload(); // reflect latest status
                }
            }
        }
    }

    /* --------------------------- Account admin -------------------------- */

    /**
     * Change the staff user's password. Returns {@code true} when successful so the caller
     * can force a logout (re-authentication).
     *
     * @param me the logged-in staff user
     * @return {@code true} if password was changed; {@code false} otherwise
     */
    private boolean changePassword(Staff me) {
        String oldP = in.readNonEmpty("Old password: ");
        String newP = in.readNonEmpty("New password: ");
        boolean ok = auth.changePassword(me.getStaffId(), oldP, newP);
        if (!ok) {
            System.out.println("Password change failed (old password incorrect?).");
            return false;
        }
        return true;
    }

    /* --------------------------- Helpers (UI) --------------------------- */

    /**
     * Prompt the user to choose a filter/sort column.
     *
     * @return the selected {@link Column} (or {@link Column#NONE})
     */
    private Column askColumn() {
        System.out.println("Filter/Sort Columns:");
        System.out.println("1.Title  2.Company  3.Level  4.Opening Date  5.Closing Date  6.Status  7.None");
        int c = in.readInt("Select: ");
        return (c == 7) ? Column.NONE : Column.fromUserChoice(c);
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
}
