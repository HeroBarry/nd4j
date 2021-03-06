/*
 *
 *  * Copyright 2015 Skymind,Inc.
 *  *
 *  *    Licensed under the Apache License, Version 2.0 (the "License");
 *  *    you may not use this file except in compliance with the License.
 *  *    You may obtain a copy of the License at
 *  *
 *  *        http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  *    Unless required by applicable law or agreed to in writing, software
 *  *    distributed under the License is distributed on an "AS IS" BASIS,
 *  *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  *    See the License for the specific language governing permissions and
 *  *    limitations under the License.
 *
 *
 */

package org.nd4j.linalg.jcublas.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.driver.CUstream;
import jcuda.driver.CUstream_flags;
import jcuda.driver.JCudaDriver;
import jcuda.runtime.JCuda;
import jcuda.runtime.cudaMemcpyKind;
import jcuda.runtime.cudaStream_t;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.api.ops.Accumulation;
import org.nd4j.linalg.api.ops.Op;
import org.nd4j.linalg.jcublas.CublasPointer;
import org.nd4j.linalg.jcublas.buffer.JCudaBuffer;
import org.nd4j.linalg.jcublas.complex.ComplexDouble;
import org.nd4j.linalg.jcublas.context.ContextHolder;
import org.nd4j.linalg.jcublas.context.CudaContext;
import org.nd4j.linalg.jcublas.gpumetrics.GpuMetrics;
import org.nd4j.linalg.jcublas.kernel.KernelFunctions;

import java.util.ArrayList;
import java.util.List;

import static jcuda.driver.JCudaDriver.cuMemGetInfo;

/**
 * Wraps the generation of kernel parameters
 * , creating, copying
 * and destroying any cuda device allocations
 *
 * @author bam4d
 *
 */
public class KernelParamsWrapper implements AutoCloseable {

    private boolean closeInvoked = false;

    private boolean closeContext;

    private CudaContext context;

    /**
     * List of processed kernel parameters ready to be passed to the kernel
     */
    final public Object[] kernelParameters;

    /**
     * The pointers that need to be freed as part of this closable resource
     */
    final List<CublasPointer> pointersToFree;

    /**
     * The pointers that have results that need to be passed back to host buffers
     */
    final List<CublasPointer> resultPointers;

    /**
     * The operation that should receive the result
     */
    private Op resultOp;

    /**
     * The list of processed kernel parameters, These should be get passed to the cuda kernel
     * @return
     */
    public Object[] getKernelParameters() {
        return kernelParameters;
    }

    /**
     * conversion list of arrays to their assigned cublas pointer
     */
    private Multimap<INDArray, CublasPointer> arrayToPointer;

    private int resultLength = 1;


    /**
     * set the array that will contain the results, If the array is not set, then data from the device will not be copied to the host
     * @param array
     * @return
     */
    public KernelParamsWrapper setResultArray(INDArray array) {
        CublasPointer resultPointer = arrayToPointer.get(array).iterator().next();
        resultPointer.setResultPointer(true);
        if(resultPointer == null) {
            throw new RuntimeException("Results array must be supplied as a kernel parameter");
        }

        resultPointers.add(resultPointer);

        return this;
    }

    /**
     * set the Op that this result is for
     * @param op
     * @param result
     * @return
     */
    public KernelParamsWrapper setResultOp(Accumulation op, INDArray result) {
        resultOp = op;
        setResultArray(result);
        return this;
    }
    /**
     * Create a new wrapper for the kernel parameters.
     *
     * This wrapper manages the host - and device communication and.
     *
     * To set the result on a specific operation, use setResultOp()
     * To set the array which is the result INDArray, use setResultArray()
     * @param kernelParams
     */
    public KernelParamsWrapper(Op op,Object... kernelParams) {
        this(op,false, kernelParams);
    }
    /**
     * Create a new wrapper for the kernel parameters.
     *
     * This wrapper manages the host - and device communication and.
     *
     * To set the result on a specific operation, use setResultOp()
     * To set the array which is the result INDArray, use setResultArray()
     * @param kernelParams
     */
    public KernelParamsWrapper(Op op,boolean closeContext,Object... kernelParams) {
        kernelParameters = new Object[kernelParams.length];
        arrayToPointer = ArrayListMultimap.create();
        pointersToFree = new ArrayList<>();
        resultPointers = new ArrayList<>();
        context = new CudaContext(closeContext);
        context.initOldStream();
        context.initStream();
        this.closeContext = closeContext;

        for(int i = 0; i < kernelParams.length; i++) {
            Object arg = kernelParams[i];

            // If the instance is a JCudaBuffer we should assign it to the device
            if(arg instanceof JCudaBuffer) {
                JCudaBuffer buffer = (JCudaBuffer) arg;
                CublasPointer pointerToFree = new CublasPointer(buffer,context);
                kernelParameters[i] = pointerToFree.getDevicePointer();
                pointersToFree.add(pointerToFree);
            }
            else if(arg instanceof INDArray) {
                INDArray array = (INDArray) arg;
                CublasPointer pointerToFree = new CublasPointer(array,context);
                kernelParameters[i] = pointerToFree.getDevicePointer();
                pointersToFree.add(pointerToFree);
                arrayToPointer.put(array, pointerToFree);
            }
            else
                kernelParameters[i] = arg;

        }


    }

    /**
     * Free all the buffers from this kernel's parameters
     */
    @Override
    public void close() throws Exception {
        if(closeInvoked)
            return;

        for(CublasPointer cublasPointer : pointersToFree) {
            if(resultPointers.contains(cublasPointer)) {
                //sets the result for the buffer
                //since this ends up being a scalar
                if(closeContext) {
                    if(cublasPointer.getBuffer().length() == 1 && resultOp instanceof Accumulation) {
                        setResultForOp(resultOp, cublasPointer);
                    }
                    else
                        cublasPointer.copyToHost();
                    cublasPointer.close();
                }
                else
                    context.setResultPointer(cublasPointer);
            }

        }


        if(closeContext)
            context.destroy();
        closeInvoked = true;
    }

    /**
     * Set the result within the accumulation operation
     * @param acc
     * @param devicePointer
     */
    private void setResultForOp(Op acc, CublasPointer devicePointer) {
        if (devicePointer.getBuffer().dataType() == DataBuffer.Type.DOUBLE) {
            double[] data = new double[resultLength];
            Pointer get = Pointer.to(data);

            JCuda.cudaMemcpyAsync(
                    get
                    , devicePointer.getDevicePointer()
                    , resultLength * Sizeof.DOUBLE
                    , cudaMemcpyKind.cudaMemcpyDeviceToHost
                    , context.getOldStream());
            context.syncOldStream();


            if(acc instanceof Accumulation) {
                Accumulation acc2 = (Accumulation) acc;
                acc2.setFinalResult(data[0]);
            }


        }
        else if (devicePointer.getBuffer().dataType() == DataBuffer.Type.FLOAT) {
            float[] data = new float[resultLength];
            Pointer get = Pointer.to(data);

            JCuda.cudaMemcpyAsync(
                    get
                    , devicePointer.getDevicePointer()
                    , resultLength * Sizeof.FLOAT
                    , cudaMemcpyKind.cudaMemcpyDeviceToHost
                    , context.getOldStream());
            context.syncOldStream();


            if(acc instanceof Accumulation) {
                Accumulation acc2 = (Accumulation) acc;
                acc2.setFinalResult(data[0]);
            }


        }
    }

    public CudaContext getContext() {
        return context;
    }


    /**
     * Sync the streams
     */
    public void sync() {
        context.syncStream();
    }


}