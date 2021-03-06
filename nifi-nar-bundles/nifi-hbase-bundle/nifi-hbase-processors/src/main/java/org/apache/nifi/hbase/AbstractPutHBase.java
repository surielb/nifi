/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.hbase;


import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.components.AllowableValue;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.hbase.put.PutFlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;

/**
 * Base class for processors that put data to HBase.
 */
public abstract class AbstractPutHBase extends AbstractWriteHBase {



    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        final int batchSize = context.getProperty(BATCH_SIZE).asInteger();
        List<FlowFile> flowFiles = session.get(batchSize);
        if (flowFiles == null || flowFiles.size() == 0) {
            return;
        }

        final Map<String,List<PutFlowFile>> tablePuts = new HashMap<>();

        // Group FlowFiles by HBase Table
        for (final FlowFile flowFile : flowFiles) {
            final PutFlowFile putFlowFile = createPut(session, context, flowFile);

            if (putFlowFile == null) {
                // sub-classes should log appropriate error messages before returning null
                session.transfer(flowFile, REL_FAILURE);
            } else if (!putFlowFile.isValid()) {
                if (StringUtils.isBlank(putFlowFile.getTableName())) {
                    getLogger().error("Missing table name for FlowFile {}; routing to failure", new Object[]{flowFile});
                } else if (null == putFlowFile.getRow()) {
                    getLogger().error("Missing row id for FlowFile {}; routing to failure", new Object[]{flowFile});
                } else if (putFlowFile.getColumns() == null || putFlowFile.getColumns().isEmpty()) {
                    getLogger().error("No columns provided for FlowFile {}; routing to failure", new Object[]{flowFile});
                } else {
                    // really shouldn't get here, but just in case
                    getLogger().error("Failed to produce a put for FlowFile {}; routing to failure", new Object[]{flowFile});
                }
                session.transfer(flowFile, REL_FAILURE);
            } else {
                List<PutFlowFile> putFlowFiles = tablePuts.get(putFlowFile.getTableName());
                if (putFlowFiles == null) {
                    putFlowFiles = new ArrayList<>();
                    tablePuts.put(putFlowFile.getTableName(), putFlowFiles);
                }
                putFlowFiles.add(putFlowFile);
            }
        }

        getLogger().debug("Sending {} FlowFiles to HBase in {} put operations", new Object[]{flowFiles.size(), tablePuts.size()});

        final long start = System.nanoTime();
        final List<PutFlowFile> successes = new ArrayList<>();

        for (Map.Entry<String, List<PutFlowFile>> entry : tablePuts.entrySet()) {
            try {
                clientService.put(entry.getKey(), entry.getValue());
                successes.addAll(entry.getValue());
            } catch (Exception e) {
                getLogger().error(e.getMessage(), e);

                for (PutFlowFile putFlowFile : entry.getValue()) {
                    getLogger().error("Failed to send {} to HBase due to {}; routing to failure", new Object[]{putFlowFile.getFlowFile(), e});
                    final FlowFile failure = session.penalize(putFlowFile.getFlowFile());
                    session.transfer(failure, REL_FAILURE);
                }
            }
        }

        final long sendMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        getLogger().debug("Sent {} FlowFiles to HBase successfully in {} milliseconds", new Object[]{successes.size(), sendMillis});

        for (PutFlowFile putFlowFile : successes) {
            session.transfer(putFlowFile.getFlowFile(), REL_SUCCESS);
            final String details = "Put " + putFlowFile.getColumns().size() + " cells to HBase";
            session.getProvenanceReporter().send(putFlowFile.getFlowFile(), getTransitUri(putFlowFile), details, sendMillis);
        }

    }


    protected String getTransitUri(PutFlowFile actionFlowFile) {
        return "hbase://" + actionFlowFile.getTableName() + "/" + new String(actionFlowFile.getRow(), StandardCharsets.UTF_8);
    }

    /**
     * Sub-classes provide the implementation to create a put from a FlowFile.
     *
     * @param session
     *              the current session
     * @param context
     *              the current context
     * @param flowFile
     *              the FlowFile to create a Put from
     *
     * @return a PutFlowFile instance for the given FlowFile
     */
    protected abstract PutFlowFile createPut(final ProcessSession session, final ProcessContext context, final FlowFile flowFile);

}
