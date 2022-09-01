package org.opensourceway.sbom.manager.batch.step;

import org.jetbrains.annotations.NotNull;
import org.openeuler.sbom.manager.dao.RawSbomRepository;
import org.opensourceway.sbom.constants.BatchContextConstants;
import org.opensourceway.sbom.constants.SbomConstants;
import org.opensourceway.sbom.manager.batch.ExecutionContextUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;

import java.util.UUID;

public class ReadSbomEndStep implements Tasklet {

    private static final Logger logger = LoggerFactory.getLogger(ReadSbomEndStep.class);

    @Autowired
    private RawSbomRepository rawSbomRepository;

    @Nullable
    @Override
    public RepeatStatus execute(@NotNull StepContribution contribution, @NotNull ChunkContext chunkContext) {
        ExecutionContext jobContext = ExecutionContextUtils.getJobContext(contribution);
        UUID rawSbomId = (UUID) jobContext.get(BatchContextConstants.BATCH_RAW_SBOM_ID_KEY);
        logger.info("start ReadSbomEndStep rawSbomId:{}", rawSbomId);

        rawSbomRepository.findById(rawSbomId).ifPresent(rawSbom -> {
            rawSbom.setTaskStatus(SbomConstants.TASK_STATUS_FINISH);
            rawSbomRepository.save(rawSbom);
        });

        logger.info("finish ReadSbomEndStep");
        return RepeatStatus.FINISHED;
    }

}