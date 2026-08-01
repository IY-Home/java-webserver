package com.youfuns.auth;

import com.youfuns.logger.LoggerManager;

import java.util.*;

public class UserRoleHolder<P extends Enum<P> & Permission> {
    private final UUID id;
    private final Set<UserRole<P>> roles;

    private static DefaultPermissions<?> defaultPermissions;

    static void setDefaultPermissions(DefaultPermissions<?> defaultPermissions) {
        UserRoleHolder.defaultPermissions = defaultPermissions;
    }

    public UserRoleHolder(UUID id, Set<UserRole<P>> roles) {
        LoggerManager.quickLog(this, "Creating UserRoleHolder instance...");
        this.id = id;
        this.roles = new HashSet<>(Set.copyOf(roles));
        LoggerManager.quickLog(this, "Created UserRoleHolder instance with roles " + this.roles.toString());
    }

    public Set<UserRole<P>> getRoles() {
        LoggerManager.quickLog(this, "Getting UserRoles from UserRoleHolder instance...");
        return Set.copyOf(roles);
    }

    public void addRole(RoleToken commandingUser, UserRole<P> role) {
        // Check if the commanding user has the permission to add user roles
        defaultPermissions.checkManageRoles(commandingUser);
        // If no exception, add the role to self roles
        this.roles.add(role);
        LoggerManager.quickLog(this, "Role " + role + " added to UserRoleHolder instance by commanding user");
    }

    public void removeRole(RoleToken commandingUser, UserRole<P> role) {
        // Check if the commanding user has the permission to remove user roles
        defaultPermissions.checkManageRoles(commandingUser);
        // If no exception, remove the role from self roles
        this.roles.remove(role);
        LoggerManager.quickLog(this, "Role " + role + " removed from UserRoleHolder instance by commanding user");
    }

    public UUID getId() {
        return id;
    }

    public RoleToken getToken() {
        return defaultPermissions.issueToken(this);
    }
}
