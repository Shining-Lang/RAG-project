package com.lsn.ragkb.security;

/**
 * 存储当前请求的用户信息，通过 ThreadLocal 在请求链路中传递。
 * 由 Sa-Token 拦截器在请求进入时写入，无需每个方法手动传参。
 */
public class UserContext {

    private static final ThreadLocal<Long> USER_ID = ThreadLocal.withInitial(() -> 1L);
    private static final ThreadLocal<String> DEPARTMENT_ID = ThreadLocal.withInitial(() -> "default");
    private static final ThreadLocal<String> ROLE = ThreadLocal.withInitial(() -> "admin");

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static String getDepartmentId() {
        return DEPARTMENT_ID.get();
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(ROLE.get());
    }

    public static void set(Long userId, String departmentId, String role) {
        USER_ID.set(userId);
        DEPARTMENT_ID.set(departmentId);
        ROLE.set(role);
    }

    public static void clear() {
        USER_ID.remove();
        DEPARTMENT_ID.remove();
        ROLE.remove();
    }
}