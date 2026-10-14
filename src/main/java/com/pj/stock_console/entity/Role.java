package com.pj.stock_console.entity;

public enum Role {
    ADMIN("관리자"),
    TIER1("1등급"),
    TIER2("2등급"),
    TIER3("3등급"); // 기본 가입 유저

    private final String description;

    Role(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
