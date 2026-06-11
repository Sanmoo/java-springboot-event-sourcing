package com.sanmoo.eventsourcing.creditaccount.core.projection;

import com.sanmoo.eventsourcing.creditaccount.core.port.model.CreditAccountSummary;

public record ProjectionTick(CreditAccountSummary summary, boolean applied) {}
