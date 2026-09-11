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
            if (!columnExists(conn, "article", "word_count")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE article ADD COLUMN word_count INT DEFAULT 0");
                }
                log.info("word_count column added");
            }
            if (!columnExists(conn, "user", "huawei_id")) {
                try (Statement st = conn.createStatement()) {
                    st.execute("ALTER TABLE user ADD COLUMN huawei_id VARCHAR(100) DEFAULT NULL");
                }
                log.info("huawei_id column added");
            }
            backfill(conn);
        } catch (Exception e) {
            // 必须快速失败：article.word_count 列缺失会让所有文章查询直接 500，
            // 静默放行只会把「启动成功」的假象拖到线上大面积报错时才暴露。
            // 若确因数据库账号无 ALTER 权限，请先手工执行 blog-server/sql 下的建表/变更脚本。
            throw new IllegalStateException(
                    "数据库结构迁移失败（article.word_count / user.huawei_id），请检查数据库账号权限或手工执行 sql 脚本: "
                            + e.getMessage(), e);
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?")) {
            ps.setString(1, table);
            ps.setString(2, column);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
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
