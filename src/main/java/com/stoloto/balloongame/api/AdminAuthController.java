package com.stoloto.balloongame.api;

import com.stoloto.balloongame.api.dto.AdminLoginRequest;
import com.stoloto.balloongame.api.dto.AdminProfileResponse;
import com.stoloto.balloongame.api.dto.AdminTokenResponse;
import com.stoloto.balloongame.api.dto.ChangePasswordRequest;
import com.stoloto.balloongame.api.dto.RefreshTokenRequest;
import com.stoloto.balloongame.api.exception.GameException;
import com.stoloto.balloongame.config.AdminPrincipal;
import com.stoloto.balloongame.service.AdminAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/auth")
@RequiredArgsConstructor
@Tag(name = "Admin Auth", description = "JWT login for the operator panel. Refresh token is returned in the JSON body.")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    @Operation(summary = "Login — returns access + refresh tokens")
    @PostMapping("/login")
    public AdminTokenResponse login(@Valid @RequestBody AdminLoginRequest body, HttpServletRequest request) {
        return adminAuthService.login(
                body.username(),
                body.password(),
                request.getHeader("User-Agent"),
                request.getRemoteAddr()
        );
    }

    @Operation(summary = "Rotate refresh token")
    @PostMapping("/refresh")
    public AdminTokenResponse refresh(@Valid @RequestBody RefreshTokenRequest body, HttpServletRequest request) {
        return adminAuthService.refresh(
                body.refreshToken(),
                request.getHeader("User-Agent"),
                request.getRemoteAddr()
        );
    }

    @Operation(summary = "Revoke refresh token")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) RefreshTokenRequest body) {
        if (body != null) {
            adminAuthService.logout(body.refreshToken());
        }
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Current admin profile")
    @SecurityRequirement(name = "Bearer")
    @GetMapping("/me")
    public AdminProfileResponse me(@AuthenticationPrincipal AdminPrincipal principal) {
        requireJwtPrincipal(principal);
        return adminAuthService.me(principal.id());
    }

    @Operation(summary = "Change password and revoke refresh tokens")
    @SecurityRequirement(name = "Bearer")
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@AuthenticationPrincipal AdminPrincipal principal,
                                               @Valid @RequestBody ChangePasswordRequest body) {
        requireJwtPrincipal(principal);
        adminAuthService.changePassword(principal.id(), body.currentPassword(), body.newPassword());
        return ResponseEntity.noContent().build();
    }

    private static void requireJwtPrincipal(AdminPrincipal principal) {
        if (principal == null || principal.id() == null) {
            throw GameException.authUnauthorized();
        }
    }
}
