package org.opensourceway.sbom.manager.service.license;

import org.apache.commons.lang3.tuple.Pair;
import org.opensourceway.sbom.clients.license.vo.LicenseNameAndUrl;
import org.opensourceway.sbom.manager.model.ExternalPurlRef;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public interface LicenseService {

    Integer getBulkRequestSize();

    boolean needRequest();

    Set<Pair<ExternalPurlRef, Object>> extractLicenseForPurlRefChunk(UUID sbomId,
                                                                     List<ExternalPurlRef> externalPurlChunk);

    void persistExternalLicenseRefChunk(Set<Pair<ExternalPurlRef, Object>> externalLicenseRefSet, Map<String, LicenseNameAndUrl> licenseInfoMap);

}