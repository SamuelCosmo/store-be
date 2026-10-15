package com.store.platform.token;

import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.order.Order;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.Store;
import com.store.platform.store.StoreRepository;
import com.store.platform.store.StoreService;
import com.store.platform.token.dto.TokenSaleRequest;
import com.store.platform.token.dto.TokenSaleResponse;
import com.store.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenSaleRepository tokenSaleRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final StoreService storeService;

    /** Venta de fichas en caja: cobra quantity × tokenPrice (snapshot del
     *  cliente) por CASH/CARD. El canje NO pasa por aquí — es una orden con
     *  paymentMethod=TOKEN. */
    @Transactional
    public TokenSaleResponse purchase(TokenSaleRequest request) {
        AuthUser auth = AuthContext.current();
        Long storeId = AuthContext.requireStoreId();
        if (request.paymentMethod() == Order.PaymentMethod.TOKEN) {
            throw new BusinessException("Token sales can't be paid with tokens");
        }
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + storeId));
        BigDecimal unitPrice = store.getClient().getTokenPrice();
        if (unitPrice == null || unitPrice.signum() <= 0) {
            throw new BusinessException("Token sales are disabled — set a token price in settings");
        }
        TokenSale sale = tokenSaleRepository.save(TokenSale.builder()
                .store(store)
                .user(userRepository.getReferenceById(auth.userId()))
                .quantity(request.quantity())
                .unitPrice(unitPrice)
                .totalPrice(unitPrice.multiply(BigDecimal.valueOf(request.quantity())))
                .paymentMethod(request.paymentMethod())
                .build());
        return TokenSaleResponse.from(sale);
    }

    /** Ventas del rango [from, to] (default hoy) — scoping como /api/orders:
     *  tienda de sesión, o todo el cliente para un ADMIN sin tienda activa. */
    @Transactional(readOnly = true)
    public List<TokenSaleResponse> list(Long storeId, LocalDate from, LocalDate to) {
        AuthUser auth = AuthContext.current();
        Instant start = (from == null ? LocalDate.now() : from)
                .atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant end = (to == null ? LocalDate.now() : to)
                .plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
        if (storeId == null && auth.storeId() == null && auth.role() == com.store.platform.user.User.Role.ADMIN) {
            return tokenSaleRepository
                    .findByStoreClientIdAndCreatedAtBetweenOrderByCreatedAtDesc(auth.clientId(), start, end)
                    .stream().map(TokenSaleResponse::from).toList();
        }
        return tokenSaleRepository
                .findByStoreIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                        storeService.resolveScope(storeId), start, end)
                .stream().map(TokenSaleResponse::from).toList();
    }
}
