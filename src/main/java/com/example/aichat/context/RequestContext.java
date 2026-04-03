package com.example.aichat.context;

import java.util.HashMap;
import java.util.Map;

public class RequestContext {

    private static final ThreadLocal<Map<String, Object>> CONTEXT =
        ThreadLocal.withInitial(HashMap::new);

    public static void set(String key, Object value) {
        CONTEXT.get().put(key, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T get(String key) {
        return (T) CONTEXT.get().get(key);
    }

    public static void clear() {
        CONTEXT.remove();
    }
}
