package nn;

/*******************************************************************************
 * Copyright (c) 2015-2018 Skymind, Inc.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 ******************************************************************************/



import lombok.extern.slf4j.Slf4j;
import org.nd4j.linalg.api.buffer.DataType;
import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.factory.Nd4j;

import java.util.concurrent.atomic.AtomicLong;
import org.nd4j.linalg.api.ndarray.ShapeInfoProvider;

/**
 * @author raver119@gmail.com
 */
@Slf4j
public abstract class BaseShapeInfoProvider implements ShapeInfoProvider {
    protected AtomicLong bytes = new AtomicLong(0);

    /**
     * This method creates shapeInformation buffer, based on shape being passed in
     *
     * @param shape
     * @return
     */
    @Override
    public Pair<DataBuffer, long[]> createShapeInformation(long[] shape, DataType dataType) {
        char order = Nd4j.order();

        return createShapeInformation(shape, order, dataType);
    }

    /**
     * This method creates shapeInformation buffer, based on shape & order being passed in
     *
     * @param shape
     * @param order
     * @return
     */
    @Override
    public Pair<DataBuffer, long[]> createShapeInformation(long[] shape, char order, DataType dataType) {
        long[] stride = Nd4j.getStrides(shape, order);

        // this won't be view, so ews is 1
        int ews = 1;

        return createShapeInformation(shape, stride, ews, order, dataType, false);
    }

    @Override
    public Pair<DataBuffer, long[]> createShapeInformation(long[] shape, long[] stride, long elementWiseStride, char order, DataType dataType, boolean empty) {
        DataBuffer buffer = Shape.createShapeInformation(shape, stride, elementWiseStride, order, dataType, empty);
        buffer.setConstant(true);
        return Pair.create(buffer, buffer.asLong());
    }

    @Override
    public Pair<DataBuffer, long[]> createShapeInformation(long[] shape, long[] stride, long elementWiseStride, char order, long extras) {
        DataBuffer buffer = Shape.createShapeInformation(shape, stride, elementWiseStride, order, extras);
        buffer.setConstant(true);
        return Pair.create(buffer, buffer.asLong());
    }


    @Override
    public long getCachedBytes() {
        return bytes.get();
    }
}
