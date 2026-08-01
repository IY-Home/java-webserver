package com.youfuns.auth;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public class UserRole<P extends Enum<P> & Permission> {
    private final String name;
    private final Set<P> permissions;

    public UserRole(String name, Set<P> permissions) {
        this.name = name;
        this.permissions = Collections.unmodifiableSet(permissions);
    }

    public String getName() { return name; }
    public Set<P> getPermissions() { return permissions; }
    public boolean hasPermission(P permission) {
        return permissions.contains(permission);
    }
}
