package com.store.platform.user.dto;

import com.store.platform.store.Store;
import com.store.platform.user.User;

import java.util.List;

public record UserResponse(
        Long id,
        Long clientId,
        String name,
        String email,
        User.Role role,
        List<Long> storeIds) {

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getClient().getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getStores().stream().map(Store::getId).sorted().toList());
    }
}
