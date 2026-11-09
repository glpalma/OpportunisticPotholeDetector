package com.pothole.sighting.data;

import java.util.function.Supplier;

import com.pothole.sighting.domain.IngestTransaction;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class SpringIngestTransaction implements IngestTransaction {

    private final TransactionTemplate transactions;

    SpringIngestTransaction(TransactionTemplate transactions) {
        this.transactions = transactions;
    }

    @Override
    public <T> T execute(Supplier<T> work) {
        return transactions.execute(status -> work.get());
    }
}
