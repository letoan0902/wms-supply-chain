package vn.wms.common.constant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RolesTest {

    @Test
    void allContainsTheSixBusinessRolesWithoutDuplicates() {
        assertThat(Roles.ALL)
                .containsExactly("ADMIN", "WAREHOUSE_MANAGER", "WAREHOUSE_STAFF", "PURCHASING", "SALES", "ACCOUNTANT")
                .doesNotHaveDuplicates();
    }

    @Test
    void validatesAndPrefixesRoleNames() {
        assertThat(Roles.isValid(Roles.SALES)).isTrue();
        assertThat(Roles.isValid("HACKER")).isFalse();
        assertThat(Roles.isValid(null)).isFalse();
        assertThat(Roles.toAuthority(Roles.ADMIN)).isEqualTo("ROLE_ADMIN");
    }
}
