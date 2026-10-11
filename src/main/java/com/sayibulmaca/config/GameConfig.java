package com.sayibulmaca.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
@ConfigurationProperties(prefix = "game")
@Getter
@Setter
public class GameConfig {

    private Map<String, Map<String, Integer>> timeControl;
    private int secretSetupTimeoutSeconds;
    private int maxGuessesPerPlayer;
    private int firstMoveTimeoutSeconds;
    
}
