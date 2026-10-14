package com.pj.stock_console.entity;

public enum CrewRole {
    LEADER("리더"),
    ANALYST("애널리스트"),
    SCOUTER("스카우터"),
    RISK_MANAGER("리스크 매니저");

    private final String description;

    CrewRole(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
