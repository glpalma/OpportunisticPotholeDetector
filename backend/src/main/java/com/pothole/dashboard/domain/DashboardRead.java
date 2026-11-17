package com.pothole.dashboard.domain;

import java.io.Writer;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DashboardRead {

    int MAP_LIMIT = 3000;

    List<MapPoint> map(PotholeCriteria criteria);

    PotholePage list(PotholeCriteria criteria);

    Optional<PotholeDetail> detail(UUID id, Instant neglectCutoff);

    Optional<Path> image(UUID sightingId, boolean thumbnail);

    List<ReviewCandidate> nextUnreviewed(int limit, Instant afterCapturedAt, UUID afterId, Instant neglectCutoff);

    boolean setReview(UUID sightingId, String verdict, Instant reviewedAt);

    DashboardStats stats(Instant neglectCutoff);

    FleetStatus fleet();

    void writeSightingsCsv(PotholeCriteria criteria, Writer writer);
}
