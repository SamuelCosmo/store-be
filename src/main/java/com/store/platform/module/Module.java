package com.store.platform.module;

/** Catálogo de módulos contratables del tenant (docs/13-modules-and-permissions).
 *  client_modules = lo que la plataforma vendió; user_modules ⊆ client_modules
 *  = lo que el ADMIN asignó a cada usuario. */
public enum Module {
    CATALOG,
    INVENTORY,
    POS,
    KIOSK,
    KDS,
    BAR,
    TOKENS,
    REPORTS,
    TICKETS,
    RESERVATIONS
}
