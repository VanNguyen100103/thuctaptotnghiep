package com.ut.edu.backend.store;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Owner requests a paid plan. FREE_TRIAL is rejected in the service -
 * it isn't purchasable, only ever granted at store registration.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubscribeRequest {

    @NotNull(message = "Plan is required")
    private SubscriptionPlan plan;
}
