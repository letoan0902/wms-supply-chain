package vn.wms.configserver;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Khởi động Config Server thật (cổng ngẫu nhiên) và gọi API cấu hình để chắc chắn config-repo được phục vụ. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ConfigServerApplicationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void servesSharedApplicationConfiguration() {
        ResponseEntity<String> response = restTemplate.getForEntity("/application/default", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("config-repo/application.yml")
                .contains("eureka.client.service-url.defaultZone")
                .contains("spring.rabbitmq.host");
    }

    @Test
    void mergesServiceSpecificFileWithSharedFile() {
        ResponseEntity<String> response = restTemplate.getForEntity("/auth-service/default", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody())
                .contains("config-repo/auth-service.yml")
                .contains("config-repo/application.yml");
    }

    @Test
    void exposesHealthEndpoint() {
        ResponseEntity<String> response = restTemplate.getForEntity("/actuator/health", String.class);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("UP");
    }
}
