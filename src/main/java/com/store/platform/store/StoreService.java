package com.store.platform.store;

import com.store.platform.client.Client;
import com.store.platform.client.ClientRepository;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.common.exception.ResourceNotFoundException;
import com.store.platform.security.AuthContext;
import com.store.platform.security.AuthUser;
import com.store.platform.store.dto.DeviceRequest;
import com.store.platform.store.dto.DeviceResponse;
import com.store.platform.store.dto.StoreRequest;
import com.store.platform.store.dto.StoreResponse;
import com.store.platform.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final DeviceRepository deviceRepository;
    private final ClientRepository clientRepository;

    @Transactional
    public StoreResponse create(Long clientId, StoreRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + clientId));
        if (storeRepository.countByClientId(clientId) >= client.getMaxStores()) {
            throw new BusinessException("Store limit reached for this plan (" + client.getMaxStores() + ")");
        }
        Store store = Store.builder()
                .client(client)
                .name(request.name())
                .description(request.description())
                .address(request.address())
                .businessType(request.businessType())
                .active(request.active() == null || request.active())
                .build();
        return StoreResponse.from(storeRepository.save(store));
    }

    public StoreResponse getById(Long id, Long clientId) {
        return StoreResponse.from(findOwnedOrThrow(id, clientId));
    }

    public List<StoreResponse> listByClient(Long clientId) {
        return storeRepository.findByClientId(clientId).stream()
                .map(StoreResponse::from)
                .toList();
    }

    @Transactional
    public StoreResponse update(Long id, StoreRequest request, Long clientId) {
        Store store = findOwnedOrThrow(id, clientId);
        store.setName(request.name());
        store.setDescription(request.description());
        store.setAddress(request.address());
        store.setBusinessType(request.businessType());
        if (request.active() != null) store.setActive(request.active());
        return StoreResponse.from(storeRepository.save(store));
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
            throw new BusinessException(
                    request.type() + " limit reached for this plan (" + limit + " per store)");
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
        throw new AccessDeniedException("Switch stores via login (storeId)");
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
