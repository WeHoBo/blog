package com.blog.front.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WordCounterTest {

    @Test
    void count_null_returnsZero() {
        assertEquals(0, WordCounter.count(null));
        assertEquals(0, WordCounter.count(""));
        assertEquals(0, WordCounter.count("   "));
    }

    @Test
    void count_plainText() {
        assertEquals(7, WordCounter.count("你好世界abc"));
    }

    @Test
    void count_excludesCodeBlocks() {
        String md = "正文内容\n```java\nSystem.out.println(\"code\");\n```\n后面还有正文";
        assertEquals(10, WordCounter.count(md));
    }

    @Test
    void count_excludesInlineCode() {
        String md = "这是`inline code`测试";
        assertEquals(4, WordCounter.count(md));
    }

    @Test
    void count_keepsLinkText() {
        String md = "点击[链接文字](https://example.com)继续";
        assertEquals(8, WordCounter.count(md));
    }

    @Test
    void count_excludesImages() {
        String md = "看图![示例图片](https://example.com/a.png)结束";
        assertEquals(4, WordCounter.count(md));
    }

    @Test
    void count_excludesHtmlTags() {
        String md = "一段<div class=\"x\">嵌套</div>文字";
        assertEquals(6, WordCounter.count(md));
    }

    @Test
    void count_stripsMarkdownSymbols() {
        String md = "# 标题\n- 列表项1\n- 列表项2\n**加粗** *斜体*";
        assertEquals(14, WordCounter.count(md));
    }

    @Test
    void count_orderedList() {
        String md = "1. 第一项\n2. 第二项\n3. 第三项";
        assertEquals(9, WordCounter.count(md));
    }

    @Test
    void count_excludesFootnotes() {
        String md = "正文内容[^1]\n\n[^1]: 脚注说明文字";
        assertEquals(4, WordCounter.count(md));
    }
}
