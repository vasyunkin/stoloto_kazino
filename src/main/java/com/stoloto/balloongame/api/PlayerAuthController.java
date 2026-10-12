package com.stoloto.balloongame.api;

import com.stoloto.balloongame.api.dto.PlayerCredentialsRequest;
import com.stoloto.balloongame.api.dto.PlayerTokenResponse;
import com.stoloto.balloongame.api.dto.RefreshTokenRequest;
import com.stoloto.balloongame.service.PlayerAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Player auth", description = "Регистрация и вход игрока. Гость по X-Player-Id остаётся.")
public class PlayerAuthController {

    private final PlayerAuthService authService;

    @Operation(summary = "Создать аккаунт. Новый кошелёк, welcome-баланс по конфигу. Гостевой кошелёк не сливается.")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerTokenResponse register(@Valid @RequestBody PlayerCredentialsRequest request) {
        return authService.register(request.email(), request.password());
    }

    @Operation(summary = "Войти по email и паролю")
    @PostMapping("/login")
    public PlayerTokenResponse login(@Valid @RequestBody PlayerCredentialsRequest request) {
        return authService.login(request.email(), request.password());
    }

    @Operation(summary = "Обменять refresh-токен игрока")
    @PostMapping("/refresh")
    public PlayerTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return authService.refresh(request.refreshToken());
    }

    @Operation(summary = "Отозвать refresh-токен")
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestBody(required = false) RefreshTokenRequest request) {
        authService.logout(request == null ? null : request.refreshToken());
    }
}
