package com.store.platform.module;

import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

/** Catálogo de módulos + consulta de lo que el tenant compró. La asignación
 *  por usuario vive en UserController (PUT /api/users/{id}/modules). */
@RestController
@RequestMapping("/api/modules")
@RequiredArgsConstructor
public class ModuleController {

    private final ModuleService moduleService;

    /** Catálogo completo del producto — referencia para las UIs. */
    @GetMapping
    public List<String> catalog() {
        return Arrays.stream(Module.values()).map(Enum::name).toList();
    }

    /** Módulos efectivos del usuario actual — resueltos en JwtAuthFilter. */
    @GetMapping("/me")
    public List<String> mine() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("MODULE_"))
                .map(a -> a.substring(7))
                .sorted()
                .toList();
    }

    /** Módulos contratados por el tenant del usuario — el ADMIN los asigna
     *  a sus usuarios (user_modules ⊆ client_modules). */
    @GetMapping("/available")
    @PreAuthorize("hasRole('ADMIN')")
    public List<String> available() {
        AuthUser me = AuthContext.current();
        return ModuleService.names(moduleService.clientModules(me.clientId()));
    }
}
