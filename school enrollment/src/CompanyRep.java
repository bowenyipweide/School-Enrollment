/**
 * Company representative user.
 * <p>
 * Extends {@link User} with company-specific attributes and an {@code approved} flag
 * (must be {@code true} before the representative can operate in the system; enforcement
 * is handled by higher-level services such as {@code AuthService} / menus).
 *
 * <h3>CSV mapping</h3>
 * Columns (in order): <br>
 * <code>Email, Name, Company, Department, Position, Approved, Password</code><br>
 * <ul>
 *   <li>Login ID is the {@code Email} (also used as contact email).</li>
 *   <li>Null strings are normalized to {@code ""}.</li>
 *   <li>If the Password column is absent, a convenience constructor defaults it to {@code "password"}.</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code getUserId()} == {@code getEmail()} (email is the login ID).</li>
 *   <li>{@code company}, {@code department}, {@code position} are never {@code null} (empty string if unspecified).</li>
 *   <li>{@code approved} indicates whether the account has been cleared by staff.</li>
 * </ul>
 *
 * @since v1.3
 */
public class CompanyRep extends User {
    /** Employer name. */
    private String company;
    /** Organizational department. */
    private String department;
    /** Job title / position. */
    private String position;
    /** Must be true before menus allow full access; checked by higher layers. */
    private boolean approved;

    /**
     * Full constructor used when all CSV columns are present.
     *
     * @param email      login ID and contact email (non-null)
     * @param name       representative's display name
     * @param company    company name (normalized to {@code ""} if null)
     * @param department department name (normalized to {@code ""} if null)
     * @param position   job title (normalized to {@code ""} if null)
     * @param approved   whether staff has approved this account
     * @param password   raw password; if null/blank, {@code User} will normalize to a default
     */
    public CompanyRep(String email, String name, String company, String department,
                      String position, boolean approved, String password) {
        // userId = email, email = email; role = COMPANY_REP
        super(email, name, email, password, "COMPANY_REP");
        this.company = company == null ? "" : company;
        this.department = department == null ? "" : department;
        this.position = position == null ? "" : position;
        this.approved = approved;
    }

    /**
     * Convenience constructor for when the Password column is absent.
     * Defaults password to {@code "password"}.
     *
     * @param email      login ID and contact email
     * @param name       representative's name
     * @param company    company name
     * @param department department
     * @param position   job title
     * @param approved   approval flag
     */
    public CompanyRep(String email, String name, String company, String department, String position, boolean approved) {
        this(email, name, company, department, position, approved, "password");
    }

    // -------------------- Getters & setters --------------------

    /** @return company name (never {@code null}) */
    public String getCompany() { return company; }

    /**
     * Sets company name.
     * @param company new value; {@code null} becomes empty string
     */
    public void setCompany(String company) { this.company = company == null ? "" : company; }

    /** @return department (never {@code null}) */
    public String getDepartment() { return department; }

    /**
     * Sets department.
     * @param department new value; {@code null} becomes empty string
     */
    public void setDepartment(String department) { this.department = department == null ? "" : department; }

    /** @return position / job title (never {@code null}) */
    public String getPosition() { return position; }

    /**
     * Sets position / job title.
     * @param position new value; {@code null} becomes empty string
     */
    public void setPosition(String position) { this.position = position == null ? "" : position; }

    /** @return {@code true} if staff has approved this representative */
    public boolean isApproved() { return approved; }

    /**
     * Sets approval flag (authorization is enforced by higher layers).
     * @param approved new approval state
     */
    public void setApproved(boolean approved) { this.approved = approved; }

    /**
     * Convenience alias for clarity in menus.
     * Equivalent to {@link #getUserId()} and {@link #getEmail()} for this subclass.
     *
     * @return the login email (same value as {@code getUserId()})
     */
    public String getLoginEmail() { return getUserId(); } // same as getEmail()

    @Override
    public String toString() {
        return String.format("%s | %s @ %s (%s) | %s | approved=%s",
                getEmail(), getName(), company, department, position, approved);
    }
}
