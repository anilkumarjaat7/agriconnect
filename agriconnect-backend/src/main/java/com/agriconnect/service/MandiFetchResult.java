package com.agriconnect.service;

import com.agriconnect.dto.MandiRecord;

import java.util.List;

/** Immutable result of one paginated read from the government API. */
public record MandiFetchResult(List<MandiRecord> records, int pagesFetched) {
}
