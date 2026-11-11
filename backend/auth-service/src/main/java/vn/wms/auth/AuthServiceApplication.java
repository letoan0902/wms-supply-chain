package vn.wms.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Auth Service (cổng 8081): quản lý tài khoản, phát hành JWT và phân quyền theo vai trò (RBAC).
 */
@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
