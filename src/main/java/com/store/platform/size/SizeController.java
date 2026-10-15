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
    public List<SizeResponse> list(@RequestParam(required = false) Boolean deleted) {
        return Boolean.TRUE.equals(deleted) ? sizeService.listDeleted() : sizeService.list();
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public SizeResponse update(@PathVariable Long id, @Valid @RequestBody SizeRequest request) {
        return sizeService.update(id, request);
    }

    @PutMapping("/order")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public List<SizeResponse> reorder(@RequestBody List<Long> orderedIds) {
        return sizeService.reorder(orderedIds);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        sizeService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public SizeResponse restore(@PathVariable Long id) {
        return sizeService.restore(id);
    }
}
