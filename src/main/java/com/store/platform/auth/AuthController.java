package com.store.platform.auth;

import com.store.platform.auth.dto.AuthResponse;
import com.store.platform.auth.dto.LoginRequest;
import com.store.platform.auth.dto.RegisterRequest;
import com.store.platform.auth.dto.StoreOption;
import com.store.platform.common.ClientIp;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

    /** Solo plataforma — crea el primer ADMIN de un tenant que nació sin admin. */
    @PostMapping("/register")
    @PreAuthorize("hasRole('SUPERADMIN')")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request,
                              HttpServletRequest http) {
        return authService.login(request, ClientIp.from(http));
    }

    /** Login de plataforma — rechaza roles de tenant; lo usa /superadmin. */
    @PostMapping("/superadmin")
    public AuthResponse superadminLogin(@Valid @RequestBody LoginRequest request,
                                        HttpServletRequest http) {
        return authService.loginSuperadmin(request, ClientIp.from(http));
    }

    /** Tiendas elegibles de la cuenta — se consulta tras el error
     *  "storeId required" para que el login muestre el selector. */
    @PostMapping("/stores")
    public List<StoreOption> stores(@Valid @RequestBody LoginRequest request,
                                    HttpServletRequest http) {
        return authService.storesForLogin(request, ClientIp.from(http));
    }
}
