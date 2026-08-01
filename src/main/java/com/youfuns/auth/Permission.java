package com.youfuns.auth;

public interface Permission {
    String name();
    boolean needsSpecificUser();
}