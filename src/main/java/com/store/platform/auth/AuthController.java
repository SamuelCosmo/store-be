package com.store.platform.auth;

import com.store.platform.auth.dto.AuthResponse;
import com.store.platform.auth.dto.LoginRequest;
import com.store.platform.auth.dto.RegisterRequest;
import com.store.platform.auth.dto.StoreOption;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Login de plataforma — rechaza roles de tenant; lo usa /superadmin. */
    @PostMapping("/superadmin")
    public AuthResponse superadminLogin(@Valid @RequestBody LoginRequest request) {
        return authService.loginSuperadmin(request);
    }

    /** Tiendas elegibles de la cuenta — se consulta tras el error
     *  "storeId required" para que el login muestre el selector. */
    @PostMapping("/stores")
    public List<StoreOption> stores(@Valid @RequestBody LoginRequest request) {
        return authService.storesForLogin(request);
    }
}
