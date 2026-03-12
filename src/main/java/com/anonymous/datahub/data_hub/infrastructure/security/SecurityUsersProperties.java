package com.anonymous.datahub.data_hub.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security")
public class SecurityUsersProperties {

    private UserCredential admin = new UserCredential();
    private UserCredential report = new UserCredential();

    public UserCredential getAdmin() {
        return admin;
    }

    public void setAdmin(UserCredential admin) {
        this.admin = admin;
    }

    public UserCredential getReport() {
        return report;
    }

    public void setReport(UserCredential report) {
        this.report = report;
    }

    public static class UserCredential {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
