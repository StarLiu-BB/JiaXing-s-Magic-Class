package com.zhixue.order.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.zhixue.api.course.RemoteCourseService;
import com.zhixue.order.mapper.OrderMapper;
import com.zhixue.order.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfSystemProperty(named = "zhixue.test.isolated-mysql", matches = "true")
class SeckillMysqlTest {
    @Test void migrationAndRepeatableReadConcurrencyUseRealMysql() throws Exception {
        Path directory = Files.createTempDirectory(Path.of("target"), "m-").toAbsolutePath();
        Path data = directory.resolve("data");
        Path socket = directory.resolve("s.sock");
        int port;
        try (ServerSocket reserved = new ServerSocket(0)) { port = reserved.getLocalPort(); }
        Process server = null;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Process init = new ProcessBuilder("mysqld", "--no-defaults", "--initialize-insecure", "--datadir=" + data,
                    "--log-error=" + directory.resolve("init.log")).redirectErrorStream(true)
                    .redirectOutput(directory.resolve("init-output.log").toFile()).start();
            assertThat(init.waitFor(60, TimeUnit.SECONDS)).isTrue();
            assertThat(init.exitValue()).isZero();
            server = new ProcessBuilder("mysqld", "--no-defaults", "--datadir=" + data, "--socket=" + socket,
                    "--port=" + port, "--bind-address=127.0.0.1", "--mysqlx=OFF",
                    "--log-error=" + directory.resolve("mysql.log"), "--pid-file=" + directory.resolve("mysql.pid"))
                    .redirectErrorStream(true).redirectOutput(directory.resolve("server-output.log").toFile()).start();
            String url = "jdbc:mysql://127.0.0.1:" + port + "/?allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=500&socketTimeout=5000";
            boolean ready = false;
            for (int i = 0; i < 100; i++) {
                try (var connection = DriverManager.getConnection(url, "root", "")) { ready = true; break; }
                catch (Exception unavailable) { Thread.sleep(100); }
            }
            assertThat(ready).withFailMessage("MySQL startup failed: %s", Files.exists(directory.resolve("mysql.log"))
                    ? Files.readString(directory.resolve("mysql.log")) : Files.readString(directory.resolve("server-output.log"))).isTrue();
            runSql(socket, directory, "CREATE DATABASE zhixue_marketing; CREATE TABLE zhixue_marketing.seckill_order "
                    + "(activity_id BIGINT,user_id BIGINT,order_no VARCHAR(64));");
            runFile(socket, directory, Path.of("src/main/resources/sql/order_schema.sql"));
            runSql(socket, directory, "ALTER TABLE zhixue_order.`order` DROP INDEX uk_order_activity_user, DROP COLUMN activity_id;"
                    + "INSERT INTO zhixue_order.`order` (id,order_no,user_id,product_id,product_type,amount,remark) VALUES"
                    + "(1,'HIST',1003,2007,0,9.90,'秒杀活动 63001'),(2,'ORDINARY',1003,2005,0,99,NULL);"
                    + "INSERT INTO zhixue_marketing.seckill_order VALUES(63001,1003,'HIST');");
            Path migration = Path.of("src/main/resources/sql/upgrade/2026-10-23-seckill-idempotency.sql");
            runFile(socket, directory, migration);
            runFile(socket, directory, migration);
            try (var connection = DriverManager.getConnection(url, "root", ""); var statement = connection.createStatement()) {
                try (var rows = statement.executeQuery("SELECT activity_id FROM zhixue_order.`order` WHERE order_no='HIST'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getLong(1)).isEqualTo(63001L);
                }
                try (var rows = statement.executeQuery("SELECT activity_id FROM zhixue_order.`order` WHERE order_no='ORDINARY'")) {
                    assertThat(rows.next()).isTrue(); assertThat(rows.getObject(1)).isNull();
                }
            }

            DriverManagerDataSource ds = new DriverManagerDataSource(url.replace("/?", "/zhixue_order?"), "root", "");
            MybatisConfiguration config = new MybatisConfiguration(); config.setMapUnderscoreToCamelCase(true);
            config.addMapper(OrderMapper.class);
            MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
            factory.setDataSource(ds); factory.setConfiguration(config);
            var sessions = new SqlSessionTemplate(factory.getObject());
            OrderMapper mapper = spy(sessions.getMapper(OrderMapper.class));
            var transactions = new TransactionTemplate(new DataSourceTransactionManager(ds));
            transactions.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
            var queried = new CyclicBarrier(2);
            doAnswer(call -> {
                var result = call.callRealMethod();
                queried.await(5, TimeUnit.SECONDS);
                return result;
            }).when(mapper).findSeckillOrder(63002L, 1004L);
            var service = new OrderServiceImpl(mapper, mock(RabbitTemplate.class), mock(RemoteCourseService.class));
            Callable<String> create = () -> transactions.execute(status -> service.createSeckillOrder(
                    1004L, 2007L, new java.math.BigDecimal("9.90"), 63002L).getOrderNo());
            Future<String> a = pool.submit(create), b = pool.submit(create);
            assertThat(a.get(10, TimeUnit.SECONDS)).isEqualTo(b.get(10, TimeUnit.SECONDS));
            verify(mapper).lockSeckillOrder(63002L, 1004L);
            try (var connection = ds.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM `order` WHERE activity_id=63002 AND user_id=1004")) {
                rows.next(); assertThat(rows.getLong(1)).isEqualTo(1L);
            }

            runSql(socket, directory, "ALTER TABLE zhixue_order.`order` DROP INDEX uk_order_activity_user;"
                    + "INSERT INTO zhixue_order.`order` (id,order_no,user_id,product_id,product_type,amount,activity_id)"
                    + "VALUES(3,'DUP',1003,2007,0,9.90,63001);");
            Process conflict = client(socket, directory).redirectInput(migration.toFile()).start();
            assertThat(conflict.waitFor(15, TimeUnit.SECONDS)).isTrue();
            assertThat(conflict.exitValue()).isNotZero();
            assertThat(Files.readString(directory.resolve("client.log"))).contains("Duplicate activity/user");
            try (var connection = ds.getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM `order` WHERE activity_id=63001 AND user_id=1003")) {
                rows.next(); assertThat(rows.getLong(1)).isEqualTo(2L);
            }
        } finally {
            pool.shutdownNow();
            if (server != null) {
                server.destroy();
                if (!server.waitFor(15, TimeUnit.SECONDS)) { server.destroyForcibly(); server.waitFor(5, TimeUnit.SECONDS); }
            }
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private ProcessBuilder client(Path socket, Path directory) {
        return new ProcessBuilder("mysql", "--no-defaults", "--protocol=SOCKET", "--socket=" + socket,
                "--default-character-set=utf8mb4", "-uroot").redirectErrorStream(true)
                .redirectOutput(directory.resolve("client.log").toFile());
    }
    private void runSql(Path socket, Path directory, String sql) throws Exception {
        Process process = client(socket, directory).start();
        try (var input = process.getOutputStream()) { input.write(sql.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
        assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).withFailMessage(Files.readString(directory.resolve("client.log"))).isZero();
    }
    private void runFile(Path socket, Path directory, Path sql) throws Exception {
        Process process = client(socket, directory).redirectInput(sql.toFile()).start();
        assertThat(process.waitFor(15, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).withFailMessage(Files.readString(directory.resolve("client.log"))).isZero();
    }
}
