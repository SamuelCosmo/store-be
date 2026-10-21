package com.store.platform.store;

import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.storage.StorageService;
import com.store.platform.store.dto.DeviceRequest;
import com.store.platform.store.dto.DeviceResponse;
import com.store.platform.store.dto.StoreRequest;
import com.store.platform.store.dto.StoreResponse;
import com.store.platform.store.dto.StoreSettingsRequest;
import com.store.platform.store.dto.StoreSettingsResponse;
import com.store.platform.user.User;
import com.store.platform.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final DeviceRepository deviceRepository;
    private final ClientRepository clientRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;

    @Transactional
    public StoreResponse create(Long clientId, StoreRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        if (storeRepository.countByClientIdAndDeletedAtIsNull(clientId) >= client.getMaxStores()) {
            throw new BusinessException("STORE_LIMIT",
                    "Store limit reached for this plan (" + client.getMaxStores() + ")",
                    java.util.Map.of("max", client.getMaxStores()));
        }
        Store store = Store.builder()
                .client(client)
                .name(request.name())
                .description(request.description())
                .address(request.address())
                .businessType(request.businessType())
                .active(request.active() == null || request.active())
                .logoUrl(normalize(request.logoUrl()))
                .primaryColor(normalize(request.primaryColor()))
                .accentColor(normalize(request.accentColor()))
                .canvasColor(normalize(request.canvasColor()))
                .build();
        return StoreResponse.from(storeRepository.save(store));
    }

    public StoreResponse getById(Long id, Long clientId) {
        return StoreResponse.from(findOwnedOrThrow(id, clientId));
    }

    public List<StoreResponse> listByClient(Long clientId) {
        return storeRepository.findByClientIdAndDeletedAtIsNull(clientId).stream()
                .map(StoreResponse::from)
                .toList();
    }

    /** Solo eliminadas — alimenta el toggle "ver eliminadas" del admin. */
    public List<StoreResponse> listDeletedByClient(Long clientId) {
        return storeRepository.findByClientIdAndDeletedAtIsNotNull(clientId).stream()
                .map(StoreResponse::from)
                .toList();
    }

    @Transactional
    public void delete(Long id, Long clientId) {
        // soft delete — las órdenes históricas siguen apuntando a la tienda
        Store store = findOwnedOrThrow(id, clientId);
        store.setDeletedAt(Instant.now());
        storeRepository.save(store);
    }

    @Transactional
    public StoreResponse restore(Long id, Long clientId) {
        Store store = storeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + id));
        if (!store.getClient().getId().equals(clientId)) {
            throw new AccessDeniedException("Store does not belong to your client");
        }
        // mismo tope que create — si no, borrar/crear/restaurar evade el plan
        int max = store.getClient().getMaxStores();
        if (store.getDeletedAt() != null
                && storeRepository.countByClientIdAndDeletedAtIsNull(clientId) >= max) {
            throw new BusinessException("STORE_LIMIT",
                    "Store limit reached for this plan (" + max + ")", java.util.Map.of("max", max));
        }
        store.setDeletedAt(null);
        return StoreResponse.from(storeRepository.save(store));
    }

    /** Versión paginada para la tabla del admin. */
    public Page<StoreResponse> pageByClient(Long clientId, String search, Pageable pageable) {
        String q = search == null ? "" : search.trim();
        return storeRepository.pageByClient(clientId, q, pageable).map(StoreResponse::from);
    }

    @Transactional
    public StoreResponse update(Long id, StoreRequest request, Long clientId) {
        Store store = findOwnedOrThrow(id, clientId);
        store.setName(request.name());
        store.setDescription(request.description());
        store.setAddress(request.address());
        store.setBusinessType(request.businessType());
        if (request.active() != null) store.setActive(request.active());
        // marca: null = conservar, "" = quitar — igual que el logo del tenant
        if (request.logoUrl() != null) {
            String old = store.getLogoUrl();
            String next = normalize(request.logoUrl());
            store.setLogoUrl(next);
            if (old != null && !old.equals(next)) storageService.deleteByUrl(old);
        }
        if (request.primaryColor() != null) store.setPrimaryColor(normalize(request.primaryColor()));
        if (request.accentColor() != null) store.setAccentColor(normalize(request.accentColor()));
        if (request.canvasColor() != null) store.setCanvasColor(normalize(request.canvasColor()));
        return StoreResponse.from(storeRepository.save(store));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public StoreSettingsResponse getSettings(Long id, Long clientId) {
        return StoreSettingsResponse.from(findOwnedOrThrow(id, clientId));
    }

    @Transactional
    public StoreSettingsResponse updateSettings(Long id, StoreSettingsRequest request, Long clientId) {
        Store store = findOwnedOrThrow(id, clientId);
        // blank = sin nombre de menú → se usa el nombre del establecimiento
        store.setMenuName(request.menuName() != null && !request.menuName().isBlank()
                ? request.menuName().trim() : null);
        if (request.kitchenOkMin() != null) store.setKitchenOkMin(request.kitchenOkMin());
        if (request.kitchenWarnMin() != null) store.setKitchenWarnMin(request.kitchenWarnMin());
        // mismos defaults que StoreSettingsResponse (8/15) si la columna es null
        int ok = store.getKitchenOkMin() != null ? store.getKitchenOkMin() : 8;
        int warn = store.getKitchenWarnMin() != null ? store.getKitchenWarnMin() : 15;
        if (warn <= ok) {
            throw new BusinessException("KITCHEN_THRESHOLDS", "kitchenWarnMin must be greater than kitchenOkMin");
        }
        if (request.kitchenSound() != null) store.setKitchenSound(request.kitchenSound());
        return StoreSettingsResponse.from(storeRepository.save(store));
    }

    @Transactional
    public DeviceResponse addDevice(Long storeId, DeviceRequest request, Long clientId) {
        Store store = findOwnedOrThrow(storeId, clientId);
        Client client = store.getClient();
        int limit = request.type() == Device.Type.POS
                ? client.getMaxPosPerStore()
                : client.getMaxKiosksPerStore();
        long registered = deviceRepository.countByStoreIdAndTypeAndActiveTrue(storeId, request.type());
        if (registered >= limit) {
            throw new BusinessException("DEVICE_LIMIT",
                    request.type() + " limit reached for this plan (" + limit + " per store)",
                    java.util.Map.of("limit", limit));
        }
        Device device = Device.builder()
                .store(store)
                .type(request.type())
                .name(request.name())
                .active(true)
                .build();
        return DeviceResponse.from(deviceRepository.save(device));
    }

    public List<DeviceResponse> listDevices(Long storeId, Long clientId) {
        findOwnedOrThrow(storeId, clientId);
        return deviceRepository.findByStoreId(storeId).stream()
                .map(DeviceResponse::from)
                .toList();
    }

    public Store findOrThrow(Long id) {
        return storeRepository.findById(id)
                .filter(s -> s.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found: " + id));
    }

    /**
     * Resolves the store a catalog/order operation applies to: the optional
     * {@code ?storeId=} override if given, else the session's active store.
     * Non-admins are confined to their session store — switch stores via login.
     */
    public Long resolveScope(Long requestedStoreId) {
        AuthUser auth = AuthContext.current();
        if (requestedStoreId == null) {
            return AuthContext.requireStoreId();
        }
        if (requestedStoreId.equals(auth.storeId())) {
            return requestedStoreId;
        }
        if (auth.role() == User.Role.ADMIN) {
            findOwnedOrThrow(requestedStoreId, auth.clientId());
            return requestedStoreId;
        }
        // el resto puede scopear a cualquier tienda asignada (user_stores) —
        // ej. un gerente con varios establecimientos viendo el KDS de otra
        if (userRepository.findStoreIds(auth.userId()).contains(requestedStoreId)) {
            findOwnedOrThrow(requestedStoreId, auth.clientId());
            return requestedStoreId;
        }
        throw new AccessDeniedException("You are not assigned to this store");
    }

    /** Store lookup scoped to a tenant — foreign stores are invisible, not forbidden. */
    public Store findOwnedOrThrow(Long id, Long clientId) {
        Store store = findOrThrow(id);
        if (!store.getClient().getId().equals(clientId)) {
            throw new AccessDeniedException("Store does not belong to your client");
        }
        return store;
    }
}
