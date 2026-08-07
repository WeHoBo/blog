package com.blog.front.util;

/**
 * 统计 Markdown 正文的中英文字数。
 * 剔除代码块、行内代码、图片、链接、HTML 标签及 Markdown 语法符号。
 */
public final class WordCounter {

    private WordCounter() {
    }

    public static int count(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return 0;
        }
        String text = markdown;
        // 代码块（``` 或 ~~~）
        text = text.replaceAll("(?s)```.*?```", " ");
        text = text.replaceAll("(?s)~~~.*?~~~", " ");
        // 行内代码
        text = text.replaceAll("`[^`]*`", " ");
        // 图片 ![alt](url)
        text = text.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", " ");
        // 链接 [text](url) 保留文字
        text = text.replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1");
        // HTML 标签
        text = text.replaceAll("<[^>]+>", " ");
        // 脚注定义行 [^n]: xxx
        text = text.replaceAll("(?m)^\\s*\\[\\^\\d+\\]:.*$", " ");
        // 脚注引用 [^n]
        text = text.replaceAll("\\[\\^\\d+\\]", " ");
        // Markdown 语法符号
        text = text.replaceAll("[#>*_~\\-\\[\\]()!|=\\\\`]", " ");
        text = text.replaceAll("(?m)^\\s*\\d+\\.", " ");
        text = text.replaceAll("\\s+", "");
        return text.length();
    }
}
