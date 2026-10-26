package board.webhook;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.webhook")
public record WebhookProperties(String secret, long toleranceSeconds) {}
