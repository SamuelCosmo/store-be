package com.store.platform.size;

import com.store.platform.size.dto.SizeRequest;
import com.store.platform.size.dto.SizeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sizes")
@RequiredArgsConstructor
public class SizeController {

    private final SizeService sizeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<SizeResponse> create(@Valid @RequestBody SizeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(sizeService.create(request));
    }

    @GetMapping
    public List<SizeResponse> list() {
        return sizeService.list();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public SizeResponse update(@PathVariable Long id, @Valid @RequestBody SizeRequest request) {
        return sizeService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sizeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
