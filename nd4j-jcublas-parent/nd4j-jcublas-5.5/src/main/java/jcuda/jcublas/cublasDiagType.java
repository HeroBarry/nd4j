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
package jcuda.jcublas;

/**
 * Indicates whether the main diagonal of the dense matrix is
 * unity and consequently should not be touched or modified 
 * by the function.
 */
public class cublasDiagType
{
    /**
     * The matrix diagonal has non-unit elements
     */
    public static final int CUBLAS_DIAG_NON_UNIT = 0;
    
    /**
     * The matrix diagonal has unit elements
     */
    public static final int CUBLAS_DIAG_UNIT = 1;

    /**
     * Private constructor to prevent instantiation
     */
    private cublasDiagType(){}

    /**
     * Returns a string representation of the given constant
     *
     * @return A string representation of the given constant
     */
    public static String stringFor(int n)
    {
        switch (n)
        {
            case CUBLAS_DIAG_NON_UNIT: return "CUBLAS_DIAG_NON_UNIT";
            case CUBLAS_DIAG_UNIT: return "CUBLAS_DIAG_UNIT";
        }
        return "INVALID cublasDiagType: "+n;
    }
}

