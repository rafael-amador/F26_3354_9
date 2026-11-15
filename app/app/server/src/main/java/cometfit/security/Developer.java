package cometfit.security;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.DiscriminatorValue;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;

/**
 * Developer — Deliverable 1 §5.2, extends User.
 * Attributes: adminRole, permissions : List&lt;String&gt;. Operation: hasPermission(perm).
 * Actor "Developer" of UC-4; "administers" AppModule.
 */
@Entity
@DiscriminatorValue("DEVELOPER")
public class Developer extends User {

    @Column(name = "admin_role")
    private String adminRole;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "developer_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "permission")
    private List<String> permissions = new ArrayList<>();

    public boolean hasPermission(String perm) {
        return permissions.contains(perm);
    }

    @Override
    public String role() { return "DEVELOPER"; }

    public String getAdminRole() { return adminRole; }
    public void setAdminRole(String adminRole) { this.adminRole = adminRole; }
    public List<String> getPermissions() { return permissions; }
    public void setPermissions(List<String> permissions) { this.permissions = new ArrayList<>(permissions); }
}
