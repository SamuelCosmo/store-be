package com.store.platform.client;

import com.store.platform.client.dto.ClientRequest;
import com.store.platform.client.dto.ClientResponse;
import com.store.platform.client.dto.ClientSettingsRequest;
import com.store.platform.client.dto.ClientSettingsResponse;
import com.store.platform.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClientService {

    private final ClientRepository clientRepository;

    @Transactional
    public ClientResponse create(ClientRequest request) {
        Client client = Client.builder()
                .name(request.name())
                .maxStores(request.maxStores())
                .maxPosPerStore(request.maxPosPerStore())
                .maxKiosksPerStore(request.maxKiosksPerStore())
                .build();
        // TODO: crear la cuenta administrador del cliente (User con rol ADMIN, email + contraseña)
        return ClientResponse.from(clientRepository.save(client));
    }

    public ClientResponse getById(Long id) {
        return ClientResponse.from(findOrThrow(id));
    }

    /** Plataforma: todos los tenants. */
    public List<ClientResponse> list() {
        return clientRepository.findAll().stream().map(ClientResponse::from).toList();
    }

    /** Plataforma: nombre + límites del plan. */
    @Transactional
    public ClientResponse update(Long id, ClientRequest request) {
        Client client = findOrThrow(id);
        client.setName(request.name());
        client.setMaxStores(request.maxStores());
        client.setMaxPosPerStore(request.maxPosPerStore());
        client.setMaxKiosksPerStore(request.maxKiosksPerStore());
        return ClientResponse.from(clientRepository.save(client));
    }

    public ClientSettingsResponse getSettings(Long id) {
        return ClientSettingsResponse.from(findOrThrow(id));
    }

    @Transactional
    public ClientSettingsResponse updateSettings(Long id, ClientSettingsRequest request) {
        Client client = findOrThrow(id);
        if (request.currency() != null) client.setCurrency(request.currency());
        if (request.taxRate() != null) client.setTaxRate(request.taxRate());
        if (request.kioskReceiptSeconds() != null) client.setKioskReceiptSeconds(request.kioskReceiptSeconds());
        if (request.sessionHours() != null) client.setSessionHours(request.sessionHours());
        return ClientSettingsResponse.from(clientRepository.save(client));
    }

    public Client findOrThrow(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id));
    }
}
