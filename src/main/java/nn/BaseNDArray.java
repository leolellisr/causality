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

package nn;


import org.nd4j.shade.guava.primitives.Ints;
import org.nd4j.shade.guava.primitives.Longs;
import com.google.flatbuffers.FlatBufferBuilder;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.math3.util.FastMath;
import org.nd4j.autodiff.samediff.serde.FlatBuffersMapper;
import org.nd4j.common.base.Preconditions;
import org.nd4j.graph.ByteOrder;
import org.nd4j.graph.FlatArray;
import org.nd4j.linalg.api.blas.BlasBufferUtil;
import org.nd4j.linalg.api.blas.params.MMulTranspose;
import org.nd4j.linalg.api.buffer.*;
import org.nd4j.linalg.api.iter.FirstAxisIterator;
import org.nd4j.linalg.api.iter.NdIndexIterator;
import org.nd4j.linalg.api.memory.MemoryWorkspace;
import org.nd4j.linalg.api.ops.CustomOp;
import org.nd4j.linalg.api.ops.DynamicCustomOp;
import org.nd4j.linalg.api.ops.impl.reduce.HashCode;
import org.nd4j.linalg.api.ops.impl.reduce.bool.All;
import org.nd4j.linalg.api.ops.impl.reduce.bool.Any;
import org.nd4j.linalg.api.ops.impl.reduce.floating.*;
import org.nd4j.linalg.api.ops.impl.reduce.same.*;
import org.nd4j.linalg.api.ops.impl.reduce3.EqualsWithEps;
import org.nd4j.linalg.api.ops.impl.reduce3.EuclideanDistance;
import org.nd4j.linalg.api.ops.impl.reduce3.ManhattanDistance;
import org.nd4j.linalg.api.ops.impl.reduce.longer.MatchCondition;
import org.nd4j.linalg.api.ops.impl.reduce.same.Max;
import org.nd4j.linalg.api.ops.impl.reduce.same.Min;
import org.nd4j.linalg.api.ops.impl.broadcast.*;
import org.nd4j.linalg.api.ops.impl.controlflow.Where;
import org.nd4j.linalg.api.ops.impl.scalar.*;
import org.nd4j.linalg.api.ops.impl.scalar.comparison.*;
import org.nd4j.linalg.api.ops.impl.shape.Tile;
import org.nd4j.linalg.api.ops.impl.summarystats.StandardDeviation;
import org.nd4j.linalg.api.ops.impl.summarystats.Variance;
import org.nd4j.linalg.api.ops.impl.transforms.custom.Assign;
import org.nd4j.linalg.api.ops.impl.transforms.bool.MatchConditionTransform;
import org.nd4j.linalg.api.ops.impl.transforms.custom.EqualTo;
import org.nd4j.linalg.api.ops.impl.transforms.custom.GreaterThan;
import org.nd4j.linalg.api.ops.impl.transforms.custom.LessThan;
import org.nd4j.linalg.api.ops.impl.transforms.custom.NotEqualTo;
import org.nd4j.linalg.api.ops.impl.transforms.same.Negative;
import org.nd4j.linalg.api.ops.impl.transforms.pairwise.arithmetic.*;
import org.nd4j.linalg.api.ops.impl.transforms.comparison.*;
import org.nd4j.linalg.api.ops.performance.PerformanceTracker;
import org.nd4j.linalg.api.shape.LongShapeDescriptor;
import org.nd4j.linalg.api.shape.Shape;
import org.nd4j.linalg.api.shape.options.ArrayOptionsHelper;
import org.nd4j.linalg.exception.*;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.*;
import org.nd4j.linalg.indexing.conditions.Condition;
import org.nd4j.linalg.indexing.conditions.Conditions;
import org.nd4j.linalg.api.memory.MemcpyDirection;
import org.nd4j.common.primitives.Pair;
import org.nd4j.linalg.string.NDArrayStrings;
import org.nd4j.common.util.ArrayUtil;
import org.nd4j.linalg.util.LinAlgExceptions;
import org.nd4j.linalg.util.NDArrayMath;
import org.nd4j.linalg.workspace.WorkspaceUtils;

import java.io.*;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.nd4j.common.primitives.Triple;
import static org.nd4j.linalg.api.buffer.DataType.BFLOAT16;
import static org.nd4j.linalg.api.buffer.DataType.BOOL;
import static org.nd4j.linalg.api.buffer.DataType.BYTE;
import static org.nd4j.linalg.api.buffer.DataType.COMPRESSED;
import static org.nd4j.linalg.api.buffer.DataType.DOUBLE;
import static org.nd4j.linalg.api.buffer.DataType.FLOAT;
import static org.nd4j.linalg.api.buffer.DataType.HALF;
import static org.nd4j.linalg.api.buffer.DataType.INT;
import static org.nd4j.linalg.api.buffer.DataType.LONG;
import static org.nd4j.linalg.api.buffer.DataType.SHORT;
import static org.nd4j.linalg.api.buffer.DataType.UBYTE;
import static org.nd4j.linalg.api.buffer.DataType.UINT16;
import static org.nd4j.linalg.api.buffer.DataType.UINT32;
import static org.nd4j.linalg.api.buffer.DataType.UINT64;
import static org.nd4j.linalg.api.buffer.DataType.UNKNOWN;
import static org.nd4j.linalg.api.buffer.DataType.UTF8;
import org.nd4j.linalg.api.ndarray.INDArray;
import static org.nd4j.linalg.factory.Nd4j.*;


/**
 * NDArray: (think numpy)
 * <p/>
 * A few things of note.
 * <p/>
 * An NDArray can have any number of dimensions.
 * <p/>
 * An NDArray is accessed via strides.
 * <p/>
 * Strides are how to index over
 * a contiguous block of data.
 * <p/>
 * This block of data has 2 orders(as of right now):
 * fortran and c
 *
 * @author Adam Gibson
 */
@Slf4j
public abstract class BaseNDArray implements INDArray, Iterable {

    private static final long serialVersionUID = 3285982317165542614L;

    protected transient volatile DataBuffer shapeInformation;
    protected transient volatile DataBuffer data;
    //protected transient DataBuffer shape;
    //protected transient DataBuffer stride;
    protected transient boolean compressed = false;

    protected transient boolean released = false;

    // this field holds jvm copy of shapeInfo
    protected transient JvmShapeInfo jvmShapeInfo;


    private static final AtomicLong arrayCounter = new AtomicLong(0);
    protected transient final long arrayId = arrayCounter.getAndIncrement();


    //Precalculate these arrays (like [3,2,1,0], [2,1,0], [1,0], [0] etc) for use in TAD, to avoid creating same int[]s over and over
    private static final int[][] tadFinalPermuteDimensions;
    static {
        tadFinalPermuteDimensions = new int[32][0];
        tadFinalPermuteDimensions[1] = new int[] {1, 0}; //Edge case for 1d tensors: selectively apply to column vectors
        for (int i = 2; i < 32; i++) {
            tadFinalPermuteDimensions[i] = new int[i];
            for (int k = i - 1, j = 0; k >= 0; k--, j++)
                tadFinalPermuteDimensions[i][j] = k;
        }
        int t =1;
    }

    public BaseNDArray() {
    }

    @Override
    public boolean isCompressed() {
        return compressed;
    }

    @Override
    public void markAsCompressed(boolean reallyCompressed) {
        this.compressed = reallyCompressed;
    }

    /**
     *
     * @param buffer
     */
    public BaseNDArray(DataBuffer buffer) {
        this.data = buffer;
        if (buffer.length() >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Length of buffer can not be >= Integer.MAX_VALUE");
        long[] shape = {1, (int) buffer.length()};
        long[] stride = Nd4j.getStrides(shape);
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,  1, Nd4j.order(), buffer.dataType(), false));
        init(shape, stride);
    }

    /**
     *
     * @param buffer
     * @param shape
     * @param stride
     * @param offset
     * @param ordering
     */
    public BaseNDArray(DataBuffer buffer, int[] shape, int[] stride, long offset, char ordering) {
        Shape.assertValidOrder(ordering);
        this.data = offset > 0 ? Nd4j.createBuffer(buffer, offset, Shape.lengthOfBuffer(shape, stride)) : buffer;
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(ArrayUtil.toLongArray(shape), ArrayUtil.toLongArray(stride),
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, buffer.dataType(), false));
        init(shape, stride);
        // Shape.setElementWiseStride(this.shapeInfo(),Shape.elementWiseStride(shape, stride, ordering == 'f'));

    }

    public BaseNDArray(DataBuffer buffer, long[] shape, long[] stride, long offset, char ordering) {
        this(buffer, shape, stride, offset, Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering);
    }

    public BaseNDArray(DataBuffer buffer, long[] shape, long[] stride, long offset, long ews, char ordering) {
        Shape.assertValidOrder(ordering);
        this.data = offset > 0 ? Nd4j.createBuffer(buffer, offset, Shape.lengthOfBuffer(shape, stride)) : buffer;
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride, ews, ordering, buffer.dataType(), false ));
        init(shape, stride);
        // Shape.setElementWiseStride(this.shapeInfo(),Shape.elementWiseStride(shape, stride, ordering == 'f'));
    }

    public BaseNDArray(DataBuffer buffer, long[] shape, long[] stride, long offset,  char ordering, DataType dataType) {
        this(buffer, shape, stride, offset, Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, dataType);
    }

    public BaseNDArray(DataBuffer buffer, long[] shape, long[] stride, long offset, long ews, char ordering, DataType dataType) {
        this.data = offset > 0 ? Nd4j.createBuffer(buffer, offset, Shape.lengthOfBuffer(shape, stride)) : buffer;
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride, ews, ordering, dataType, false));
        init(shape, stride);
    }

    public BaseNDArray(DataBuffer buffer, long[] shape, long[] stride, char ordering, DataType type) {
        this.data = buffer;
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, type, false));
        init(shape, stride);
        // Shape.setElementWiseStride(this.shapeInfo(),Shape.elementWiseStride(shape, stride, ordering == 'f'));
    }

    public BaseNDArray(DataBuffer buffer, long[] shape, long[] stride, char ordering, DataType type, MemoryWorkspace workspace) {
        this.data = buffer;
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, type, false));
        init(shape, stride);
        // Shape.setElementWiseStride(this.shapeInfo(),Shape.elementWiseStride(shape, stride, ordering == 'f'));
    }

    public BaseNDArray(DataBuffer buffer,  DataType dataType, long[] shape, long[] stride, long offset, char ordering) {
        this.data = offset > 0 ? Nd4j.createBuffer(buffer, offset, Shape.lengthOfBuffer(shape, stride)) : buffer;
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, dataType, false));
        init(shape, stride);
        // Shape.setElementWiseStride(this.shapeInfo(),Shape.elementWiseStride(shape, stride, ordering == 'f'));
    }

    /**
     * Initialize the ndarray as a matrix
     * with the given data (indices preserved)
     * @param data
     */
    public BaseNDArray(double[][] data) {
        this(data, Nd4j.order());
    }

    /**
     *
     * @param data
     * @param ordering
     */
    public BaseNDArray(double[][] data, char ordering) {
        this(internalCreateBuffer(ordering == 'c' ? ArrayUtil.flatten(data) : ArrayUtil.flattenF(data)),
                new int[] {data.length, data[0].length},
                Nd4j.getStrides(new int[] {data.length, data[0].length}, ordering), 0, ordering);

        int c = columns();
        for (int r = 0; r < rows(); r++) {
            Preconditions.checkState(data[r].length == c, "data[%s].length=%s must be equal to number of columns %s", r, data[r].length, c );
        }
    }


    /**
     * Create with the specified shape and buffer
     *
     * @param shape  the shape
     * @param buffer the buffer
     */
    public BaseNDArray(int[] shape, DataBuffer buffer) {
        this.data = buffer;
        init(shape, Nd4j.getStrides(shape));
    }

    /**
     * Create this ndarray with the given data and shape and 0 offset
     *
     * @param data  the data to use
     * @param shape the shape of the ndarray
     */
    public BaseNDArray(float[] data, int[] shape, char ordering) {
        this(data, shape, 0, ordering);
    }

    /**
     * @param data     the data to use
     * @param shape    the shape of the ndarray
     * @param offset   the desired offset
     * @param ordering the ordering of the ndarray
     */
    public BaseNDArray(float[] data, int[] shape, long offset, char ordering) {
        this(data, shape, Nd4j.getStrides(shape, ordering), offset);
    }

    public BaseNDArray(double[] data, long[] shape, long offset, char ordering) {
        this(data, shape, Nd4j.getStrides(shape, ordering), offset);
    }

    public BaseNDArray(float[] data, long[] shape, long offset, char ordering) {
        this(data, shape, Nd4j.getStrides(shape, ordering), offset);
    }


    /**
     * Construct an ndarray of the specified shape
     * with an empty data array
     *
     * @param shape    the shape of the ndarray
     * @param stride   the stride of the ndarray
     * @param offset   the desired offset
     * @param ordering the ordering of the ndarray
     */
    public BaseNDArray(int[] shape, int[] stride, long offset, char ordering) {
        this(Nd4j.createBuffer(shape.length == 0 ? 1 : ArrayUtil.prodLong(shape)), shape, stride, offset, ordering);
    }

    public BaseNDArray(long[] shape, long[] stride, long offset, char ordering) {
        this(Nd4j.createBuffer(shape.length == 0 ? 1 : ArrayUtil.prodLong(shape)), shape, stride, offset, ordering);
    }

    /**
     * Construct an ndarray of the specified shape.
     *
     * @param shape    the shape of the ndarray
     * @param stride   the stride of the ndarray
     * @param offset   the desired offset
     * @param ordering the ordering of the ndarray
     * @param initialize Whether to initialize the INDArray. If true: initialize. If false: don't.
     */
    public BaseNDArray(int[] shape, int[] stride, long offset, char ordering, boolean initialize) {
        this(Nd4j.createBuffer(shape.length == 0 ? 1 : ArrayUtil.prodLong(shape), initialize), shape, stride, offset, ordering);
    }

    public BaseNDArray(long[] shape, long[] stride, long offset, char ordering, boolean initialize) {
        this(Nd4j.createBuffer(shape.length == 0 ? 1 : ArrayUtil.prodLong(shape), initialize), shape, stride, offset, ordering);
    }

    public BaseNDArray(DataType type, long[] shape, long[] stride, long offset, char ordering, boolean initialize) {
        this(Nd4j.createBuffer(type, shape.length == 0 ? 1 : ArrayUtil.prodLong(shape), initialize), type, shape, stride, offset, ordering);
    }

    public BaseNDArray(DataType type, long[] shape, long[] stride, long offset, char ordering, boolean initialize, MemoryWorkspace workspace) {
        this(Nd4j.createBuffer(type, shape.length == 0 ? 1 : ArrayUtil.prodLong(shape), initialize, workspace), type, shape, stride, offset, ordering);
    }


    /**
     * Create the ndarray with
     * the specified shape and stride and an offset of 0
     *
     * @param shape    the shape of the ndarray
     * @param stride   the stride of the ndarray
     * @param ordering the ordering of the ndarray
     */
    public BaseNDArray(int[] shape, int[] stride, char ordering) {
        this(shape, stride, 0, ordering);
    }


    /**
     *
     * @param shape
     * @param offset
     * @param ordering
     */
    public BaseNDArray(int[] shape, long offset, char ordering) {
        this(shape, Nd4j.getStrides(shape, ordering), offset, ordering);
    }

    public BaseNDArray(long[] shape, long offset, char ordering) {
        this(shape, Nd4j.getStrides(shape, ordering), offset, ordering);
    }


    /**
     * Create an ndarray
     * with the given shape
     * @param shape
     */
    public BaseNDArray(int[] shape) {
        this(shape, 0, Nd4j.order());
    }

    public BaseNDArray(long[] shape) {
        this(shape, 0, Nd4j.order());
    }


    /**
     * Creates a new <i>n</i> times <i>m</i> <tt>DoubleMatrix</tt>.
     *
     * @param newRows    the number of rows (<i>n</i>) of the new matrix.
     * @param newColumns the number of columns (<i>m</i>) of the new matrix.
     */
    public BaseNDArray(int newRows, int newColumns, char ordering) {
        Shape.assertValidOrder(ordering);
        this.data = Nd4j.createBuffer((long) newRows * newColumns);
        long[] shape = new long[] {newRows, newColumns};
        long[] stride = Nd4j.getStrides(shape, ordering);
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, Nd4j.dataType(), false));
        init(shape, stride);
    }

    public BaseNDArray(long newRows, long newColumns, char ordering) {
        Shape.assertValidOrder(ordering);
        this.data = Nd4j.createBuffer((long) newRows * newColumns);
        long[] shape = new long[] {newRows, newColumns};
        long[] stride = Nd4j.getStrides(shape, ordering);
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, Nd4j.dataType(), false));
        init(shape, stride);
    }


    /**
     * Create an ndarray from the specified slices.
     * This will go through and merge all of the
     * data from each slice in to one ndarray
     * which will then take the specified shape
     *
     * @param slices the slices to merge
     * @param shape  the shape of the ndarray
     */
    public BaseNDArray(List<INDArray> slices, int[] shape, char ordering) {
        this(slices, shape, Nd4j.getStrides(shape, ordering), ordering);
    }

    public BaseNDArray(List<INDArray> slices, long[] shape, char ordering) {
        this(slices, shape, Nd4j.getStrides(shape, ordering), ordering);
    }


    /**
     * Create an ndarray from the specified slices.
     * This will go through and merge all of the
     * data from each slice in to one ndarray
     * which will then take the specified shape
     *
     * @param slices the slices to merge
     * @param shape  the shape of the ndarray
     */
    public BaseNDArray(List<INDArray> slices, int[] shape, int[] stride, char ordering) {
        Shape.assertValidOrder(ordering);
        DataBuffer ret = slices.get(0).data().dataType() == (DataType.FLOAT)
                ? Nd4j.createBuffer(new float[ArrayUtil.prod(shape)])
                : Nd4j.createBuffer(new double[ArrayUtil.prod(shape)]);
        this.data = ret;
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(ArrayUtil.toLongArray(shape), ArrayUtil.toLongArray(stride),
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, slices.get(0).dataType(), false));
        init(shape, stride);
        //    Shape.setElementWiseStride(this.shapeInfo(),Shape.elementWiseStride(shape, stride, ordering == 'f'));

        if (slices.get(0).isScalar()) {
            for (int i = 0; i < length(); i++) {
                putScalar(i, slices.get(i).getDouble(0));
            }
        } else {
            for (int i = 0; i < slices(); i++) {
                putSlice(i, slices.get(i));
            }
        }
    }


    public BaseNDArray(List<INDArray> slices, long[] shape, long[] stride, char ordering) {
        DataBuffer ret = Nd4j.createBuffer(slices.get(0).dataType(), Shape.lengthOf(shape), false); /*slices.get(0).data().dataType() == (DataType.FLOAT)
                ? Nd4j.createBuffer(new float[ArrayUtil.prod(shape)])
                : Nd4j.createBuffer(new double[ArrayUtil.prod(shape)]);
                */
        this.data = ret;
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, slices.get(0).dataType(), false));
        init(shape, stride);
        //    Shape.setElementWiseStride(this.shapeInfo(),Shape.elementWiseStride(shape, stride, ordering == 'f'));

        if (slices.get(0).isScalar()) {
            for (int i = 0; i < length(); i++) {
                putScalar(i, slices.get(i).getDouble(0));
            }
        } else {
            for (int i = 0; i < slices(); i++) {
                putSlice(i, slices.get(i));
            }
        }
    }

    /**
     *
     * @param data
     * @param shape
     * @param stride
     * @param ordering
     */
    public BaseNDArray(float[] data, int[] shape, int[] stride, char ordering) {
        this(data, shape, stride, 0, ordering);
    }

    /**
     *
     * @param data
     * @param shape
     * @param stride
     * @param offset
     * @param ordering
     */
    public BaseNDArray(float[] data, int[] shape, int[] stride, long offset, char ordering) {
        Shape.assertValidOrder(ordering);
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(ArrayUtil.toLongArray(shape), ArrayUtil.toLongArray(stride),
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, DataType.FLOAT, data != null && data.length > 0 ? false : true));
        if (data != null && data.length > 0) {

            long perfD = PerformanceTracker.getInstance().helperStartTransaction();

            this.data = internalCreateBuffer(data, offset);

            PerformanceTracker.getInstance().helperRegisterTransaction(0, perfD, data.length * Nd4j.sizeOfDataType(DataType.FLOAT), MemcpyDirection.HOST_TO_HOST);

            if (offset >= data.length)
                throw new IllegalArgumentException("invalid offset: must be < data.length");
        }

        init(shape, stride);
    }

    public BaseNDArray(float[] data, long[] shape, long[] stride, long offset, char ordering) {
        Shape.assertValidOrder(ordering);
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, DataType.FLOAT, data != null && data.length > 0 ? false : true));
        if (data != null && data.length > 0) {
            this.data = Nd4j.createTypedBuffer(data, DataType.FLOAT);
            if (offset >= data.length)
                throw new IllegalArgumentException("invalid offset: must be < data.length");
        }

        init(shape, stride);
    }

    public BaseNDArray(double[] data, long[] shape, long[] stride, long offset, char ordering) {
        Shape.assertValidOrder(ordering);
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape, stride,
                Shape.elementWiseStride(shape, stride, ordering == 'f'), ordering, DataType.DOUBLE, data != null && data.length > 0 ? false : true));
        if (data != null && data.length > 0) {
            this.data = Nd4j.createBuffer(data, offset);
            if (offset >= data.length)
                throw new IllegalArgumentException("invalid offset: must be < data.length");
        }

        init(shape, stride);
    }

    /**
     *
     * @param data
     * @param shape
     * @param stride
     * @param offset
     */
    public BaseNDArray(DataBuffer data, int[] shape, int[] stride, long offset) {
        this.data = Nd4j.createBuffer(data, offset, ArrayUtil.prodLong(shape));
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(ArrayUtil.toLongArray(shape), ArrayUtil.toLongArray(stride),
                Shape.elementWiseStride(shape, stride, Nd4j.order() == 'f'), Nd4j.order(), data.dataType(), false));
        init(shape, stride);
        //  Shape.setElementWiseStride(this.shapeInfo(),Shape.elementWiseStride(shape, stride, Nd4j.order() == 'f'));


    }

    /**
     *
     * @param data
     * @param shape
     * @param strides
     */
    public BaseNDArray(int[] data, int[] shape, int[] strides) {
        this(internalCreateBuffer(data), shape, strides);
    }

    /**
     *
     * @param data
     * @param shape
     */
    public BaseNDArray(DataBuffer data, int[] shape) {
        this(data, shape, Nd4j.getStrides(shape, Nd4j.order()), 0, Nd4j.order());
    }

    public BaseNDArray(DataBuffer data, long[] shape) {
        this(data, shape, Nd4j.getStrides(shape, Nd4j.order()), 0, Nd4j.order());
    }


    /**
     *
     * @param buffer
     * @param shape
     * @param offset
     */
    public BaseNDArray(DataBuffer buffer, int[] shape, long offset) {
        this(Nd4j.createBuffer(buffer, offset, ArrayUtil.prodLong(shape)), shape, Nd4j.getStrides(shape), offset,
                Nd4j.order());
    }

    /**
     *
     * @param buffer
     * @param shape
     * @param ordering
     */
    public BaseNDArray(DataBuffer buffer, int[] shape, char ordering) {
        this(buffer, shape, Nd4j.getStrides(shape, ordering), 0, ordering);
    }

    public BaseNDArray(DataBuffer buffer, long[] shape, char ordering) {
        this(buffer, shape, Nd4j.getStrides(shape, ordering), 0, ordering);
    }

    /**
     *
     * @param data
     * @param shape
     * @param ordering
     */
    public BaseNDArray(double[] data, int[] shape, char ordering) {
        this(Nd4j.createBuffer(data), shape, ordering);
    }

    public BaseNDArray(double[] data, long[] shape, char ordering) {
        this(Nd4j.createBuffer(data), shape, ordering);
    }

    public BaseNDArray(float[] data, long[] shape, char ordering) {
        this(Nd4j.createBuffer(data), shape, ordering);
    }

    /**
     *
     * @param data
     * @param shape
     * @param stride
     * @param offset
     * @param ordering
     */
    public BaseNDArray(double[] data, int[] shape, int[] stride, long offset, char ordering) {
        this(internalCreateBuffer(data, offset), shape, stride, offset, ordering);
    }

    /**
     *
     * @param data
     * @param order
     */
    public BaseNDArray(float[] data, char order) {
        this(internalCreateBuffer(data), order);
    }

    protected static DataBuffer internalCreateBuffer(float[] data) {
        long perfX = PerformanceTracker.getInstance().helperStartTransaction();

        DataBuffer buffer = (DataBuffer) Nd4j.createBuffer(data);
        PerformanceTracker.getInstance().helperRegisterTransaction(0, perfX, data.length * Nd4j.sizeOfDataType(buffer.dataType()), MemcpyDirection.HOST_TO_HOST);

        return (DataBuffer) buffer;
    }

    protected static DataBuffer internalCreateBuffer(double[] data) {
        long perfX = PerformanceTracker.getInstance().helperStartTransaction();

        DataBuffer buffer = Nd4j.createBuffer(data);
        PerformanceTracker.getInstance().helperRegisterTransaction(0, perfX, data.length * Nd4j.sizeOfDataType(buffer.dataType()), MemcpyDirection.HOST_TO_HOST);

        return (DataBuffer) buffer;
    }

    protected static DataBuffer internalCreateBuffer(int[] data) {
        long perfX = PerformanceTracker.getInstance().helperStartTransaction();

        DataBuffer buffer = Nd4j.createBuffer(data);
        PerformanceTracker.getInstance().helperRegisterTransaction(0, perfX, data.length * Nd4j.sizeOfDataType(buffer.dataType()), MemcpyDirection.HOST_TO_HOST);

        return (DataBuffer) buffer;
    }

    protected static DataBuffer internalCreateBuffer(float[] data, long offset) {
        long perfX = PerformanceTracker.getInstance().helperStartTransaction();

        DataBuffer buffer = Nd4j.createBuffer(data, offset);
        PerformanceTracker.getInstance().helperRegisterTransaction(0, perfX, data.length * Nd4j.sizeOfDataType(buffer.dataType()), MemcpyDirection.HOST_TO_HOST);

        return (DataBuffer) buffer;
    }

    protected static DataBuffer internalCreateBuffer(double[] data, long offset) {
        long perfX = PerformanceTracker.getInstance().helperStartTransaction();

        DataBuffer buffer = Nd4j.createBuffer(data, offset);
        PerformanceTracker.getInstance().helperRegisterTransaction(0, perfX, data.length * Nd4j.sizeOfDataType(buffer.dataType()), MemcpyDirection.HOST_TO_HOST);

        return (DataBuffer) buffer;
    }

    /**
     *
     * @param floatBuffer
     * @param order
     */
    public BaseNDArray(DataBuffer floatBuffer, char order) {
        this(floatBuffer, new int[] {(int) floatBuffer.length()},
                Nd4j.getStrides(new int[] {(int) floatBuffer.length()}, order), 0, order);
        Shape.assertValidOrder(order);
        if (floatBuffer.length() >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Length of buffer can not be >= Integer.MAX_VALUE");
    }

    /**
     *
     * @param buffer
     * @param shape
     * @param strides
     */
    public BaseNDArray(DataBuffer buffer, int[] shape, int[] strides) {
        this(buffer, shape, strides, 0, Nd4j.order());
    }


    /**
     * Create this ndarray with the given data and shape and 0 offset
     *
     * @param data  the data to use
     * @param shape the shape of the ndarray
     */
    public BaseNDArray(float[] data, int[] shape) {
        this(data, shape, 0);
    }


    /**
     *
     * @param data
     * @param shape
     * @param offset
     */
    public BaseNDArray(float[] data, int[] shape, long offset) {
        this(data, shape, offset, Nd4j.order());

    }

    /**
     * Construct an ndarray of the specified shape
     * with an empty data array
     *
     * @param shape  the shape of the ndarray
     * @param stride the stride of the ndarray
     * @param offset the desired offset
     */
    public BaseNDArray(int[] shape, int[] stride, long offset) {
        this(new float[ArrayUtil.prod(shape)], shape, stride, offset, Nd4j.order());
    }

    public BaseNDArray(long[] shape, long[] stride, long offset) {
        this(new float[ArrayUtil.prod(shape)], shape, stride, offset, Nd4j.order());
    }

    /**
     * Create the ndarray with
     * the specified shape and stride and an offset of 0
     *
     * @param shape  the shape of the ndarray
     * @param stride the stride of the ndarray
     */
    public BaseNDArray(int[] shape, int[] stride) {
        this(shape, stride, 0);
    }

    /**
     *
     * @param shape
     * @param offset
     */
    public BaseNDArray(int[] shape, long offset) {
        this(shape, Nd4j.getStrides(shape), offset);
    }

    /**
     *
     * @param shape
     * @param ordering
     */
    public BaseNDArray(int[] shape, char ordering) {
        this(shape, 0, ordering);
    }


    /**
     * Creates a new <i>n</i> times <i>m</i> <tt>DoubleMatrix</tt>.
     *
     * @param newRows    the number of rows (<i>n</i>) of the new matrix.
     * @param newColumns the number of columns (<i>m</i>) of the new matrix.
     */
    public BaseNDArray(int newRows, int newColumns) {
        this(newRows, newColumns, Nd4j.order());
    }

    public BaseNDArray(long newRows, long newColumns) {
        this(newRows, newColumns, Nd4j.order());
    }


    /**
     * Create an ndarray from the specified slices.
     * This will go through and merge all of the
     * data from each slice in to one ndarray
     * which will then take the specified shape
     *
     * @param slices the slices to merge
     * @param shape  the shape of the ndarray
     */
    public BaseNDArray(List<INDArray> slices, int[] shape) {
        this(slices, shape, Nd4j.order());
    }

    public BaseNDArray(List<INDArray> slices, long[] shape) {
        this(slices, shape, Nd4j.order());
    }

    /**
     * Create an ndarray from the specified slices.
     * This will go through and merge all of the
     * data from each slice in to one ndarray
     * which will then take the specified shape
     *
     * @param slices the slices to merge
     * @param shape  the shape of the ndarray
     */
    public BaseNDArray(List<INDArray> slices, int[] shape, int[] stride) {
        this(slices, shape, stride, Nd4j.order());
    }

    public BaseNDArray(List<INDArray> slices, long[] shape, long[] stride) {
        this(slices, shape, stride, Nd4j.order());
    }

    /**
     *
     * @param data
     * @param shape
     * @param stride
     */
    public BaseNDArray(float[] data, int[] shape, int[] stride) {
        this(data, shape, stride, Nd4j.order());
    }


    /**
     *
     * @param data
     * @param shape
     * @param stride
     * @param offset
     */
    public BaseNDArray(float[] data, int[] shape, int[] stride, long offset) {
        this(data, shape, stride, offset, Nd4j.order());
    }

    public BaseNDArray(double[] data, long[] shape, long[] stride, long offset) {
        this(data, shape, stride, offset, Nd4j.order());
    }

    public BaseNDArray(float[] data, long[] shape, long[] stride, long offset) {
        this(data, shape, stride, offset, Nd4j.order());
    }

    /**
     *
     * @param data
     */
    public BaseNDArray(float[] data) {
        this(Nd4j.createBuffer(data));
    }


    /**
     * Initialize the ndarray
     * with the given data
     * @param data
     */
    public BaseNDArray(float[][] data) {
        this(data, Nd4j.order());
    }

    /**
     *
     * @param data
     * @param ordering
     */
    public BaseNDArray(float[][] data, char ordering) {
        this(internalCreateBuffer(ordering == 'c' ? ArrayUtil.flatten(data) : ArrayUtil.flattenF(data)),
                new int[] {data.length, data[0].length},
                Nd4j.getStrides(new int[] {data.length, data[0].length}, ordering), 0, ordering);

        int c = columns();
        for (int r = 0; r < rows(); r++) {
            Preconditions.checkState(data[r].length == c, "data[%s].length=%s must be equal to number of columns %s", r, data[r].length, c );
        }
    }



    /**
     * Constructor for stride and offset
     *
     * @param buffer
     * @param shape
     * @param offset
     * @param ordering
     */
    public BaseNDArray(DataBuffer buffer, int[] shape, long offset, char ordering) {
        this(buffer, shape, Nd4j.getStrides(shape, ordering), offset, ordering);
    }

    public BaseNDArray(double[] data, int[] shape, int[] stride, long offset) {
        this(data, shape, stride, offset, Nd4j.order());
    }


    /**
     * Returns whether the ndarray is valid or not
     * @return true if the ndarray is valid
     * false otherwise
     */
    @Deprecated
    public boolean isValid() {
        try {
            linearIndex(length() - 1);
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    protected INDArray create(DataBuffer data, int[] shape, long offset) {
        return (INDArray) Nd4j.create(data, shape, offset);
    }

    @Override
    public int elementWiseStride() {
        return Shape.elementWiseStride(shapeInfoDataBuffer());
    }

    public long tensorsAlongDimension(int... dimension) {
        if (dimension == null || dimension.length == 0)
            throw new IllegalArgumentException("Invalid input: dimensions not specified (null or length 0)");
        if (dimension.length >= rank() || dimension.length == 1 && dimension[0] == Integer.MAX_VALUE)
            return 1;
        for (int i = 0; i < dimension.length; i++)
            if (dimension[i] < 0)
                dimension[i] += rank();
        long[] tensorShape = ArrayUtil.keep(shape(), dimension);
        long len = ArrayUtil.prodLong(tensorShape);
        if (len == 0)
            throw new IllegalStateException("Illegal length found after removing index");
        long length = length();
        if (length / len >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Tensors along dimension can not be >= Integer.MAX_VALUE");
        return length / len;
    }

    public INDArray tensorAlongDimension(long index, int... dimension) {
        if (dimension == null || dimension.length == 0)
            throw new IllegalArgumentException("Invalid input: dimensions not specified (null or length 0)");

        Preconditions.checkArgument(!this.isEmpty(), "tensorAlongDimension(...) can't be used on empty tensors");

        if (dimension.length >= rank()  || dimension.length == 1 && dimension[0] == Integer.MAX_VALUE)
            return this;
        for (int i = 0; i < dimension.length; i++)
            if (dimension[i] < 0)
                dimension[i] += rank();

        //dedup
        if (dimension.length > 1)
            dimension = Ints.toArray(new ArrayList<>(new TreeSet<>(Ints.asList(dimension))));

        if (dimension.length > 1) {
            Arrays.sort(dimension);
        }

        long tads = tensorsAlongDimension(dimension);
        if (index >= tads)
            throw new IllegalArgumentException("Illegal index " + index + " out of tads " + tads);


        if (dimension.length == 1) {
            if (dimension[0] == 0 && isColumnVector()) {
                return this.transpose();
            } else if (dimension[0] == 1 && isRowVector()) {
                return this;
            }
        }

        Pair<DataBuffer, DataBuffer> tadInfo = Nd4j.getExecutioner().getTADManager().getTADOnlyShapeInfo( this, dimension);
        DataBuffer shapeInfo = tadInfo.getFirst();
        long[] jShapeInfo = shapeInfo.asLong();
        long[] shape = Shape.shape((long[])jShapeInfo);
        long[] stride = Shape.stride((long[])jShapeInfo);
        long offset = offset() + tadInfo.getSecond().getLong(index);
        long ews = shapeInfo.getLong(jShapeInfo[0] * 2 + 2);
        var tadOrder = (char) shapeInfo.getInt(jShapeInfo[0] * 2 + 3);
        INDArray toTad = (INDArray) Nd4j.create(data(), shape, stride, offset, ews, tadOrder);
        return (INDArray)  toTad;
    }

    private void setShapeInformation(Pair<DataBuffer, long[]> shapeInfo) {
        this.shapeInformation = shapeInfo.getFirst();
        this.jvmShapeInfo = new JvmShapeInfo(shapeInfo.getSecond());
    }


    private INDArray doTad(int index, int... dimension) {
        if (dimension == null || dimension.length == 0)
            throw new IllegalArgumentException("Invalid input: dimensions not specified (null or length 0)");

        if (dimension.length >= rank())
            return this;
        for (int i = 0; i < dimension.length; i++)
            if (dimension[i] < 0)
                dimension[i] += rank();

        if (dimension.length > 1)
            Arrays.sort(dimension);

        long tads = tensorsAlongDimension(dimension);
        if (index >= tads)
            throw new IllegalArgumentException("Illegal index " + index + " out of tads " + tads);


        if (dimension.length == 1) {
            if (dimension[0] == 0 && isColumnVector()) {
                return this.transpose();
            } else if (dimension[0] == 1 && isRowVector()) {
                return this;
            }
        }


        long[] tensorShape = ArrayUtil.keep(shape(), dimension);
        int[] reverseDimensions = ArrayUtil.reverseCopy(dimension);
        int[] remove = ArrayUtil.removeIndex(ArrayUtil.range(0, rank()), dimension);
        int[] newPermuteDims = Ints.concat(remove, reverseDimensions);
        int[] finalPermuteDims = tadFinalPermuteDimensions[dimension.length];
        
        INDArray permuted = permute(newPermuteDims);
        long sliceIdx = NDArrayMath.sliceOffsetForTensor(index,  permuted, tensorShape);

        INDArray ret2 = permuted.slice(sliceIdx);
        if (dimension.length == tensorShape.length && ArrayUtil.prodLong(tensorShape) == ret2.length()) {
            if (dimension.length == 1 && ret2.isRowVector())
                return ret2;
            if (finalPermuteDims.length != ret2.rank()) {
                finalPermuteDims = new int[ret2.rank()];
                int count = 0;
                for (int i = finalPermuteDims.length - 1; i >= 0; i--)
                    finalPermuteDims[count++] = i;
            }
            long[]  finalPermuteDims_l= new long[finalPermuteDims.length];
            int i = 0;
            for(int dim : finalPermuteDims){
                finalPermuteDims_l[i] = (long) dim;
                i += 1;
            }
            return ret2.permutei(finalPermuteDims);
        }


        int length = ArrayUtil.prod(tensorShape);
        int tensorLength = ArrayUtil.prod(tensorShape);
        long offset = index * tensorLength / NDArrayMath.lengthPerSlice( ret2);

        if (sliceIdx == 0 && length == NDArrayMath.lengthPerSlice(ret2)) {
            if (offset > Integer.MAX_VALUE)
                throw new ND4JArraySizeException();
            ret2 = ret2.slice((int) offset);
            if (dimension.length == 1 && ret2.isRowVectorOrScalar())
                return ret2;
            
            long[]  finalPermuteDims_l= new long[finalPermuteDims.length];
            int i = 0;
            for(int dim : finalPermuteDims){
                finalPermuteDims_l[i] = (long) dim;
                i += 1;
            }
            
            return ret2.permutei(finalPermuteDims);
        }

        else if (length == NDArrayMath.lengthPerSlice( ret2)) {
            offset -= ret2.slices() * (offset / ret2.slices());

            if (offset > Integer.MAX_VALUE)
                throw new ND4JArraySizeException();
            ret2 = ret2.slice((int) offset);
            if (dimension.length == 1 && ret2.isRowVectorOrScalar())
                return ret2;
            
            long[]  finalPermuteDims_l= new long[finalPermuteDims.length];
            int i = 0;
            for(int dim : finalPermuteDims){
                finalPermuteDims_l[i] = (long) dim;
                i += 1;
            }
            
            return ret2.permutei(finalPermuteDims);
        }

        while (ret2.length() > length) {
            sliceIdx = NDArrayMath.sliceOffsetForTensor(index,  ret2, tensorShape);
            sliceIdx -= ret2.slices() * (sliceIdx / ret2.slices());
            ret2 = ret2.slice(sliceIdx);
        }

        if (dimension.length == 1 && ret2.isRowVectorOrScalar())
            return ret2;

        long[]  finalPermuteDims_l= new long[finalPermuteDims.length];
            int i = 0;
            for(int dim : finalPermuteDims){
                finalPermuteDims_l[i] = (long) dim;
                i += 1;
            }
            
        return ret2.permutei(finalPermuteDims);
    }

    @Override
    public long vectorsAlongDimension(int dimension) {
        if (dimension == 0 && isVector() || isRowVectorOrScalar())
            return 1;
        if (size(dimension) == 1 && !isVector()) {
            for (int i = dimension; i < rank(); i++) {
                if (size(i) != 1)
                    return vectorsAlongDimension(i);
            }

            return length();

        } else if (size(0) == 1 && !isVectorOrScalar()) {
            int realDimension = rank() - getLeadingOnes();
            long length = length();
            if (length / size(realDimension) >= Integer.MAX_VALUE)
                throw new IllegalArgumentException("Vectors along dimension can not be >= Integer.MAX_VALUE");
            return length / size(realDimension);
        }

        long length = length();

        if (dimension >= jvmShapeInfo.rank) {
            if (length / size(jvmShapeInfo.rank - 1) >= Integer.MAX_VALUE)
                throw new IllegalArgumentException("Vectors along dimension can not be >= Integer.MAX_VALUE");
            return (int) (length / size(jvmShapeInfo.rank - 1));
        }
        if (length / size(dimension) >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Vectors along dimension can not be >= Integer.MAX_VALUE");
        return length / size(dimension);
    }

    @Override
    public INDArray vectorAlongDimension(int index, int dimension) {
        if (dimension < 0) {
            dimension = jvmShapeInfo.getRank() + dimension;
        }

        //return the whole thing
        if (dimension == jvmShapeInfo.getRank() - 1 && size(dimension) == 1 && rank() > 2
                || rank() > 2 && dimension == 0 && size(dimension) == 1) {
            return this;
        }

        return tensorAlongDimension(index, dimension);
    }

    @Override
    public void setOrder(char order) {
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(shape(), stride(), elementWiseStride(), order, this.dataType(), isEmpty()));
    }

    @Override
    public void setShapeAndStride(int[] shape, int[] stride) {
        setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(ArrayUtil.toLongArray(shape), ArrayUtil.toLongArray(stride),  0, ordering(), this.dataType(), false));
    }

    @Override
    public INDArray cumsumi(int dimension) {
        validateNumericalArray("cumsumi", true);

        if(isScalar() || isEmpty())
            return this;

        if (isVector()) {
            double s = 0.0;
            for (int i = 0; i < length(); i++) {
                s += getDouble(i);
                putScalar(i, s);
            }
        } else if (dimension == Integer.MAX_VALUE) {
            INDArray flattened = ravel();
            double prevVal = flattened.getDouble(0);
            for (int i = 1; i < flattened.length(); i++) {
                double d = prevVal + flattened.getDouble(i);
                flattened.putScalar(i, d);
                prevVal = d;
            }

            return flattened;
        } else {
            for (int i = 0; i < vectorsAlongDimension(dimension); i++) {
                INDArray vec = vectorAlongDimension(i, dimension);
                vec.cumsumi(0);

            }
        }

        return this;
    }

    @Override
    public Number normmaxNumber() {
        return normmax(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number norm2Number() {
        return norm2(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number norm1Number() {
        return norm1(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number stdNumber() {
        return std(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number prodNumber() {
        if(isScalar())
            return getNumber(0);
        return prod(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number meanNumber() {
        validateNumericalArray("meanNumber", false);
        if(isScalar())
            return getNumber(0);
        return mean(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number ameanNumber() {
        return amean(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number varNumber() {
        return var(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number maxNumber() {
        if(isScalar())
            return getNumber(0);
        return max(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number amaxNumber() {
        return amax(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number minNumber() {
        if(isScalar())
            return getNumber(0);
        return min(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number aminNumber() {
        return amin(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number scan(Condition condition) {
        MatchCondition op = new MatchCondition(this, condition);
        return Nd4j.getExecutioner().exec(op).getDouble(0);
    }

    @Override
    public Number sumNumber() {
        validateNumericalArray("sum", false);
        if(isScalar())
            return getNumber(0);
        INDArray scalar = sum(Integer.MAX_VALUE);
        Nd4j.getExecutioner().commit();
        return scalar.getDouble(0);
    }

    @Override
    public Number entropyNumber() {
        return entropy(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number shannonEntropyNumber() {
        return shannonEntropy(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public Number logEntropyNumber() {
        return logEntropy(Integer.MAX_VALUE).getDouble(0);
    }

    @Override
    public INDArray cumsum(int dimension) {
        validateNumericalArray("cumsum", true);
        return dup().cumsumi(dimension);
    }

    @Override
    public INDArray assign(final INDArray arr) {
        Preconditions.checkState((this.isScalar() && arr.isScalar()) || (this.isVector() && arr.isVector()) || Shape.shapeEqualWithSqueeze(this.shape(), arr.shape()),
                "Cannot assign arrays: arrays must both be scalars, both vectors, or shapes must be equal other than size 1 dimensions. Attempting to do x.assign(y)" +
                        " with x.shape=%ndShape and y.shape=%ndShape", this, arr );

        Preconditions.checkArgument(this.length() == arr.length(), "Length of both arrays must be equal");

        Nd4j.getExecutioner().exec(new org.nd4j.linalg.api.ops.impl.transforms.any.Assign(  arr,  this));
        return this;
    }

    @Override
    public INDArray putScalar(long i, double value) {
        Preconditions.checkArgument(dataType() != DataType.BOOL || value == 0.0 || value == 1.0, "Cannot put value %s into boolean array" +
                " - only putScalar with values 0 or 1 is allowed on boolean arrays", value);
        if (i < 0)
            i += rank();

        // TODO: i'm not sure that rank == 1 has fair shortcut here
        if (isScalar()) {
            autoProcessScalarCall();
            data.put(i, value);
            return this;
        } else if (rank() == 1) {
            data.put(i * stride(0), value);
            return this;
        }

        // we cant raise rank here, if original rank is 1
        if (isRowVector() && rank() == 2) {
            return putScalar(0, i, value);
        } else if (isColumnVector() && rank() == 2) {
            return putScalar(i, 0, value);
        }
        long[] indexes = ordering() == 'c' ? Shape.ind2subC(this, i) : Shape.ind2sub(this, i);
        return putScalar(indexes, value);
    }

    @Override
    public INDArray putScalar(long i, float value) {
        return putScalar(i, (double) value);
    }

    @Override
    public INDArray putScalar(long i, int value) {
        return putScalar(i, (double) value);
    }

    @Override
    public INDArray putScalar(int[] indexes, double value) {
        Nd4j.getCompressor().autoDecompress(this);

        Preconditions.checkArgument(dataType() != DataType.BOOL || value == 0.0 || value == 1.0, "Cannot put value %s into boolean array" +
                " - only putScalar with values 0 or 1 is allowed on boolean arrays", value);

        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] < 0)
                indexes[i] += this.size(i);
        }

        if (indexes.length == 1) {
            return putScalar(indexes[0], value);
        } else if (indexes.length == 2) {
            return putScalar(indexes[0], indexes[1], value);
        } else if (indexes.length == 3) {
            return putScalar(indexes[0], indexes[1], indexes[2], value);
        } else if (indexes.length == 4) {
            return putScalar(indexes[0], indexes[1], indexes[2], indexes[3], value);
        } else {
            autoProcessScalarCall();
            long offset = Shape.getOffset(jvmShapeInfo.javaShapeInformation, indexes);
            data.put(offset, value);
        }
        return this;
    }

    @Override
    public INDArray putScalar(long[] indexes, double value) {
        Nd4j.getCompressor().autoDecompress(this);

        Preconditions.checkArgument(dataType() != DataType.BOOL || value == 0.0 || value == 1.0, "Cannot put value %s into boolean array" +
                " - only putScalar with values 0 or 1 is allowed on boolean arrays", value);


        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] < 0)
                indexes[i] += size(i);
        }

        if (indexes.length == 1) {
            return putScalar(indexes[0], value);
        } else if (indexes.length == 2) {
            return putScalar(indexes[0], indexes[1], value);
        } else if (indexes.length == 3) {
            return putScalar(indexes[0], indexes[1], indexes[2], value);
        } else if (indexes.length == 4) {
            return putScalar(indexes[0], indexes[1], indexes[2], indexes[3], value);
        } else {
            autoProcessScalarCall();
            long offset = Shape.getOffset(jvmShapeInfo.javaShapeInformation, indexes);
            data.put(offset, value);
        }
        return this;
    }

    @Override
    public INDArray putScalar(long[] indexes, float value) {
        return putScalar(indexes, (double) value);
    }

    @Override
    public INDArray putScalar(long row, long col, double value) {
        Nd4j.getCompressor().autoDecompress(this);
        autoProcessScalarCall();

        Preconditions.checkArgument(dataType() != DataType.BOOL || value == 0.0 || value == 1.0, "Cannot put value %s into boolean array" +
                " - only putScalar with values 0 or 1 is allowed on boolean arrays", value);

        if (rank() > 2)
            throw new IllegalStateException("Cannot use putScalar(int,int,double) on a rank " + rank() + " INDArray");
        long offset = Shape.getOffsetUnsafe(jvmShapeInfo.javaShapeInformation, row, col);
        data.put(offset, value);
        return this;
    }

    @Override
    public INDArray putScalar(long dim0, long dim1, long dim2, double value) {
        Nd4j.getCompressor().autoDecompress(this);
        autoProcessScalarCall();

        Preconditions.checkArgument(dataType() != DataType.BOOL || value == 0.0 || value == 1.0, "Cannot put value %s into boolean array" +
                " - only putScalar with values 0 or 1 is allowed on boolean arrays", value);

        if (rank() != 3)
            throw new IllegalStateException(
                    "Cannot use putScalar(int,int,int,double) on a rank " + rank() + " INDArray");
        long offset = 0; // Shape.getOffsetUnsafe(javaShapeInformation, dim0, dim1, dim2);
        long size_0 = jvmShapeInfo.javaShapeInformation[1];
        long size_1 = jvmShapeInfo.javaShapeInformation[1 + 1];
        long size_2 = jvmShapeInfo.javaShapeInformation[1 + 2];

        if (size_0 != 1)
            offset += dim0 * jvmShapeInfo.javaShapeInformation[1 + 0 + 3];
        if (size_1 != 1)
            offset += dim1 * jvmShapeInfo.javaShapeInformation[1 + 1 + 3];
        if (size_2 != 1)
            offset += dim2 * jvmShapeInfo.javaShapeInformation[1 + 2 + 3];

        data.put(offset, value);
        return this;
    }

    @Override
    public INDArray putScalar(long dim0, long dim1, long dim2, long dim3, double value) {
        Nd4j.getCompressor().autoDecompress(this);
        autoProcessScalarCall();
        Preconditions.checkArgument(dataType() != DataType.BOOL || value == 0.0 || value == 1.0, "Cannot put value %s into boolean array" +
                " - only putScalar with values 0 or 1 is allowed on boolean arrays", value);

        if (rank() != 4)
            throw new IllegalStateException(
                    "Cannot use putScalar(int,int,int,int,double) on a rank " + rank() + " INDArray");
        long offset = Shape.getOffsetUnsafe(jvmShapeInfo.javaShapeInformation, dim0, dim1, dim2, dim3);
        data.put(offset, value);
        return this;
    }

    @Override
    public INDArray putScalar(int[] indexes, float value) {
        return putScalar(indexes, (double) value);
    }

    @Override
    public INDArray putScalar(int[] indexes, int value) {
        return putScalar(indexes, (double) value);
    }

    @Override
    public INDArray putScalar(long[] indexes, int value) {
        return putScalar(indexes, (double) value);
    }

    @Override
    public INDArray eps(Number other) {
        validateNumericalArray("eps", true);
        return (INDArray) Nd4j.getExecutioner().exec(new ScalarEps( this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), other));
    }

    @Override
    public INDArray eps(INDArray other) {
        validateNumericalArray("eps", true);
        return (INDArray) Nd4j.getExecutioner().exec(new Eps( this,  other, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering())));
    }

    @Override
    public INDArray lt(Number other) {
        validateNumericalArray("less than (lt)", false);
        return (INDArray) Nd4j.getExecutioner().exec(new ScalarLessThan( this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), other));
    }

    @Override
    public INDArray lte(Number other) {
        validateNumericalArray("less than or equals (lte)", false);
        return (INDArray) Nd4j.getExecutioner().exec(new ScalarLessThanOrEqual( this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), other));
    }

    @Override
    public INDArray eq(Number other) {
        Preconditions.checkArgument(dataType() != DataType.BOOL || other.doubleValue() == 0.0 || other.doubleValue() == 1.0, "Scalar equality on boolean arrays can only be applied with values 0 or 1: got value %s",other);
        return (INDArray) Nd4j.getExecutioner().exec(new ScalarEquals( this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), other));
    }

    @Override
    public INDArray gt(Number other) {
        validateNumericalArray("greater than (gt)", false);
        return (INDArray) Nd4j.getExecutioner().exec(new ScalarGreaterThan( this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), other));
    }

    @Override
    public INDArray gte(Number other) {
        validateNumericalArray("greater than or equals (gte)", false);
        return (INDArray) Nd4j.getExecutioner().exec(new ScalarGreaterThanOrEqual( this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), other));
    }

    @Override
    public INDArray lt(INDArray other) {
        validateNumericalArray("less than (lt)", false);
        if (Shape.shapeEquals(this.shape(), other.shape())) {
            return (INDArray) Nd4j.getExecutioner().exec(new LessThan(this,  other, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering())))[0];
        } else if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return Nd4j.exec(new LessThan(new INDArray[]{this, other}, new INDArray[]{Nd4j.createUninitialized(DataType.BOOL, Shape.broadcastOutputShape(this.shape(), other.shape()))}))[0];
        } else
            throw new IllegalArgumentException("Shapes must be broadcastable");
    }

    @Override
    public INDArray neq(Number other) {
        Preconditions.checkArgument(dataType() != DataType.BOOL || other.doubleValue() == 0.0 || other.doubleValue() == 1.0, "Scalar non-equality on boolean arrays can only be applied with values 0 or 1: got value %s",other);
        Preconditions.checkState(!isEmpty(), "Cannot perform operation neq (not equal) on empty array");
        return (INDArray) Nd4j.getExecutioner().exec(new ScalarNotEquals(this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), other));
    }

    @Override
    public INDArray neq(INDArray other) {
        Preconditions.checkState(!isEmpty(), "Cannot perform operation neq (not equal) on empty array");
        return (INDArray) Nd4j.getExecutioner().exec(new NotEqualTo(this,  other, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering())))[0];
    }

    @Override
    public INDArray eq(INDArray other) {
        if (Shape.shapeEquals(this.shape(), other.shape())) {
            return (INDArray) Nd4j.getExecutioner().exec(new EqualTo(this,  other, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering())))[0];
        } else if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return Nd4j.exec(new EqualTo(new INDArray[]{this, other}, new INDArray[]{Nd4j.createUninitialized(DataType.BOOL, Shape.broadcastOutputShape(this.shape(), other.shape()))}))[0];
        } else
            throw new IllegalArgumentException("Shapes must be broadcastable");
    }

    @Override
    public INDArray gt(INDArray other) {
        validateNumericalArray("greater than (gt)", false);
        if (Shape.shapeEquals(this.shape(), other.shape())) {
            return Nd4j.getExecutioner().exec(new GreaterThan( this,  other, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering())))[0];
        } else if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return Nd4j.exec(new GreaterThan(new INDArray[]{this, other}, new INDArray[]{Nd4j.createUninitialized(DataType.BOOL, Shape.broadcastOutputShape(this.shape(), other.shape()))}))[0];
        } else
            throw new IllegalArgumentException("Shapes must be broadcastable");
    }

    @Override
    public INDArray isInfinite(){
        validateNumericalArray("isInfinite", true);
        if(isEmpty())
            return (INDArray) Nd4j.empty(DataType.BOOL);
        return Nd4j.getExecutioner().exec(new MatchConditionTransform(this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), Conditions.isInfinite()));
    }

    @Override
    public INDArray isNaN(){
        validateNumericalArray("isNaN", true);
        if(isEmpty())
            return (INDArray) Nd4j.empty(DataType.BOOL);
        return Nd4j.getExecutioner().exec(new MatchConditionTransform(this, Nd4j.createUninitialized(DataType.BOOL, this.shape(), this.ordering()), Conditions.isNan()));
    }

    @Override
    public INDArray neg() {
        validateNumericalArray("negative (neg)", true);
        if(isEmpty())
            return this;
        return Nd4j.getExecutioner().exec(new Negative(this, Nd4j.createUninitialized(this.dataType(), this.shape(), this.ordering())));
    }

    @Override
    public INDArray negi() {
        validateNumericalArray("negative (negi)", true);
        if(isEmpty())
            return this;
        Nd4j.getExecutioner().exec(new Negative(this));
        return this;
    }

    @Override
    public INDArray rdiv(Number n, INDArray result) {
        return rdivi(n, result);
    }

    @Override
    public INDArray rdivi(Number n, INDArray result) {
        validateNumericalArray("rdivi", false);
        if (Double.isNaN(n.doubleValue()))
            n = Nd4j.EPS_THRESHOLD;
        Nd4j.getExecutioner().exec(new ScalarReverseDivision(this, null, result, n));
        return result;
    }

    @Override
    public INDArray rsub(Number n, INDArray result) {
        return rsubi(n, result);
    }

    @Override
    public INDArray rsubi(Number n, INDArray result) {
        validateNumericalArray("rsubi", false);

        if (Double.isNaN(n.doubleValue()))
            n = Nd4j.EPS_THRESHOLD;

        Nd4j.getExecutioner().exec(new ScalarReverseSubtraction(this, result, n));
        return result;
    }

    @Override
    public INDArray div(Number n, INDArray result) {
        return divi(n, result);
    }

    @Override
    public INDArray divi(Number n, INDArray result) {
        validateNumericalArray("divi", false);

        if (Double.isNaN(n.doubleValue()))
            n = Nd4j.EPS_THRESHOLD;
        Nd4j.getExecutioner().exec(new ScalarDivision((INDArray)this, null,(INDArray) result, n));
        return result;
    }

    @Override
    public INDArray mul(Number n, INDArray result) {
        return muli(n, result);
    }

    @Override
    public INDArray muli(Number n, INDArray result) {
        validateNumericalArray("muli", false);
        if (Double.isNaN(n.doubleValue()))
            n = Nd4j.EPS_THRESHOLD;
        Nd4j.getExecutioner().exec(new ScalarMultiplication((INDArray)this, null, (INDArray)result, n));
        return result;
    }

    @Override
    public INDArray sub(Number n, INDArray result) {
        return subi(n, result);
    }

    @Override
    public INDArray subi(Number n, INDArray result) {
        validateNumericalArray("subi", false);

        if (Double.isNaN(n.doubleValue()))
            n = Nd4j.EPS_THRESHOLD;

        Nd4j.getExecutioner().exec(new ScalarSubtraction((INDArray)this, null, (INDArray) result, n));
        return result;
    }

    @Override
    public INDArray add(Number n, INDArray result) {
        return addi(n, result);
    }

    @Override
    public INDArray addi(Number n, INDArray result) {
        validateNumericalArray("addi", false);
        if (Double.isNaN(n.doubleValue()))
            n = Nd4j.EPS_THRESHOLD;

        Nd4j.getExecutioner().exec(new ScalarAdd((INDArray)this, null, (INDArray)result, n));
        return result;
    }

    @Override
    public INDArray getScalar(long row, long column) {
        return getScalar(new long[] {row, column});
    }

    @Override
    public INDArray dup() {
        return dup(Nd4j.order());
    }

    @Override
    public INDArray dup(char order) {
        WorkspaceUtils.assertValidArray((INDArray)this, "Cannot duplicate INDArray");
        if (this.isCompressed() && this.ordering() == order) {
            INDArray ret = (INDArray) Nd4j.createArrayFromShapeBuffer(data().dup(), this.shapeInfoDataBuffer());
            ret.markAsCompressed(true);
            return ret;
        }
        if(isEmpty())
            return this;

        Nd4j.getCompressor().autoDecompress((INDArray)this);

        // fixme: eventually it would be nice to have this in native code
        if (isS()) {
            Collection<String> list = new ArrayList<String>();
            for (int e = 0; e < this.length(); e++)
                list.add(this.getString(e));

            return Nd4j.create((Collection<String>)list, this.shape(), this.ordering());
        }

        INDArray z = Nd4j.createUninitialized(this.dataType(), this.shape(), order);
        z.assign((INDArray)this);
        return (INDArray) z;
    }

    @Override
    public int getInt(int... indices) {
        return (int) getDouble(indices);
    }

    @Override
    public long getLong(long index) {
        Nd4j.getCompressor().autoDecompress((INDArray)this);
        Preconditions.checkState(!isEmpty(), "Unable to get value from empty array");

        if (index >= length()) {
            throw new IllegalArgumentException("Unable to get linear index " + index + ": values is greater than length (" + length() + ")");
        }

        autoProcessScalarCall();

        if (index == 0)
            return data().getLong(index);

        long[] dimensions = ordering() == 'c' ? Shape.ind2subC((INDArray)this, index) : Shape.ind2sub((INDArray)this, index);
        Shape.assertShapeLessThan(dimensions, shape());
        return getLong(dimensions);
    }

    @Override
    public long getLong(long... indices) {
        if(isScalar())
            return data().getLong(0);
        return Shape.getLong((INDArray)this, indices);
    }

    @Override
    public double getDouble(int... indices) {
        autoProcessScalarCall();
        Nd4j.getCompressor().autoDecompress((INDArray)this);
        Preconditions.checkState(!isEmpty(), "Unable to get value from empty array");

        for (int i = 0; i < indices.length; i++) {
            if (indices[i] < 0)
                indices[i] += rank();
        }
        if (indices.length == 1) {
            if (rank() == 1)
                return Shape.getDouble((INDArray)this, indices[0]);
            else if (isRowVector())
                return Shape.getDouble((INDArray)this, 0, indices[0]);
            else if (isColumnVector())
                return Shape.getDouble((INDArray)this, indices[0], 0);
            else if ((isScalar() || length() == 1) && indices[0] == 0)
                return data().getDouble(0);
        }
        return Shape.getDouble((INDArray)this, indices);
    }

    @Override
    public double getDouble(long... indices) {
        autoProcessScalarCall();
        Nd4j.getCompressor().autoDecompress((INDArray)this);
        Preconditions.checkState(!isEmpty(), "Unable to get value from empty array");

        for (int i = 0; i < indices.length; i++) {
            if (indices[i] < 0)
                indices[i] += rank();
        }
        if (indices.length == 1) {
            if (rank() == 1)
                return Shape.getDouble((INDArray)this, indices[0]);
            else if (isRowVector())
                return Shape.getDouble((INDArray)this, 0, indices[0]);
            else if (isColumnVector())
                return Shape.getDouble((INDArray)this, indices[0], 0);
            else if (isScalar() && indices[0] == 0)
                return data().getDouble(0);
            else
                throw new IllegalStateException("Indexes length must be > 1 for non vectors and scalars");
        }
        return Shape.getDouble((INDArray)this, indices);
    }

    @Override
    public float getFloat(int... indices) {
        return (float) getDouble(indices);
    }

    @Override
    public float getFloat(long... indices) {
        return (float) getDouble(indices);
    }

    @Override
    public boolean isScalar() {
        if (isEmpty())
            return false;

        if (jvmShapeInfo.rank == 0) {
            return true;
        } else if (jvmShapeInfo.rank > 2) {
            return false;
        } else if (jvmShapeInfo.rank == 1) {
            return shape()[0] == 1;
        } else if (jvmShapeInfo.rank == 2) {
            return shape()[0] == 1 && shape()[1] == 1 || length() == 1;
        }

        else
            return false;

    }

    @Override
    public INDArray put(int[] indices, INDArray element) {
        Nd4j.getCompressor().autoDecompress((INDArray)this);
        if (!element.isScalar())
            throw new IllegalArgumentException("Unable to insert anything but a scalar");
        if (isRowVector() && indices[0] == 0 && indices.length == 2) {
            int ix = 0;
            for (int i = 1; i < indices.length; i++)
                ix += indices[i] * stride(i);
            if (ix >= data.length())
                throw new IllegalArgumentException("Illegal indices " + Arrays.toString(indices));
            data.put(ix, element.getDouble(0));
        } else {
            int ix = 0;
            for (int i = 0; i < indices.length; i++)
                if (size(i) != 1)
                    ix += indices[i] * stride(i);
            if (ix >= data.length())
                throw new IllegalArgumentException("Illegal indices " + Arrays.toString(indices));
            data.put(ix, element.getDouble(0));
        }
        return this;
    }

    @Override
    public INDArray match(INDArray comp, Condition condition) {
        // TODO: obviously, we can make this broadcastable, eventually. But this will require new CustomOp based on MatchCondition
        Preconditions.checkArgument(Arrays.equals(this.shape(), comp.shape()), "Shapes must be equal");
        Preconditions.checkArgument(this.dataType() == comp.dataType(), "Data types bmust be equal");
        return Nd4j.getExecutioner().exec(new MatchConditionTransform((INDArray)this, comp, Nd4j.createUninitialized(DataType.BOOL, this.shape()), condition));
    }

    @Override
    public INDArray match(Number comp, Condition condition) {
        return Nd4j.getExecutioner().exec(new MatchConditionTransform((INDArray)this,comp.doubleValue(), condition));
    }

    @Override
    public INDArray getWhere(INDArray comp, Condition condition) {
        return BooleanIndexing.chooseFrom(new INDArray[]{(INDArray)this,comp},condition);
    }

    @Override
    public INDArray getWhere(Number comp, Condition condition) {
        return BooleanIndexing.chooseFrom(new INDArray[]{(INDArray)this},Arrays.asList(comp.doubleValue()),Collections.<Integer>emptyList(),condition);
    }

    @Override
    public INDArray putWhere(INDArray comp, INDArray put, Condition condition) {
        Nd4j.getCompressor().autoDecompress((INDArray)this);
        MatchConditionTransform matchCondition = new MatchConditionTransform((INDArray)this,comp,condition);
        Nd4j.getExecutioner().exec(matchCondition);
        return putWhereWithMask((INDArray)matchCondition.z(),put);
    }

    @Override
    public INDArray putWhere(Number comp, INDArray put, Condition condition) {
        return putWhere((INDArray)Nd4j.scalar(comp),put,condition);
    }

    @Override
    public INDArray putWhere(Number comp, Number put, Condition condition) {
        return putWhere((INDArray)Nd4j.scalar(comp),Nd4j.scalar(put),condition);
    }


    @Override
    public INDArray putWhereWithMask(INDArray mask, INDArray put) {
        INDArray output = dup();
        Nd4j.getExecutioner().execAndReturn(new Where(new INDArray[]{mask,(INDArray)this,put},new INDArray[]{output}));
        return output;
    }

    @Override
    public INDArray putWhereWithMask(INDArray mask, Number put) {
        return putWhereWithMask((INDArray)mask,Nd4j.scalar(put));
    }

    @Override
    public INDArray put(int i, int j, INDArray element) {
        return put(new int[] {i, j}, element);
    }

    @Override
    public INDArray put(int i, int j, Number element) {
        return putScalar(new int[] {i, j}, element.doubleValue());
    }

    @Override
    public INDArray putSlice(int slice, INDArray put) {
        Nd4j.getCompressor().autoDecompress((INDArray)this);


        if (isScalar()) {
            Preconditions.checkState(put.isScalar(), "Invalid dimension. Can only insert a scalar in to another scalar");
            put(0, put.getScalar(0));
            return this;
        } else if (isVector()) {
            Preconditions.checkState(put.isVectorOrScalar() && put.length() == length(),
                    "Invalid dimension on insertion. Can only insert scalars/vectors into other scalar/vectors");
            if (put.isScalar())
                putScalar(slice, put.getDouble(0));
            else
                for (int i = 0; i < length(); i++)
                    putScalar(i, put.getDouble(i));
            return this;
        }

        assertSlice(put, slice);


        INDArray view = slice(slice);

        if (put.length() == 1) {
            putScalar(slice, put.getDouble(0));
        } else {
            if(!(view.isVector() && put.isVector() && view.length() == put.length()) && !view.equalShapes(put)){
                throw new IllegalStateException("Cannot put slice: array to be put (" + Arrays.toString(put.shape()) +
                        ") and slice array (" + Arrays.toString(view.shape()) + ") have different shapes");
            }
            view.assign(put);
        }
        return this;
    }

    protected void assertSlice(INDArray put, long slice) {
        Preconditions.checkArgument(slice < slices(), "Invalid slice specified: slice %s must be in range 0 (inclusive) to numSlices=%s (exclusive)", slice, slices());
        long[] sliceShape = put.shape();
        if (Shape.isRowVectorShape(sliceShape)) {
            return;
        } else {
            long[] requiredShape = ArrayUtil.removeIndex(shape(), 0);

            //no need to compare for scalar; primarily due to shapes either being [1] or length 0
            if (put.isScalar())
                return;

            if (isVector() && put.isVector() && put.length() < length())
                return;
            //edge case for column vectors
            if (Shape.isColumnVectorShape(sliceShape))
                return;
            if (!Shape.shapeEquals(sliceShape, requiredShape) && !Shape.isRowVectorShape(requiredShape)
                    && !Shape.isRowVectorShape(sliceShape))
                throw new IllegalStateException(String.format("Invalid shape size of %s . Should have been %s ",
                        Arrays.toString(sliceShape), Arrays.toString(requiredShape)));
        }
    }

    public boolean isMatrix() {
        return rank() == 2;
    }

    protected INDArray newShape(long[] newShape, char ordering) {

        return Nd4j.create(data(), newShape, stride(), 0, ordering);
    }

    protected INDArray create(DataBuffer data, int[] newShape, int[] newStrides, long offset, char ordering) {
        return (INDArray)Nd4j.create(data, newShape, newStrides, offset, ordering);
    }

    protected INDArray create(DataBuffer data, long[] newShape, long[] newStrides, long offset, char ordering) {
        return (INDArray)Nd4j.create(data, newShape, newStrides, offset, ordering);
    }

    protected INDArray create(DataBuffer data, int[] newShape, int[] newStrides, long offset) {
        return (INDArray) Nd4j.create(data, newShape, newStrides, offset);
    }

    protected INDArray create(int[] shape) {
        return (INDArray)Nd4j.create(shape, getStrides(shape, Nd4j.order()), 0);
    }

    protected INDArray create(int[] shape, int[] strides, long offset) {
        return (INDArray)Nd4j.create(shape, strides, offset);
    }

    protected int[] getStrides(int[] shape, char ordering) {
        return Nd4j.getStrides(shape, ordering);
    }

    @Override
    public double squaredDistance(INDArray other) {
        validateNumericalArray("squaredDistance", false);
        double d2 = distance2(other);
        return d2 * d2;
    }

    @Override
    public double distance2(INDArray other) {
        validateNumericalArray("distance2", false);
        Nd4j.getCompressor().autoDecompress(this);
        return Nd4j.getExecutioner().execAndReturn(new EuclideanDistance(this, other)).getFinalResult().doubleValue();
    }

    @Override
    public double distance1(INDArray other) {
        validateNumericalArray("distance1", false);
        Nd4j.getCompressor().autoDecompress((INDArray)this);
        return Nd4j.getExecutioner().execAndReturn(new ManhattanDistance((INDArray)this, other)).getFinalResult().doubleValue();
    }

    @Override
    public INDArray get(INDArray indices) {
        if(indices.rank() > 2) {
            throw new ND4JIllegalArgumentException("Indices must be a vector or matrix.");
        }

        if (rank() == 1) {
            Preconditions.checkArgument(indices.rank() <= 1, "For 1D vector indices must be either scalar or vector as well");
            INDArray ret = Nd4j.createUninitialized(this.dataType(), indices.length());
            for (int e = 0; e < indices.length(); e++) {
                long idx = indices.getLong(e);
                double value =  getDouble(idx);
                ret.putScalar(e, (double)value);
            }

            return (INDArray) ret;
        } else if(indices.rows() == rank()) {
            INDArray ret = (INDArray)Nd4j.create(this.dataType(), indices.columns());

            for(int i = 0; i < indices.columns(); i++) {
                int[] specifiedIndex = indices.getColumn(i).dup().data().asInt();
                double v = getDouble(specifiedIndex);
                ret.putScalar(i, (double) v);
            }

            return ret;
        }
        else {
            List<INDArray> arrList = new ArrayList<>();

            if(indices.isMatrix() || indices.isColumnVector()
                    || (indices.isScalar() && indices.rank() == 2)) { // we need this for compatibility with legacy code
                for(int i = 0; i < indices.rows(); i++) {
                    if(i == 0)  {
                        INDArray row = indices.getRow(i);
                        for(int j = 0; j < row.length(); j++) {
                            arrList.add(slice(row.getInt(j)));
                        }
                    }
                    else {
                        INDArray row = indices.slice(i);
                        for(int j = 0; j < row.length(); j++) {
                            INDArray put = arrList.get(j).slice(row.getInt(j));
                            put = put.reshape(Longs.concat(new long[]{1},put.shape()));
                            arrList.set(j,put);
                        }
                    }

                }
            }
            else if(indices.isRowVector()) {
                for(int i = 0; i < indices.length(); i++) {
                    INDArray add = slice(indices.getInt(i));
                    add = add.reshape(Longs.concat(new long[] {1,},add.shape()));
                    arrList.add(add);
                }
            }

            return Nd4j.concat(0,arrList.toArray(new INDArray[arrList.size()]));

        }


    }

    @Override
    public INDArray put(INDArray indices, INDArray element) {
        if(indices.rank() > 2) {
            throw new ND4JIllegalArgumentException("Indices must be a vector or matrix.");
        }

        if(indices.rows() == rank()) {
            NdIndexIterator ndIndexIterator = new NdIndexIterator(element.shape());
            for(int i = 0; i < indices.columns(); i++) {
                int[] specifiedIndex = indices.getColumn(i).dup().data().asInt();
                putScalar(specifiedIndex,element.getDouble(ndIndexIterator.next()));
            }
        }
        else {
            List<INDArray> arrList = new ArrayList<>();

            if(indices.isMatrix() || indices.isColumnVector()) {
                for(int i = 0; i < indices.rows(); i++) {
                    INDArray row = indices.getRow(i);
                    for(int j = 0; j < row.length(); j++) {
                        INDArray slice = slice(row.getInt(j));
                        Nd4j.getExecutioner().execAndReturn(new Assign(new INDArray[]{slice,element},new INDArray[]{slice}));
                        arrList.add(slice(row.getInt(j)));
                    }
                }
            }
            else if(indices.isRowVector()) {
                for(int i = 0; i < indices.length(); i++) {
                    arrList.add(slice(indices.getInt(i)));
                }
            }
        }
        return this;
    }

    @Override
    public INDArray put(INDArrayIndex[] indices, INDArray element) {
        Nd4j.getCompressor().autoDecompress(this);
        boolean isSpecifiedIndex = false;
        for(INDArrayIndex idx : indices){
            if(idx instanceof SpecifiedIndex){
                isSpecifiedIndex = true;
                break;
            }
        }

        if(!isSpecifiedIndex){
            return get(indices).assign(element);
        } else {
            //Can't get a view, so we'll do it in subsets instead
            // This is inefficient, but it is correct...
            int numSpecified = 0;
            List<long[]> specifiedIdxs = new ArrayList<>();
            List<Integer> specifiedIdxDims = new ArrayList<>();

            INDArrayIndex[] destinationIndices = indices.clone();  //Shallow clone
            INDArrayIndex[] sourceIndices = indices.clone();
            for( int i=0; i<indices.length; i++){
                INDArrayIndex idx = indices[i];
                if(idx instanceof SpecifiedIndex){
                    numSpecified++;
                    long[] idxs = ((SpecifiedIndex) idx).getIndexes();
                    specifiedIdxs.add(idxs);
                    specifiedIdxDims.add(i);
                } else if(idx instanceof PointIndex){
                    //Example: [2,3,3].put(point(1), ..., [1,x,y]) -> can't use point(1) on [1,x,y]
                    sourceIndices[i] = NDArrayIndex.point(0);
                }
            }
            int[] counts = new int[specifiedIdxs.size()];
            int[] dims = new int[specifiedIdxDims.size()];
            for( int i=0; i<specifiedIdxs.size(); i++ ){
                counts[i] = specifiedIdxs.get(i).length;
                dims[i] = specifiedIdxDims.get(i);
            }

            NdIndexIterator iter = new NdIndexIterator(counts);
            while(iter.hasNext()){
                long[] iterationIdxs = iter.next();
                for(int i=0; i<iterationIdxs.length; i++ ){
                    long[] indicesForDim = specifiedIdxs.get(i);
                    destinationIndices[dims[i]] = NDArrayIndex.point(indicesForDim[(int)iterationIdxs[i]]);
                    sourceIndices[dims[i]] = NDArrayIndex.point(iterationIdxs[i]);
                }

                INDArray sourceView = element.get(sourceIndices);
                INDArray destinationView = this.get(destinationIndices);
                destinationView.assign(sourceView);
            }
        }
        return this;
    }

    @Override
    public INDArray put(INDArrayIndex[] indices, Number element) {
        Nd4j.getCompressor().autoDecompress(this);
        INDArray get = get(indices);
        for (int i = 0; i < get.length(); i++)
            get.putScalar(i, element.doubleValue());
        return this;
    }

    @Override
    public INDArray swapAxes(int dimension, int with) {
        int[] shape = ArrayUtil.range(0, shape().length);
        shape[dimension] = with;
        shape[with] = dimension;
        return permute(shape);
    }


    @Override
    public boolean isView() {
        /*
            We don't really use Shape offset value anywhere
            And it's possible to be not a view, and have non-empty originalBuffer
         */
        // length/data.length can be different in case of Threshold conversion
        if(isEmpty() || isS())
            return false;

        boolean c2 = (length() < data().length() && data.dataType() != DataType.INT);
        boolean c3 = (data().originalDataBuffer() != null && data != data.originalDataBuffer());

        return (boolean)c2 || (boolean)c3;
    }

    @Override
    public boolean isSparse() {
        return false;
    }

    @Override
    public DataBuffer data() {
        return data;
    }

    @Override
    public void setData(DataBuffer data) {
        this.data = data;
    }

    @Override
    public long slices() {
        return size(0);
    }

    protected INDArray create(DataBuffer buffer) {
        return (INDArray)Nd4j.create(buffer);
    }

    @Override
    public INDArray cond(Condition condition) {
        if(isEmpty())
            return (INDArray)Nd4j.empty(DataType.BOOL);
        INDArray ret = (INDArray)Nd4j.createUninitialized(DataType.BOOL, this.shape());
        Nd4j.getExecutioner().exec(new MatchConditionTransform(this,ret, condition));
        return ret;
    }

    protected void init(int[] shape, int[] stride) {
        //null character
        if (shapeInformation == null || jvmShapeInfo == null || ordering() == '\u0000') {
            //Shape.setOrder(shapeInfo(), Nd4j.order());
            Pair<DataBuffer,long[]>  si = Nd4j.getShapeInfoProvider().createShapeInformation(ArrayUtil.toLongArray(shape), ArrayUtil.toLongArray(stride), 1, Nd4j.order(), this.dataType(), false);
            setShapeInformation(si);
        }

    }

    protected void init(long[] shape, long[] stride) {
        //null character
        if (shapeInformation == null || jvmShapeInfo == null || ordering() == '\u0000') {
            Pair<DataBuffer,long[]>  si = Nd4j.getShapeInfoProvider().createShapeInformation(shape,stride, 1, Nd4j.order(), this.dataType(), false);
            setShapeInformation(si);
        }

    }

    @Override
    public INDArray getScalar(long i) {
        if (i >= this.length())
            throw new ND4JIllegalStateException("Index can't be greater then array length");

        if (i < 0)
            i += this.length();

        long idx = this.isScalar() ? 0 : Shape.getOffset(jvmShapeInfo.javaShapeInformation, Shape.ind2subC(this.shape(), i));
        DataBuffer buffer = Nd4j.createBuffer( this.data(), this.data().originalOffset() + idx, 1);
        Pair<DataBuffer,long[]>  shape = Nd4j.getShapeInfoProvider().createShapeInformation(new long[0], new long[0],1,'c', dataType(), false);
        return Nd4j.createArrayFromShapeBuffer(buffer, shape);
    }

    /**
     * Do a row wise op (a,s,m,d)
     * a : add
     * s : subtract
     * m : multiply
     * d : divide
     * h : reverse subtraction
     * t : reverse division
     *
     * @param columnVector the column  vector
     * @param operation    the operation
     * @return
     */
    protected INDArray doColumnWise(INDArray columnVector, char operation) {
        Nd4j.getCompressor().autoDecompress(this);
       if(columnVector.isScalar()) {
           switch (operation) {
               case 'a':
                   addi(columnVector.getDouble(0));
                   break;
               case 'p':
                   assign(columnVector.getDouble(0));
                   break;
               case 's':
                   subi(columnVector.getDouble(0));
                   break;
               case 'm':
                   muli(columnVector.getDouble(0));
                   break;
               case 'd':
                   divi(columnVector.getDouble(0));
                   break;
               case 'h':
                   rsubi(columnVector.getDouble(0));
                   break;
               case 't':
                   rdivi(columnVector.getDouble(0));
                   break;

           }

           return this;
       }

       else if(isScalar()) {
           switch (operation) {
               case 'a':
                   return columnVector.addi(getDouble(0));
               case 'p':
                   return columnVector.assign(getDouble(0));
               case 's':
                   return columnVector.subi(getDouble(0));
               case 'm':
                   return columnVector.muli(getDouble(0));
               case 'd':
                   return columnVector.divi(getDouble(0));
               case 'h':
                   return columnVector.rsubi(getDouble(0));
               case 't':
                   return columnVector.rdivi(getDouble(0));

           }
       }

        //Input validation: require (a) columnVector to actually be a column vector, and (b) this.size(0) to match columnVector.size(0)
        //Or, simply require it to be a rank 1 vector
        if ((!columnVector.isColumnVector() && columnVector.rank() > 1) || this.size(0) != columnVector.size(0) || columnVector.length() <= 1) {
            throw new IllegalStateException("Mismatched shapes (shape = " + Arrays.toString(shape())
                    + ", column vector shape =" + Arrays.toString(columnVector.shape()) + ")");
        }

        if (columnVector.data().sameUnderlyingData(data()))
            return doColumnWise(columnVector.dup(), operation);
        if (equalShapes(columnVector)) {
            switch (operation) {
                case 'a':
                    addi(columnVector);
                    break;
                case 'p':
                    assign(columnVector);
                    break;
                case 's':
                    subi(columnVector);
                    break;
                case 'm':
                    muli(columnVector);
                    break;
                case 'd':
                    divi(columnVector);
                    break;
                case 'h':
                    rsubi(columnVector);
                    break;
                case 't':
                    rdivi(columnVector);
                    break;
            }

            return this;
        }
        if (rows() == 1 && columnVector.isScalar()) {
            applyScalarOp(columnVector, operation);
        } else {
            // special optimization case, broadcast turns into ScalarOp Along Dimension
            if (rank() == 2 && elementWiseStride() == 1 && ordering() == 'c' && columnVector.elementWiseStride() == 1) {
                switch (operation) {
                    case 'a': {
                        ScalarAdd op = new ScalarAdd(this, columnVector, this, 0.0);
                        op.setDimension(1);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 'p': {
                        ScalarSet op = new ScalarSet(this, columnVector, this, 0.0);
                        op.setDimension(1);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 's': {
                        ScalarSubtraction op = new ScalarSubtraction(this, columnVector, this, 0.0);
                        op.setDimension(1);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 'm': {
                        ScalarMultiplication op =
                                new ScalarMultiplication(this, columnVector, this, 0.0);
                        op.setDimension(1);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 'd': {
                        ScalarDivision op = new ScalarDivision(this, columnVector, this, 0.0);
                        op.setDimension(1);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 'h': {
                        ScalarReverseSubtraction op =
                                new ScalarReverseSubtraction(this, columnVector, this, 0.0);
                        op.setDimension(1);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 't': {
                        ScalarReverseDivision op =
                                new ScalarReverseDivision(this, columnVector, this, 0.0);
                        op.setDimension(1);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                }
            } else {
                applyBroadcastOp(columnVector, operation);
            }

        }

        return this;

    }

    /**
     * Do a row wise op (a,s,m,d)
     * a : add
     * s : subtract
     * m : multiply
     * d : divide
     * h : reverse subtraction
     * t : reverse division
     *
     * @param rowVector the row vector
     * @param operation the operation
     * @return
     */
    protected INDArray doRowWise(INDArray rowVector, final char operation) {
        Nd4j.getCompressor().autoDecompress(this);


        if(rowVector.isScalar()) {
            switch (operation) {
                case 'a':
                    addi(rowVector.getDouble(0));
                    break;
                case 'p':
                    assign(rowVector.getDouble(0));
                    break;
                case 's':
                    subi(rowVector.getDouble(0));
                    break;
                case 'm':
                    muli(rowVector.getDouble(0));
                    break;
                case 'd':
                    divi(rowVector.getDouble(0));
                    break;
                case 'h':
                    rsubi(rowVector.getDouble(0));
                    break;
                case 't':
                    rdivi(rowVector.getDouble(0));
                    break;

            }

            return this;
        }
        else if(isScalar()) {
            switch (operation) {
                case 'a':
                    return rowVector.addi(getDouble(0));
                case 'p':
                    return rowVector.assign(getDouble(0));
                case 's':
                    return rowVector.subi(getDouble(0));
                case 'm':
                    return rowVector.muli(getDouble(0));
                case 'd':
                    return rowVector.divi(getDouble(0));
                case 'h':
                    return rowVector.rsubi(getDouble(0));
                case 't':
                    return rowVector.rdivi(getDouble(0));

            }
        }

        //Input validation: require (a) rowVector to actually be a row vector, and (b) this.size(1) to match rowVector.size(1)
        if (!rowVector.isRowVector() || this.rank() > 1 && rowVector.rank() > 1 &&  this.size(1) != rowVector.size(1) || rowVector.length() <= 1) {
            throw new IllegalStateException("Mismatched shapes (shape = " + Arrays.toString(shape())
                    + ", row vector shape =" + Arrays.toString(rowVector.shape()) + ")");
        }

        if (rowVector.data().sameUnderlyingData(data()))
            return doRowWise(rowVector.dup(), operation);

        if (isVector()) {
            switch (operation) {
                case 'a':
                    addi(rowVector);
                    break;
                case 'p':
                    assign(rowVector);
                    break;
                case 's':
                    subi(rowVector);
                    break;
                case 'm':
                    muli(rowVector);
                    break;
                case 'd':
                    divi(rowVector);
                    break;
                case 'h':
                    rsubi(rowVector);
                    break;
                case 't':
                    rdivi(rowVector);
                    break;
            }

            return this;
        }

        if (rank() == 2 && columns() == 1 && rowVector.isScalar()) {
            applyScalarOp(rowVector, operation);
        } else {
            // special optimization case, broadcast turns into ScalarOp Along Dimension
            if (rank() == 2 && elementWiseStride() == 1 && ordering() == 'f' && rowVector.elementWiseStride() == 1) {
                switch (operation) {
                    case 'a': {
                        ScalarAdd op = new ScalarAdd(this, rowVector, this, 0.0);
                        op.setDimension(0);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 'p': {
                        ScalarSet op = new ScalarSet(this, rowVector, this, 0.0);
                        op.setDimension(0);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 's': {
                        ScalarSubtraction op = new ScalarSubtraction(this, rowVector, this, 0.0);
                        op.setDimension(0);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 'm': {
                        ScalarMultiplication op = new ScalarMultiplication(this, rowVector, this, 0.0);
                        op.setDimension(0);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 'd': {
                        ScalarDivision op = new ScalarDivision(this, rowVector, this, 0.0);
                        op.setDimension(0);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 'h': {
                        ScalarReverseSubtraction op =
                                new ScalarReverseSubtraction(this, rowVector, this, 0.0);
                        op.setDimension(0);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }
                    case 't': {
                        ScalarReverseDivision op = new ScalarReverseDivision(this, rowVector, this, 0.0);
                        op.setDimension(0);
                        Nd4j.getExecutioner().exec(op);
                        break;
                    }

                }
            } else {
                applyBroadcastOp(rowVector, operation);
            }
        }

        return this;
    }


    private void applyBroadcastOp(INDArray vector, final char operation) {
        Nd4j.getCompressor().autoDecompress(this);
        int alongDimension = Shape.isRowVectorShape(vector.shape()) ? 1 : 0;

        // FIXME: probably this is wrong, because strict equality is always false in current DataBuffer mechanics
        if (this.data() == vector.data())
            vector = vector.dup();
        switch (operation) {
            case 'a':
                Nd4j.getExecutioner().exec(new BroadcastAddOp(this, vector, this, alongDimension));
                return;
            case 's':
                Nd4j.getExecutioner().exec(new BroadcastSubOp(this, vector, this, alongDimension));
                return;
            case 'm':
                Nd4j.getExecutioner().exec(new BroadcastMulOp(this, vector, this, alongDimension));
                return;
            case 'd':
                Nd4j.getExecutioner().exec(new BroadcastDivOp(this, vector, this, alongDimension));
                return;
            case 'h':
                Nd4j.getExecutioner().exec(new BroadcastRSubOp(this, vector, this, alongDimension));
                return;
            case 't':
                Nd4j.getExecutioner().exec(new BroadcastRDivOp(this, vector, this, alongDimension));
                return;
            case 'p':
                Nd4j.getExecutioner().exec(new BroadcastCopyOp(this, vector, this, alongDimension));
                return;
            default:
                throw new UnsupportedOperationException("Unknown operation: " + operation);
        }
    }

    private void applyScalarOp(INDArray vector, char operation) {
        Nd4j.getCompressor().autoDecompress(this);
        switch (operation) {
            case 'a':
                addi(vector.getDouble(0));
                break;
            case 's':
                subi(vector.getDouble(0));
                break;
            case 'm':
                muli(vector.getDouble(0));
                break;
            case 'd':
                divi(vector.getDouble(0));
                break;
            case 'h':
                rsubi(vector.getDouble(0));
                break;
            case 't':
                rdivi(vector.getDouble(0));
                break;
        }
    }

    protected DataBuffer shapeOf() {
        //        if (shape == null)
        //            shape = Shape.shapeOf(shapeInfoDataBuffer());
        //        return shape;

        return Shape.shapeOf(shapeInfoDataBuffer());
    }

    protected DataBuffer strideOf() {
        //        if (stride == null)
        //            stride = Shape.stride(shapeInfoDataBuffer());
        //        return stride;
        return Shape.stride(shapeInfoDataBuffer());
    }

    @Override
    public int stride(int dimension) {
        int rank = jvmShapeInfo.rank;
        Preconditions.checkArgument(dimension < rank, "Cannot get stride for dimension %s from rank %s array: " +
                "dimension indices must be in range -rank <= dimension < rank", dimension, rank);
        if (dimension < 0)
            return (int) stride()[dimension + rank];
        return (int) stride()[dimension];
    }

    @Override
    public INDArray rdiviColumnVector(INDArray columnVector) {
        validateNumericalArray("rdiviColumnVector", false);
        return doColumnWise(columnVector, 't');
    }

    @Override
    public INDArray rdivColumnVector(INDArray columnVector) {
        validateNumericalArray("rdivColumnVector", false);
        return dup().rdiviColumnVector(columnVector);
    }

    @Override
    public INDArray rdiviRowVector(INDArray rowVector) {
        validateNumericalArray("rdiviRowVector", false);
        return doRowWise(rowVector, 't');
    }

    @Override
    public INDArray rdivRowVector(INDArray rowVector) {
        validateNumericalArray("rdivRowVector", false);
        return dup().rdiviRowVector(rowVector);
    }

    @Override
    public INDArray rsubiColumnVector(INDArray columnVector) {
        validateNumericalArray("rsubiColumnVector", false);
        return doColumnWise(columnVector, 'h');
    }

    @Override
    public INDArray rsubColumnVector(INDArray columnVector) {
        validateNumericalArray("rsubColumnVector", false);
        return dup().rsubiColumnVector(columnVector);
    }

    @Override
    public INDArray rsubiRowVector(INDArray rowVector) {
        validateNumericalArray("rsubiRowVector", false);
        return doRowWise(rowVector, 'h');
    }

    @Override
    public INDArray rsubRowVector(INDArray rowVector) {
        validateNumericalArray("rsubRowVector", false);
        return dup().rsubiRowVector(rowVector);
    }

    @Override
    public INDArray put(int i, INDArray element) {
        Preconditions.checkArgument(element.isScalar(), "Element must be a scalar: element has shape %ndShape", element);
        return putScalar(i, element.getDouble(0));
    }

    @Override
    public INDArray diviColumnVector(INDArray columnVector) {
        validateNumericalArray("diviColumnVector", false);
        return doColumnWise(columnVector, 'd');
    }

    @Override
    public INDArray divColumnVector(INDArray columnVector) {
        validateNumericalArray("divColumnVector", false);
        return dup().diviColumnVector(columnVector);
    }

    @Override
    public INDArray diviRowVector(INDArray rowVector) {
        validateNumericalArray("diviRowVector", false);
        return doRowWise(rowVector, 'd');
    }

    @Override
    public INDArray divRowVector(INDArray rowVector) {
        validateNumericalArray("divRowVector", false);
        return dup().diviRowVector(rowVector);
    }

    @Override
    public INDArray muliColumnVector(INDArray columnVector) {
        validateNumericalArray("muliColumnVector", false);
        return doColumnWise(columnVector, 'm');
    }

    @Override
    public INDArray mulColumnVector(INDArray columnVector) {
        validateNumericalArray("mulColumnVector", false);
        return dup().muliColumnVector(columnVector);
    }

    @Override
    public INDArray muliRowVector(INDArray rowVector) {
        validateNumericalArray("muliRowVector", false);
        return doRowWise(rowVector, 'm');
    }

    @Override
    public INDArray mulRowVector(INDArray rowVector) {
        validateNumericalArray("mulRowVector", false);
        return dup().muliRowVector(rowVector);
    }

    @Override
    public INDArray subiColumnVector(INDArray columnVector) {
        validateNumericalArray("subiColumnVector", false);
        return doColumnWise(columnVector, 's');
    }

    @Override
    public INDArray subColumnVector(INDArray columnVector) {
        validateNumericalArray("subColumnVector", false);
        return dup().subiColumnVector(columnVector);
    }

    @Override
    public INDArray subiRowVector(INDArray rowVector) {
        validateNumericalArray("subiRowVector", false);
        return doRowWise(rowVector, 's');
    }

    @Override
    public INDArray subRowVector(INDArray rowVector) {
        validateNumericalArray("subRowVector", false);
        return dup().subiRowVector(rowVector);
    }

    @Override
    public INDArray addiColumnVector(INDArray columnVector) {
        validateNumericalArray("addiColumnVector", false);
        return doColumnWise(columnVector, 'a');
    }

    @Override
    public INDArray putiColumnVector(INDArray columnVector) {
        return doColumnWise(columnVector, 'p');
    }

    @Override
    public INDArray addColumnVector(INDArray columnVector) {
        validateNumericalArray("addColumnVector", false);
        return dup().addiColumnVector(columnVector);
    }

    @Override
    public INDArray addiRowVector(INDArray rowVector) {
        validateNumericalArray("addiRowVector", false);
        return doRowWise(rowVector, 'a');
    }

    @Override
    public INDArray putiRowVector(INDArray rowVector) {
        validateNumericalArray("putiRowVector", false);
        return doRowWise(rowVector, 'p');
    }

    @Override
    public INDArray addRowVector(INDArray rowVector) {
        validateNumericalArray("addRowVector", false);
        return dup().addiRowVector(rowVector);
    }

    @Override
    public INDArray mmul(INDArray other, INDArray result, MMulTranspose mMulTranspose) {
        return mMulTranspose.exec(this, other, result);
    }

    @Override
    public INDArray mmul(INDArray other, MMulTranspose mMulTranspose) {
        return mMulTranspose.exec(this, other, null);
    }

    @Override
    public INDArray mmul(INDArray other, char resultOrder) {
        Preconditions.checkArgument(resultOrder == 'c' || resultOrder == 'f', "Order must be either 'c' or 'f', but [" + resultOrder + "] was given");
        Preconditions.checkState(this.dataType() == other.dataType(), "Matrix multiplication: arrays must have same dtype: %s vs. %s", this.dataType(), other.dataType());
        // FIXME: add support for 3D+ here?
        long[] shape = other.rank() == 1 ? new long[]{rows()} : new long[]{rows(), other.columns()};
        INDArray result = createUninitialized(this.dataType(), shape, resultOrder);
        if (result.isScalar())
            return Nd4j.scalar(this.dataType(), Nd4j.getBlasWrapper().dot(this, other)).reshape(1, 1);
        return mmuli(other, result);
    }

    @Override
    public INDArray mmul(INDArray other) {
        return mmul(other, (this.ordering() == 'f' && other.ordering() == 'f' && other.rank() != 1) ? 'f' : 'c');
    }

    protected INDArray create(int[] shape, char ordering) {
        return Nd4j.create(shape, ordering);
    }

    @Override
    public double[][] toDoubleMatrix() {
        if(!isMatrix()) {
            throw new ND4JIllegalStateException("Unable to create a 2d array from a non matrix! Shape: " + Shape.shapeToStringShort(this));
        }

        if (this.size(0) > Integer.MAX_VALUE || this.size(1) > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();

        double[][] ret = new double[rows()][columns()];
        for(int i = 0; i < ret.length; i++) {
            ret[i] = getRow(i).dup().data().asDouble();
        }

        return ret;
    }

    @Override
    public double[] toDoubleVector() {
        if(!isVectorOrScalar()) {
            throw new ND4JIllegalStateException("Unable to create a 1d array from a non vector! Shape: " + Shape.shapeToStringShort(this));
        }
        return dup().data().asDouble();
    }

    @Override
    public float[] toFloatVector() {
        if(!isVectorOrScalar()) {
            throw new ND4JIllegalStateException("Unable to create a 1d array from a non vector! Shape: " + Shape.shapeToStringShort(this));
        }
        return dup().data().asFloat();
    }

    @Override
    public float[][] toFloatMatrix() {
        if(!isMatrix()) {
            throw new ND4JIllegalStateException("Unable to create a 2d array from a non matrix! Shape: " + Shape.shapeToStringShort(this));
        }

        if (this.rows() > Integer.MAX_VALUE || this.columns() > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();

        float[][] ret = new float[(int) rows()][ (int) columns()];
        for(int i = 0; i < ret.length; i++) {
            ret[i] = getRow(i).dup().data().asFloat();
        }

        return ret;
    }

    @Override
    public int[] toIntVector() {
        if (isEmpty())
            return new int[0];

        if(!isVectorOrScalar()) {
            throw new ND4JIllegalStateException("Unable to create a 1d array from a non vector! Shape: " + Shape.shapeToStringShort(this));
        }
        if(isView() || elementWiseStride() != 1){
            return dup().data().asInt();
        }
        return data().asInt();
    }

    @Override
    public long[] toLongVector() {
        if(!isVectorOrScalar()) {
            throw new ND4JIllegalStateException("Unable to create a 1d array from a non vector! Shape: " + Shape.shapeToStringShort(this));
        }
        if(isView() || elementWiseStride() != 1){
            return dup().data().asLong();
        }
        return data().asLong();
    }

    @Override
    public long[][] toLongMatrix() {
        if(!isMatrix()) {
            throw new ND4JIllegalStateException("Unable to create a 2d array from a non matrix! Shape: " + Shape.shapeToStringShort(this));
        }

        if (this.rows() > Integer.MAX_VALUE || this.columns() > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();

        long[][] ret = new long[(int) rows()][(int) columns()];
        for(int i = 0; i < ret.length; i++) {
            ret[i] = getRow(i).dup().data().asLong();
        }

        return ret;
    }

    @Override
    public int[][] toIntMatrix() {
        if(!isMatrix()) {
            throw new ND4JIllegalStateException("Unable to create a 2d array from a non matrix! Shape: " + Shape.shapeToStringShort(this));
        }

        if (this.rows() > Integer.MAX_VALUE || this.columns() > Integer.MAX_VALUE)
            throw new ND4JArraySizeException();

        int[][] ret = new int[(int) rows()][(int) columns()];
        for(int i = 0; i < ret.length; i++) {
            ret[i] = getRow(i).dup().data().asInt();
        }

        return ret;
    }

    /**
     * Perform an copy matrix multiplication
     *
     * @param other  the other matrix to perform matrix multiply with
     * @param result the result ndarray
     * @return the result of the matrix multiplication
     */
    @Override
    public INDArray mmul(INDArray other, INDArray result) {
        return mmuli(other, result);
    }

    @Override
    public INDArray div(INDArray other) {
        if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return divi(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), Shape.broadcastOutputShape(this.shape(), other.shape()), this.ordering()));
        } else {
            return divi(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), this.shape(), this.ordering()));
        }
    }

    @Override
    public INDArray div(INDArray other, INDArray result) {
        validateNumericalArray("div", true);
        return divi(other, result);
    }

    @Override
    public INDArray mul(INDArray other) {
        validateNumericalArray("mul", false);
        if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return muli(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), Shape.broadcastOutputShape(this.shape(), other.shape()), this.ordering()));
        } else {
            INDArray z = Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), this.shape(), this.ordering());
            return muli(other, z);
        }
    }

    @Override
    public INDArray mul(INDArray other, INDArray result) {
        return muli(other, result);
    }

    @Override
    public INDArray sub(INDArray other) {
        validateNumericalArray("sub", false);
        if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return subi(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), Shape.broadcastOutputShape(this.shape(), other.shape()), this.ordering()));
        } else {
            return subi(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), this.shape(), this.ordering()));
        }
    }

    @Override
    public INDArray sub(INDArray other, INDArray result) {
        return subi(other, result);
    }

    @Override
    public INDArray add(INDArray other) {
        validateNumericalArray("add", false);
        if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return addi(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), Shape.broadcastOutputShape(this.shape(), other.shape()), this.ordering()));
        } else {
            return addi(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), this.shape(), this.ordering()));
        }
    }

    @Override
    public INDArray add(INDArray other, INDArray result) {
        validateNumericalArray("add", false);
        return addi(other, result);
    }

    @Override
    public INDArray mmuli(INDArray other, MMulTranspose transpose) {
        validateNumericalArray("mmuli", false);
        return dup().mmuli(other, this,transpose);
    }

    @Override
    public INDArray mmuli(INDArray other) {
        validateNumericalArray("mmuli", false);
        return dup().mmuli(other, this);
    }

    @Override
    public INDArray mmuli(INDArray other, INDArray result, MMulTranspose transpose) {
        return transpose.exec(this, other, result);
    }

    @Override
    public INDArray mmuli(INDArray other, INDArray result) {
        validateNumericalArray("mmuli", false);
        LinAlgExceptions.assertMultiplies(this, other);
        if(other.rank() == 1){
            //GEMV edge case
            Preconditions.checkState(result.length() == this.size(0) && this.size(1) == other.size(0),
                    "Invalid matrix multiplication: %ndShape x %ndShape with result shape %ndShape", this, other, result);
        } else {
            //Standard case
            Preconditions.checkState(
                    result.rank() == 2 && result.size(0) == this.size(0) && result.size(1) == other.size(1),
                    "Invalid result array shape: expected shape [%s,%s], got shape %ndShape result array for %ndShape x %ndShape", this.size(0), other.size(1), result,
                    this, other);
        }

        if (other.isScalar()) {
            return muli(other.getDouble(0), result);
        }
        if (isScalar()) {
            return other.muli(getDouble(0), result);
        }

        /* check sizes and resize if necessary */


        if (result == this || result == other) {
            /* actually, blas cannot do multiplications in-place. Therefore, we will fake by
             * allocating a temporary object on the side and copy the result later.
             */
            INDArray temp = Nd4j.create(result.dataType(), result.shape(), Nd4j.getStrides(result.shape(), 'f'), 'f');

            if (other.columns() == 1 || other.rank() == 1) {
                Nd4j.getBlasWrapper().level2().gemv(BlasBufferUtil.getCharForTranspose(result),
                        BlasBufferUtil.getCharForTranspose(this), 1.0, this, other, 0.0, temp);
            }

            else {
                Nd4j.getBlasWrapper().level3().gemm(BlasBufferUtil.getCharForTranspose(result),
                        BlasBufferUtil.getCharForTranspose(this), BlasBufferUtil.getCharForTranspose(temp), 1.0,
                        this, other, 0.0, temp);
            }

            result.assign(temp);


        } else {

            //We require that the result array is 'f' (fortran) order
            // However, user might have called mmuli with a c order array for the result
            // In which case, we need to allocate a temporary f order array, and later do an assign to the real result array

            boolean requiresTemp = result.ordering() != 'f' || result.isView() || !Shape.hasDefaultStridesForShape(result);
            INDArray gemmResultArr;
            if (requiresTemp) {
                //Can use createUninitialized due to beta==0.0 parameter in gemm
                gemmResultArr = Nd4j.createUninitialized(result.dataType(), result.shape(), 'f');
            } else {
                gemmResultArr = result;
            }

            if (other.columns() == 1 || other.rank() == 1) {
                Nd4j.getBlasWrapper().level2().gemv(
                        ordering(),
                        BlasBufferUtil.getCharForTranspose(other),
                        1.0,
                        this,
                        other,
                        0.0,
                        gemmResultArr);
            } else {
                //gemm doesn't support strides so vectors and views
                //don't work
                Nd4j.getBlasWrapper().level3().gemm(ordering(),
                        BlasBufferUtil.getCharForTranspose(other),
                        BlasBufferUtil.getCharForTranspose(gemmResultArr),
                        1.0,
                        this,
                        other,
                        0.0,
                        gemmResultArr);
            }

            if (requiresTemp) {
                result.assign(gemmResultArr);
            }
        }

        // 1D edge case: reshape back to vector
        if (other.rank() == 1)
            result = result.reshape(result.length());
        return result;
    }

    private INDArray create(int[] shape, int[] stride) {
        return Nd4j.create(shape, stride);
    }

    @Override
    public INDArray divi(INDArray other) {
        return divi(other, this);
    }

    @Override
    public INDArray divi(INDArray other, INDArray result) {
        validateNumericalArray("divi", false);
        Shape.assertBroadcastable("divi", this, other, result);
        Nd4j.exec(new DivOp(this, other, result));
        return result;
    }

    @Override
    public INDArray muli(INDArray other) {
        return muli(other, this);
    }

    @Override
    public INDArray muli(INDArray other, INDArray result) {
        validateNumericalArray("muli", false);
        Shape.assertBroadcastable("muli", this, other, result);
        Nd4j.exec(new MulOp(this, other, result));
        return result;
    }

    @Override
    public INDArray subi(INDArray other) {
        return subi(other, this);
    }

    /**
     * in place subtraction of two matrices
     *
     * @param other  the second ndarray to subtract
     * @param result the result ndarray
     * @return the result of the subtraction
     */
    @Override
    public INDArray subi(INDArray other, INDArray result) {
        validateNumericalArray("subi", false);
        Shape.assertBroadcastable("subi", this, other, result);
        Nd4j.exec(new SubOp(this, other, result));
        return result;
    }

    @Override
    public INDArray addi(INDArray other) {
        return addi(other, this);
    }

    @Override
    public INDArray addi(INDArray other, INDArray result) {
        validateNumericalArray("addi", false);
        Shape.assertBroadcastable("addi", this, other, result);
        Nd4j.exec(new AddOp(this, other, result));
        return result;
    }

    @Override
    public INDArray normmax(boolean keepDims, int... dimension) {
        validateNumericalArray("normmax", false);
        return Nd4j.getExecutioner().exec(new NormMax(this, keepDims, dimension));
    }

    @Override
    public INDArray normmax(int... dimension) {
        return normmax(false, dimension);
    }

    @Override
    public INDArray rdiv(INDArray other) {
        validateNumericalArray("rdiv", false);
        if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return rdivi(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), Shape.broadcastOutputShape(this.shape(), other.shape()), this.ordering()));
        } else {
            return rdivi(other, this.ulike());
        }
    }

    @Override
    public INDArray rdivi(INDArray other) {
        return rdivi(other, this);
    }

    @Override
    public INDArray rdiv(INDArray other, INDArray result) {
        validateNumericalArray("rdiv", false);
        return dup().rdivi(other, result);
    }

    @Override
    public INDArray rdivi(INDArray other, INDArray result) {
        validateNumericalArray("rdivi", false);
        Shape.assertBroadcastable("rdivi", this, other, result);
        Nd4j.exec(new RDivOp(this, other, result));
        return result;
    }

    @Override
    public INDArray rsub(INDArray other, INDArray result) {
        validateNumericalArray("rsub", false);
        return rsubi(other, result);
    }

    @Override
    public INDArray rsub(INDArray other) {
        validateNumericalArray("rsub", false);
        if (Shape.areShapesBroadcastable(this.shape(), other.shape())) {
            return rsubi(other, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), other.dataType()), Shape.broadcastOutputShape(this.shape(), other.shape()), this.ordering()));
        } else {
            return rsubi(other, this.ulike());
        }
    }

    @Override
    public INDArray rsubi(INDArray other) {
        return rsubi(other, this);
    }

    @Override
    public INDArray rsubi(INDArray other, INDArray result) {
        validateNumericalArray("rsubi", false);
        Shape.assertBroadcastable("rsubi", this, other, result);
        Nd4j.exec(new RSubOp(this, other, result));
        return result;
    }

    @Override
    public INDArray assign(Number value) {
        Preconditions.checkState(dataType() != DataType.BOOL || value.doubleValue() == 0.0 || value.doubleValue() == 1.0, "Only values 0 or 1 are allowed for scalar " +
                "assign on boolean arrays: got value %s on to assign to boolean array with shape %ndShape", value, this);
        Nd4j.getExecutioner().exec(new ScalarSet(this, value));
        return this;
    }

    @Override
    public INDArray assign(boolean value) {
        return assign(value ? 1 : 0);
    }

    @Override
    public INDArray assignIf(INDArray arr, Condition condition) {
        BooleanIndexing.assignIf(this, arr, condition);
        return this;
    }

    @Override
    public INDArray replaceWhere(INDArray arr, Condition condition) {
        Nd4j.getCompressor().autoDecompress(this);
        BooleanIndexing.replaceWhere(this, arr, condition);
        return this;
    }

    @Override
    @Deprecated  //TODO: Investigate. Not deprecated in the base interface.
    public long linearIndex(long i) {
        long idx = i;
        for (int j = 0; j < jvmShapeInfo.rank - 1; j++) {
            if (size((int) i) == 1)
                continue;
            idx += i * stride(j);
        }
        return Shape.offset(jvmShapeInfo.javaShapeInformation) + (idx);
    }

    @Override
    public INDArray slice(long slice) {
        Nd4j.getCompressor().autoDecompress(this);


        long slices = slices();
        if (slice >= slices)
            throw new IllegalArgumentException("Illegal slice " + slice);

        if (jvmShapeInfo.rank == 0 ) {
            throw new IllegalArgumentException("Can't slice a 0-d NDArray");
        }


        if (slice < 0)
            slice += rank();
        INDArrayIndex[] indexes = new INDArrayIndex[rank()];
        indexes[0] = NDArrayIndex.point(slice);
        for (int i = 1; i < rank(); i++) {
            indexes[i] = NDArrayIndex.all();
        }
        return get(indexes);
    }



    protected INDArray createScalarForIndex(long i, boolean applyOffset) {
        if(isVector())
            return getScalar(i);
        return Nd4j.create(data(), new long[] {1, 1}, new long[] {1, 1}, i);
    }

    protected INDArray createScalar(double d) {
        return Nd4j.scalar(d);
    }

    @Override
    public int getTrailingOnes() {
        int numLeadingOnes = 0;
        for (int i = rank() - 1; i > 0; i--) {
            if (size(i) == 1)
                numLeadingOnes++;
        }

        return numLeadingOnes;
    }

    @Override
    public int getLeadingOnes() {
        int numLeadingOnes = 0;
        for (int i = 0; i < rank(); i++) {
            if (size(i) == 1)
                numLeadingOnes++;
        }

        return numLeadingOnes;
    }

    @Override
    public INDArray slice(long slice, int dimension) {
        Nd4j.getCompressor().autoDecompress(this);

        long slices = size(dimension);
        if (slice >= slices)
            throw new IllegalArgumentException("Illegal slice " + slice);

        if (jvmShapeInfo.rank == 0) {
            if (slice == 0)
                return createScalarForIndex(slice, true);
            else
                throw new IllegalArgumentException("Can't slice a 0-d NDArray");

        }


        if (slice < 0)
            slice += rank();
        INDArrayIndex[] indexes = new INDArrayIndex[rank()];
        indexes[dimension] = NDArrayIndex.point(slice);
        for (int i = 0; i < rank(); i++) {
            if (i != dimension)
                indexes[i] = NDArrayIndex.all();
        }
        return get(indexes);

    }

    @Override
    public INDArray getScalar(int[] indexes) {
        if (indexes.length > rank())
            throw new ND4JIllegalStateException("Indexes can't be longer then array rank");

        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] < 0)
                indexes[i] += this.size(i);
        }
        long idx = Shape.getOffset(jvmShapeInfo.javaShapeInformation, indexes);
        DataBuffer buffer = Nd4j.createBuffer(this.data(), idx, 1);
        Pair<DataBuffer,long[]> shape = Nd4j.getShapeInfoProvider().createShapeInformation(new long[0], new long[0],1, 'c', this.dataType(), false);
        return Nd4j.createArrayFromShapeBuffer(buffer, shape);
    }

    @Override
    public INDArray getScalar(long... indexes) {
        if (indexes.length > rank())
            throw new ND4JIllegalStateException("Indexes can't be longer then array rank");

        for (int i = 0; i < indexes.length; i++) {
            if (indexes[i] < 0)
                indexes[i] += this.size(i);
        }

        long idx = Shape.getOffset(jvmShapeInfo.javaShapeInformation, indexes);
        DataBuffer buffer = Nd4j.createBuffer(this.data(), idx, 1);
        Pair<DataBuffer,long[]>  shape = Nd4j.getShapeInfoProvider().createShapeInformation(new long[0], new long[0],1,'c', this.dataType(), false);
        return Nd4j.createArrayFromShapeBuffer(buffer, shape);
    }

    @Override
    public INDArray rdiv(Number n) {
        //return dup().rdivi(n);
        return rdivi(n, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), n), this.shape(), this.ordering()));
    }

    @Override
    public INDArray rdivi(Number n) {
        return rdivi(n, this);
    }

    @Override
    public INDArray rsub(Number n) {
        validateNumericalArray("rsub", false);
        return rsubi(n, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), n),this.shape(), this.ordering()));
    }

    @Override
    public INDArray rsubi(Number n) {
        return rsubi(n, this);
    }

    @Override
    public INDArray div(Number n) {
        validateNumericalArray("div", false);
        return divi(n, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), n),this.shape(), this.ordering()));
    }

    @Override
    public INDArray divi(Number n) {
        return divi(n, this);
    }

    @Override
    public INDArray mul(Number n) {
        validateNumericalArray("mul", false);
        return muli(n, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), n), this.shape(), this.ordering()));
    }

    @Override
    public INDArray muli(Number n) {
        return muli(n, this);
    }

    @Override
    public INDArray sub(Number n) {
        validateNumericalArray("sub", false);
        return subi(n, Nd4j.createUninitialized(this.dataType(), this.shape(), this.ordering()));
    }

    @Override
    public INDArray subi(Number n) {
        return subi(n, this);
    }

    @Override
    public INDArray add(Number n) {
        validateNumericalArray("add", false);
        return addi(n, Nd4j.createUninitialized(Shape.pickPairwiseDataType(this.dataType(), n),this.shape(), this.ordering()));
    }

    @Override
    public INDArray addi(Number n) {
        return addi(n, this);
    }

    @Override
    public INDArray repmat(long[] shape) {
        Nd4j.getCompressor().autoDecompress(this);
        long rows = rows() * shape[0];
        long cols = columns() * shape[1];
        INDArray ret = reshape(1, length()).repeat(0, shape[0]).reshape(rows, columns()).repeat(0, shape[1]);
        return ret.reshape(rows, cols);
    }

    @Deprecated
    @Override
    public INDArray repmat(int[] shape) {
        long[] longShape = ArrayUtil.toLongArray(shape);
        return repmat(longShape);
    }

    @Override
    public INDArray repeat(int dimension, long... repeats) {
        Nd4j.getCompressor().autoDecompress(this);
        CustomOp op = DynamicCustomOp.builder("repeat")
                .addInputs(this)
                .addIntegerArguments(ArrayUtil.toInts(repeats))     //TODO int cast
                .build();
        op.addIArgument(dimension); //Native op: last iarg is dimension

        LongShapeDescriptor l = op.calculateOutputShape().get(0);
        INDArray out = Nd4j.create(l);
        op.addOutputArgument(out);
        Nd4j.exec(op);
        return out;
    }

    @Override
    public INDArray putRow(long row, INDArray toPut) {
        if (isRowVector() && toPut.isVector()) {
            return assign(toPut);
        }
        return put(new INDArrayIndex[] {NDArrayIndex.point(row), NDArrayIndex.all()}, toPut);
    }

    @Override
    public INDArray putColumn(int column, INDArray toPut) {
        Nd4j.getCompressor().autoDecompress(this);

        if (isColumnVector() && toPut.isVector()) {
            return assign(toPut);
        }
        return put(new INDArrayIndex[] {NDArrayIndex.all(), NDArrayIndex.point(column)}, toPut);
    }

    @Override
    public Number getNumber(long i){
        switch (dataType()){
            case DOUBLE:
            case FLOAT:
            case HALF:
            case BFLOAT16:
                return getDouble(i);
            case LONG:
            case INT:
            case SHORT:
            case UBYTE:
            case BYTE:
            case BOOL:
            case UINT64:
            case UINT32:
            case UINT16:
                return getLong(i);
            case UTF8:
            case COMPRESSED:
            case UNKNOWN:
            default:
                throw new UnsupportedOperationException("Cannot get number from array of datatype: " + dataType());
        }
    }

    @Override
    public Number getNumber(long... idx){
        switch (dataType()){
            case DOUBLE:
            case FLOAT:
            case HALF:
                return getDouble(idx);
            case LONG:
            case INT:
            case SHORT:
            case UBYTE:
            case BYTE:
            case BOOL:
                return getLong(idx);
            case UTF8:
            case COMPRESSED:
            case UNKNOWN:
            default:
                throw new UnsupportedOperationException("Cannot get number from array of datatype: " + dataType());
        }
    }

    @Override
    public double getDouble(long i) {
        Nd4j.getCompressor().autoDecompress(this);
        Preconditions.checkState(!isEmpty(), "Unable to get value from empty array");

        if (i >= length()) {
            throw new IllegalArgumentException("Unable to get linear index " + i + ": values is greater than length (" + length() + ")");
        }

        autoProcessScalarCall();

        if (i == 0)
            return data().getDouble(i);

        long[] dimensions = ordering() == 'c' ? Shape.ind2subC(this, i) : Shape.ind2sub(this, i);
        Shape.assertShapeLessThan(dimensions, shape());
        return getDouble(dimensions);

    }

    @Override
    public double getDouble(long i, long j) {
        return getDouble(new long[] {i, j});
    }

    @Override
    public float getFloat(long i) {
        return (float) getDouble(i);
    }

    @Override
    public float getFloat(long i, long j) {
        return (float) getDouble(i, j);
    }

    @Override
    public INDArray transpose() {
        Preconditions.checkState(rank() >= 2, "Can't transpose array with rank < 2: array shape %ndShape", this);

        return permute(ArrayUtil.reverseCopy(ArrayUtil.range(0, rank())));
    }

    /**
     *
     * Return transposed version of this matrix.
     *
     * PLEASE NOTE: This method is NOT in place, it will return transposed copy instead.
     */
    @Override
    public INDArray transposei() {
        Preconditions.checkState(rank() >= 2, "Can't transpose array with rank < 2: array shape %ndShape", this);
        
        return permutei(ArrayUtil.reverseCopy(ArrayUtil.range(0, rank())));
    }

    protected INDArray create(DataBuffer data, int[] shape, int[] strides) {
        return Nd4j.create(data, shape, strides, 0, ordering());
    }

    @Deprecated
    @Override
    public INDArray reshape(char order, int... newShape) {
        return reshape(order, ArrayUtil.toLongArray(newShape));
    }

    @Override
    public INDArray reshape(char order, long... newShape) {
        return reshape(order, false, newShape);
    }

    @Override
    public INDArray reshape(char order, boolean enforceView, long... newShape){
        Nd4j.getCompressor().autoDecompress(this);

        // special case for empty reshape
        if (this.length() == 1 && (newShape == null || newShape.length == 0) && this.elementWiseStride() == 1) {
            return Nd4j.create(this.data(), new int[0], new int[0], 0);
        }

        if (newShape == null || newShape.length < 1)
            throw new ND4JIllegalStateException(
                    "Can't reshape(long...) without shape arguments. Got empty shape instead.");

        // TODO: maybe toFlatten() makes more sense here?
        // reshape(-1) special case
        if (newShape.length == 1 && newShape[0] == -1)
            newShape[0] = this.length();

        int numberNegativesOnes = 0;
        long[] shape = ArrayUtil.copy(newShape);


        for (int i = 0; i < shape.length; i++) {
            if (shape[i] < 0) {
                if (numberNegativesOnes >= 1)
                    throw new IllegalArgumentException("Only one dimension can be negative ones. Got shape "
                            + Arrays.toString(newShape));

                numberNegativesOnes++;

                int shapeLength = 1;
                for (int j = 0; j < shape.length; j++)
                    if (shape[j] >= 1)
                        shapeLength *= shape[j];
                long realShape = Math.abs(length() / shapeLength);
                long[] thisNewShape = new long[shape.length];
                for (int j = 0; j < shape.length; j++) {
                    if (i != j) {
                        thisNewShape[j] = shape[j];
                    } else
                        thisNewShape[j] = realShape;
                }

                shape = thisNewShape;
                break;

            }
        }

        long prod = ArrayUtil.prodLong(shape);

        if (prod != this.length()){
            throw new ND4JIllegalStateException("New shape length doesn't match original length: [" + prod + "] vs [" + this.length() + "]. Original shape: "+Arrays.toString(this.shape())+" New Shape: "+Arrays.toString(newShape));
        }





        INDArray reshapeAttempt = Shape.newShapeNoCopy(this, shape, order == 'f');
        if (reshapeAttempt != null) {
            // kinda strange get/set usage
            //  reshapeAttempt.setOrder(Shape.getOrder(reshapeAttempt));
            return reshapeAttempt;
        }

        if(enforceView){
            throw new ND4JIllegalStateException("Unable to reshape array as view, called with enforceView=true. " +
                    "Use enforceView=false to return a copy instead, or call reshape on a non-strided array. Array shape info: " + this.shapeInfoToString().replaceAll("\n",""));
        }


        if (order != ordering()) {
            INDArray ret = Nd4j.createUninitialized(this.dataType(), shape, order);
            ret.setData(dup(order).data());
            return ret;
        } else if (this.isEmpty()) {
            return Nd4j.create(this.dataType(), shape);
        } else {
            INDArray ret = this.dup(order);
            return Nd4j.create(ret.data(), shape);
        }
    }

    @Override
    public double getDoubleUnsafe(long offset) {
        return data().getDouble(offset);
    }

    @Override
    public INDArray putScalarUnsafe(long offset, double value) {
        autoProcessScalarCall();
        data().put(offset, value);
        return this;
    }

    @Override
    public INDArray reshape(char order, int rows, int columns) {
        return reshape(order, new long[] {rows, columns});
    }

    /**
     * Reshape the ndarray in to the specified dimensions,
     * possible errors being thrown for invalid shapes
     *
     * Note here that one dimension can be -1.
     * The dimension that is -1 will be inferred from the shape and
     * the length of the ndarray
     *
     * @param shape the shape of the ndarray.
     * @return the new reshaped nd array
     */

    @Override
    public INDArray reshape(int[] shape) {
        return reshape(Nd4j.order(), shape);
    }

    @Override
    public INDArray reshape(long... shape) {
        return reshape(Nd4j.order(), shape);
    }

    @Override
    public INDArray prod(boolean keepDims, int... dimension) {
        validateNumericalArray("prod", false);
        return Nd4j.getExecutioner().exec(new Prod(this, keepDims, dimension));
    }

    @Override
    public INDArray prod(int... dimension) {
        return prod(false, dimension);
    }

    @Override
    public INDArray mean(boolean keepDims, int... dimension) {
        validateNumericalArray("mean", false);
        return Nd4j.getExecutioner().exec(new Mean(this, keepDims, dimension));
    }

    @Override
    public INDArray mean(int... dimension) {
        return mean(false, dimension);
    }

    @Override
    public INDArray amean(int... dimension) {
        validateNumericalArray("amean", false);
        return Nd4j.getExecutioner().exec(new AMean(this, dimension));
    }

    @Override
    public INDArray mean(@NonNull INDArray result, boolean keepDims, int... dimension) {
        validateNumericalArray("mean", false);
        return Nd4j.getExecutioner().exec(new Mean(this, result, keepDims, dimension));
    }

    @Override
    public INDArray mean(@NonNull INDArray result, int... dimension) {
        return mean(result, false, dimension);
    }

    @Override
    public INDArray var(int... dimension) {
        validateNumericalArray("var", false);
        return Nd4j.getExecutioner().exec(new Variance(this, dimension));
    }

    @Override
    public INDArray var(boolean biasCorrected, int... dimension) {
        validateNumericalArray("var", false);
        return Nd4j.getExecutioner().exec(new Variance(this, biasCorrected, dimension));
    }

    @Override
    public INDArray max(boolean keepDims, int... dimension) {
        validateNumericalArray("max", false);
        return Nd4j.getExecutioner().exec(new Max(this, keepDims, dimension));
    }

    @Override
    public INDArray max(int... dimension) {
        return max(false, dimension);
    }

    @Override
    public INDArray amax(int... dimension) {
        validateNumericalArray("amax", false);
        return Nd4j.getExecutioner().exec(new AMax(this, dimension));
    }

    @Override
    public INDArray min(boolean keepDims, int... dimension) {
        validateNumericalArray("min", false);
        return Nd4j.getExecutioner().exec(new Min(this, keepDims, dimension));
    }

    @Override
    public INDArray min(int... dimension) {
        return min(false, dimension);
    }

    @Override
    public INDArray amin(int... dimension) {
        validateNumericalArray("amin", false);
        return Nd4j.getExecutioner().exec(new AMin(this, dimension));
    }

    @Override
    public INDArray sum(int... dimension) {
        validateNumericalArray("sum", true);
        return Nd4j.getExecutioner().exec(new Sum(this, dimension));
    }

    @Override
    public INDArray sum(boolean keepDim, int... dimension) {
        validateNumericalArray("sum", true);
        return Nd4j.getExecutioner().exec(new Sum(this, null, keepDim, dimension));
    }

    @Override
    public INDArray entropy(int... dimension) {
        validateNumericalArray("entropy", false);
        return Nd4j.getExecutioner().exec(new Entropy(this, dimension));
    }

    @Override
    public INDArray shannonEntropy(int... dimension) {
        validateNumericalArray("shannonEntropy", false);
        return Nd4j.getExecutioner().exec(new ShannonEntropy(this, dimension));
    }

    @Override
    public INDArray logEntropy(int... dimension) {
        validateNumericalArray("logEntropy", false);
        return Nd4j.getExecutioner().exec(new LogEntropy(this, dimension));
    }

    @Override
    public INDArray sum(@NonNull INDArray result, boolean keepDims, int... dimension) {
        validateNumericalArray("sum", true);
        return Nd4j.getExecutioner().exec(new Sum(this, result, keepDims, dimension));
    }

    @Override
    public INDArray sum(@NonNull INDArray result, int... dimension) {
        return sum(result, false, dimension);
    }

    @Override
    public INDArray norm1(int... dimension) {
        return norm1(false, dimension);
    }

    @Override
    public INDArray norm1(boolean keepDims, int... dimension) {
        validateNumericalArray("norm1", false);
        return Nd4j.getExecutioner().exec(new Norm1(this, keepDims, dimension));
    }

    @Override
    public INDArray std(int... dimension) {
        return std(true, dimension);
    }

    @Override
    public INDArray std(boolean biasCorrected, int... dimension) {
        return std(biasCorrected, false, dimension);
    }

    @Override
    public INDArray std(boolean biasCorrected, boolean keepDims, int... dimension) {
        validateNumericalArray("std", false);
        return Nd4j.getExecutioner().exec(new StandardDeviation(this, biasCorrected, keepDims, dimension));
    }

    @Override
    public Number stdNumber(boolean biasCorrected) {
        validateNumericalArray("stdNumber", false);
        return Nd4j.getExecutioner().exec(new StandardDeviation(this, biasCorrected)).getDouble(0);
    }

    @Override
    public INDArray norm2(boolean keepDims, int... dimension) {
        validateNumericalArray("norm2", false);
        return Nd4j.getExecutioner().exec(new Norm2(this, keepDims, dimension));
    }

    @Override
    public INDArray norm2(int... dimension) {
        return norm2(false, dimension);
    }

    @Override
    public int columns() {
        if (isMatrix())
            return (int) size(1);
        else if (Shape.isColumnVectorShape(shape())) {
            return 1;
        } else if (Shape.isRowVectorShape(shape())) {
            return (int) length();
        }
        throw new IllegalStateException("Rank is [" + rank() + "]; columns() call is not valid");


    }

    @Override
    public int rows() {
        if (isMatrix())
            return (int) size(0);
        else if (Shape.isRowVectorShape(shape())) {
            return 1;
        } else if (Shape.isColumnVectorShape(shape())) {
            return (int) length();
        }

        throw new IllegalStateException("Rank is " + rank() + " rows() call is not valid");
    }

    @Override
    public INDArray ravel(char ordering) {
        Nd4j.getCompressor().autoDecompress(this);
        if(ordering == this.ordering() && Shape.hasDefaultStridesForShape(this)){
            return reshape(ordering, length());
        }
        return dup(ordering).reshape(ordering, length());
    }

    @Override
    public INDArray ravel() {
        return reshape(length());
    }

    @Override
    public void sliceVectors(List<INDArray> list) {
        if (isVector())
            list.add(this);
        else {
            for (int i = 0; i < slices(); i++) {
                slice(i).sliceVectors(list);
            }
        }
    }

    @Override
    public INDArray reshape(long newRows, long newColumns) {
        return reshape(new long[] {newRows, newColumns});
    }

    @Override
    public INDArray getColumn(long c) {
        Nd4j.getCompressor().autoDecompress(this);

        if (isColumnVector() && c == 0)
            return this;
        else if (isColumnVector() && c > 0)
            throw new IllegalArgumentException("Illegal index for column");
        Preconditions.checkArgument(this.rank() == 2, "getColumn() can be called on 2D arrays only");
        return tensorAlongDimension(c, 0);
    }

    @Override
    public INDArray getColumn(long c, boolean keepDim) {
        INDArray col = getColumn(c);
        if(!keepDim)
            return col;
        return col.reshape(col.length(), 1);
    }

    @Override
    public INDArray getRows(int[] rindices) {
        Nd4j.getCompressor().autoDecompress(this);

        if (!isMatrix() && !isVector())
            throw new IllegalArgumentException("Unable to get columns from a non matrix or vector");
        if (isVector())
            return Nd4j.pullRows(this, 1, rindices);
        else {
            INDArray ret = Nd4j.createUninitialized(this.dataType(), new long[] {rindices.length, columns()});
            for (int i = 0; i < rindices.length; i++)
                ret.putRow(i, getRow(rindices[i]));
            return ret;
        }
    }

    @Override
    public INDArray get(INDArrayIndex... indexes) {
        Nd4j.getCompressor().autoDecompress(this);

        int numPoint = 0;
        int numInterval = 0;
        int numAll = 0;
        int numNewAxis = 0;
        int numSpecified = 0;
        for(INDArrayIndex i : indexes){
            if(i instanceof PointIndex){
                numPoint++;
            } else if(i instanceof NDArrayIndexAll){
                numAll++;
            } else if(i instanceof IntervalIndex){
                numInterval++;
            } else if(i instanceof NewAxis){
                numNewAxis++;
            } else if(i instanceof SpecifiedIndex){
                numSpecified++;
            } else {
                throw new IllegalStateException("Unknown index: " + i);
            }
        }

        // Padding remaining dimensions with all() index if too few indices provided
        if (indexes.length - numNewAxis < this.rank()) {
            INDArrayIndex[] newIndexes = new INDArrayIndex[this.rank() + numNewAxis];
            for (int e = 0; e < indexes.length; e++)
                newIndexes[e] = indexes[e];

            for (int e = indexes.length; e < newIndexes.length; e++) {
                numAll++;
                newIndexes[e] = NDArrayIndex.all();
            }

            indexes = newIndexes;
        }

        Preconditions.checkState((numPoint + numInterval + numAll + numSpecified) == rank(), "Illegal set of indices for array: need at least" +
                " %s point/interval/all/specified indices for rank %s array (%ndShape), got indices %s", rank(), rank(), this, indexes);

        int outRank = rank() + numNewAxis - numPoint;
        Preconditions.checkState(outRank >= 0, "Illegal set of indices for array: %ndShape, %s", this, indexes);


        //To work out sub-array, we need to work out 3 things: offset, shape and strides. We calculate all of these
        long[] outShape = new long[outRank];
        long[] outStrides = new long[outRank];
        long offset = offset();                     //Start with existing offset if view

        int outIdx = 0;     //Axis number counter for output array
        int inIdx = 0;      //Axis number counter for input array
        for( int i=0; i<indexes.length; i++ ){
            if(indexes[i] instanceof PointIndex){
                //Point indexes don't appear in output
                PointIndex pi = (PointIndex)indexes[i];
                offset += pi.offset() * stride(inIdx);
                inIdx++;
            } else if(indexes[i] instanceof NDArrayIndexAll){
                //All index: doesn't change offset. Axis is in both in and output arrays
                outShape[outIdx] = size(inIdx);
                outStrides[outIdx] = stride(inIdx);
                inIdx++;
                outIdx++;
            } else if(indexes[i] instanceof IntervalIndex){
                //Interval index: Axis is in both in and output arrays, but output might be smaller
                IntervalIndex ii = (IntervalIndex)indexes[i];
                long start = ii.offset();
                long endInc = ii.end() - (ii.isInclusive() ? 0 : 1);
                if (endInc >= size(inIdx)) {
                    throw new IllegalStateException("Indices are out of range: Cannot get interval index " + indexes[i] +
                            " on array with size(" + inIdx + ")=" + size(inIdx) + ". Array shape: " + Arrays.toString(shape()) +
                            ", indices: " + Arrays.toString(indexes));
                }
                long stride = ii.stride();
                long length = (endInc - start)/stride + 1;

                offset += ii.offset() * stride(inIdx);
                outShape[outIdx] = length;
                outStrides[outIdx] = ii.stride() * stride(inIdx);
                inIdx++;
                outIdx++;
            } else if(indexes[i] instanceof NewAxis) {
                //New axis: appends a 1 in shape. Axis not present in input, but is present in output
                outShape[outIdx] = 1;
                if (outIdx > 0) { //Stride doesn't matter for 1 size axis anyway...
                    outStrides[outIdx] = outStrides[outIdx - 1];
                } else {
                    outStrides[outIdx] = 1;
                }
                outIdx++;
            } else if(indexes[i] instanceof SpecifiedIndex){
                //Specified index: axis present in both input and output
                SpecifiedIndex si = (SpecifiedIndex)indexes[i];
                outShape[outIdx++] = si.length();
                inIdx++;
                //Don't care about strides for specified index, as result won't be a view
            } else {
                throw new IllegalStateException("Unknown index type: " + i);    //Should never happen
            }
        }


        //Note: If we have specified indices, we can't return a view. Instead, we copy the specified sub-arrays from
        // the input array to the output array.
        //How? Create the output array, then do loop over the specified indices only, and copy sub-arrays for all other axes
        if (numSpecified > 0) {
            INDArray out = Nd4j.create(dataType(), outShape);

            //Need to copy subsets here
            long[] specifiedSizes = new long[numSpecified];
            SpecifiedIndex[] si = new SpecifiedIndex[numSpecified];
            int j=0;
            for( int i=0; i<indexes.length; i++ ){
                if(indexes[i] instanceof SpecifiedIndex){
                    specifiedSizes[j] = indexes[i].length();
                    si[j] = (SpecifiedIndex)indexes[i];
                    j++;
                }
            }
            NdIndexIterator iter = new NdIndexIterator(specifiedSizes);

            //What we need to do here: Iterate over sub-arrays for both input and output
            //(1) Get from input: requested indices, except for:
            //    i. specified indices -> replace with loop + point
            //    ii. new axis indices -> ignore/exclude (don't appear in input)
            //    iii. interval indices -> replace with all
            //(2) Get from output: requested indices, except for:
            //    i. point indices -> ignore/exclude (don't appear in output)
            //    ii. new axis indices -> replace with point(0)


            INDArrayIndex[] pointIdxsIn = new INDArrayIndex[indexes.length - numNewAxis];       //Indices for source (this array)
            int[] specifiedAxisIn = new int[numSpecified];
            int specCount = 0;
            j = 0;
            for( int i=0; i<indexes.length; i++ ){
                if(indexes[i] instanceof NewAxis)
                    continue;   //Skip new axis in source dims
                if(indexes[i] instanceof SpecifiedIndex)
                    specifiedAxisIn[specCount++] = j;
                pointIdxsIn[j++] = indexes[i];
            }

            INDArrayIndex[] pointIdxsOut = new INDArrayIndex[indexes.length-numPoint];          //Indices for destination (output array)
            j = 0;
            specCount = 0;
            int[] specifiedAxisOut = new int[numSpecified];
            for( int i=0; i<indexes.length; i++ ){
                if(indexes[i] instanceof NewAxis){
                    pointIdxsOut[j++] = NDArrayIndex.point(0);
                    continue;
                } else if(indexes[i] instanceof PointIndex){
                    continue;
                } else if(indexes[i] instanceof SpecifiedIndex){
                    specifiedAxisOut[specCount++] = j;
                } else if(indexes[i] instanceof IntervalIndex){
                    pointIdxsOut[j++] = NDArrayIndex.all();
                    continue;
                }
                pointIdxsOut[j++] = indexes[i];
            }


            //Iterate over sub-arrays; copy from source to destination
            while( iter.hasNext()){
                long[] specifiedIdxs = iter.next();
                for( int i=0; i<specifiedIdxs.length; i++ ){
                    long sourceIdx = si[i].getIndexes()[(int)specifiedIdxs[i]];
                    pointIdxsIn[specifiedAxisIn[i]] = NDArrayIndex.point(sourceIdx);
                    int outI = (int)specifiedIdxs[i];
                    pointIdxsOut[specifiedAxisOut[i]] = NDArrayIndex.point(outI);
                }

                out.get(pointIdxsOut).assign(get(pointIdxsIn));
            }

            return out;
        }


        char order = Shape.getOrder(outShape, outStrides, -1);
        INDArray out = create(data, outShape, outStrides, offset, order);
        return out;
    }

    @Override
    public INDArray getColumns(int... cindices) {
        if (!isMatrix() && !isVector())
            throw new IllegalArgumentException("Unable to get columns from a non matrix or vector");
        if (isVector()) {
            return Nd4j.pullRows(this, 0, cindices, this.ordering());
        } else {
            INDArray ret = Nd4j.createUninitialized(this.dataType(), new long[]{rows(), cindices.length});
            for (int i = 0; i < cindices.length; i++)
                ret.putColumn(i, getColumn(cindices[i]));
            return ret;
        }

    }

    protected INDArray create(int rows, int length) {
        return create(new int[] {rows, length});
    }

    @Override
    public INDArray getRow(long r) {
        if (isRowVector() && r == 0)
            return this;
        else if (isRowVector() && r > 0)
            throw new IllegalArgumentException("Illegal index for row: requested row " + r + " but this.size(0)=" + this.size(0));

        Preconditions.checkArgument(rank() == 2, "getRow() can be called on 2D arrays only");
        Preconditions.checkArgument(r < rows(), "Row index must be smaller than total number of rows");

        return tensorAlongDimension(r, 1);
    }

    @Override
    public INDArray getRow(long r, boolean keepDim) {
        INDArray row = getRow(r);
        if(!keepDim)
            return row;
        return row.reshape(1, row.length());
    }

    public boolean equalsWithEps(Object o, double eps) {
        Nd4j.getCompressor().autoDecompress(this);


        if (o == null)
            return false;

        if (!(o instanceof INDArray))
            return false;

        INDArray n = (INDArray) o;
        Nd4j.getCompressor().autoDecompress(n);

        if (n == this)
            return true;

        if (this.rank() != n.rank())
            return false;

        if (this.length() != n.length())
            return false;

        if (this.isEmpty() != n.isEmpty())
            return false;

        if (this.isEmpty() && n.isEmpty())
            return Shape.shapeEquals(this.shape(), n.shape());

        if (this.dataType() != n.dataType())
            return false;

        // meh
        if (this.dataType() == DataType.UTF8 && n.dataType() == DataType.UTF8) {
            for (long e = 0; e < this.length(); e++) {
                String str1 = this.getString(e);
                String str2 = n.getString(e);

                if (!str1.equals(str2))
                    return false;
            }

            return true;
        }

        //epsilon equals
        if (isScalar() && n.isScalar()) {
            if (isZ()) {
                long val = getLong(0);
                long val2 =  n.getLong(0);

                return val == val2;
            } else if (isR()) {
                double val = getDouble(0);
                double val2 = n.getDouble(0);

                if (Double.isNaN(val) != Double.isNaN(val2))
                    return false;

                return Math.abs(val - val2) < eps;
            } else if (isB()) {
                int val = getInt(0);
                int val2 =  n.getInt(0);

                return val == val2;
            }

        } else if (isVector() && n.isVector()) {
            EqualsWithEps op = new EqualsWithEps(this, n, eps);
            Nd4j.exec(op);
            double diff = op.z().getDouble(0);

            return diff < 0.5;
        }

        if (!Arrays.equals(this.shape(), n.shape()))
            return false;


        if (!Shape.shapeEquals(shape(), n.shape())) {
            return false;
        }


        if (slices() != n.slices())
            return false;

        if (n.ordering() == ordering()) {
            EqualsWithEps op = new EqualsWithEps(this, n, eps);
            Nd4j.getExecutioner().exec(op);
            double diff = op.z().getDouble(0);

            return diff < 0.5;
        } else {
            EqualsWithEps op = new EqualsWithEps(this, n, eps);
            Nd4j.getExecutioner().exec(op);
            double diff = op.z().getDouble(0);

            return diff < 0.5;
        }
    }

    @Override
    public boolean equalShapes(@NonNull INDArray other){
        if(isEmpty() != other.isEmpty())
            return false;
        if(rank() != other.rank())
            return false;
        for( int i=0; i<rank(); i++ ){
            if(size(i) != other.size(i)){
                return false;
            }
        }
        return true;
    }

    /**
     * Compare two matrices. Returns true if and only if other is also a
     * DoubleMatrix which has the same size and the maximal absolute
     * difference in matrix elements is smaller than 1e-5.
     *
     * @param o
     */
    @Override
    public boolean equals(Object o) {
        return equalsWithEps(o, Nd4j.EPS_THRESHOLD);
    }

    @Override
    public int hashCode() {
        long  longHash = Nd4j.exec(new HashCode(this))[0].getLong(0);
        return Math.abs(longHash) <= Integer.MAX_VALUE ? (int) longHash : (int) (longHash % Integer.MAX_VALUE);
    }

    @Override
    public DataBuffer shapeInfoDataBuffer() {
        return shapeInformation;
    }

    @Override
    public LongBuffer shapeInfo() {
        return shapeInformation.asNioLong();
    }

    public long[] shape() {
        return jvmShapeInfo.shape;
    }

    @Override
    public String shapeInfoToString() {
        return Shape.shapeToString(this);
    }

    @Override
    public long[] stride() {
        return jvmShapeInfo.stride;
    }


    @Override
    public long offset() {
        return data().offset();
    }

    @Override
    public char ordering() {
        return jvmShapeInfo.order;
    }

    @Override
    public long size(int dimension) {
        if (dimension < 0)
            dimension += jvmShapeInfo.rank;

        if (isScalar()) {
            if (dimension == 0 || dimension == 1 || dimension < 0)
                return length();
            else
                throw new IllegalArgumentException("Illegal dimension for scalar " + dimension);
        }

        if (dimension >= rank())
            throw new IllegalArgumentException("Invalid size: cannot get size of dimension " + dimension + " for rank "
                    + rank() + " NDArray (array shape: " + Arrays.toString(this.shape()) + ")");

        return jvmShapeInfo.shape[dimension];
    }

    @Override
    public int rank() {
        return jvmShapeInfo.rank;
    }

    @Override
    public long length() {
        if (isEmpty())
            return 0;
        return jvmShapeInfo.length;
    }

    @Override
    public INDArray broadcast(INDArray result) {
        Nd4j.getCompressor().autoDecompress(this);

        long[] shape = result.shape();

        if (Shape.shapeEquals(shape, shape()))
            return this;

        // if we're on scalar, we can just create new array
        if (this.isScalar())
            return Nd4j.createUninitialized(this.dataType(), shape).assign(this.getDouble(0));




        boolean compatible = true;
        int count = shape.length - 1;
        int thisCount = jvmShapeInfo.rank - 1;
        for (int i = shape.length - 1; i > 0; i--) {
            if (count < 0 || thisCount < 0)
                break;
            if (shape[count] != shape()[thisCount] && shape[count] != 1 && shape()[thisCount] != 1) {
                compatible = false;
                break;
            }

            count--;
            thisCount--;
        }

        if (!compatible)
            throw new IllegalArgumentException("Incompatible broadcast from " + Arrays.toString(shape()) + " to "
                    + Arrays.toString(shape));



        long[] retShape = new long[shape.length];
        List<Integer> broadCastDimensions = new ArrayList<>();
        List<Integer> nonBroadCastDimensions = new ArrayList<>();
        for (int i = 0; i < retShape.length; i++) {
            if (shape().length == 1) {
                if (i == 0) {
                    if (i < shape().length)
                        retShape[i] = Math.max(1, shape[i]);
                    else
                        retShape[i] = shape[i];
                } else {
                    if (i < shape().length)
                        retShape[i] = Math.max(shape[i], size(i));
                    else
                        retShape[i] = shape[i];
                }
            } else {
                if (i < rank() && size(i) == 1)
                    broadCastDimensions.add(i);
                else
                    nonBroadCastDimensions.add(i);
                if (i < shape().length)
                    retShape[i] = Math.max(shape[i], size(i));
                else
                    retShape[i] = shape[i];
            }

        }


        if (isRowVector()) {
            //number of times to repeat each value
            for (int i = 0; i < result.slices(); i++) {
                result.putSlice(i, this);
            }
        } else if (isColumnVector()) {
            for (int i = 0; i < result.columns(); i++) {
                result.putColumn(i, this);
            }
        }

        else {
            int[] repeat = new int[shape.length];
            for(int i = 0; i < shape.length; i++) {
                if(i < rank()) {
                    if(size(i) == 1)
                        repeat[i] = (int) shape[i];
                    else {
                        repeat[i] = 1;
                    }
                }

                else {
                    repeat[i] = (int) shape[i];
                }
            }

            if (this.isView()) {
                Nd4j.getExecutioner().execAndReturn(new Tile(new INDArray[]{this.dup(this.ordering())},new INDArray[]{result},repeat));
            } else
                Nd4j.getExecutioner().execAndReturn(new Tile(new INDArray[]{this},new INDArray[]{result},repeat));
        }
        return result;

    }

    @Override
    public INDArray broadcast(long... shape) {
      return broadcast(Nd4j.createUninitialized(this.dataType(), shape, this.ordering()));
    }

    @Deprecated
    @Override
    public INDArray dimShuffle(Object[] rearrange, int[] newOrder, boolean[] broadCastable) {
        return dimShuffle(rearrange, ArrayUtil.toLongArray(newOrder), broadCastable);
    }

    /**
     * Dimshuffle: an extension of permute that adds the ability
     * to broadcast various dimensions.
     * <p/>
     * See theano for more examples.
     * This will only accept integers and xs.
     * <p/>
     * An x indicates a dimension should be broadcasted rather than permuted.
     *
     * @param rearrange the dimensions to swap to
     * @return the newly permuted array
     */
    @Override
    public INDArray dimShuffle(Object[] rearrange, long[] newOrder, boolean[] broadCastable) {
        Nd4j.getCompressor().autoDecompress(this);

        if (broadCastable.length != jvmShapeInfo.rank)
            throw new IllegalArgumentException(
                    "The broadcastable dimensions must be the same length as the current shape");

        boolean broadcast = false;
        Set<Object> set = new HashSet<>();
        for (int i = 0; i < rearrange.length; i++) {
            set.add(rearrange[i]);
            if (rearrange[i] instanceof Integer) {
                Integer j = (Integer) rearrange[i];
                if (j >= broadCastable.length)
                    throw new IllegalArgumentException(
                            "Illegal dimension, dimension must be < broadcastable.length (aka the real dimensions");
            } else if (rearrange[i] instanceof Character) {
                Character c = (Character) rearrange[i];
                if (c != 'x')
                    throw new IllegalArgumentException("Illegal input: Must be x");
                broadcast = true;

            } else
                throw new IllegalArgumentException("Only characters and integers allowed");
        }

        //just do permute
        if (!broadcast) {
            int[] ret = new int[rearrange.length];
            for (int i = 0; i < ret.length; i++)
                ret[i] = (Integer) rearrange[i];
            return permute(ret);
        } else {
            List<Integer> drop = new ArrayList<>();
            for (int i = 0; i < broadCastable.length; i++) {
                if (!set.contains(i)) {
                    if (broadCastable[i])
                        drop.add(i);
                    else
                        throw new IllegalArgumentException(
                                "We can't drop the given dimension because its not broadcastable");
                }

            }


            //list of dimensions to keep
            int[] shuffle = new int[broadCastable.length];
            int count = 0;
            for (int i = 0; i < rearrange.length; i++) {
                if (rearrange[i] instanceof Integer) {
                    shuffle[count++] = (Integer) rearrange[i];
                }
            }


            List<Integer> augment = new ArrayList<>();
            for (int i = 0; i < rearrange.length; i++) {
                if (rearrange[i] instanceof Character)
                    augment.add(i);
            }

            Integer[] augmentDims = augment.toArray(new Integer[1]);

            count = 0;

            int dropIdx = 0;
            int[] newShape = new int[shuffle.length + drop.size()];
            for (int i = 0; i < newShape.length; i++) {
                if (i < shuffle.length) {
                    newShape[count++] = shuffle[i];
                } else
                    newShape[count++] = drop.get(dropIdx++);
            }

            INDArray ret;   //TODO is this correct? This was old behaviour before adding permute input check
            if(newShape.length == this.rank()){
                ret = permute(newShape);
            } else {
                ret = dup();
            }
            List<Long> newDims = new ArrayList<>();
            long[] shape = Arrays.copyOfRange(ret.shape(), 0, shuffle.length);
            for (int i = 0; i < shape.length; i++) {
                newDims.add(shape[i]);
            }

            for (int i = 0; i < augmentDims.length; i++) {
                newDims.add(augmentDims[i], 1L);
            }

            long[] toReshape = ArrayUtil.toArrayLong(newDims);


            ret = ret.reshape(toReshape);
            return ret;

        }


    }

    @Override
    public INDArray permute(int... rearrange) {
        Preconditions.checkArgument(rearrange.length == rank(), "Incorrect number of arguments for permute function:" +
                " got arguments %s for rank %s array. Number of arguments must equal array rank", rearrange, rank());
        Nd4j.getCompressor().autoDecompress(this);
        boolean alreadyInOrder = true;
        //IntBuffer shapeInfo = shapeInfo();
        int rank = jvmShapeInfo.rank;
        for (int i = 0; i < rank; i++) {
            if (rearrange[i] != i) {
                alreadyInOrder = false;
                break;
            }
        }

        if (alreadyInOrder)
            return this;

        checkArrangeArray(rearrange);
        long[] newShape = doPermuteSwap(shape(), rearrange);
        long[] newStride = doPermuteSwap(stride(), rearrange);

        char newOrder = Shape.getOrder(newShape, newStride, 1);

        INDArray value = create(data(), newShape, newStride, offset(), newOrder);
        return value;
    }

    @Override
    public INDArray permutei(int... rearrange) {
        Preconditions.checkArgument(rearrange.length == rank(), "Incorrect number of arguments for permute function:" +
                " got arguments %s for rank %s array. Number of arguments must equal array rank", rearrange, rank());
        boolean alreadyInOrder = true;
        LongBuffer shapeInfo = shapeInfo();
        int rank = jvmShapeInfo.rank;
        for (int i = 0; i < rank; i++) {
            if (rearrange[i] != i) {
                alreadyInOrder = false;
                break;
            }
        }

        if (alreadyInOrder)
            return this;

        checkArrangeArray(rearrange);
        long[] newShape = doPermuteSwap(shape(), rearrange);
        long[] newStride = doPermuteSwap(stride(), rearrange);
        char newOrder = Shape.getOrder(newShape, newStride, 1);

        long ews = shapeInfo.get(2 * rank + 2);

        Pair<DataBuffer,long[]> si = Nd4j.getShapeInfoProvider().createShapeInformation(newShape, newStride,  ews, newOrder, dataType(), isEmpty());
        setShapeInformation(si);


        if (shapeInfo.get(2 * rank + 2) > 0) {
            //for the backend to work - no ews for permutei
            //^^ not true anymore? Not sure here. Marking this for raver
            setShapeInformation(Nd4j.getShapeInfoProvider().createShapeInformation(newShape, newStride, 0, newOrder, dataType(), isEmpty()));
        }

        //this.shape = null;
        //this.stride = null;


        return this;
    }


    @Deprecated
    protected long[] doPermuteSwap(LongBuffer shape, int[] rearrange) {
        long[] ret = new long[rearrange.length];
        for (int i = 0; i < rearrange.length; i++) {
            ret[i] = shape.get(rearrange[i]);
        }
        return ret;
    }

    @Deprecated
    protected int[] doPermuteSwap(IntBuffer shape, int[] rearrange) {
        int[] ret = new int[rearrange.length];
        for (int i = 0; i < rearrange.length; i++) {
            ret[i] = shape.get(rearrange[i]);
        }
        return ret;
    }

    @Deprecated
    protected int[] doPermuteSwap(DataBuffer shape, int[] rearrange) {
        int[] ret = new int[rearrange.length];
        for (int i = 0; i < rearrange.length; i++) {
            ret[i] = shape.getInt(rearrange[i]);
        }
        return ret;
    }

    protected long[] doPermuteSwap(long[] shape, int[] rearrange) {
        long[] ret = new long[rearrange.length];
        for (int i = 0; i < rearrange.length; i++) {
            ret[i] = shape[rearrange[i]];
        }

        return ret;
    }


    protected void checkArrangeArray(int[] arr) {
        Preconditions.checkArgument(arr.length == jvmShapeInfo.rank, "Invalid rearrangement: number of arrangement (%s) != rank (%s)",
                arr.length, jvmShapeInfo.rank);
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] >= arr.length)
                throw new IllegalArgumentException("The specified dimensions can't be swapped. Given element " + i
                        + " was >= number of dimensions");
            if (arr[i] < 0)
                throw new IllegalArgumentException("Invalid dimension: " + i + " : negative value");


        }

        for (int i = 0; i < arr.length; i++) {
            for (int j = 0; j < arr.length; j++) {
                if (i != j && arr[i] == arr[j])
                    throw new IllegalArgumentException("Permute array must have unique elements");
            }
        }

    }

    protected void autoProcessScalarCall() {
       /* if (Nd4j.getExecutioner().getProfilingMode() != OpExecutioner.ProfilingMode.DISABLED && Nd4j.getExecutioner().getProfilingMode() != OpExecutioner.ProfilingMode.SCOPE_PANIC)
            OpProfiler.getInstance().processScalarCall();*/
    }

    /**
     * Checks whether the matrix is a vector.
     */
    @Override
    public boolean isVector() {
        if (jvmShapeInfo.rank == 1)
            return true;

        return isRowVector() || isColumnVector();
    }

    @Override
    public boolean isVectorOrScalar() {
        return isVector() || isScalar();
    }

    @Override
    public boolean isSquare() {
        return isMatrix() && rows() == columns();
    }

    @Override
    public boolean isRowVector() {
        return (rank() == 2 && rows() == 1) && length() > 1 || rank() == 1 && length() > 1;
    }

    @Override
    public boolean isColumnVector() {
        return rank() == 2 && columns() == 1 && length() > 1;
    }

    @Override
    public boolean isColumnVectorOrScalar() {
        return isColumnVector() || isScalar();
    }

    @Override
    public boolean isRowVectorOrScalar() {
        return isRowVector() || isScalar();
    }

    /**
     * Generate string representation of the matrix.
     * Printing will switch to scientific notation on a per element basis
     *      - when abs value is greater than or equal to 10000
     *      - when abs value is less than or equal to 0.0001 and not zero
     *
     *  If the number of elements in the array is greater than 1000 (by default) only the first and last three elements
     *  in a dimension are included. This can be changed globally using {@link NDArrayStrings#setMaxPrintElements(long)}
     *
     *
     */
    @Override
    public String toString() {
        return toString(new NDArrayStrings());
    }


    @Override
    public String toString(@NonNull NDArrayStrings options){
        if(wasClosed())
            return "<Closed NDArray, id=" + getId() + ", dtype=" + dataType() + ", shape=" + Arrays.toString(shape()) + ">";
        if (!isCompressed() && !preventUnpack)
            return options.format(this);
        else if (isCompressed() && compressDebug)
            return "COMPRESSED ARRAY. SYSTEM PROPERTY compressdebug is true. This is to prevent auto decompression from being triggered.";
        else if (preventUnpack)
            return "Array string unpacking is disabled.";
        return options.format(this);
    }

    @Override
    public String toString(long maxElements, boolean forceSummarize, int precision){
        return toString(new NDArrayStrings(maxElements, forceSummarize, precision));
    }


    @Override
    public String toStringFull(){
        return toString(Long.MAX_VALUE, false, -1 * dataType().precision());
    }

    @Override
    public Object element() {

        if (!isScalar())
            throw new IllegalStateException("Unable to retrieve element from non scalar matrix");
        if (data.dataType() == DataType.FLOAT)
            return data.getFloat(0);
        return data.getDouble(0);
    }

    @Override
    public INDArray remainder(INDArray denominator) {
        if (Shape.areShapesBroadcastable(this.shape(), denominator.shape())) {
            return remainder(denominator, Nd4j.createUninitialized(this.dataType(), Shape.broadcastOutputShape(this.shape(), denominator.shape())));
        } else
            return remainder(denominator, this.ulike());
    }

    @Override
    public INDArray remainder(INDArray denominator, INDArray result) {
        validateNumericalArray("remainder", false);
        Preconditions.checkArgument(Shape.areShapesBroadcastable(this.shape(), denominator.shape()),"Shapes must be broadcastable");

        RemainderOp op = new RemainderOp(this, denominator, result);
        Nd4j.getExecutioner().exec(op);
        return result;
    }

    @Override
    public INDArray remainder(Number denominator) {
        return remainder(denominator, Nd4j.createUninitialized(this.dataType(), this.shape()));
    }

    @Override
    public INDArray remainder(Number denominator, INDArray result) {
        validateNumericalArray("remainder", false);

        ScalarRemainder op = new ScalarRemainder(this, null, result, denominator);
        Nd4j.getExecutioner().exec(op);
        return result;
    }

    @Override
    public INDArray remainderi(INDArray denominator) {
        validateNumericalArray("remainderi", false);
        RemainderOp op = new RemainderOp(this, denominator, this);
        Nd4j.getExecutioner().exec(op);
        return this;
    }

    @Override
    public INDArray remainderi(Number denominator) {
        validateNumericalArray("remainderi", false);
        ScalarRemainder op = new ScalarRemainder(this, null, this, denominator);
        Nd4j.getExecutioner().exec(op);
        return this;
    }

    @Override
    public INDArray fmod(INDArray denominator) {
        validateNumericalArray("fmod", false);
        if (Shape.areShapesBroadcastable(this.shape(), denominator.shape())) {
            return fmod(denominator, Nd4j.createUninitialized(Nd4j.defaultFloatingPointType(), Shape.broadcastOutputShape(this.shape(), denominator.shape())));
        } else
            return fmod(denominator, this.ulike());
    }

    @Override
    public INDArray fmod(INDArray denominator, INDArray result) {
        validateNumericalArray("fmod", false);
        if (Shape.areShapesBroadcastable(this.shape(), denominator.shape())) {
            long[] outShape = Shape.broadcastOutputShape(this.shape(), denominator.shape());
            Preconditions.checkArgument(Shape.shapeEquals(outShape, result.shape()), "Result shape doesn't match expectations: " + Arrays.toString(result.shape()));

            Nd4j.exec(new FloorModOp(new INDArray[]{this, denominator}, new INDArray[]{result}));

            return result;
        } else {
            FModOp op = new FModOp(this, denominator, result);
            Nd4j.getExecutioner().exec(op);
            return result;
        }
    }

    @Override
    public INDArray fmod(Number denominator) {
        return fmod(denominator, Nd4j.createUninitialized(this.dataType(), this.shape()));
    }

    @Override
    public INDArray fmod(Number denominator, INDArray result) {
        validateNumericalArray("fmod", false);
        ScalarFMod op = new ScalarFMod(this, null, result, denominator);
        Nd4j.getExecutioner().exec(op);
        return result;
    }

    @Override
    public INDArray fmodi(INDArray denominator) {
        validateNumericalArray("fmodi", false);
        FModOp op = new FModOp(this, denominator, this);
        Nd4j.getExecutioner().exec(op);
        return this;
    }

    @Override
    public INDArray fmodi(Number denominator) {
        validateNumericalArray("fmodi", false);
        ScalarFMod op = new ScalarFMod(this, null, this, denominator);
        Nd4j.getExecutioner().exec(op);
        return this;
    }

    @Override
    public Iterator<Object> iterator() {
        return new FirstAxisIterator(this);
    }

    @Override
    public long originalOffset() {
        if (data().originalOffset() >= Integer.MAX_VALUE)
            throw new IllegalArgumentException("Original offset of buffer can not be >= Integer.MAX_VALUE");

        return data().originalOffset();
    }

    private void readObject(ObjectInputStream s) {
        try {
            s.defaultReadObject();
            read(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

    }

    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        write(out);
    }

    //Custom serialization for Java serialization
    protected void write(ObjectOutputStream out) throws IOException {
        if (this.isView()) {
            //As per Nd4j.write, duplicate before writing to the output stream
            //BaseDataBuffer.write(...) doesn't know about strides etc, so dup (or equiv. strategy) is necessary here
            //Furthermore, because we only want to save the *actual* data for a view (not the full data), the shape info
            // (mainly strides, offset, element-wise stride) may be different in the duped array vs. the view array
            INDArray copy = this.dup();
            copy.shapeInfoDataBuffer().write(out);
            copy.data().write(out);
        } else {
            shapeInformation.write(out);
            data().write(out);
        }
    }

    //Custom deserialization for Java serialization
    protected void read(ObjectInputStream s) {
        Triple<DataBuffer.AllocationMode, Long, DataType> headerShape = BaseDataBuffer.readHeader(s);

        shapeInformation = Nd4j.createBuffer(new int[Shape.shapeInfoLength(rank())]);
        shapeInformation.read(s, headerShape.getLeft(), headerShape.getMiddle(), headerShape.getRight());

        setShapeInformation(Pair.create(shapeInformation, shapeInformation.asLong()));

        Triple<DataBuffer.AllocationMode, Long, DataType> headerData = BaseDataBuffer.readHeader(s);
        data = Nd4j.createBuffer(headerData.getRight(), headerData.getMiddle(), false);
        data().read(s, headerData.getLeft(), headerData.getMiddle(), headerData.getRight());
    }

    @Override
    public INDArray argMax(int... dimension) {
        return Nd4j.argMax(this, dimension);
    }

    @Override
    public boolean isAttached() {
        if (isEmpty())
            return false;

        Preconditions.checkArgument(!(data == null && !isEmpty()), "Array has no buffer!");

        return data.isAttached() ||
                (data.underlyingDataBuffer() != null && data.underlyingDataBuffer().isAttached()) ||
                (data.originalDataBuffer() != null && data.originalDataBuffer().isAttached());
    }

    @Override
    public boolean isInScope() {
        if (!isAttached())
            return true;

        return data.isInScope();
    }

    @Override
    public INDArray detach() {
        if (!isAttached())
            return this;

        WorkspaceUtils.assertValidArray(this, "Cannot detach INDArray");

        Nd4j.getExecutioner().commit();

        /*
         two options here
         1) we're within some workspace
         2) we're out of any workspace
        */
        if (Nd4j.getMemoryManager().getCurrentWorkspace() == null) {
            if (!isView()) {
                Nd4j.getExecutioner().commit();
                DataBuffer buffer = Nd4j.createBuffer(this.dataType(), this.length(), false);

                Nd4j.getMemoryManager().memcpy(buffer, this.data());

                return Nd4j.createArrayFromShapeBuffer(buffer, this.shapeInfoDataBuffer());
            } else {
                INDArray copy = Nd4j.createUninitialized(this.dataType(), this.shape(), this.ordering());
                copy.assign(this);
                Nd4j.getExecutioner().commit();

                return copy;
            }
        } else {
            MemoryWorkspace workspace = Nd4j.getMemoryManager().getCurrentWorkspace();
            Nd4j.getMemoryManager().setCurrentWorkspace(null);
            INDArray copy = null;

            if (!isView()) {
                Nd4j.getExecutioner().commit();
                DataBuffer buffer = Nd4j.createBuffer(this.dataType(), this.length(), false);

                //Pointer.memcpy(buffer.pointer(), this.data.pointer(), this.lengthLong() * Nd4j.sizeOfDataType(this.data.dataType()));
                Nd4j.getMemoryManager().memcpy(buffer, this.data());

                copy = Nd4j.createArrayFromShapeBuffer(buffer, this.shapeInfoDataBuffer()); //this.dup(this.ordering());


            } else {
                copy = Nd4j.createUninitialized(this.dataType(), this.shape(), this.ordering());
                copy.assign(this);
                Nd4j.getExecutioner().commit();
            }

            Nd4j.getMemoryManager().setCurrentWorkspace(workspace);

            return copy;
        }
    }

    @Override
    public INDArray leverage() {
        WorkspaceUtils.assertValidArray(this, "Cannot leverage INDArray to new workspace");
        if (!isAttached())
            return this;

        MemoryWorkspace workspace = Nd4j.getMemoryManager().getCurrentWorkspace();
        if (workspace == null) {
            return this.detach();
        }

        MemoryWorkspace parentWorkspace = workspace.getParentWorkspace();

        if (this.data.getParentWorkspace() == parentWorkspace)
            return this;

        // if there's no parent ws - just detach
        if (parentWorkspace == null)
            return this.detach();
        else {
            Nd4j.getExecutioner().commit();

            // temporary set parent ws as current ws
            Nd4j.getMemoryManager().setCurrentWorkspace(parentWorkspace);

            INDArray copy = null;
            if (!this.isView()) {
                Nd4j.getExecutioner().commit();
                DataBuffer buffer = Nd4j.createBuffer(this.length(), false);
                Nd4j.getMemoryManager().memcpy(buffer, this.data());

                copy = Nd4j.createArrayFromShapeBuffer(buffer, this.shapeInfoDataBuffer());
            } else {
                copy = this.dup(this.ordering());
                Nd4j.getExecutioner().commit();
            }

            // restore current ws
            Nd4j.getMemoryManager().setCurrentWorkspace(workspace);
            return copy;
        }
    }

    @Override
    public INDArray leverageTo(String id) {
        return leverageTo(id, false);
    }

    @Override
    public INDArray leverageTo(String id, boolean enforceExistence) throws Nd4jNoSuchWorkspaceException {
        WorkspaceUtils.assertValidArray(this, "Cannot leverage INDArray to new workspace");
        if (!isAttached())
            return this;

        if (!Nd4j.getWorkspaceManager().checkIfWorkspaceExists(id)) {
            if(enforceExistence){
                throw new Nd4jNoSuchWorkspaceException(id);
            } else {
                return this;
            }
        }

        MemoryWorkspace current = Nd4j.getMemoryManager().getCurrentWorkspace();
        MemoryWorkspace target = Nd4j.getWorkspaceManager().getWorkspaceForCurrentThread(id);

        if (this.data.getParentWorkspace() == target)
            return this;

        Nd4j.getMemoryManager().setCurrentWorkspace(target);
        INDArray copy = null;
        if (!this.isView()) {
            Nd4j.getExecutioner().commit();
            DataBuffer buffer = Nd4j.createBuffer(this.dataType(), this.length(), false);
            Nd4j.getMemoryManager().memcpy(buffer, this.data());

            copy = Nd4j.createArrayFromShapeBuffer(buffer, this.shapeInfoDataBuffer());
        } else {
            copy = this.dup(this.ordering());
            Nd4j.getExecutioner().commit();
        }

        Nd4j.getMemoryManager().setCurrentWorkspace(current);

        return copy;
    }

    public INDArray leverageOrDetach(String id){
        if(!isAttached()){
            return this;
        }

        if(!Nd4j.getWorkspaceManager().checkIfWorkspaceExistsAndActive(id)){
            return detach();
        }
        return leverageTo(id);
    }

    @Override
    public INDArray migrate() {
        return migrate(false);
    }

    @Override
    public INDArray migrate(boolean detachOnNoWs){
        WorkspaceUtils.assertValidArray(this, "Cannot leverage INDArray to new workspace");

        MemoryWorkspace current = Nd4j.getMemoryManager().getCurrentWorkspace();

        if (current == null) {
            if(detachOnNoWs){
                return detach();
            } else {
                return this;
            }
        }

        INDArray copy = null;

        if (!this.isView()) {
            Nd4j.getExecutioner().commit();
            DataBuffer buffer = Nd4j.createBuffer(this.dataType(), this.length(), false);
            Nd4j.getMemoryManager().memcpy(buffer, this.data());

            copy = Nd4j.createArrayFromShapeBuffer(buffer, this.shapeInfoDataBuffer());
        } else {
            copy = this.dup(this.ordering());
            Nd4j.getExecutioner().commit();
        }

        return copy;
    }

    @Override
    public Number percentileNumber(Number quantile) {
        validateNumericalArray("percentileNumber", false);
        if (quantile.intValue() < 0 || quantile.intValue() > 100)
            throw new ND4JIllegalStateException("Percentile value should be in 0...100 range");

        if (isScalar())
            return this.getDouble(0);

        INDArray sorted = Nd4j.sort(this.dup(this.ordering()), true);

        return getPercentile(quantile, sorted);
    }

    @Override
    public Number medianNumber() {
        validateNumericalArray("medianNumber", false);
        if(isScalar())
            return getNumber(0);
        return percentileNumber(50);
    }

    @Override
    public INDArray median(int... dimension) {
        validateNumericalArray("median", false);
        //Check edge case: size 1 element. No dimension == full array
        if(dimension.length == 0){
            return Nd4j.scalar(dataType(), medianNumber().doubleValue());
        }
        long shapeProd = 1;
        for (int d : dimension) {
            shapeProd *= size(d);
        }
        if (shapeProd == 1) {
            long[] newShape = ArrayUtil.removeIndex(shape(), dimension);
            return dup('c').reshape('c', newShape);
        }
        return percentile(50, dimension);
    }

    protected double getPercentile(Number quantile, INDArray sorted) {
        validateNumericalArray("getPercentile", false);
        if (quantile.intValue() == 0)
            return sorted.getDouble(0);
        else if (quantile.intValue() == 100)
            return sorted.getDouble(sorted.length() - 1);

        double pos = (quantile.doubleValue() / 100.0) * (double) (sorted.length() + 1);
        if (pos < 1)
            return sorted.getDouble(0);
        else if (pos >= sorted.length())
            return sorted.getDouble(sorted.length() - 1);

        double fposition = FastMath.floor(pos);
        int position = (int)fposition;

        double diff = pos - fposition;

        double lower = sorted.getDouble(position-1);
        double upper = sorted.getDouble(position);

        return lower + diff * (upper - lower);
    }

    @Override
    public INDArray percentile(Number quantile, int... dimension) {
        validateNumericalArray("percentile", false);
        if (quantile.doubleValue() < 0 || quantile.doubleValue() > 100)
            throw new ND4JIllegalStateException("Percentile value should be in 0...100 range");

        if (isScalar())
            return Nd4j.scalar(this.getDouble(0));

        INDArray sorted = Nd4j.getNDArrayFactory().sort(this.dup(this.ordering()), false, dimension);

        // there's no practical sense doing this on GPU, stride will be just size of TAD.
        INDArray ret = Nd4j.createUninitialized(Nd4j.defaultFloatingPointType(), sorted.tensorsAlongDimension(dimension));
        for (int i = 0; i < ret.length(); i++) {
            ret.putScalar(i, getPercentile(quantile, sorted.tensorAlongDimension(i, dimension)));
        }

        return ret;

    }

    protected abstract int stringBuffer(FlatBufferBuilder builder, DataBuffer buffer);

    @Override
    public int toFlatArray(FlatBufferBuilder builder) {
        if(isView()){
            return dup(this.ordering()).toFlatArray(builder);
        }
        int shape = FlatArray.createShapeVector(builder, this.shapeInfoDataBuffer().asLong());
        int buffer = this.isEmpty() ? 0 : this.dataType() == DataType.UTF8 ? stringBuffer(builder, this.data()) : FlatArray.createBufferVector(builder, this.data().asBytes());
        byte type = this.isEmpty() ? FlatBuffersMapper.getDataTypeAsByte(this.dataType()) : FlatBuffersMapper.getDataTypeAsByte(this.data().dataType());
        int array = FlatArray.createFlatArray(builder, shape, buffer, type, ByteOrder.BE);

        return array;
    }

    protected static DataTypeEx convertType(DataType type) {
        if (type == DataType.HALF) {
            return DataTypeEx.FLOAT16;
        } else if (type == DataType.FLOAT) {
            return DataTypeEx.FLOAT;
        } else if (type == DataType.DOUBLE) {
            return DataTypeEx.DOUBLE;

        } else if(type == DataType.INT) {
            return DataTypeEx.INT8;
        } else if(type == DataType.LONG) {
            return DataTypeEx.INT16;

        } else
            throw new IllegalStateException("Unknown dataType: [" + type + "]");
    }

    @Override
    public boolean isEmpty() {
        return Shape.isEmpty(jvmShapeInfo.javaShapeInformation);
    }

    @Override
    public long[] shapeInfoJava() {
        return jvmShapeInfo.javaShapeInformation;
    }

    @Override
    public DataType dataType() {
        if (data != null)
            return data.dataType();

        long e = Shape.extras(jvmShapeInfo.javaShapeInformation);

        if (e != 0) {
            DataType t = ArrayOptionsHelper.dataType(jvmShapeInfo.javaShapeInformation);
            if (t != DataType.UNKNOWN)
                return t;
        }

        return DataType.UNKNOWN;
    }

    @Override
    public boolean isR() {
        DataType dtype = dataType();
        return dtype == DataType.FLOAT || dtype == DataType.DOUBLE || dtype == DataType.HALF || dtype == DataType.BFLOAT16;
    }

    @Override
    public boolean isZ() {
        return !isR() && !isB() && !isS();
    }

    @Override
    public boolean isB() {
        return dataType() == DataType.BOOL;
    }

    @Override
    public boolean isS() {
        return dataType() == DataType.UTF8;
    }

    @Override
    public INDArray castTo(DataType dataType) {
        if(dataType == dataType())  //No-op if correct datatype
            return this;
        if(isEmpty() && rank() == 0){
            return Nd4j.empty(dataType);
        }
        INDArray result = Nd4j.createUninitialized(dataType, this.shape(), this.ordering());
        result.assign(this);
        return result;
    }

    @Override
    public boolean all() {
        INDArray r = Nd4j.getExecutioner().exec(new All(this));
        return r.getDouble(0) != 0.0;
    }

    @Override
    public boolean any() {
        INDArray r = Nd4j.getExecutioner().exec(new Any(this));
        return r.getDouble(0) != 0.0;
    }

    @Override
    public boolean none() {
        return !any();
    }


    /**
     * Validate that the operation is being applied on a numerical array (not boolean or utf8).
     * Some operations (such as sum, norm2, add(Number) etc don't make sense when applied to boolean/utf8 arrays
     * @param opName Operation name to print in the exception
     */
    protected void validateNumericalArray(String opName, boolean allowEmpty){
        if(dataType() == DataType.BOOL || dataType() == DataType.UTF8)
            throw new IllegalStateException("Cannot apply operation " + opName + " to array with " + dataType() + " datatype. Array shape: " + Arrays.toString(shape()));
        if(!allowEmpty && isEmpty())
            throw new IllegalStateException("Cannot perform operation " + opName + " on empty array with datatype " + dataType());
    }

    @Override
    public boolean closeable() {
        if (released || isAttached())
            return false;

        // empty arrays have no buffer at all
        if (isEmpty())
            return true;

        if (isView())
            return false;

        return data.closeable();
    }

    @Override
    public void close() {
        // empty arrays have no buffer at all
        if (released || isEmpty())
            return;

        Nd4j.getExecutioner().commit();

        if (!closeable())
            throw new ND4JIllegalStateException("Can't release this INDArray");

        data.close();

        released = true;
    }

    @Override
    public INDArray like() {
        return Nd4j.create(this.dataType(), this.shape(), Nd4j.getStrides(this.shape(), this.ordering()), this.ordering());
    }

    @Override
    public INDArray ulike() {
        return Nd4j.createUninitialized(this.dataType(), this.shape(), this.ordering());
    }

    @Override
    public boolean wasClosed() {
        // data can be null if that's empty array
        if (released || (data() != null && data().wasClosed()))
            return true;

        return false;
    }

    @Override
    public long getId(){
        return arrayId;
    }
}
