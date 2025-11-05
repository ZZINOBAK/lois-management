package com.lois.management;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DbConnectionTest {

    @Autowired
    DataSource dataSource;
    @Autowired
    JdbcTemplate jdbcTemplate;

    @Test
    void canConnect() throws Exception {
        assertThat(dataSource.getConnection()).isNotNull();

        Integer x = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
        assertThat(x).isEqualTo(1);
    }

}
