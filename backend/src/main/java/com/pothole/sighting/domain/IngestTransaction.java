package com.pothole.sighting.domain;

import java.util.function.Supplier;

public interface IngestTransaction {

    <T> T execute(Supplier<T> work);
}
