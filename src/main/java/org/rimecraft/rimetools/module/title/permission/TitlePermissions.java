package org.rimecraft.rimetools.module.title.permission;

public final class TitlePermissions {
    public static final String ADMIN = "rime-tools.title.admin";
    public static final String ADMIN_TITLES = "rime-tools.title.admin.titles";
    public static final String ADMIN_ASSIGN = "rime-tools.title.admin.assign";

    private TitlePermissions() {
    }

    public static String title(String titleId) {
        return "rime-tools.title.title." + titleId;
    }
}