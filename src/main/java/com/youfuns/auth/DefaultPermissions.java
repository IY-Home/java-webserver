package com.youfuns.auth;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;

public class DefaultPermissions<T extends Enum<T> & Permission> {
    final PermissionChecker<T> checker;
    final T manageSelfPermission;
    final T manageUsersPermission;
    final T assignRolePermission;

    DefaultPermissions(PermissionChecker<T> checker, T manageSelf, T manageAdmin, T assignRole) {
        this.checker = checker;
        this.manageSelfPermission = manageSelf;
        this.manageUsersPermission = manageAdmin;
        this.assignRolePermission = assignRole;
    }

    void checkManageSelf(RoleToken rt, UUID userId) {
        checker.checkPermissionAndThrowWithUser(rt, userId, this.manageSelfPermission);
    }

    void checkManageUsers(RoleToken rt) {
        checker.checkPermissionAndThrow(rt, this.manageUsersPermission);
    }

    void checkManageRoles(RoleToken rt) {
        checker.checkPermissionAndThrow(rt, this.assignRolePermission);
    }

    RoleToken issueToken(UserRoleHolder uh) {
        return checker.issueToken(uh);
    }

    public void assignToClasses(Class<?>... classes) {
        for (Class<?> clazz : classes) {
            try {
                Method method = clazz.getMethod("setDefaultPermissions", DefaultPermissions.class);
                method.invoke(null, this);
            } catch (NoSuchMethodException | IllegalAccessException |
                     InvocationTargetException e) {
                continue;
            }
        }
    }
}