package com.sanmoo.eventsourcing.creditaccount.application.result;

import com.sanmoo.eventsourcing.creditaccount.domain.model.*;
import java.util.List;
import java.util.Map;

public record CreditAccountResult(
    String creditAccountId,
    boolean opened,
    String creditLimit,
    String outstandingBalance,
    String authorizedAmount,
    String availableLimit,
    List<Map<String, Object>> authorizations
) {}
