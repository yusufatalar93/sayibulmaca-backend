package com.sayibulmaca.controller;

import com.sayibulmaca.config.GameConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
public class GameConfigController {

    private final GameConfig gameConfig;

    @GetMapping("/game")
    public ResponseEntity<GameConfig> getGameConfig() {
        return ResponseEntity.ok(gameConfig);
    }
}
