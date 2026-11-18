package com.store.platform.client;

import com.store.platform.client.dto.ClientSettingsRequest;
import com.store.platform.common.exception.BusinessException;
import com.store.platform.audit.AuditService;
import com.store.platform.storage.StorageService;
import com.store.platform.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    @Mock ClientRepository clientRepository;
    @Mock UserRepository userRepository;
    @Mock StorageService storageService;
    @Mock AuditService auditService;
    @InjectMocks ClientService clientService;

    private final Client client = Client.builder().id(1L).name("Kofi").build();

    @BeforeEach
    void setUp() {
        lenient().when(clientRepository.findById(1L)).thenReturn(Optional.of(client));
    }

    private static ClientSettingsRequest settings(String currency, Map<String, Integer> hours, String tz) {
        return new ClientSettingsRequest(null, null, currency, null, null, null, hours, tz);
    }

    @Test
    void unknownCurrencyIsRejected() {
        assertThrows(BusinessException.class,
                () -> clientService.updateSettings(1L, settings("XYZ", null, null)));
        verify(clientRepository, never()).save(any());
    }

    @Test
    void unknownTimezoneIsRejected() {
        assertThrows(BusinessException.class,
                () -> clientService.updateSettings(1L, settings(null, null, "Mars/Olympus")));
    }

    @Test
    void sessionHoursOnlyForTenantRoles() {
        assertThrows(BusinessException.class,
                () -> clientService.updateSettings(1L, settings(null, Map.of("SUPERADMIN", 1), null)));
    }

    @Test
    void validSettingsAreSaved() {
        when(clientRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var response = clientService.updateSettings(1L, settings("USD", null, "America/Cancun"));
        assertEquals("USD", response.currency());
        assertEquals("America/Cancun", response.timezone());
    }

    @Test
    void timezoneDefaultsToMexicoCity() {
        assertEquals("America/Mexico_City", client.zone().getId());
    }
}
