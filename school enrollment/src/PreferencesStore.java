import java.util.Objects;

/**
 * Abstraction for storing and retrieving per-user, per-view list preferences
 * (filter column, keyword, sort column).
 *
 * <p><b>Purpose</b></p>
 * <ul>
 *   <li>Decouple UI/menus from the persistence details of user preferences.</li>
 *   <li>Allow alternative implementations (CSV, in-memory, DB) without changing callers.</li>
 * </ul>
 *
 * <p><b>SOLID notes</b></p>
 * <ul>
 *   <li><b>ISP:</b> Narrow, client-specific interface exposing only the operations menus need.</li>
 *   <li><b>DIP:</b> Menus depend on this abstraction; {@code PreferenceService} is one concrete impl.</li>
 * </ul>
 *
 * @since v1.35
 * @see PreferenceService
 * @see ListPreferences
 */
public interface PreferencesStore {

    /**
     * Retrieve the preferences for a given user and logical view.
     * <p>
     * Implementations should never return {@code null}. If no preferences exist yet,
     * return a new {@link ListPreferences} with default values.
     *
     * @param user the user whose preferences are requested (non-null)
     * @param view a logical view identifier (e.g., {@code "student_internships"}) (non-null)
     * @return a non-null preferences object (may be a new default instance)
     */
    ListPreferences get(User user, String view);

    /**
     * Persist/replace the preferences for a given user and view.
     * Callers typically mutate a {@link ListPreferences} returned from {@link #get(User, String)}
     * and then call {@code put(...)} to save.
     *
     * @param user  the user whose preferences are being stored (non-null)
     * @param view  the logical view identifier (non-null)
     * @param prefs the preferences to persist (non-null)
     */
    void put(User user, String view, ListPreferences prefs);

    /**
     * Helper to build a stable key from user and view. Implementations may use or ignore this.
     *
     * @param u    the user (non-null)
     * @param view the logical view identifier (non-null)
     * @return a stable composite key string
     */
    static String key(User u, String view) {
        return Objects.requireNonNull(u).getUserId() + "::" + Objects.requireNonNull(view);
    }
}
