package com.youfuns.auth;

import com.youfuns.logger.LoggerManager;

public record ResultReturn(Result result, String message) {
    public enum Result {
        SUCCESS(true), FAILURE(false), WARNING(true);
        public final boolean isSuccess;
        Result(boolean isSuccess) {
            this.isSuccess = isSuccess;
        }
    }
    public ResultReturn {
        LoggerManager.quickLog(this, "Creating ResultReturn with result: " + result + ", message: " + message);
    }
    public boolean isSuccess() {
        return result.isSuccess;
    }
}
