package com.youfuns.auth;

import com.youfuns.logger.LoggerManager;
import com.youfuns.logger.SimpleLogger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PermissionChecker<T extends Enum<T> & Permission> {
    @FunctionalInterface
    public interface UserPermissionHandler {
        boolean checkUser(UUID executor, UUID target);
    }
    @FunctionalInterface
    public interface PermissionHandler {
        boolean checkUser(UUID executor);
    }

    private final Map<T, UserPermissionHandler> userPermHandlers;
    private final Map<T, PermissionHandler> permHandlers;

    private final TokenManager tokenManager;

    private static final boolean STRICT_CHECK = false;

    public PermissionChecker() {
        userPermHandlers = new HashMap<>();
        permHandlers = new HashMap<>();
        tokenManager = new TokenManager();
    }

    public boolean checkPermission(RoleToken rt, T requiredPermission) {
        UserRoleHolder uh = tokenManager.getToken(rt);
        if (uh == null) {
            LoggerManager.quickLog(PermissionChecker.class, "The provided token is null", SimpleLogger.Level.ERROR);
            return false;
        }
        if (requiredPermission.needsSpecificUser() && STRICT_CHECK) {
            LoggerManager.quickLog(PermissionChecker.class, "Permission " + requiredPermission + " needs a specific user target for verification", SimpleLogger.Level.ERROR);
            return false;
        }
        return checkPermissionOnly(uh, requiredPermission);
    }
    public boolean checkPermissionWithUser(RoleToken rt, UUID target, T requiredPermission) {
        UserRoleHolder uh = tokenManager.getToken(rt);
        if (uh == null) {
            LoggerManager.quickLog(PermissionChecker.class, "The provided token is null", SimpleLogger.Level.ERROR);
            return false;
        }
        if (!checkPermissionOnly(uh, requiredPermission) || !checkUserHandler(requiredPermission, uh.getId(), target)) {
            LoggerManager.quickLog(
                    PermissionChecker.class,
                    ("Permission denied for permission with specific target user."),
            SimpleLogger.Level.ERROR);

            return false;
        }
        return true;
    }
    public boolean checkPermissionOnly(RoleToken rt, T requiredPermission) {
        UserRoleHolder uh = tokenManager.getToken(rt);
        if (uh == null) {
            LoggerManager.quickLog(PermissionChecker.class, "The provided token is null", SimpleLogger.Level.ERROR);
            return false;
        }
        return checkPermissionOnly(uh, requiredPermission) && checkHandler(requiredPermission, uh.getId());
    }

    PermissionChecker onPermission(T perm, PermissionHandler permHandler) {
        if (perm == null) return this;
        permHandlers.put(perm, permHandler);
        return this;
    }

    PermissionChecker onUserPermission(T perm, UserPermissionHandler userPermHandler) {
        if (perm == null) return this;
        if (!perm.needsSpecificUser()) return this;
        userPermHandlers.put(perm, userPermHandler);
        return this;
    }

    private boolean checkPermissionOnly(UserRoleHolder<T> uh, T requiredPermission) {
        LoggerManager.quickLog(PermissionChecker.class, "Checking permission " + requiredPermission + " for user");
        boolean hasPermission = false;
        for (UserRole<T> userRole : uh.getRoles()) {
            if (userRole.hasPermission(requiredPermission)) {
                hasPermission = true;
                break;
            }
        }
        if (!hasPermission) {
            LoggerManager.quickLog(PermissionChecker.class, "Permission denied!", SimpleLogger.Level.ERROR);
            return false;
        }
        return hasPermission;
    }

    private boolean checkHandler(T requiredPermission, UUID userId) {
        if (!permHandlers.containsKey(requiredPermission)) return true;
        return permHandlers.get(requiredPermission).checkUser(userId);
    }
    private boolean checkUserHandler(T requiredPermission, UUID userId, UUID targetId) {
        if (!userPermHandlers.containsKey(requiredPermission) || !requiredPermission.needsSpecificUser()) return true;
        return userPermHandlers.get(requiredPermission).checkUser(userId, targetId);
    }

    public void checkPermissionAndThrow(RoleToken rt, T requiredPermission) {
        if (!checkPermission(rt, requiredPermission)) {
            throw new AccessDeniedException("Permission denied for permission " + requiredPermission);
        }
    }
    public void checkPermissionAndThrowWithUser(RoleToken rt, UUID targetId, T requiredPermission) {
        if (!checkPermissionWithUser(rt, targetId, requiredPermission)) {
            throw new AccessDeniedException("Permission denied for permission " + requiredPermission + " on target user");
        }
    }

    public RoleToken issueToken(UserRoleHolder uh) {
        return tokenManager.issueToken(uh);
    }

    private static final class TokenManager {
        private static final int TTL_SECONDS = 10;
        private final Map<RoleToken, UserRoleHolder> activeTokens = new ConcurrentHashMap<>();
        private final ScheduledExecutorService cleanup = Executors.newSingleThreadScheduledExecutor();
        public TokenManager() {
            cleanup.scheduleAtFixedRate(this::removeExpiredTokens, 1, 1, TimeUnit.MINUTES);
        }
        private void removeExpiredTokens() {
            LoggerManager.quickLog(this, "Removing expired tokens...");
            Instant now = Instant.now();
            activeTokens.entrySet().removeIf(entry -> entry.getKey().expirationDate().isBefore(now));
        }

        public UserRoleHolder getToken(RoleToken roleToken) {
            LoggerManager.quickLog(this, "Validating token...");
            UserRoleHolder userRoleHolder = activeTokens.get(roleToken);
            activeTokens.remove(roleToken); // Remove single-use token
            return userRoleHolder;
        }

        public RoleToken issueToken(UserRoleHolder uh) {
            LoggerManager.quickLog(this, "Issuing token to user with roles " + uh.getRoles().toString());
            RoleToken token = new RoleToken(UUID.randomUUID(), Instant.now(), Instant.now().plusSeconds(TTL_SECONDS), uh.getId());
            activeTokens.put(token, uh);
            LoggerManager.quickLog(this, "Token issued.");
            return token;
        }
    }
}

