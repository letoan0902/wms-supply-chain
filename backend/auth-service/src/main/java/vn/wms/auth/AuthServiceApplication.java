package vn.wms.auth;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Auth Service (cổng 8081): quản lý tài khoản, phát hành JWT và phân quyền theo vai trò (RBAC).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@OpenAPIDefinition(info = @Info(title = "WMS Auth Service API", version = "v1",
        description = "Tài khoản, đăng nhập JWT và phân quyền RBAC"))
public class AuthServiceApplication {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }

//    /**
//     * [DEV ONLY] Log BCrypt hash của mật khẩu demo khi service khởi động.
//     * Dùng giá trị này để điền vào cột password_hash trong docker/mysql/init.sql.
//     * Xóa hoặc comment bean này khi lên môi trường production.
//     */
//    @Bean
//    ApplicationRunner logDemoPasswordHash() {
//        return args -> {
//            String demoPassword = "Demopassword";
//            String hash = new BCryptPasswordEncoder().encode(demoPassword);
//            log.info("========================================================");
//            log.info("[DEV] Demo user password  : {}", demoPassword);
//            log.info("[DEV] BCrypt hash          : {}", hash);
//            log.info("[DEV] Dùng hash trên để cập nhật cột password_hash trong docker/mysql/init.sql");
//            log.info("========================================================");
//        };
//    }
}
