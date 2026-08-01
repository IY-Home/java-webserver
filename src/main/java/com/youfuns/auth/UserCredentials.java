package com.youfuns.auth;

import com.youfuns.logger.LoggerManager;
import com.youfuns.logger.SimpleLogger;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class UserCredentials {
    private final UUID id;
    private final List<String> usernames;
    private String passwordHash;
    private boolean isLocked;
    private Instant lockoutExpiry;
    private final AtomicInteger failedAttempts = new AtomicInteger(0);
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_DURATION_SECONDS = 300; // 5 minutes

    private static DefaultPermissions<?> defaultPermissions;

    static void setDefaultPermissions(DefaultPermissions<?> defaultPermissions) {
        UserCredentials.defaultPermissions = defaultPermissions;
    }

    public List<String> getUsernames() {
        return List.copyOf(usernames);
    }

    public UserCredentials(UUID id, List<String> usernames, String password) {
        LoggerManager.quickLog(this, "Creating UserCredentials instance...");
        this.id = id;
        LoggerManager.quickLog(this, "Hashing usernames and password...");
        this.usernames = new ArrayList<>(List.copyOf(usernames));
        ResultReturn passwordCheck = PasswordStrengthValidator.validatePasswordWithMessage(password);
        if (!passwordCheck.isSuccess()) throw new IllegalArgumentException(passwordCheck.message());
        this.passwordHash = HashingService.argon2Hash(password);
        LoggerManager.quickLog(this, "Created UserCredentials instance");
    }

    public record LoginResult(ResultReturn resultReturn, String jwtToken) {
        public boolean isSuccess() {
            return resultReturn.isSuccess();
        }
    }

    // ============= VALIDATION METHODS =============
    public LoginResult login(String username, String password) {
        ResultReturn loginResult = validateLogin(username, password);
        String jwt = null;
        if (loginResult.isSuccess()) {
            jwt = JwtService.generateToken(this.id);
        }
        return new LoginResult(loginResult, jwt);
    }

    public boolean validateJwt(String jwt) {
        String subject = JwtService.extractSubject(jwt);
        try {
            UUID uuid = UUID.fromString(jwt);
        } catch (NullPointerException | IllegalArgumentException e) {
            return false;
        }
        return true;
    }
    private ResultReturn validateLogin(String username, String password) {
        LoggerManager.quickLog(this, "Processing login for user " + username);

        if (isLocked) {
            if (Instant.now().isBefore(lockoutExpiry)) {
                LoggerManager.quickLog(this, "Login attempt on locked account", SimpleLogger.Level.WARN);
                return new ResultReturn(ResultReturn.Result.FAILURE, "Account is temporarily locked. Try again later.");
            } else {
                // Lockout expired, reset
                isLocked = false;
                failedAttempts.set(0);
            }
        }

        boolean usernameExists = usernames.contains(username);
        boolean passwordValid = HashingService.verifyArgon2Hash(password, this.passwordHash);

        boolean authenticated = usernameExists && passwordValid;

        if (authenticated) {
            // Reset failed attempts on success
            failedAttempts.set(0);
            LoggerManager.quickLog(this, "Successful login for user");
            return new ResultReturn(ResultReturn.Result.SUCCESS, "Login successful");
        } else {
            int attempts = failedAttempts.get();
            // Increment failed attempts if username is valid
            if (usernameExists) {
                attempts = failedAttempts.incrementAndGet();
                LoggerManager.quickLog(this, "Failed login attempt " + attempts +
                        " for user", SimpleLogger.Level.WARN);
            }
            // Lock account if too many failures
            if (attempts >= MAX_FAILED_ATTEMPTS) {
                lockAccount();
                return new ResultReturn(ResultReturn.Result.FAILURE, "Account is temporarily locked");
            }

            // Return generic message
            return new ResultReturn(ResultReturn.Result.FAILURE, "Login failed");
        }
    }

    private void lockAccount() {
        lockAccount(LOCKOUT_DURATION_SECONDS);
    }

    private void lockAccount(int seconds) {
        isLocked = true;
        lockoutExpiry = Instant.now().plusSeconds(seconds);
        LoggerManager.quickLog(this, "Account locked for user for " + seconds + " seconds", SimpleLogger.Level.WARN);
    }

    // For when you have a specific permission needed
    public void unlockAccount(RoleToken rt) {
        LoggerManager.quickLog(this, "Called unlock account for user");
        defaultPermissions.checkManageUsers(rt);
        isLocked = false;
        lockoutExpiry = null;
        failedAttempts.set(0);
        LoggerManager.quickLog(this, "Account unlocked by admin for user", SimpleLogger.Level.INFO);
    }

    public void lockAccount(RoleToken rt, int seconds) {
        LoggerManager.quickLog(this, "Called lock account for user");
        defaultPermissions.checkManageUsers(rt);
        lockAccount(seconds);
    }

    // ============= PASSWORD MANAGEMENT =============

    public ResultReturn changePassword(RoleToken rt, String oldPassword, String newPassword) {
        LoggerManager.quickLog(this, "Called change password for user");

        defaultPermissions.checkManageSelf(rt, this.id);

        // Verify old password
        if (!HashingService.verifyArgon2Hash(oldPassword, this.passwordHash)) {
            LoggerManager.quickLog(this, "Failed password change attempt - incorrect old password",
                    SimpleLogger.Level.WARN);
            return new ResultReturn(ResultReturn.Result.FAILURE, "Incorrect current password.");
        }

        // Update to new password
        this.passwordHash = HashingService.argon2Hash(newPassword);
        LoggerManager.quickLog(this, "Password changed for user");
        return new ResultReturn(ResultReturn.Result.SUCCESS, "Password successfully changed.");
    }

    public void resetPasswordAdmin(RoleToken rt, String newPassword) {
        LoggerManager.quickLog(this, "Called change password for user by admin");
        defaultPermissions.checkManageUsers(rt);
        this.passwordHash = HashingService.argon2Hash(newPassword);
        LoggerManager.quickLog(this, "Password reset by admin for user");
    }

    // ============= USERNAME MANAGEMENT =============

    public ResultReturn addUsername(RoleToken rt, String username) {
        LoggerManager.quickLog(this, "Called add username for user");
        defaultPermissions.checkManageSelf(rt, this.id);

        // Check if already exists
        if (usernames.contains(username)) {
            LoggerManager.quickLog(this, "Username already exists: " + username);
            return new ResultReturn(ResultReturn.Result.FAILURE, "Username already exists.");
        }
        this.usernames.add(username);
        LoggerManager.quickLog(this, "Added username to UserCredentials instance");
        return new ResultReturn(ResultReturn.Result.SUCCESS, "Username successfully added.");
    }

    public ResultReturn removeUsername(RoleToken rt, String username) {
        LoggerManager.quickLog(this, "Called remove username for user");
        defaultPermissions.checkManageSelf(rt, this.id);

        boolean removed = usernames.remove(username);

        if (removed) {
            LoggerManager.quickLog(this, "Removed username from UserCredentials instance");
            return new ResultReturn(ResultReturn.Result.SUCCESS, "Username successfully removed.");
        } else {
            LoggerManager.quickLog(this, "Username could not be removed. It may not exist.");
            return new ResultReturn(ResultReturn.Result.FAILURE, "Username does not exist.");
        }
    }

    public ResultReturn addUsernameAdmin(RoleToken rt, String username) {
        LoggerManager.quickLog(this, "Called add username for user by admin");

        defaultPermissions.checkManageUsers(rt);


        if (usernames.contains(username)) {
            return new ResultReturn(ResultReturn.Result.FAILURE, "Username already exists.");
        }

        this.usernames.add(username);
        LoggerManager.quickLog(this, "Added username by admin to UserCredentials instance");
        return new ResultReturn(ResultReturn.Result.SUCCESS, "Username successfully added.");
    }

    public ResultReturn removeUsernameAdmin(RoleToken rt, String username) {
        LoggerManager.quickLog(this, "Called remove username for user by admin");
        defaultPermissions.checkManageUsers(rt);

        boolean removed = usernames.remove(username);

        if (removed) {
            LoggerManager.quickLog(this, "Removed username by admin from UserCredentials instance");
            return new ResultReturn(ResultReturn.Result.SUCCESS, "Username successfully removed.");
        } else {
            return new ResultReturn(ResultReturn.Result.FAILURE, "Username does not exist.");
        }
    }

    // ============= HELPER METHODS =============

    public boolean isLocked(RoleToken rt) {
        LoggerManager.quickLog(this, "Called isLocked for user");
        defaultPermissions.checkManageSelf(rt, this.id);
        if (isLocked && Instant.now().isAfter(lockoutExpiry)) {
            // Auto-unlock if lockout expired
            isLocked = false;
            lockoutExpiry = null;
            failedAttempts.set(0);
        }
        return isLocked;
    }

    public int getRemainingAttempts(RoleToken rt) {
        defaultPermissions.checkManageSelf(rt, this.id);
        LoggerManager.quickLog(this, "Called getRemainingAttempts for user");
        return Math.max(0, MAX_FAILED_ATTEMPTS - failedAttempts.get());
    }

    public boolean isLockedAdmin(RoleToken rt) {
        LoggerManager.quickLog(this, "Called isLocked for user by admin");
        defaultPermissions.checkManageUsers(rt);
        if (isLocked && Instant.now().isAfter(lockoutExpiry)) {
            // Auto-unlock if lockout expired
            isLocked = false;
            lockoutExpiry = null;
            failedAttempts.set(0);
        }
        return isLocked;
    }

    public int getRemainingAttemptsAdmin(RoleToken rt) {
        defaultPermissions.checkManageUsers(rt);
        LoggerManager.quickLog(this, "Called getRemainingAttempts for user");
        return Math.max(0, MAX_FAILED_ATTEMPTS - failedAttempts.get());
    }

}