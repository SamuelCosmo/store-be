package com.store.platform.common.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.store.platform.auth.AuthService;
import jakarta.annotation.PreDestroy;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Persiste en {@code app_logs} lo que el SUPERADMIN necesita ver: todo
 * WARN/ERROR de cualquier logger (con stack trace) + INFO de auditoría
 * (requests que mutan, eventos del front, logins). Inserta con JDBC directo
 * para no meter Hibernate en el camino del logging.
 */
// ponytail: insert síncrono por evento (toma una 2a conexión del pool) — pasar a AsyncAppender/batch si el volumen lo pide
@Component
public class DbLogAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

    private static final Set<String> AUDIT = Set.of(
            RequestLogFilter.class.getName(), "client", AuthService.class.getName());
    private static final ThreadLocal<Boolean> WRITING = new ThreadLocal<>();

    private final DataSource dataSource;
    private final JdbcTemplate jdbc;
    private final int retentionDays;

    public DbLogAppender(DataSource dataSource, JdbcTemplate jdbc,
                         @Value("${app.logs.retention-days:30}") int retentionDays) {
        this.dataSource = dataSource;
        this.jdbc = jdbc;
        this.retentionDays = retentionDays;
    }

    @EventListener(ApplicationReadyEvent.class)
    void attach() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        setContext(ctx);
        setName("db");
        start();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(this);
    }

    @PreDestroy
    void detach() {
        LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(this);
        stop();
    }

    @Override
    protected void append(ILoggingEvent e) {
        boolean keep = e.getLevel().isGreaterOrEqual(Level.WARN)
                || (e.getLevel() == Level.INFO && AUDIT.contains(e.getLoggerName()));
        if (!keep || WRITING.get() != null) return;
        WRITING.set(true);
        // conexión propia en autocommit: fuera de la transacción del request —
        // si el negocio hace rollback (justo cuando hay error) el log sobrevive,
        // y no choca con transacciones readOnly
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     insert into app_logs (created_at, level, logger, message, stack,
                         request_id, user_id, client_id, store_id, role)
                     values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")) {
            c.setAutoCommit(true);
            Map<String, String> mdc = e.getMDCPropertyMap();
            String logger = e.getLoggerName();
            ps.setTimestamp(1, new Timestamp(e.getTimeStamp()));
            ps.setString(2, e.getLevel().toString());
            ps.setString(3, logger.length() > 120 ? logger.substring(logger.length() - 120) : logger);
            ps.setString(4, e.getFormattedMessage());
            ps.setString(5, e.getThrowableProxy() == null ? null : ThrowableProxyUtil.asString(e.getThrowableProxy()));
            ps.setString(6, mdc.get("requestId"));
            ps.setObject(7, toLong(mdc.get("userId")));
            ps.setObject(8, toLong(mdc.get("clientId")));
            ps.setObject(9, toLong(mdc.get("storeId")));
            ps.setString(10, mdc.get("role"));
            ps.executeUpdate();
        } catch (Exception ignored) {
            // nunca romper el request por un log — la consola sigue teniendo la línea
        } finally {
            WRITING.remove();
        }
    }

    @Scheduled(cron = "0 15 4 * * *")
    void purge() {
        jdbc.update("delete from app_logs where created_at < ?",
                Timestamp.from(Instant.now().minus(Duration.ofDays(retentionDays))));
    }

    private static Long toLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
