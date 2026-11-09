package com.pothole.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pothole")
public class PotholeProperties {

    private String apiKey = "";

    private String imageDir = System.getProperty("java.io.tmpdir") + "/pothole-images";

    private final Image image = new Image();

    private final RateLimit rateLimit = new RateLimit();

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getImageDir() {
        return imageDir;
    }

    public void setImageDir(String imageDir) {
        this.imageDir = imageDir;
    }

    public Image getImage() {
        return image;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public static class Image {

        private long maxBytes = 3L * 1024 * 1024;

        public long getMaxBytes() {
            return maxBytes;
        }

        public void setMaxBytes(long maxBytes) {
            this.maxBytes = maxBytes;
        }
    }

    public static class RateLimit {

        private boolean enabled = true;

        private int requestsPerSecond = 10;

        private int burst = 20;

        private int authFailuresPerMinute = 10;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequestsPerSecond() {
            return requestsPerSecond;
        }

        public void setRequestsPerSecond(int requestsPerSecond) {
            this.requestsPerSecond = requestsPerSecond;
        }

        public int getBurst() {
            return burst;
        }

        public void setBurst(int burst) {
            this.burst = burst;
        }

        public int getAuthFailuresPerMinute() {
            return authFailuresPerMinute;
        }

        public void setAuthFailuresPerMinute(int authFailuresPerMinute) {
            this.authFailuresPerMinute = authFailuresPerMinute;
        }
    }
}
