/**
 * Abstract superclass for all authenticated users in the system.
 * <p>
 * Concrete subclasses:
 * <ul>
 *   <li>{@link Student}</li>
 *   <li>{@link Staff}</li>
 *   <li>{@link CompanyRep}</li>
 * </ul>
 *
 * <h3>Conventions</h3>
 * <ul>
 *   <li><b>userId</b>: unique login identifier (e.g., StudentID, StaffID, or CompanyRep Email).</li>
 *   <li><b>email</b>: contact email; for CompanyRep this equals the login ID.</li>
 *   <li><b>role</b>: one of {@code "STUDENT"}, {@code "STAFF"}, {@code "COMPANY_REP"}.</li>
 *   <li><b>password</b>: defaults to {@code "password"} when null/blank (see {@link #setPassword(String)}).</li>
 * </ul>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>Declared <b>abstract</b> to prevent direct instantiation; only role-specific types should be created.</li>
 *   <li>Constructors are <b>protected</b> to enforce subclass creation.</li>
 *   <li>This class intentionally does not implement equality/hashCode; identity is handled at the CSV/auth layer.</li>
 * </ul>
 *
 * <h3>Security note</h3>
 * Passwords are stored as plain strings in this assignment context. Do not log them
 * or print them to console. In a real system, store salted password hashes instead.
 *
 * @since v1.3
 * @version v1.35
 */
public abstract class User {
    private String userId;
    private String name;
    private String email;
    private String password; // default "password" if null/blank
    private String role;

    /**
     * Base constructor used by subclasses.
     *
     * @param userId   unique login identifier
     * @param name     display name
     * @param email    contact email
     * @param password raw password; null/blank becomes {@code "password"}
     * @param role     canonical role string (e.g., "STUDENT", "STAFF", "COMPANY_REP")
     */
    protected User(String userId, String name, String email, String password, String role) {
        this.userId = userId;
        this.name = name;
        this.email = email;
        setPassword(password);
        this.role = role;
    }

    /** @return the unique login identifier (e.g., StudentID/StaffID/Email). */
    public String getUserId() { return userId; }

    /** @param userId sets the unique login identifier. */
    public void setUserId(String userId) { this.userId = userId; }

    /** @return the user's display name. */
    public String getName() { return name; }

    /** @param name sets the user's display name. */
    public void setName(String name) { this.name = name; }

    /** @return the user's contact email. */
    public String getEmail() { return email; }

    /** @param email sets the user's contact email. */
    public void setEmail(String email) { this.email = email; }

    /**
     * @return the (raw) password string.
     * <p><b>Warning:</b> present only for assignment purposes; avoid exposing in logs/UI.</p>
     */
    public String getPassword() { return password; }

    /**
     * Sets the password. If {@code null} or blank, it is normalized to {@code "password"}.
     *
     * @param password new password or {@code null}/blank to use default
     */
    public void setPassword(String password) {
        this.password = (password == null || password.isBlank()) ? "password" : password;
    }

    /** @return the canonical role string (e.g., "STUDENT", "STAFF", "COMPANY_REP"). */
    public String getRole() { return role; }

    /** @param role sets the canonical role string. */
    public void setRole(String role) { this.role = role; }

    /**
     * Compares a candidate password to the stored one (case-sensitive).
     *
     * @param attempt candidate password; {@code null} always fails
     * @return {@code true} if the attempt matches the stored password; otherwise {@code false}
     */
    public boolean passwordMatches(String attempt) {
        return attempt != null && attempt.equals(this.password);
    }

    /**
     * Returns a concise debug string excluding the password.
     * @return formatted user summary
     */
    @Override
    public String toString() {
        return String.format("%s | %s | %s | role=%s", userId, name, email, role);
    }
}
