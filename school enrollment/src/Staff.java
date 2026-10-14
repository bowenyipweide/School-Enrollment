/**
 * Subclass of {@link User} representing career centre staff.
 * <p>
 * CSV schema (staff.csv): <b>StaffID, Name, Department, Email, Password</b><br>
 * Login ID is {@code StaffID} (maps to {@link User#getUserId()}).
 * </p>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>Stores department as a non-null string (nulls normalized to {@code ""}).</li>
 *   <li>Keeps {@code getStaffId}/{@code setStaffId} as compatibility aliases over {@link User#getUserId()}.</li>
 * </ul>
 *
 * @since v1.3
 * @version v1.35
 */
public class Staff extends User {
    /** Staff member's department (never null; {@code ""} if unknown). */
    private String department;

    /**
     * Full constructor mapping CSV fields to the domain object.
     *
     * @param staffId   unique staff login identifier (becomes {@code userId})
     * @param name      staff name
     * @param department department name (null becomes {@code ""})
     * @param email     contact email
     * @param password  plaintext password; blank/null becomes {@code "password"} via {@link User#setPassword(String)}
     */
    public Staff(String staffId, String name, String department, String email, String password) {
        super(staffId, name, email, password, "STAFF");
        this.department = (department == null) ? "" : department;
    }

    /**
     * Convenience constructor when the Password column is absent.
     * Uses {@code "password"} as the default initial password.
     *
     * @param staffId    unique staff login identifier
     * @param name       staff name
     * @param department department name (null becomes {@code ""})
     * @param email      contact email
     */
    public Staff(String staffId, String name, String department, String email) {
        this(staffId, name, department, email, "password");
    }

    /* ---------- Compatibility aliases (userId <-> staffId) ---------- */

    /**
     * Alias for {@link User#getUserId()} kept for backwards compatibility with older code.
     * @return staff ID (same value as {@code getUserId()})
     */
    public String getStaffId() { return getUserId(); }

    /**
     * Alias for {@link User#setUserId(String)} kept for backwards compatibility.
     * @param id new staff ID
     */
    public void setStaffId(String id) { setUserId(id); }

    /* ------------------------------ Accessors ------------------------------ */

    /**
     * Returns the department. Guaranteed non-null (may be {@code ""}).
     * @return department name
     */
    public String getDepartment() { return department; }

    /**
     * Sets the department, normalizing null to {@code ""}.
     * @param department department name
     */
    public void setDepartment(String department) {
        this.department = (department == null) ? "" : department;
    }

    /**
     * Compact debug representation: {@code staffId | name | department | email}.
     */
    @Override
    public String toString() {
        return String.format("%s | %s | %s | %s",
                getStaffId(), getName(), department, getEmail());
    }
}
