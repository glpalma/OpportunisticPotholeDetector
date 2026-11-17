package com.pothole.dashboard.domain;

import java.util.List;

public record PotholePage(int page, int pageSize, long total, List<PotholeSummary> items) {
}
