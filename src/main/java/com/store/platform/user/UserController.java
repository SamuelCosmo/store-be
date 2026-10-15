package com.store.platform.user;

import com.store.platform.auth.dto.StoreOption;
import com.store.platform.security.AuthContext;
import com.store.platform.user.dto.ActiveRequest;
import com.store.platform.user.dto.PasswordRequest;
import com.store.platform.user.dto.UserRequest;
import com.store.platform.user.dto.UserResponse;
import com.store.platform.user.dto.UserUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
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

    /** Tiendas visibles del usuario logueado (selector multi-tienda del KDS). */
    @GetMapping("/me/stores")
    public List<StoreOption> myStores() {
        return userService.viewableStores();
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest request) {
        Long clientId = AuthContext.current().clientId();
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(clientId, request));
    }

    /** Sin page/size → array completo; con ellos → Page.
     *  Se decide por presencia explícita: el fallback de Boot pagina hasta sin params. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public Object listByClient(@RequestParam(required = false) String search,
                               @RequestParam(required = false) Boolean deleted,
                               @RequestParam(required = false) Integer page,
                               @RequestParam(required = false) Integer size,
                               Pageable pageable) {
        Long clientId = AuthContext.current().clientId();
        if (Boolean.TRUE.equals(deleted)) {
            return userService.listDeleted(clientId);
        }
        return page != null || size != null
                ? userService.pageByClient(clientId, search, pageable)
                : userService.listByClient(clientId);
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

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    public UserResponse restore(@PathVariable Long id) {
        return userService.restore(id);
    }
}
