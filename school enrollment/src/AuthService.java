import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Authentication & account administration backed by the three user CSVs
 * managed by {@link CsvUtil}.
 *
 * <p><b>Sources</b>:</p>
 * <ul>
 *   <li>{@code sample_student_list.csv}</li>
 *   <li>{@code sample_staff_list.csv}</li>
 *   <li>{@code sample_company_representative_list.csv}</li>
 * </ul>
 *
 * <p>Provides:</p>
 * <ul>
 *   <li>Diagnostic login with explicit failure reasons.</li>
 *   <li>Backwards-compatible login (null on failure).</li>
 *   <li>Password changes persisted to CSV.</li>
 *   <li>Company-rep registration & approval.</li>
 *   <li>Lightweight directory lookups used by menus.</li>
 * </ul>
 *
 * @since v1.35
 */
public class AuthService {

    /** In-memory student directory (CSV-backed). */
    private List<Student> students = new ArrayList<>();
    /** In-memory staff directory (CSV-backed). */
    private List<Staff> staff = new ArrayList<>();
    /** In-memory company-rep directory (CSV-backed). */
    private List<CompanyRep> reps = new ArrayList<>();

    /** Construct and eagerly load all user CSVs. */
    public AuthService() { reload(); }

    /**
     * Reload all three user CSVs into memory.
     *
     * <p>On I/O error, logs to {@code System.err} and replaces the affected list with an empty one.</p>
     *
     * @since v1.35
     */
    public final void reload() {
        try { students = CsvUtil.readStudents(); }
        catch (IOException e) { System.err.println("AuthService: students CSV: " + e.getMessage()); students = new ArrayList<>(); }
        try { staff    = CsvUtil.readStaff(); }
        catch (IOException e) { System.err.println("AuthService: staff CSV: " + e.getMessage());    staff    = new ArrayList<>(); }
        try { reps     = CsvUtil.readCompanyReps(); }
        catch (IOException e) { System.err.println("AuthService: reps CSV: " + e.getMessage());     reps     = new ArrayList<>(); }
    }

    /* ========================== Diagnostic Login ========================= */

    /**
     * Specific reasons for failed logins.
     */
    public static enum LoginFailureReason {
        /** No matching user found across all directories. */ USER_NOT_FOUND,
        /** Password did not match. */                         WRONG_PASSWORD,
        /** Company representative exists but is not approved. */ NOT_APPROVED,
        /** No failure. */                                      NONE
    }

    /**
     * Result wrapper for a login attempt, carrying success flag,
     * failure reason (when any), and the {@link AuthResult} on success.
     */
    public static class LoginAttempt {
        private final boolean success;
        private final LoginFailureReason reason;
        private final AuthResult result;

        /**
         * @param success whether authentication succeeded
         * @param reason  failure reason (defaults to {@link LoginFailureReason#NONE} when null)
         * @param result  non-null only when {@code success==true}
         */
        public LoginAttempt(boolean success, LoginFailureReason reason, AuthResult result) {
            this.success = success;
            this.reason = reason == null ? LoginFailureReason.NONE : reason;
            this.result = result;
        }
        /** @return {@code true} on success. */
        public boolean success() { return success; }
        /** @return failure reason (or {@link LoginFailureReason#NONE} on success). */
        public LoginFailureReason reason() { return reason; }
        /** @return the authenticated principal, non-null only on success. */
        public AuthResult result() { return result; }
    }

    /**
     * Diagnostic login that explains why a login fails.
     *
     * <p>Resolution order: Student → Staff → CompanyRep.</p>
     * <ul>
     *   <li>On success: {@code success=true} and {@code result != null}.</li>
     *   <li>On failure: {@code success=false}, with {@link LoginFailureReason} set to
     *       {@code USER_NOT_FOUND}, {@code WRONG_PASSWORD}, or {@code NOT_APPROVED} (for reps).</li>
     * </ul>
     *
     * @param idOrEmail student ID / staff ID / email (rep or student)
     * @param password  attempted password
     * @return structured {@link LoginAttempt}
     * @since v1.35
     */
    public LoginAttempt attemptLogin(String idOrEmail, String password) {
        if (idOrEmail == null || password == null) {
            return new LoginAttempt(false, LoginFailureReason.USER_NOT_FOUND, null);
        }
        String key = idOrEmail.trim();

        // Locate user across all roles
        Student s = findStudentByIdOrEmail(key);
        Staff   st = findStaffByIdOrEmail(key);
        CompanyRep r = findRepByEmail(key);

        if (s == null && st == null && r == null) {
            return new LoginAttempt(false, LoginFailureReason.USER_NOT_FOUND, null);
        }

        // Student
        if (s != null) {
            if (!safeEquals(s.getPassword(), password)) {
                return new LoginAttempt(false, LoginFailureReason.WRONG_PASSWORD, null);
            }
            return new LoginAttempt(true, LoginFailureReason.NONE, AuthResult.forStudent(s));
        }

        // Staff
        if (st != null) {
            if (!safeEquals(st.getPassword(), password)) {
                return new LoginAttempt(false, LoginFailureReason.WRONG_PASSWORD, null);
            }
            return new LoginAttempt(true, LoginFailureReason.NONE, AuthResult.forStaff(st));
        }

        // Company Rep
        if (r != null) {
            if (!safeEquals(r.getPassword(), password)) {
                return new LoginAttempt(false, LoginFailureReason.WRONG_PASSWORD, null);
            }
            if (!r.isApproved()) {
                return new LoginAttempt(false, LoginFailureReason.NOT_APPROVED, null);
            }
            return new LoginAttempt(true, LoginFailureReason.NONE, AuthResult.forRep(r));
        }

        // Should not reach here
        return new LoginAttempt(false, LoginFailureReason.USER_NOT_FOUND, null);
    }

    /* =============================== Login =============================== */

    /**
     * Backwards-compatible login: returns {@link AuthResult} on success, {@code null} on any failure.
     *
     * <p>Internally delegates to {@link #attemptLogin(String, String)} so there is one source of truth.</p>
     *
     * @param idOrEmail student ID / staff ID / email
     * @param password  attempted password
     * @return {@link AuthResult} on success; {@code null} otherwise
     * @since v1.35
     */
    public AuthResult login(String idOrEmail, String password) {
        LoginAttempt la = attemptLogin(idOrEmail, password);
        return la.success() ? la.result() : null;
    }

    /* =========================== Password change ========================= */

    /**
     * Change a user's password and persist it to the corresponding CSV.
     *
     * @param idOrEmail login identifier (student ID, staff ID, or email)
     * @param oldPassword current password (must match)
     * @param newPassword new password to set
     * @return {@code true} if the password is changed and written to disk; {@code false} otherwise
     * @since v1.35
     */
    public boolean changePassword(String idOrEmail, String oldPassword, String newPassword) {
        if (idOrEmail == null || oldPassword == null || newPassword == null) return false;
        String key = idOrEmail.trim();

        for (Student s : students) {
            if (equalsId(s.getStudentId(), key) || equalsId(s.getEmail(), key)) {
                if (!safeEquals(s.getPassword(), oldPassword)) return false;
                s.setPassword(newPassword);
                try { CsvUtil.writeStudents(students); } catch (IOException e) { System.err.println(e.getMessage()); }
                return true;
            }
        }
        for (Staff st : staff) {
            if (equalsId(st.getStaffId(), key) || equalsId(st.getEmail(), key)) {
                if (!safeEquals(st.getPassword(), oldPassword)) return false;
                st.setPassword(newPassword);
                try { CsvUtil.writeStaff(staff); } catch (IOException e) { System.err.println(e.getMessage()); }
                return true;
            }
        }
        for (CompanyRep r : reps) {
            if (equalsId(r.getEmail(), key)) {
                if (!safeEquals(r.getPassword(), oldPassword)) return false;
                r.setPassword(newPassword);
                try { CsvUtil.writeCompanyReps(reps); } catch (IOException e) { System.err.println(e.getMessage()); }
                return true;
            }
        }
        return false;
    }

    /* ====================== Company Rep registration ===================== */

    /**
     * Register a new company representative.
     *
     * <p>On duplicate email, throws with the exact message required by the UI:
     * <i>"Invalid, Company Representative's Email already exist in system."</i></p>
     *
     * @param email       login/email (unique key)
     * @param name        display name
     * @param company     company name
     * @param department  department name (optional)
     * @param position    position title (optional)
     * @param password    initial password (defaults to {@code "password"} if blank)
     * @return {@code true} if written to CSV successfully; {@code false} on I/O error
     * @throws IllegalStateException    if a rep with the same email already exists
     * @throws IllegalArgumentException if email is null/blank
     * @since v1.35
     */
    public boolean registerCompanyRep(String email, String name, String company, String department, String position, String password) {
        if (email == null || email.isBlank()) throw new IllegalArgumentException("Email is required.");
        String key = email.trim();

        // Reject duplicates instead of updating/replacing
        if (findRepByEmail(key) != null) {
            throw new IllegalStateException("Invalid, Company Representative's Email already exist in system.");
        }

        CompanyRep rep = new CompanyRep(
                key,
                nz(name),
                nz(company),
                nz(department),
                nz(position),
                false, // pending approval
                (password == null || password.isBlank()) ? "password" : password
        );
        reps.add(rep);

        try { CsvUtil.writeCompanyReps(reps); return true; }
        catch (IOException e) { System.err.println(e.getMessage()); return false; }
    }

    /**
     * Approve or reject a company representative by email and persist the change.
     *
     * @param email    representative's email
     * @param approved {@code true} to approve; {@code false} to revoke/keep pending
     * @return {@code true} on successful write; {@code false} on I/O error or missing rep
     * @since v1.35
     */
    public boolean setCompanyRepApproval(String email, boolean approved) {
        CompanyRep r = findRepByEmail(email);
        if (r == null) return false;
        r.setApproved(approved);
        try { CsvUtil.writeCompanyReps(reps); return true; }
        catch (IOException e) { System.err.println(e.getMessage()); return false; }
    }

    /**
     * Find a company representative by email (case-insensitive).
     *
     * @param email rep email
     * @return matching {@link CompanyRep} or {@code null}
     * @since v1.35
     */
    public CompanyRep findRepByEmail(String email) {
        if (email == null) return null;
        String key = email.trim();
        for (CompanyRep r : reps) if (equalsId(r.getEmail(), key)) return r;
        return null;
    }

    /**
     * List all company representatives whose accounts are not approved.
     *
     * @return new list containing only pending reps
     * @since v1.35
     */
    public List<CompanyRep> listPendingReps() {
        List<CompanyRep> out = new ArrayList<>();
        for (CompanyRep r : reps) if (!r.isApproved()) out.add(r);
        return out;
    }

    /* ================================ Helpers ============================ */

    /**
     * Find a student by StudentID or email.
     *
     * @param idOrEmail student ID or email
     * @return {@link Student} or {@code null}
     * @since v1.35
     */
    private Student findStudentByIdOrEmail(String idOrEmail) {
        if (idOrEmail == null) return null;
        String key = idOrEmail.trim();
        for (Student s : students) {
            if (equalsId(s.getStudentId(), key) || equalsId(s.getEmail(), key)) return s;
        }
        return null;
    }

    /**
     * Find a staff member by StaffID or email.
     *
     * @param idOrEmail staff ID or email
     * @return {@link Staff} or {@code null}
     * @since v1.35
     */
    private Staff findStaffByIdOrEmail(String idOrEmail) {
        if (idOrEmail == null) return null;
        String key = idOrEmail.trim();
        for (Staff st : staff) {
            if (equalsId(st.getStaffId(), key) || equalsId(st.getEmail(), key)) return st;
        }
        return null;
    }

    /** Case-insensitive ID comparison with null-safety. */
    private static boolean equalsId(String a, String b) { return a != null && b != null && a.trim().equalsIgnoreCase(b.trim()); }
    /** Null-safe equality for passwords (exact match). */
    private static boolean safeEquals(String a, String b) { return a != null && b != null && a.equals(b); }
    /** Null → empty helper. */
    private static String nz(String s) { return s == null ? "" : s; }

    /**
     * Find a student by their StudentID (exact model field).
     *
     * @param studentId ID to search
     * @return {@link Student} or {@code null}
     * @since v1.35
     */
    public Student findStudentById(String studentId) {
        if (studentId == null) return null;
        for (Student s : students) {
            if (studentId.equalsIgnoreCase(s.getStudentId())) return s;
        }
        return null;
    }

    /**
     * Find a company representative by email (public alias for menus).
     *
     * @param email representative email
     * @return {@link CompanyRep} or {@code null}
     * @since v1.35
     */
    public CompanyRep findCompanyRepByEmail(String email) {
        if (email == null) return null;
        for (CompanyRep r : reps) {
            if (email.equalsIgnoreCase(r.getEmail())) return r;
        }
        return null;
    }

    /* =============================== Result ============================== */

    /**
     * Authentication result with the resolved role and the bound principal.
     *
     * <p>Exactly one of {@link #getStudent()}, {@link #getStaff()}, or {@link #getRep()}
     * is non-null based on {@link #getRole()}.</p>
     *
     * @since v1.35
     */
    public static class AuthResult {
        /** Role of the authenticated principal. */
        public enum Role { STUDENT, STAFF, COMPANY_REP }

        private final Role role;
        private final Student student;
        private final Staff staff;
        private final CompanyRep rep;

        private AuthResult(Role role, Student s, Staff st, CompanyRep r) {
            this.role = role; this.student = s; this.staff = st; this.rep = r;
        }

        /** @return result for a student principal. */
        public static AuthResult forStudent(Student s)  { return new AuthResult(Role.STUDENT, s, null, null); }
        /** @return result for a staff principal. */
        public static AuthResult forStaff(Staff st)     { return new AuthResult(Role.STAFF, null, st, null); }
        /** @return result for a company-rep principal. */
        public static AuthResult forRep(CompanyRep rep) { return new AuthResult(Role.COMPANY_REP, null, null, rep); }

        /** @return authenticated role. */
        public Role getRole() { return role; }
        /** @return student principal (or {@code null}). */
        public Student getStudent() { return student; }
        /** @return staff principal (or {@code null}). */
        public Staff getStaff() { return staff; }
        /** @return company-rep principal (or {@code null}). */
        public CompanyRep getRep() { return rep; }
    }
}
