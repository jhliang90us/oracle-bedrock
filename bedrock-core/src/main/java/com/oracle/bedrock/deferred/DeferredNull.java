/*
 * File: DeferredNull.java
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * The contents of this file are subject to the terms and conditions of 
 * the Common Development and Distribution License 1.0 (the "License").
 *
 * You may not use this file except in compliance with the License.
 *
 * You can obtain a copy of the License by consulting the LICENSE.txt file
 * distributed with this file, or by consulting https://oss.oracle.com/licenses/CDDL
 *
 * See the License for the specific language governing permissions
 * and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file LICENSE.txt.
 *
 * MODIFICATIONS:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 */

package com.oracle.bedrock.deferred;

/**
 * A strongly-typed {@link Deferred} implementation that always returns <code>null</code>.
 * <p>
 * Copyright (c) 2014. All Rights Reserved. Oracle Corporation.<br>
 * Oracle is a registered trademark of Oracle Corporation and/or its affiliates.
 *
 * @author Brian Oliver
 */
public class DeferredNull<T> implements Deferred<T>
{
    /**
     * The type of the {@link Deferred} (even though the value is null).
     */
    private Class<T> deferredClass;


    /**
     * Constructs a {@link DeferredNull}
     *
     * @param deferredClass  the class of the {@link DeferredNull} value
     */
    public DeferredNull(Class<T> deferredClass)
    {
        this.deferredClass = deferredClass;
    }


    @Override
    public T get() throws TemporarilyUnavailableException, PermanentlyUnavailableException
    {
        return null;
    }


    @Override
    public Class<T> getDeferredClass()
    {
        return deferredClass;
    }
}
