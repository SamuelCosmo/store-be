package com.store.platform.common.logging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AppLogRepository extends JpaRepository<AppLog, Long>, JpaSpecificationExecutor<AppLog> {
}
