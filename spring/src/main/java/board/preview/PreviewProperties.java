package board.preview;

import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.preview")
public record PreviewProperties(Set<String> allowedHosts) {}
