package io.quarkus.agroal.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;

import javax.inject.Inject;

import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import io.agroal.api.AgroalDataSource;
import io.agroal.api.configuration.AgroalConnectionFactoryConfiguration;
import io.agroal.api.configuration.AgroalConnectionPoolConfiguration;
import io.quarkus.test.QuarkusUnitTest;

public class DefaultDataSourceConfigTest {

    //tag::injection[]
    @Inject
    AgroalDataSource defaultDataSource;
    //end::injection[]

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest().setArchiveProducer(
            () -> ShrinkWrap.create(JavaArchive.class)
                    .addAsResource("application-default-datasource.properties",
                            "application.properties"));

    @Test
    public void testDefaultDataSourceInjection() throws SQLException {
        testDataSource(defaultDataSource, "username-default", 3, 13, 7, Duration.ofSeconds(53), Duration.ofSeconds(54),
                Duration.ofSeconds(55), Duration.ofSeconds(56), Duration.ofSeconds(57));
    }

    private static void testDataSource(AgroalDataSource dataSource, String username, int minSize, int maxSize,
            int initialSize, Duration backgroundValidationInterval, Duration acquisitionTimeout, Duration leakDetectionInterval,
            Duration idleRemovalInterval, Duration maxLifetime) throws SQLException {
        AgroalConnectionPoolConfiguration configuration = dataSource.getConfiguration().connectionPoolConfiguration();
        AgroalConnectionFactoryConfiguration agroalConnectionFactoryConfiguration = configuration
                .connectionFactoryConfiguration();

        assertEquals("jdbc:h2:tcp://localhost/mem:default", agroalConnectionFactoryConfiguration.jdbcUrl());
        assertEquals(username, agroalConnectionFactoryConfiguration.principal().getName());
        assertEquals(minSize, configuration.minSize());
        assertEquals(maxSize, configuration.maxSize());
        assertEquals(initialSize, configuration.initialSize());
        assertEquals(backgroundValidationInterval, configuration.validationTimeout());
        assertEquals(acquisitionTimeout, configuration.acquisitionTimeout());
        assertEquals(leakDetectionInterval, configuration.leakTimeout());
        assertEquals(idleRemovalInterval, configuration.reapTimeout());
        assertEquals(maxLifetime, configuration.maxLifetime());
        assertEquals(AgroalConnectionFactoryConfiguration.TransactionIsolation.SERIALIZABLE,
                agroalConnectionFactoryConfiguration.jdbcTransactionIsolation());
        assertTrue(dataSource.getConfiguration().metricsEnabled());

        try (Connection connection = dataSource.getConnection()) {
        }
    }
}
