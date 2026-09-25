package com.store.platform.store;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    List<Device> findByStoreId(Long storeId);

    long countByStoreIdAndTypeAndActiveTrue(Long storeId, Device.Type type);
}
