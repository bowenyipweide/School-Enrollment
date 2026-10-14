import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/**
 * CLI menu for the <b>Company Representative</b> role.
 *
 * <p><b>Responsibilities</b>:</p>
 * <ul>
 *   <li>Manage the rep’s opportunities (create, edit, delete, list with filter/sort).</li>
 *   <li>Review applications (approve/reject) and appeals (approve/reject).</li>
 *   <li>Apply per-user persistent list preferences (filter/sort) via {@link PreferencesStore}.</li>
 *   <li>Delegate business rules to {@link PortalService} and lookups to {@link AuthService}.</li>
 * </ul>
 *
 * <p><b>SOLID notes</b>:</p>
 * <ul>
 *   <li><b>ISP/DIP</b>: depends on the narrow {@link PreferencesStore} abstraction instead of the concrete service.</li>
 *   <li><b>SRP</b>: keeps UI flow here; domain rules live in services/entities.</li>
 * </ul>
 *
 * @since v1.35
 */
public class CompanyRepMenu {
    /** Authentication/user-directory gateway. */
    private final AuthService auth;
    /** Console input helper. */
    private final SimpleInput in;
    /** Domain service (opportunities/applications, CSV-backed). */
    private final PortalService portal = PortalService.getInstance();

    /** Preferences provider (abstraction for ISP/DIP). */
    private final PreferencesStore prefs = PreferenceService.getInstance();

    /** Logical view name for the rep’s “My Opportunities” list (prefs key). */
    private static final String VIEW_REP_OPPS = "rep_opps";

    /** The exact columns displayed (and offered for filter/sort) in this view. */
    private static final Column[] REP_VIEW_COLS = new Column[]{
        Column.ID,
        Column.TITLE,
        Column.COMPANY,
        Column.LEVEL,
        Column.PREF_MAJOR,
        Column.EMPTY_SLOTS,
        Column.TOTAL_SLOTS,
        Column.PENDING_COMPANY,
        Column.PENDING_STUDENT,
        Column.PENDING_APPEAL,
        Column.OPENING_DATE,
        Column.CLOSING_DATE,
        Column.STATUS
    };

    /**
     * Construct a CompanyRepMenu with required collaborators.
     *
     * @param auth authentication/user-directory service
     * @param in   console input helper
     * @since v1.35
     */
    public CompanyRepMenu(AuthService auth, SimpleInput in) {
        this.auth = auth;
        this.in = in;
    }

    /**
     * Main loop for company-representative actions.
     *
     * <p>Guards access by checking {@link CompanyRep#isApproved()} and then
     * repeatedly renders the dashboard until logout or a password change that
     * forces re-authentication.</p>
     *
     * @param me the logged-in company representative
     * @since v1.35
     */
    public void run(CompanyRep me) {
        if (!me.isApproved()) {
            System.out.println("Your account has not been approved by Staff yet. Please try again later.");
            return;
        }

        while (true) {
            System.out.println();
            System.out.println("-- Company Representative Dashboard --");
            System.out.printf("Welcome, %s @ %s (%s)%n", me.getName(), me.getCompany(), me.getDepartment());

            printRepMenuTable();

            int choice = in.readInt("Choose option: ");
            switch (choice) {
                case 1 -> listMine(me);
                case 2 -> createOpp(me);
                case 3 -> editOpp(me);
                case 4 -> deleteOpp(me);
                case 5 -> viewApps(me);
                case 6 -> decide(me);
                case 7 -> decideAppeal(me); // Appeals approval flow
                case 8 -> {
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

    /**
     * Render the representative’s main menu table.
     *
     * <p>Uses {@link TablePrinter} for consistent layout.</p>
     *
     * @since v1.35
     */
    private void printRepMenuTable() {
        String[] header = {"Option", "Description"};
        List<String[]> rows = TablePrinter.list(
            TablePrinter.row("1", "List my opportunities (filter/sort any displayed column)"),
            TablePrinter.row("2", "Create new opportunity (limit 5 total)"),
            TablePrinter.row("3", "Edit an existing opportunity (PENDING/REJECTED)"),
            TablePrinter.row("4", "Delete an opportunity (no applications)"),
            TablePrinter.row("5", "View applications for an opportunity"),
            TablePrinter.row("6", "Approve/Reject a student's application"),
            TablePrinter.row("7", "Approve/Reject a student's appeal"),
            TablePrinter.row("8", "Change password"),
            TablePrinter.row("0", "Logout")
        );
        TablePrinter.printMenu("Company Representative Menu", header, rows);
    }

    /* ------------------------------- Actions ------------------------------ */

    /**
     * List this rep’s opportunities with persistent Filter/Keyword/Sort controls.
     *
     * <ul>
     *   <li>Shows Empty Slots and Total Slots separately.</li>
     *   <li>Shows counts: PENDING (company approval), SUCCESSFUL (pending student acceptance),
     *       and APPEALING (pending appeal approval).</li>
     *   <li>Filter/Sort can target any displayed column.</li>
     * </ul>
     *
     * <p>Preferences are loaded/saved via {@link PreferencesStore} using a logical
     * view key, keeping the UI decoupled from storage details.</p>
     *
     * @param me the logged-in company representative
     * @since v1.35
     */
    private void listMine(CompanyRep me) {
        while (true) {
            // Base dataset = this rep's own postings
            List<InternshipOpportunity> base = portal.listOpportunitiesByRep(me.getEmail());

            // Load or create per-user preferences for this view (via abstraction)
            ListPreferences p = prefs.get(me, VIEW_REP_OPPS);

            System.out.println();
            System.out.println("-- My Opportunities --");
            System.out.printf("Current Filtered Column: %s%n", p.getFilterColumn().label);
            System.out.printf("Current Filtered Keyword: %s%n", p.getFilterKeyword() == null ? "None" : p.getFilterKeyword());
            System.out.printf("Current Sorted Column: %s%n%n", p.getSortColumn().label);

            // Apply prefs (ascending sort by chosen column)
            List<InternshipOpportunity> list = portal.applyOppPrefs(base, p);

            // Table with counts (includes Pending Appeal Approval)
            String[] header = {
                "ID", "Title", "Company", "Level", "PrefMajor",
                "Empty Slots", "Total Slots",
                "Pending Company Approval", "Pending Student Acceptance", "Pending Appeal Approval",
                "Opening", "Closing", "Status"
            };
            List<String[]> rows = new ArrayList<>();

            for (InternshipOpportunity o : list) {
                int empty = Math.max(0, o.getSlots() - o.getConfirmed());
                int total = o.getSlots();
                int pendingCompany = portal.countApplications(o.getId(), "PENDING");
                int pendingStudent = portal.countApplications(o.getId(), "SUCCESSFUL"); // waiting for student acceptance
                int pendingAppeal  = portal.countApplications(o.getId(), "APPEALING");  // appeals pending

                rows.add(TablePrinter.row(
                    safe(o.getId()),
                    safe(o.getTitle()),
                    safe(o.getCompanyName()),
                    safe(o.getLevel()),
                    o.getPreferredMajor().isBlank() ? "ANY" : o.getPreferredMajor(),
                    String.valueOf(empty),
                    String.valueOf(total),
                    String.valueOf(pendingCompany),
                    String.valueOf(pendingStudent),
                    String.valueOf(pendingAppeal),
                    safe(o.getOpeningDate()),
                    safe(o.getClosingDate()),
                    safe(o.getStatus())
                ));
            }

            System.out.println();
            if (rows.isEmpty()) {
                System.out.println("You have not created any opportunities yet.");
            } else {
                TablePrinter.printMenu("My Opportunities (Filter/Sort applied)", header, rows);
            }

            // Controls (ALL displayed columns are available)
            System.out.println("Options: 1) Specify Filtering Column  2) Specify Filtering Keywords  3) Specify Sorting Column  9) Clear Preferences  0) Back");
            int choice = in.readInt("Enter choice: ");
            if (choice == 0) return;

            if (choice == 9) {
                p.setFilterColumn(Column.NONE);
                p.setFilterKeyword(null);
                p.setSortColumn(Column.NONE);
                prefs.put(me, VIEW_REP_OPPS, p);
                continue;
            }

            if (choice == 1) {
                Column col = askColumn(REP_VIEW_COLS);
                p.setFilterColumn(col);
                if (col == Column.NONE) p.setFilterKeyword(null);
                prefs.put(me, VIEW_REP_OPPS, p);
                continue;
            }

            if (choice == 2) {
                if (p.getFilterColumn() == Column.NONE) {
                    System.out.println("Please choose a Filtering Column first.");
                    continue;
                }
                // Only keywords present in THIS rep's base set are shown
                List<String> kws = portal.uniqueKeywordsOpp(base, p.getFilterColumn());
                pickKeyword(kws, p);
                prefs.put(me, VIEW_REP_OPPS, p);
                continue;
            }

            if (choice == 3) {
                Column col = askColumn(REP_VIEW_COLS);
                p.setSortColumn(col); // ascending
                prefs.put(me, VIEW_REP_OPPS, p);
            }
        }
    }

    /**
     * Create a new opportunity (enforces the per-rep cap via service).
     *
     * <p>Validates dates and slot bounds at the UI level before delegating to
     * {@link PortalService#createOpportunity(CompanyRep, String, String, String, String, String, String, int)}.</p>
     *
     * @param me the logged-in company representative
     * @since v1.35
     */
    private void createOpp(CompanyRep me) {
        System.out.println("\n-- Create Opportunity --");
        if (!portal.canCreateMore(me.getEmail())) {
            System.out.println("You already reached the limit of 5 opportunities (per representative, across all statuses).");
            return;
        }

        String title = in.readNonEmpty("Title: ");
        String desc  = in.readNonEmpty("Description: ");

        printLevelChoices();
        int lv = readLevel1to3("Choose (1-3): ");
        String level = switch (lv) {
            case 1 -> "BASIC";
            case 2 -> "INTERMEDIATE";
            case 3 -> "ADVANCED";
            default -> "BASIC";
        };

        String pref = in.readDefault("Preferred Major (e.g., COMPUTER SCIENCE) [blank = ANY]: ", "")
                .trim().toUpperCase();
        if (pref.equalsIgnoreCase("ANY")) pref = ""; // store blank = ANY

        String open  = readStrictDMY("Opening date (DD/MM/YYYY): ");
        String close = readStrictDMY("Closing date (DD/MM/YYYY): ");
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
        LocalDate openD  = LocalDate.parse(open, fmt);
        LocalDate closeD = LocalDate.parse(close, fmt);
        while (closeD.isBefore(openD)) {
            System.out.println("Closing date cannot be before the opening date. Please re-enter.");
            close = readStrictDMY("Closing date (DD/MM/YYYY): ");
            closeD = LocalDate.parse(close, fmt);
        }

        int slots = readIntInRange("Slots (1..10): ", 1, 10);

        try {
            InternshipOpportunity o = portal.createOpportunity(
                me, title, desc, level, pref, open, close, slots
            );
            System.out.println("Created with ID=" + o.getId() + ". Status=PENDING (awaiting Staff approval).");
        } catch (Exception e) {
            System.out.println("Create failed: " + e.getMessage());
        }
    }

    /**
     * Edit an existing opportunity (must be editable per service rule: PENDING/REJECTED).
     *
     * <p>Performs UI-side validation (dates, slots &ge; confirmed) then delegates to
     * {@link PortalService#updateOpportunity(String, String, String, String, String, String, String, int)}.</p>
     *
     * @param me the logged-in company representative
     * @since v1.35
     */
    private void editOpp(CompanyRep me) {
        System.out.println("\n-- Edit Opportunity --");
        String id = in.readNonEmpty("Enter Opportunity ID to edit: ");

        try {
            InternshipOpportunity o = portal.requireEditableOpportunity(me.getEmail(), id);
            System.out.println("Editing: " + o.getTitle() + " @ " + o.getCompanyName() + " (Status=" + o.getStatus() + ")");

            // Title
            String title = in.readDefault("Title [" + o.getTitle() + "]: ", o.getTitle()).trim();
            if (title.isBlank()) title = o.getTitle();

            // Description
            String desc = in.readDefault("Description [" + o.getDescription() + "]: ", o.getDescription()).trim();
            if (desc.isBlank()) desc = o.getDescription();

            // Level
            System.out.println("Current Level: " + o.getLevel());
            printLevelChoices();
            int lv = readLevel1to3("Choose Level (1-3) or press Enter to keep current: ", true);
            String level = (lv == -1) ? o.getLevel() : switch (lv) {
                case 1 -> "BASIC";
                case 2 -> "INTERMEDIATE";
                case 3 -> "ADVANCED";
                default -> o.getLevel();
            };

            // Preferred Major (blank/ANY -> "")
            String curPref = o.getPreferredMajor().isBlank() ? "ANY" : o.getPreferredMajor();
            String pref = in.readDefault("Preferred Major [" + curPref + "] (blank/ANY = ANY): ", curPref)
                            .trim().toUpperCase();
            if (pref.equalsIgnoreCase("ANY")) pref = ""; // store blank

            // Dates
            String open  = readStrictDMYDefault("Opening date (DD/MM/YYYY) [" + o.getOpeningDate() + "]: ", o.getOpeningDate());
            String close = readStrictDMYDefault("Closing date (DD/MM/YYYY) [" + o.getClosingDate() + "]: ", o.getClosingDate());
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);
            LocalDate openD  = LocalDate.parse(open, fmt);
            LocalDate closeD = LocalDate.parse(close, fmt);
            while (closeD.isBefore(openD)) {
                System.out.println("Closing date cannot be before the opening date. Please re-enter.");
                close = readStrictDMYDefault("Closing date (DD/MM/YYYY) [" + o.getClosingDate() + "]: ", o.getClosingDate());
                closeD = LocalDate.parse(close, fmt);
            }

            // Slots (must be >= confirmed)
            int minSlots = Math.max(1, o.getConfirmed());
            int slots = readIntInRange("Slots (" + minSlots + "..10): ", minSlots, 10);

            portal.updateOpportunity(me.getEmail(), id, title, desc, level, pref, open, close, slots);
            System.out.println("Opportunity updated.");
        } catch (Exception e) {
            System.out.println("Edit failed: " + e.getMessage());
        }
    }

    /**
     * Delete an opportunity (service ensures there are no applications and no confirmed students).
     *
     * @param me the logged-in company representative
     * @since v1.35
     */
    private void deleteOpp(CompanyRep me) {
        System.out.println("\n-- Delete Opportunity --");
        String id = in.readNonEmpty("Enter Opportunity ID to delete: ");
        boolean sure = in.readYesNo("Are you sure you want to delete this opportunity?");
        if (!sure) return;

        try {
            portal.deleteOpportunity(me.getEmail(), id);
            System.out.println("Opportunity deleted.");
        } catch (Exception e) {
            System.out.println("Delete failed: " + e.getMessage());
        }
    }

    /**
     * View applications for a specific opportunity, with student details.
     *
     * @param me the logged-in company representative
     * @since v1.35
     */
    private void viewApps(CompanyRep me) {
        String id = in.readNonEmpty("Enter Opportunity ID: ");
        try {
            List<Application> apps = portal.listApplicationsForOpportunity(me.getEmail(), id);
            if (apps.isEmpty()) {
                System.out.println("No applications yet for this opportunity.");
                return;
            }

            String[] header = {"StudentID", "Name", "Major", "Year", "Email", "Status", "AppliedAt"};
            List<String[]> rows = new ArrayList<>();

            for (Application a : apps) {
                Student st = auth.findStudentById(a.getStudentId());
                String name  = (st != null) ? st.getName()  : "-";
                String major = (st != null) ? st.getMajor() : "-";
                String year  = (st != null) ? String.valueOf(st.getYear()) : "-";
                String mail  = (st != null) ? st.getEmail() : "-";

                rows.add(TablePrinter.row(
                    a.getStudentId(),
                    name,
                    major,
                    year,
                    mail,
                    a.getStatus().toString(),
                    a.getAppliedAtIso()
                ));
            }

            System.out.println();
            TablePrinter.printMenu("Applications for Opportunity ID=" + id, header, rows);
        } catch (Exception e) {
            System.out.println("Unable to list applications: " + e.getMessage());
        }
    }

    /**
     * Approve/Reject one student's application for a specific opportunity.
     *
     * <p>Delegates to {@link PortalService#setApplicationDecision(String, String, String, boolean)}.
     * If the application is in APPEALING status, the service routes to the appeal-aware path.</p>
     *
     * @param me the logged-in company representative
     * @since v1.35
     */
    private void decide(CompanyRep me) {
        String id = in.readNonEmpty("Opportunity ID: ");
        String studentId = in.readNonEmpty("Student ID to update: ");
        boolean approve = in.readYesNo("Approve this student?");

        try {
            portal.setApplicationDecision(me.getEmail(), id, studentId, approve);
            System.out.println("Updated application status.");
        } catch (Exception e) {
            System.out.println("Update failed: " + e.getMessage());
        }
    }

    // ========================== Appeals Decisions ==========================

    /**
     * Lists all APPEALING applications across this rep’s owned opportunities,
     * shows the (max 100 words) appeal message, and allows Approve/Reject.
     *
     * <p>For the selected appeal, calls the same decision API; the service ensures
     * ownership, opportunity approval, slot capacity, and proper status transitions.</p>
     *
     * @param me the logged-in company representative
     * @since v1.35
     */
    private void decideAppeal(CompanyRep me) {
        System.out.println("\n-- Approve/Reject a student's appeal --");

        // Gather all APPEALING apps for opps owned by this rep
        List<InternshipOpportunity> myOpps = portal.listOpportunitiesByRep(me.getEmail());
        List<Application> appealing = new ArrayList<>();
        for (InternshipOpportunity o : myOpps) {
            List<Application> apps = portal.listApplicationsForOpportunity(me.getEmail(), o.getId());
            for (Application a : apps) {
                if ("APPEALING".equalsIgnoreCase(a.getStatus())) {
                    appealing.add(a);
                }
            }
        }

        if (appealing.isEmpty()) {
            System.out.println("No appeals pending right now.");
            return;
        }

        String[] header = {
            "#", "OppID",
            "StudentID", "Name", "Major", "Year", "Email",
            "AppealCount", "AppealMessage (max 100 words)", "AppliedAt", "Status"
        };
        List<String[]> rows = new ArrayList<>();
        for (int i = 0; i < appealing.size(); i++) {
            Application a = appealing.get(i);
            Student st = auth.findStudentById(a.getStudentId());
            String name  = (st != null) ? st.getName()  : "-";
            String major = (st != null) ? st.getMajor() : "-";
            String year  = (st != null) ? String.valueOf(st.getYear()) : "-";
            String mail  = (st != null) ? st.getEmail() : "-";

            rows.add(TablePrinter.row(
                String.valueOf(i + 1),
                a.getOpportunityId(),
                a.getStudentId(),
                name, major, year, mail,
                String.valueOf(a.getAppealCount()),
                truncateWords(a.getAppealMessage(), 100),
                a.getAppliedAtIso(),
                a.getStatus().toString()
            ));
        }
        TablePrinter.printMenu("Pending Appeals", header, rows);

        int pick = in.readInt("Select an appeal (#) to act on, or 0 to cancel: ");
        if (pick == 0) return;
        if (pick < 1 || pick > appealing.size()) {
            System.out.println("Invalid selection.");
            return;
        }

        Application chosen = appealing.get(pick - 1);
        boolean approve = in.readYesNo("Approve this appeal? (Yes=SUCCESSFUL, No=UNSUCCESSFUL)");

        try {
            // Reuse the same decision API; it enforces ownership, approval status, and slot availability.
            portal.setApplicationDecision(me.getEmail(), chosen.getOpportunityId(), chosen.getStudentId(), approve);
            System.out.println(approve ? "Appeal approved → status set to SUCCESSFUL." : "Appeal rejected → status set to UNSUCCESSFUL.");
        } catch (Exception e) {
            System.out.println("Unable to update appeal: " + e.getMessage());
        }
    }

    /**
     * Change the rep’s password. Returns {@code true} when successful so the caller
     * can force a logout (re-authentication).
     *
     * @param me the logged-in company representative
     * @return {@code true} if password was changed; {@code false} otherwise
     * @since v1.35
     */
    private boolean changePassword(CompanyRep me) {
        String oldP = in.readNonEmpty("Old password: ");
        String newP = in.readNonEmpty("New password: ");
        boolean ok = auth.changePassword(me.getEmail(), oldP, newP);
        if (!ok) {
            System.out.println("Password change failed (old password incorrect?).");
            return false;
        }
        return true;
    }

    /* ------------------------------ Inputs ------------------------------- */

    /**
     * Print the 3-level choice table used in create/edit flows.
     *
     * @since v1.35
     */
    private void printLevelChoices() {
        String[] lvHeader = {"#", "Level"};
        List<String[]> lvRows = TablePrinter.list(
            TablePrinter.row("1", "BASIC"),
            TablePrinter.row("2", "INTERMEDIATE"),
            TablePrinter.row("3", "ADVANCED")
        );
        TablePrinter.printMenu("Level Options", lvHeader, lvRows);
    }

    /**
     * Read a level in [1..3].
     *
     * @param prompt prompt text
     * @return 1=BASIC, 2=INTERMEDIATE, 3=ADVANCED
     * @since v1.35
     */
    private int readLevel1to3(String prompt) {
        return readLevel1to3(prompt, false);
    }

    /**
     * Read a level in [1..3], optionally allowing blank to keep current value during edit.
     *
     * @param prompt            prompt text
     * @param allowBlankToKeep  if {@code true}, Enter keeps current value and returns -1
     * @return chosen level (1..3) or -1 when keeping current value
     * @since v1.35
     */
    private int readLevel1to3(String prompt, boolean allowBlankToKeep) {
        while (true) {
            String raw = in.readDefault(prompt, "").trim();
            if (allowBlankToKeep && raw.isBlank()) return -1; // keep current
            try {
                int v = Integer.parseInt(raw);
                if (v >= 1 && v <= 3) return v;
            } catch (NumberFormatException ignored) {}
            System.out.println("Invalid level. Please enter 1, 2, or 3" + (allowBlankToKeep ? " (or press Enter to keep current)." : "."));
        }
    }

    /**
     * Read an int in the inclusive range [min..max].
     *
     * @param prompt prompt text
     * @param min    inclusive minimum
     * @param max    inclusive maximum
     * @return validated integer within range
     * @since v1.35
     */
    private int readIntInRange(String prompt, int min, int max) {
        while (true) {
            int v = in.readInt(prompt);
            if (v >= min && v <= max) return v;
            System.out.printf("Value must be between %d and %d.%n", min, max);
        }
    }

    /**
     * Read a strict calendar-valid date in DD/MM/YYYY.
     *
     * @param prompt prompt text
     * @return normalized date string (DD/MM/YYYY)
     * @since v1.35
     */
    private String readStrictDMY(String prompt) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

        while (true) {
            String raw = in.readNonEmpty(prompt).trim();
            if (!raw.matches("\\d{2}/\\d{2}/\\d{4}")) {
                System.out.println("Invalid format. Please use DD/MM/YYYY (e.g., 17/10/2025).");
                continue;
            }
            try {
                LocalDate d = LocalDate.parse(raw, fmt);
                return d.format(fmt);
            } catch (DateTimeParseException ex) {
                System.out.println("Invalid date. Please use a real calendar date in DD/MM/YYYY.");
            }
        }
    }

    /**
     * Same as {@link #readStrictDMY(String)}, but allows blank to keep the default value.
     *
     * @param prompt       prompt text
     * @param currentValue value to keep when user presses Enter
     * @return normalized date string (DD/MM/YYYY), or {@code currentValue} when kept
     * @since v1.35
     */
    private String readStrictDMYDefault(String prompt, String currentValue) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/uuuu")
                .withResolverStyle(ResolverStyle.STRICT);

        while (true) {
            String raw = in.readDefault(prompt, "").trim();
            if (raw.isBlank()) return currentValue; // keep
            if (!raw.matches("\\d{2}/\\d{2}/\\d{4}")) {
                System.out.println("Invalid format. Please use DD/MM/YYYY (e.g., 17/10/2025).");
                continue;
            }
            try {
                LocalDate d = LocalDate.parse(raw, fmt);
                return d.format(fmt);
            } catch (DateTimeParseException ex) {
                System.out.println("Invalid date. Please use a real calendar date in DD/MM/YYYY.");
            }
        }
    }

    /* ----------------------------- Helpers (UI) --------------------------- */

    /**
     * Prompt the user to choose a filter/sort column from a provided set.
     *
     * @param options the allowed columns to display/choose
     * @return the selected {@link Column} (or {@link Column#NONE} if “None” or invalid)
     * @since v1.35
     */
    private Column askColumn(Column[] options) {
        System.out.println("Filter/Sort Columns (choose any displayed column):");
        for (int i = 0; i < options.length; i++) {
            System.out.printf("%d. %s%n", i + 1, options[i].label);
        }
        System.out.printf("%d. None%n", options.length + 1);
        int c = in.readInt("Select: ");
        if (c == options.length + 1) return Column.NONE;
        if (c >= 1 && c <= options.length) return options[c - 1];
        System.out.println("Invalid choice. Keeping previous selection.");
        return Column.NONE;
    }

    /**
     * Prompt the user to pick a keyword from a list and assign it into the given preferences.
     *
     * @param kws list of available keywords (non-null)
     * @param p   preferences to mutate
     * @since v1.35
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

    /**
     * Return a placeholder dash for blank/NULL strings (for table display).
     *
     * @param s input string (nullable)
     * @return {@code "-"} for blank/null; otherwise the trimmed string
     * @since v1.35
     */
    private String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }

    /**
     * Truncate a string to at most {@code maxWords} words, appending an ellipsis if needed.
     *
     * @param s         original string (nullable)
     * @param maxWords  maximum words to keep
     * @return truncated string or {@code "-"} when blank/null
     * @since v1.35
     */
    private String truncateWords(String s, int maxWords) {
        if (s == null || s.isBlank()) return "-";
        String[] w = s.trim().split("\\s+");
        if (w.length <= maxWords) return s.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxWords; i++) {
            if (i > 0) sb.append(' ');
            sb.append(w[i]);
        }
        sb.append(" …");
        return sb.toString();
    }
}
