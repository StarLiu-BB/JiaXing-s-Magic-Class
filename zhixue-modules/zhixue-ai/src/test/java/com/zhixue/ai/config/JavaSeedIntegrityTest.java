package com.zhixue.ai.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Java 种子课与知识资料必须自洽，增量脚本只能「补缺」、且与种子逐字一致。
 *
 * <p>推荐完全依赖这批数据：课程、章节、课时或分片任何一处归属错位，表现都是
 * 「回答正常、推荐永远为空」，从日志很难看出来。</p>
 */
class JavaSeedIntegrityTest {

    /** surefire 的工作目录是模块目录 zhixue-modules/zhixue-ai */
    private static final Path COURSE_SEED = Path.of("../zhixue-course/src/main/resources/sql/course_seed.sql");
    private static final Path COURSE_UPGRADE =
            Path.of("../zhixue-course/src/main/resources/sql/upgrade/2026-10-20-java-courses.sql");
    private static final Path AI_SEED = Path.of("src/main/resources/sql/ai_seed.sql");
    private static final Path AI_UPGRADE = Path.of("src/main/resources/sql/upgrade/2026-10-20-java-knowledge.sql");

    @Test
    void javaCoursesArePublishedOnShelfWithTwoPreviewLessons() throws IOException {
        String seed = read(COURSE_SEED);
        for (long id = 2005; id <= 2007; id++) {
            List<String> f = fields(tuple(seed, id));
            assertThat(f.get(4)).as("课程 %s 的讲师", id).isEqualTo("1002");
            assertThat(f.get(5)).as("课程 %s 的分类", id).isEqualTo("110");
            assertThat(f.get(11)).as("课程 %s 允许试看", id).isEqualTo("1");
            assertThat(f.get(12)).as("课程 %s 试看课时数", id).isEqualTo("2");
            assertThat(f.get(16)).as("课程 %s 已发布", id).isEqualTo("1");
            assertThat(f.get(17)).as("课程 %s 已上架", id).isEqualTo("1");
        }
    }

    @Test
    void everyLessonBelongsToAChapterOfTheSameCourse() throws IOException {
        String seed = read(COURSE_SEED);
        Map<Long, Long> chapterCourse = new HashMap<>();
        for (long id = 3011; id <= 3018; id++) {
            chapterCourse.put(id, Long.parseLong(fields(tuple(seed, id)).get(1)));
        }
        assertThat(chapterCourse.values()).allMatch(c -> c >= 2005 && c <= 2007);

        Map<Long, Integer> lessonsPerCourse = new HashMap<>();
        for (long id = 4011; id <= 4025; id++) {
            List<String> f = fields(tuple(seed, id));
            long course = Long.parseLong(f.get(1));
            long chapter = Long.parseLong(f.get(2));
            assertThat(chapterCourse.get(chapter)).as("课时 %s 的章节属于同一门课", id).isEqualTo(course);
            assertThat(f.get(4)).as("课时 %s 的视频地址可播放", id).startsWith("https://");
            lessonsPerCourse.merge(course, 1, Integer::sum);
        }
        assertThat(lessonsPerCourse).containsEntry(2005L, 5).containsEntry(2006L, 5).containsEntry(2007L, 5);
    }

    @Test
    void knowledgeChunksBelongToJavaCoursesAndAreNotSplit() throws IOException {
        String seed = read(AI_SEED);
        Map<Long, String> docSource = new HashMap<>();
        for (long id = 91011; id <= 91018; id++) {
            List<String> f = fields(tuple(seed, id));
            assertThat(f.get(2)).as("文档 %s 归属 Java 课程", id).matches("course:200[5-7]");
            assertThat(f.get(4)).as("文档 %s 已启用", id).isEqualTo("1");
            docSource.put(id, f.get(2));
        }
        for (long id = 92011; id <= 92018; id++) {
            List<String> f = fields(tuple(seed, id));
            assertThat(docSource).as("分片 %s 的文档存在", id).containsKey(Long.parseLong(f.get(1)));
            // 不超过 TextSplitter 的 500 字阈值：与上传时的切分结果一致，「一篇资料 = 一个分片」
            assertThat(f.get(3).length()).as("分片 %s 的字数", id).isBetween(150, 500);
            assertThat(f.get(4)).as("分片 %s 的 es_id 留空，写入索引后由 AI 服务回填", id).isEqualTo("NULL");
        }
    }

    /**
     * 每篇资料恰好一个分片（chunk_index=0）：推荐按文档归属课程，多出或缺少的分片都会让检索结果偏移。
     * 统计 java-knowledge 段内所有指向 91011–91018 的分片，而不只是预期的 92011–92018。
     */
    @Test
    void everyKnowledgeDocumentHasExactlyOneChunk() throws IOException {
        String block = block(read(AI_SEED), "java-knowledge");
        Map<Long, Integer> chunksPerDoc = new HashMap<>();
        // 分片行形如 (92011, 91011, 0, '…'；文档行的第二列是带引号的标题，不会匹配
        Matcher m = Pattern.compile("\\(\\s*(\\d+)\\s*,\\s*(910\\d\\d)\\s*,\\s*(\\d+)\\s*,").matcher(block);
        while (m.find()) {
            assertThat(m.group(3)).as("分片 %s 的 chunk_index", m.group(1)).isEqualTo("0");
            chunksPerDoc.merge(Long.parseLong(m.group(2)), 1, Integer::sum);
        }
        for (long doc = 91011; doc <= 91018; doc++) {
            assertThat(chunksPerDoc.get(doc)).as("文档 %s 的分片数", doc).isEqualTo(1);
        }
        assertThat(chunksPerDoc).as("没有指向其他文档的分片").hasSize(8);
    }

    /**
     * 新 ID 在种子里只能出现一次：全新环境下重复会在 db-init 时主键冲突，
     * 但增量脚本用 INSERT IGNORE，重复行会被静默吞掉，只有这里能发现。
     */
    @Test
    void newSeedIdsAreUnique() throws IOException {
        String courseSeed = read(COURSE_SEED);
        String aiSeed = read(AI_SEED);
        for (long[] range : new long[][]{{2005, 2007}, {3011, 3018}, {4011, 4025}}) {
            for (long id = range[0]; id <= range[1]; id++) {
                assertThat(occurrences(courseSeed, id)).as("课程种子中 id=%s 出现次数", id).isEqualTo(1);
            }
        }
        for (long[] range : new long[][]{{91011, 91018}, {92011, 92018}}) {
            for (long id = range[0]; id <= range[1]; id++) {
                assertThat(occurrences(aiSeed, id)).as("AI 种子中 id=%s 出现次数", id).isEqualTo(1);
            }
        }
    }

    @Test
    void upgradeScriptsRepeatSeedBlocksAndNeverClearData() throws IOException {
        assertSameBlock(read(COURSE_SEED), read(COURSE_UPGRADE), "java-courses");
        assertSameBlock(read(AI_SEED), read(AI_UPGRADE), "java-knowledge");
        assertOnlyInsertIgnore(COURSE_UPGRADE, "zhixue_course");
        assertOnlyInsertIgnore(AI_UPGRADE, "zhixue_ai");
    }

    /**
     * 增量脚本只能「补缺」：除了 USE 指定库，每条语句都必须是 INSERT IGNORE。
     * REPLACE INTO 会先删后插、ON DUPLICATE KEY UPDATE 与 UPDATE 会改写已有数据；
     * 引号外的分号意味着一行里塞了第二条语句。
     */
    private static void assertOnlyInsertIgnore(Path script, String database) throws IOException {
        String body = read(script).replaceAll("(?m)^--.*$", "");
        List<String> statements = new ArrayList<>();
        for (String statement : body.split(";\\s*\\n")) {
            if (!statement.isBlank()) {
                statements.add(statement.strip());
            }
        }
        assertThat(statements.get(0)).as("%s 的第一条语句", script).isEqualTo("USE " + database);
        for (String sql : statements.subList(1, statements.size())) {
            String code = withoutQuotedText(sql);
            assertThat(code).as("%s 中的语句只能是 INSERT IGNORE", script).startsWith("INSERT IGNORE INTO ");
            assertThat(code).as("%s 不得在一行里写多条语句", script).doesNotContain(";");
            assertThat(code).as("%s 不得改写已有数据", script)
                    .doesNotContainPattern("(?i)\\bON\\s+DUPLICATE\\s+KEY\\b");
        }
    }

    /** 去掉单引号字符串的内容（处理 '' 转义），只留下 SQL 本身 */
    private static String withoutQuotedText(String sql) {
        return sql.replaceAll("'(?:[^']|'')*'", "''");
    }

    private static String block(String seed, String name) {
        Matcher m = Pattern.compile("-- BEGIN " + name + "[^\\n]*\\n(.*?)-- END " + name, Pattern.DOTALL).matcher(seed);
        assertThat(m.find()).as("种子中有 %s 段", name).isTrue();
        return m.group(1);
    }

    private static int occurrences(String sql, long id) {
        Matcher m = Pattern.compile("\\(\\s*" + id + "\\s*,").matcher(sql);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private static void assertSameBlock(String seed, String upgrade, String name) {
        String expected = block(seed, name).replaceAll("(?im)^insert into", "INSERT IGNORE INTO").strip();
        int use = upgrade.indexOf("USE ");
        assertThat(use).as("增量脚本含 USE 语句").isGreaterThanOrEqualTo(0);
        String actual = upgrade.substring(upgrade.indexOf(';', use) + 1).strip();
        assertThat(actual).as("增量脚本与种子 %s 段一致", name).isEqualTo(expected);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /** 取出以 "(id," 开头的整条元组（不含两端括号），正确跳过引号内的逗号与 now() 之类的括号 */
    static String tuple(String sql, long id) {
        Matcher m = Pattern.compile("\\(\\s*" + id + "\\s*,").matcher(sql);
        assertThat(m.find()).as("找到 id=%s 的种子行", id).isTrue();
        int depth = 0;
        boolean quoted = false;
        for (int i = m.start(); i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'') {
                if (quoted && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    i++;
                    continue;
                }
                quoted = !quoted;
            } else if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')' && --depth == 0) {
                return sql.substring(m.start() + 1, i);
            }
        }
        throw new AssertionError("id=" + id + " 的种子行没有闭合");
    }

    /** 按顶层逗号切分字段，去掉字符串两端的引号 */
    static List<String> fields(String tuple) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        boolean quoted = false;
        for (int i = 0; i < tuple.length(); i++) {
            char c = tuple.charAt(i);
            if (c == '\'') {
                if (quoted && i + 1 < tuple.length() && tuple.charAt(i + 1) == '\'') {
                    current.append('\'');
                    i++;
                } else {
                    quoted = !quoted;
                }
                continue;
            }
            if (!quoted && c == '(') {
                depth++;
            } else if (!quoted && c == ')') {
                depth--;
            }
            if (!quoted && depth == 0 && c == ',') {
                out.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        out.add(current.toString().trim());
        return out;
    }
}
