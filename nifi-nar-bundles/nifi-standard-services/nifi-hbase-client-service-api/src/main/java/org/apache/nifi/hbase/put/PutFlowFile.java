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
package org.apache.nifi.hbase.put;

import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.hbase.AbstractActionFlowFile;

import java.util.Collection;

/**
 * Wrapper to encapsulate all of the information for the Put along with the FlowFile.
 */
public class PutFlowFile extends AbstractActionFlowFile {


    private final Collection<PutColumn> columns;


    public PutFlowFile(String tableName, byte[] row, Collection<PutColumn> columns, FlowFile flowFile) {
        super(tableName, row, flowFile);
        this.columns = columns;

    }

    public boolean isValid() {
        if (!super.isValid() || columns == null || columns.isEmpty()) {
            return false;
        }

        for (PutColumn column : columns) {
            if (null == column.getColumnQualifier() || null == column.getColumnFamily() || column.getBuffer() == null) {
                return false;
            }
        }

        return true;
    }

    public Collection<PutColumn> getColumns() {
        return columns;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PutFlowFile) {
            PutFlowFile pff = (PutFlowFile)obj;
            return super.equals(obj)
                    && this.columns.equals(pff.columns);
        } else {
            return false;
        }
    }
}
