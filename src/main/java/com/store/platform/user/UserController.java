package com.store.platform.user;

import com.store.platform.security.AuthContext;
import com.store.platform.user.dto.ActiveRequest;
import com.store.platform.user.dto.PasswordRequest;
import com.store.platform.user.dto.UserRequest;
import com.store.platform.user.dto.UserResponse;
import com.store.platform.user.dto.UserUpdateRequest;
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

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest request) {
        return userService.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse setActive(@PathVariable Long id, @Valid @RequestBody ActiveRequest request) {
        return userService.setActive(id, request.active(),
                new PasswordRequest(request.currentPassword()));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id, @Valid @RequestBody PasswordRequest request) {
        userService.delete(id, request);
        return ResponseEntity.noContent().build();
    }
}
