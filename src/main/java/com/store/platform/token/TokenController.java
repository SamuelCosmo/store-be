package com.store.platform.token;

import com.store.platform.token.dto.TokenSaleRequest;
import com.store.platform.token.dto.TokenSaleResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/tokens")
@RequiredArgsConstructor
public class TokenController {

    private final TokenService tokenService;

    /** Venta de fichas — solo caja (staff con tienda activa), nunca kiosco. */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE') and hasAuthority('MODULE_TOKENS')")
    public ResponseEntity<TokenSaleResponse> purchase(
            @Valid @RequestBody TokenSaleRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenService.purchase(request, idempotencyKey));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER','EMPLOYEE') and hasAuthority('MODULE_TOKENS')")
    public List<TokenSaleResponse> list(
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return tokenService.list(storeId, from, to);
    }
}
