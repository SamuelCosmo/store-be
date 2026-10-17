package com.store.platform.auth.dto;

import com.store.platform.store.Store;

/** Tienda elegible al iniciar sesión / visible en terminales. Lleva la
 *  marca propia para que la caja aplique logo y paleta sin otra llamada. */
public record StoreOption(
        Long id,
        String name,
        String logoUrl,
        String primaryColor,
        String accentColor,
        String canvasColor) {

    public static StoreOption from(Store s) {
        return new StoreOption(s.getId(), s.getName(), s.getLogoUrl(),
                s.getPrimaryColor(), s.getAccentColor(), s.getCanvasColor());
    }
}
