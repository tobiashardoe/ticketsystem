package app.config;

import app.utils.Utils;
import jakarta.persistence.EntityManagerFactory;

import java.util.Properties;

public final class HibernateConfig {

    private static volatile EntityManagerFactory emf;

    private HibernateConfig() {}

    public static EntityManagerFactory getEntityManagerFactory() {
        if (emf == null) {
            synchronized (HibernateConfig.class) {
                if (emf == null) {
                    emf = HibernateEmfBuilder.build(buildProps());
                }
            }
        }
        return emf;
    }

    private static Properties buildProps() {
        Properties props = HibernateBaseProperties.createBase();

        // Bevar eksisterende data, og opret manglende tabeller ved opstart.
        props.put("hibernate.hbm2ddl.auto", "update");

        if (System.getenv("DEPLOYED") != null) {
            setDeployedProperties(props);
        } else {
            setDevProperties(props);
        }
        return props;
    }

    private static void setDeployedProperties(Properties props) {
        String dbName = requireEnvironmentVariable("DB_NAME");
        props.setProperty("hibernate.connection.url", requireEnvironmentVariable("CONNECTION_STR") + dbName);
        props.setProperty("hibernate.connection.username", requireEnvironmentVariable("DB_USERNAME"));
        props.setProperty("hibernate.connection.password", requireEnvironmentVariable("DB_PASSWORD"));
    }

    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }

    private static void setDevProperties(Properties props) {
        String connectionString = Utils.getPropertyValue("CONNECTION_STR", "config.properties");
        String dbName = Utils.getPropertyValue("DB_NAME", "config.properties");
        String username = Utils.getPropertyValue("DB_USERNAME", "config.properties");
        String password = Utils.getPropertyValue("DB_PASSWORD", "config.properties");

        props.put("hibernate.connection.url", connectionString + dbName);
        props.put("hibernate.connection.username", username);
        props.put("hibernate.connection.password", password);
    }
}
