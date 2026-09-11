package com.blog.front.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 YAML front matter 读写。
 * <p>
 * 只处理 `---` 包围的一层扁平 key: value，不引入任何 YAML 依赖 —— 这也正是
 * Hexo / Hugo / Jekyll 导出文件里最常见的那一小撮字段，足够让「导出 → 导入」形成闭环。
 * <p>
 * 设计取舍：解析失败时**宁可整段当正文**，也不要丢掉用户的内容。
 */
public final class FrontMatter {

    private FrontMatter() {
    }

    /** 解析结果：元信息 + 去掉 front matter 之后的正文 */
    public static final class Parsed {

        private final Map<String, String> meta;
        private final String body;

        Parsed(Map<String, String> meta, String body) {
            this.meta = meta;
            this.body = body;
        }

        public Map<String, String> meta() {
            return meta;
        }

        public String body() {
            return body;
        }

        public String get(String key) {
            String v = meta.get(key);
            return v == null || v.isBlank() ? null : v;
        }

        public boolean isEmpty() {
            return meta.isEmpty();
        }
    }

    public static Parsed parse(String raw) {
        Map<String, String> meta = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) {
            return new Parsed(meta, "");
        }
        String text = raw.replace("\r\n", "\n").replace("\r", "\n");
        if (text.charAt(0) == '\uFEFF') {
            text = text.substring(1);
        }
        String[] lines = text.split("\n", -1);
        // 第一行必须是单独的 ---，否则整段都是正文
        if (lines.length < 3 || !"---".equals(lines[0].trim())) {
            return new Parsed(meta, text.trim());
        }
        int close = -1;
        for (int i = 1; i < lines.length; i++) {
            if ("---".equals(lines[i].trim())) {
                close = i;
                break;
            }
        }
        // 没有闭合围栏 → 不认为是 front matter，避免把「以 --- 开头的正文」吃掉
        if (close < 0) {
            return new Parsed(meta, text.trim());
        }
        for (int i = 1; i < close; i++) {
            String line = lines[i];
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String key = line.substring(0, colon).trim().toLowerCase();
            String value = unquote(line.substring(colon + 1).trim());
            if (!key.isEmpty() && !value.isEmpty()) {
                meta.put(key, value);
            }
        }
        String body = String.join("\n", Arrays.copyOfRange(lines, close + 1, lines.length));
        return new Parsed(meta, body.strip());
    }

    /** 生成 front matter + 正文。空值字段自动省略。 */
    public static String render(Map<String, String> meta, String body) {
        StringBuilder sb = new StringBuilder("---\n");
        for (Map.Entry<String, String> e : meta.entrySet()) {
            if (e.getValue() != null && !e.getValue().isBlank()) {
                sb.append(e.getKey()).append(": ").append(e.getValue()).append('\n');
            }
        }
        sb.append("---\n\n");
        sb.append(body == null ? "" : body);
        return sb.toString();
    }

    /** `tags: a, b, c` 这类逗号分隔列表（顺带兼容中文逗号与顿号） */
    public static List<String> splitList(String value) {
        List<String> result = new ArrayList<>();
        if (value == null || value.isBlank()) {
            return result;
        }
        for (String part : value.split("[,，、]")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private static String unquote(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1).trim();
            }
        }
        return value;
    }
}
