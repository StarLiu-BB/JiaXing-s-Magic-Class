package com.zhixue.system;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 种子数据完整性测试。
 *
 * <p>种子 SQL 用单条多值 INSERT 写入。任意两行主键重复都会让整条语句失败，
 * 导致该表完全没有数据 —— 表现为"所有菜单/权限都消失"，极难排查。</p>
 *
 * <p>本测试源于一次真实失误：新增字典编辑授权时误用了已被占用的
 * sys_role_menu.id=20014，与"课程管理"授权冲突。</p>
 */
class SeedDataIntegrityTest {

    private static final String SEED = "src/main/resources/sql/system_seed.sql";

    private String seed() throws Exception {
        return Files.readString(Paths.get(SEED), StandardCharsets.UTF_8);
    }

    /** 抽取某张表 INSERT ... VALUES 段内每一行的首列（主键）。 */
    private List<Long> primaryKeysOf(String sql, String table) {
        int start = sql.indexOf("insert into " + table + " ");
        assertThat(start).as("种子中应包含 %s 的 insert", table).isGreaterThanOrEqualTo(0);
        int end = sql.indexOf(";", start);
        String block = sql.substring(start, end);

        List<Long> ids = new ArrayList<>();
        Matcher m = Pattern.compile("\\n\\s*\\((\\d+),").matcher(block);
        while (m.find()) {
            ids.add(Long.parseLong(m.group(1)));
        }
        return ids;
    }

    private void assertNoDuplicate(String table) throws Exception {
        List<Long> ids = primaryKeysOf(seed(), table);
        Set<Long> seen = new HashSet<>();
        List<Long> duplicates = new ArrayList<>();
        for (Long id : ids) {
            if (!seen.add(id)) {
                duplicates.add(id);
            }
        }
        assertThat(duplicates)
                .as("%s 种子存在重复主键，整条 INSERT 会失败导致该表为空", table)
                .isEmpty();
    }

    @Test
    void sysMenuSeedMustHaveUniquePrimaryKeys() throws Exception {
        assertNoDuplicate("sys_menu");
    }

    @Test
    void sysRoleMenuSeedMustHaveUniquePrimaryKeys() throws Exception {
        assertNoDuplicate("sys_role_menu");
    }

    @Test
    void everyPermissionUsedByDictControllerMustBeSeeded() throws Exception {
        String controller = Files.readString(
                Paths.get("src/main/java/com/zhixue/system/controller/SysDictController.java"),
                StandardCharsets.UTF_8);
        String sql = seed();

        Matcher m = Pattern.compile("@RequirePermission\\(\"([^\"]+)\"\\)").matcher(controller);
        while (m.find()) {
            String perm = m.group(1);
            // 接口要求的权限若未在菜单中定义，任何角色都无法获得它，接口永远 403
            assertThat(sql)
                    .as("接口所需权限 %s 未在 sys_menu 种子中定义，所有角色调用都会 403", perm)
                    .contains("'" + perm + "'");
        }
    }

    /**
     * 推广到所有业务模块：权限码只在 system 种子中定义，其它服务的接口若要求了
     * 种子里不存在的权限，任何角色都拿不到它，接口永远 403 且极难排查。
     */
    @Test
    void everyPermissionRequiredByAnyModuleControllerMustBeSeeded() throws Exception {
        String sql = seed();
        Pattern annotation = Pattern.compile("@RequirePermission\\(\\s*\\{?([^)}]*)\\}?");
        Pattern literal = Pattern.compile("\"([^\"]+)\"");

        List<String> missing = new ArrayList<>();
        List<Path> controllers;
        try (Stream<Path> files = Files.walk(Paths.get(".."))) {
            controllers = files
                    .filter(p -> p.toString().replace('\\', '/').matches(".*/src/main/java/.*/controller/[^/]+\\.java"))
                    .toList();
        }
        assertThat(controllers).as("应能扫描到各模块的控制器").isNotEmpty();

        // 权限码也可能以常量声明（如 PERM_REFUND = "order:refund"）后在注解或代码中引用
        Pattern constant = Pattern.compile("PERM_[A-Z_]+\\s*=\\s*\"([^\"]+)\"");

        for (Path controller : controllers) {
            String source = Files.readString(controller, StandardCharsets.UTF_8);
            List<String> perms = new ArrayList<>();
            Matcher m = annotation.matcher(source);
            while (m.find()) {
                Matcher perm = literal.matcher(m.group(1));
                while (perm.find()) {
                    perms.add(perm.group(1));
                }
            }
            Matcher c = constant.matcher(source);
            while (c.find()) {
                perms.add(c.group(1));
            }
            for (String perm : perms) {
                if (!sql.contains("'" + perm + "'")) {
                    missing.add(controller.getFileName() + " -> " + perm);
                }
            }
        }
        assertThat(missing)
                .as("以下接口所需权限未在 sys_menu 种子中定义，所有角色调用都会 403")
                .isEmpty();
    }
}
