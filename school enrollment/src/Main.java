/**
 * Entry point for the CT2003 Internship Hub (CLI).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Bootstraps core services: {@link AuthService}, {@link PortalService}, and {@link SimpleInput}.</li>
 *   <li>Presents the top-level menu (Login / Company Rep Registration / Exit).</li>
 *   <li>Delegates authenticated flows to role-specific menus:
 *       {@link StudentMenu}, {@link StaffMenu}, {@link CompanyRepMenu}.</li>
 *   <li>After each role menu returns, reloads backing stores to reflect any persisted changes.</li>
 * </ul>
 *
 * <h3>Login flow</h3>
 * Uses {@link AuthService#attemptLogin(String, String)} to obtain a
 * {@link AuthService.LoginAttempt} that carries success/failure reasons:
 * USER_NOT_FOUND, WRONG_PASSWORD, NOT_APPROVED. On success, a typed
 * {@link AuthService.AuthResult} is provided with role-specific accessors.
 *
 * @since v1.3 (menus and services); refined diagnostics in v1.35
 */
public class Main {

    /**
     * Starts the CLI and loops on the main menu until the user chooses Exit.
     *
     * @param args standard JVM args (unused)
     */
    public static void main(String[] args) {
        AuthService auth = new AuthService();
        PortalService portal = PortalService.getInstance();
        SimpleInput in = new SimpleInput();

        while (true) {
            printMainMenu(); // formatted table menu
            int choice = in.readInt("Choose option: ");

            switch (choice) {
                case 1 -> {
                    String id = in.readNonEmpty("User ID / Email: ");
                    String pw = in.readNonEmpty("Password: ");

                    // Diagnostic login provides explicit failure reasons.
                    AuthService.LoginAttempt attempt = auth.attemptLogin(id, pw);

                    if (!attempt.success()) {
                        switch (attempt.reason()) {
                            case USER_NOT_FOUND -> System.out.println("Login failed: user ID/email does not exist.");
                            case WRONG_PASSWORD -> System.out.println("Login failed: incorrect password.");
                            case NOT_APPROVED   -> System.out.println("Login failed: your account is not approved yet.");
                            default             -> System.out.println("Login failed.");
                        }
                        break;
                    }

                    // Success path: route to the correct role menu.
                    AuthService.AuthResult login = attempt.result(); // guaranteed non-null here
                    switch (login.getRole()) {
                        case STUDENT -> {
                            System.out.println("Logged in as STUDENT: " + login.getStudent().getName());
                            new StudentMenu(auth, in).run(login.getStudent());
                            // Reflect any persisted updates post-session.
                            auth.reload(); portal.reload();
                        }
                        case STAFF -> {
                            System.out.println("Logged in as STAFF: " + login.getStaff().getName());
                            new StaffMenu(auth, in).run(login.getStaff());
                            auth.reload(); portal.reload();
                        }
                        case COMPANY_REP -> {
                            System.out.println("Logged in as COMPANY REP: " + login.getRep().getName());
                            new CompanyRepMenu(auth, in).run(login.getRep());
                            auth.reload(); portal.reload();
                        }
                    }
                }
                case 2 -> {
                    System.out.println("\n-- Company Representative Registration --");
                    String email = in.readNonEmpty("Company Email (login ID): ");
                    String name = in.readNonEmpty("Your Name: ");
                    String company = in.readNonEmpty("Company: ");
                    String dept = in.readNonEmpty("Department: ");
                    String position = in.readNonEmpty("Position: ");
                    String password = in.readDefault("Password (blank = 'password'): ", "password");

                    try {
                        boolean ok = auth.registerCompanyRep(email, name, company, dept, position, password);
                        if (ok) {
                            System.out.println("Submitted. A STAFF user must approve before you can login.");
                            auth.reload();
                        } else {
                            System.out.println("Registration failed. Please try again.");
                        }
                    } catch (IllegalStateException dup) {
                        // Exact message as thrown by service (e.g., duplicate).
                        System.out.println(dup.getMessage());
                    } catch (IllegalArgumentException bad) {
                        System.out.println(bad.getMessage());
                    }
                }
                case 0 -> {
                    System.out.println("Bye!");
                    return;
                }
                default -> System.out.println("Invalid option.");
            }
        }
    }

    /**
     * Prints the top-level menu using {@link TablePrinter}.
     * Keeps presentation consistent across the application.
     */
    private static void printMainMenu() {
        String[] header = {"Option", "Action"};
        java.util.List<String[]> rows = TablePrinter.list(
            TablePrinter.row("1", "Login"),
            TablePrinter.row("2", "Register as Company Representative"),
            TablePrinter.row("0", "Exit")
        );
        TablePrinter.printMenu("CT2003 Assignment – Internship Hub (CLI)", header, rows);
    }
}
