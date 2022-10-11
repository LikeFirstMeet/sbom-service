package org.opensourceway.sbom.manager.batch.reader.license;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.opensourceway.sbom.clients.license.LicenseClient;
import org.opensourceway.sbom.clients.license.vo.LicenseNameAndUrl;
import org.opensourceway.sbom.constants.BatchContextConstants;
import org.opensourceway.sbom.manager.dao.SbomRepository;
import org.opensourceway.sbom.manager.model.ExternalPurlRef;
import org.opensourceway.sbom.manager.model.Package;
import org.opensourceway.sbom.manager.model.Sbom;
import org.opensourceway.sbom.manager.service.license.LicenseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ChunkListener;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.item.Chunk;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ExternalPurlRefListReader implements ItemReader<List<ExternalPurlRef>>, StepExecutionListener, ChunkListener {

    private static final Logger logger = LoggerFactory.getLogger(ExternalPurlRefListReader.class);

    private final LicenseService licenseService;

    @Autowired
    SbomRepository sbomRepository;

    @Autowired
    private LicenseClient licenseClient;

    private List<List<ExternalPurlRef>> chunks;

    private StepExecution stepExecution;

    private ExecutionContext jobContext;

    private ChunkContext chunkContext;

    public ExternalPurlRefListReader(LicenseService licenseService) {
        this.licenseService = licenseService;
    }

    public LicenseService getLicenseService() {
        return licenseService;
    }

    private void initMapper(UUID sbomId) {
        if (!getLicenseService().needRequest()) {
            logger.warn("license client does not request");
            return;
        }

        if (sbomId == null) {
            logger.warn("sbom id is mull");
            return;
        }
        Optional<Sbom> sbomOptional = sbomRepository.findById(sbomId);
        if (sbomOptional.isEmpty()) {
            logger.error("sbomId:{} is not exists", sbomId);
            return;
        }

        List<ExternalPurlRef> externalPurlRefs = sbomOptional.get().getPackages().stream()
                .map(Package::getExternalPurlRefs)
                .flatMap(List::stream)
                .filter(externalPurlRef -> externalPurlRef.getCategory().equals("PACKAGE_MANAGER"))
                .toList();

        this.chunks = ListUtils.partition(externalPurlRefs, getLicenseService().getBulkRequestSize())
                .stream()
                .map(ArrayList::new)// can't use ArrayList.subList(can't restart)
                .collect(Collectors.toCollection(CopyOnWriteArrayList::new));// can't use unmodifiableList(can't remove)

        // restore chunks to previous operator, then partial retry
        int remainingSize = stepExecution.getExecutionContext().getInt(BatchContextConstants.BATCH_READER_STEP_REMAINING_SIZE_KEY, 0);
        if (remainingSize > 0 && remainingSize < this.chunks.size()) {
            this.chunks = this.chunks.subList(this.chunks.size() - remainingSize, this.chunks.size());
        }

        Map<String, LicenseNameAndUrl> licenseInfoMap;
        // TODO move map to service
        licenseInfoMap = licenseClient.getLicensesInfo();
        jobContext.put(BatchContextConstants.BATCH_LICENSE_INFO_MAP, licenseInfoMap);

        logger.info("ExternalPurlRefListReader:{} use sbomId:{}, get externalPurlRefs size:{}, chunks size:{}",
                this,
                sbomId,
                externalPurlRefs.size(),
                this.chunks.size());
    }

    @Nullable
    @Override
    public List<ExternalPurlRef> read() {
        UUID sbomId = this.jobContext.containsKey(BatchContextConstants.BATCH_SBOM_ID_KEY) ?
                (UUID) this.jobContext.get(BatchContextConstants.BATCH_SBOM_ID_KEY) : null;
        logger.info("start ExternalPurlRefListReader sbomId:{}", sbomId);
        if (this.chunks == null) {
            initMapper(sbomId);
        }

        if (CollectionUtils.isEmpty(this.chunks)) {
            return null; // end of the chunks loops
        }
        return this.chunks.remove(0);
    }

    @Override
    public void beforeStep(@NotNull StepExecution stepExecution) {
        Assert.isTrue(this.stepExecution == null, "StepExecution is dirty");
        this.stepExecution = stepExecution;
        this.jobContext = this.stepExecution.getJobExecution().getExecutionContext();
    }

    @Override
    public ExitStatus afterStep(@NotNull StepExecution stepExecution) {
        int remainingSize = this.chunks.size();

        if (StringUtils.equals(ExitStatus.FAILED.getExitCode(), stepExecution.getExitStatus().getExitCode())
                && this.chunkContext.hasAttribute(BatchContextConstants.BUILD_IN_BATCH_CHUNK_FAILED_KEY)
                && this.chunkContext.hasAttribute(BatchContextConstants.BUILD_IN_BATCH_CHUNK_FAILED_INPUT_KEY)) {
            Chunk<List<ExternalPurlRef>> retryInputs = (Chunk<List<ExternalPurlRef>>) this.chunkContext.getAttribute(BatchContextConstants.BUILD_IN_BATCH_CHUNK_FAILED_INPUT_KEY);
            assert retryInputs != null;
            remainingSize += CollectionUtils.size(retryInputs.getItems());
            logger.info("restore failed chunks, failed chunks size:{}, first element pkg id:{}",
                    retryInputs.getItems().size(),
                    retryInputs.getItems().get(0).get(0).getPkg().getId());
        }

        stepExecution.getExecutionContext().putInt(BatchContextConstants.BATCH_READER_STEP_REMAINING_SIZE_KEY, remainingSize);
        return null;
    }

    @Override
    public void beforeChunk(@NotNull ChunkContext chunkContext) {
        if (this.chunkContext == null) {
            this.chunkContext = chunkContext;
        } else {
            Assert.isTrue(StringUtils.equals(this.chunkContext.getStepContext().getId(), chunkContext.getStepContext().getId()), "ChunkContext is dirty");
        }
    }

    @Override
    public void afterChunk(@NotNull ChunkContext chunkContext) {
    }

    @Override
    public void afterChunkError(@NotNull ChunkContext chunkContext) {
    }

}
