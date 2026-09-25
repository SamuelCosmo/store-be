package com.store.platform.user;

import com.store.platform.security.AuthContext;
import com.store.platform.user.dto.UserRequest;
import com.store.platform.user.dto.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        Long clientId = AuthContext.current().clientId();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(clientId, request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<UserResponse> listByClient() {
        return userService.listByClient(AuthContext.current().clientId());
    }
}
