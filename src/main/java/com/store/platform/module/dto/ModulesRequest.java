package com.store.platform.module.dto;

import com.store.platform.common.exception.BusinessException;
import com.store.platform.module.Module;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Body de PUT .../modules — nombres del catálogo Module. */
public record ModulesRequest(@NotNull List<String> modules) {

    public Set<Module> toModules() {
        try {
            return modules.stream()
                    .map(m -> Module.valueOf(m.trim().toUpperCase()))
                    .collect(Collectors.toSet());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("INVALID_MODULE", "Unknown module name");
        }
    }
}
