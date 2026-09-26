package com.zhixue.gateway.config;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 网关路由定义测试。
 *
 * <p>Spring Cloud Gateway 按 order 升序匹配，未显式设置 order 时按定义顺序。
 * 若宽泛路径排在具体路径之前，具体路径将永远不可达。</p>
 *
 * <p>本测试锁定：{@code /course/interaction/**}（interaction 服务）必须比
 * {@code /course/**}（course 服务）优先，否则弹幕互动接口被 course 服务吞掉。</p>
 */
class GatewayRouteOrderTest {

    private static Map<String, Object> properties;

    @BeforeAll
    static void loadApplicationYaml() throws IOException {
        properties = new LinkedHashMap<>();
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));
        for (PropertySource<?> source : sources) {
            if (source instanceof org.springframework.core.env.EnumerablePropertySource<?> eps) {
                for (String name : eps.getPropertyNames()) {
                    properties.put(name, eps.getProperty(name));
                }
            }
        }
    }

    /** 取出所有路由的 id，按配置中的索引顺序排列。 */
    private List<String> routeIdsInOrder() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; ; i++) {
            Object id = properties.get("spring.cloud.gateway.routes[" + i + "].id");
            if (id == null) {
                break;
            }
            ids.add(String.valueOf(id));
        }
        return ids;
    }

    /** 返回某个路由的有效优先级：显式 order 优先，否则用定义索引。 */
    private int effectivePrecedence(String routeId) {
        List<String> ids = routeIdsInOrder();
        int index = ids.indexOf(routeId);
        assertThat(index).as("路由 %s 必须存在", routeId).isGreaterThanOrEqualTo(0);
        Object order = properties.get("spring.cloud.gateway.routes[" + index + "].order");
        return order != null ? Integer.parseInt(String.valueOf(order)) : index;
    }

    private String predicatesOf(String routeId) {
        List<String> ids = routeIdsInOrder();
        int index = ids.indexOf(routeId);
        StringBuilder sb = new StringBuilder();
        for (int p = 0; ; p++) {
            Object predicate = properties.get(
                    "spring.cloud.gateway.routes[" + index + "].predicates[" + p + "]");
            if (predicate == null) {
                break;
            }
            sb.append(predicate).append(';');
        }
        return sb.toString();
    }

    @Test
    void allSevenBusinessServicesShouldHaveRoutes() {
        assertThat(routeIdsInOrder()).contains(
                "zhixue-auth", "zhixue-system", "zhixue-course", "zhixue-media",
                "zhixue-order", "zhixue-marketing", "zhixue-interaction", "zhixue-ai");
    }

    @Test
    void interactionRouteMustTakePrecedenceOverCourseRoute() {
        // /course/interaction/** 比 /course/** 更具体，必须先匹配，
        // 否则 interaction 的互动接口会被 course 服务拦截（D15）
        int interaction = effectivePrecedence("zhixue-interaction");
        int course = effectivePrecedence("zhixue-course");

        assertThat(interaction)
                .as("zhixue-interaction 必须比 zhixue-course 优先匹配，否则 /course/interaction/** 不可达")
                .isLessThan(course);
    }

    @Test
    void interactionRouteShouldStillCoverBothPaths() {
        assertThat(predicatesOf("zhixue-interaction"))
                .contains("/course/interaction/**")
                .contains("/danmaku/**");
    }

    @Test
    void courseRouteShouldKeepStripPrefix() {
        List<String> ids = routeIdsInOrder();
        int index = ids.indexOf("zhixue-course");
        assertThat(String.valueOf(properties.get(
                "spring.cloud.gateway.routes[" + index + "].filters[0]")))
                .isEqualTo("StripPrefix=1");
    }
}
