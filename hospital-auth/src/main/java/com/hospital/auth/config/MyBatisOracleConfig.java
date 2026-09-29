package com.hospital.auth.config;

import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.boot.autoconfigure.ConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Oracle 迁移适配：null 参数统一以 JdbcType.NULL 绑定。
 * Oracle JDBC 驱动拒绝 Types.OTHER 的 setNull（ORA-17004），MySQL 则容忍；
 * 显式 Customizer 保证优先级高于 yml 绑定路径。
 */
@Configuration
public class MyBatisOracleConfig {

    @Bean
    public ConfigurationCustomizer oracleJdbcTypeForNullCustomizer() {
        return configuration -> configuration.setJdbcTypeForNull(JdbcType.NULL);
    }
}
