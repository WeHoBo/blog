package com.blog.front.config;

import com.blog.front.util.WordCounter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * 启动时迁移：为 article 表补充 word_count 列并回填已有文章字数。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WordCountMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            if (!columnExists(conn)) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE article ADD COLUMN word_count INT DEFAULT 0");
                }
                log.info("word_count column added");
            }
            backfill(conn);
        } catch (Exception e) {
            log.warn("word_count migration failed: {}", e.getMessage());
        }
    }

    private boolean columnExists(Connection conn) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'article' AND column_name = 'word_count'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1) > 0;
        }
    }

    private void backfill(Connection conn) throws Exception {
        int updated = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, content_md FROM article WHERE word_count IS NULL OR word_count = 0")) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String md = rs.getString("content_md");
                int count = WordCounter.count(md);
                try (PreparedStatement up = conn.prepareStatement("UPDATE article SET word_count = ? WHERE id = ?")) {
                    up.setInt(1, count);
                    up.setLong(2, id);
                    up.executeUpdate();
                }
                updated++;
            }
        }
        if (updated > 0) {
            log.info("backfilled word_count for {} articles", updated);
        }
    }
}
