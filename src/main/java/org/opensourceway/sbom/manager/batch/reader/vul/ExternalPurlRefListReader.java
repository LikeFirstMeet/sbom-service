package org.opensourceway.sbom.manager.batch.reader.vul;

import org.apache.commons.collections4.ListUtils;
import org.jetbrains.annotations.NotNull;
import org.openeuler.sbom.manager.dao.SbomRepository;
import org.openeuler.sbom.manager.model.ExternalPurlRef;
import org.openeuler.sbom.manager.model.Package;
import org.openeuler.sbom.manager.model.Sbom;
import org.openeuler.sbom.manager.service.vul.VulService;
import org.opensourceway.sbom.constants.BatchContextConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ExternalPurlRefListReader implements ItemReader<List<ExternalPurlRef>>, StepExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(ExternalPurlRefListReader.class);

    @Autowired
    SbomRepository sbomRepository;

    private Iterator<List<ExternalPurlRef>> iterator = null;

    private StepExecution stepExecution;

    private ExecutionContext jobContext;

    private final VulService vulService;

    public ExternalPurlRefListReader(VulService vulService) {
        this.vulService = vulService;
    }

    public VulService getVulService() {
        return vulService;
    }

    private void initMapper() {
        if (!getVulService().needRequest()) {
            logger.warn("vul client does not request");
            return;
        }

        UUID sbomId = this.jobContext.containsKey(BatchContextConstants.BATCH_SBOM_ID_KEY) ?
                (UUID) this.jobContext.get(BatchContextConstants.BATCH_SBOM_ID_KEY) : null;

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
                .toList();

        List<List<ExternalPurlRef>> chunks = ListUtils.partition(externalPurlRefs, getVulService().getBulkRequestSize());
        this.iterator = chunks.iterator();
        logger.info("ExternalPurlRefListReader use sbomId:{}, get externalPurlRefs size:{}, chunks size:{}",
                sbomId,
                externalPurlRefs.size(),
                chunks.size());
    }

    @Nullable
    @Override
    public List<ExternalPurlRef> read() {
        if (iterator == null) {
            initMapper();
        }
        logger.info("start ExternalPurlRefListReader");

        if (iterator != null && iterator.hasNext())
            return iterator.next();
        else
            return null; // end of data
    }

    @Override
    public void beforeStep(@NotNull StepExecution stepExecution) {
        this.stepExecution = stepExecution;
        this.jobContext = this.stepExecution.getJobExecution().getExecutionContext();
        this.iterator = null;
    }

    @Override
    public ExitStatus afterStep(@NotNull StepExecution stepExecution) {
        return null;
    }

}