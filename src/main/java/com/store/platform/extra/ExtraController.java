package com.store.platform.extra;

import com.store.platform.extra.dto.ExtraRequest;
import com.store.platform.extra.dto.ExtraResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/extras")
@RequiredArgsConstructor
public class ExtraController {

    private final ExtraService extraService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<ExtraResponse> create(@Valid @RequestBody ExtraRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(extraService.create(request));
    }

    @GetMapping
    public List<ExtraResponse> list() {
        return extraService.list();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ExtraResponse update(@PathVariable Long id, @Valid @RequestBody ExtraRequest request) {
        return extraService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        extraService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
