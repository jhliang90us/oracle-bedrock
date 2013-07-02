/*
 * File: DeferredMBeanAttribute.java
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

package com.oracle.tools.deferred.jmx;

import com.oracle.tools.deferred.Deferred;
import com.oracle.tools.deferred.InstanceUnavailableException;
import com.oracle.tools.deferred.UnresolvableInstanceException;

import java.io.IOException;

import javax.management.InstanceNotFoundException;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

import javax.management.remote.JMXConnector;

/**
 * A {@link DeferredMBeanAttribute} is a {@link Deferred} for an
 * MBean attribute.
 * <p>
 * Copyright (c) 2012. All Rights Reserved. Oracle Corporation.<br>
 * Oracle is a registered trademark of Oracle Corporation and/or its affiliates.
 *
 * @author Brian Oliver
 */
public class DeferredMBeanAttribute<T> implements Deferred<T>
{
    /**
     * A {@link Deferred} for the {@link JMXConnector}
     * that should be used to determine the MBean attribute.
     */
    private Deferred<JMXConnector> m_deferredJMXConnector;

    /**
     * The {@link ObjectName} for the required MBean.
     */
    private ObjectName m_objectName;

    /**
     * The attribute name of the MBean to retrieve.
     */
    private String m_attributeName;

    /**
     * The {@link Class} of the MBean attribute value.
     */
    private Class<T> m_attributeClass;


    /**
     * Constructs a {@link DeferredMBeanAttribute} given a {@link Deferred}
     * for the {@link JMXConnector}, the name of the MBean object and attribute.
     *
     * @param deferredJMXConnector  the {@link Deferred} for the
     *                              {@link JMXConnector} from which to acquire the
     *                              MBean attribute
     * @param objectName            the {@link ObjectName} of the MBean
     * @param attributeName         the name of the attribute
     * @param attributeClass        the {@link Class} of the attribute value
     */
    public DeferredMBeanAttribute(Deferred<JMXConnector> deferredJMXConnector,
                                  ObjectName             objectName,
                                  String                 attributeName,
                                  Class<T>               attributeClass)
    {
        m_deferredJMXConnector = deferredJMXConnector;
        m_objectName           = objectName;
        m_attributeName        = attributeName;
        m_attributeClass       = attributeClass;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public T get() throws UnresolvableInstanceException, InstanceUnavailableException
    {
        try
        {
            JMXConnector connector = m_deferredJMXConnector.get();

            if (connector == null)
            {
                throw new InstanceUnavailableException(this);
            }
            else
            {
                MBeanServerConnection connection = connector.getMBeanServerConnection();
                Object                attribute  = connection.getAttribute(m_objectName, m_attributeName);

                return m_attributeClass.cast(attribute);
            }
        }
        catch (IOException e)
        {
            // when an IOException occurs it represents a failed attempt to use a
            // connector that was previously available, which inevitably means that
            // the previous connection has now failed and so must this mbean
            throw new UnresolvableInstanceException(this, e);
        }
        catch (NullPointerException e)
        {
            // when an NPE occurs it means the server isn't available, but
            // we can retry
            throw new InstanceUnavailableException(this, e);
        }
        catch (ClassCastException e)
        {
            // when we can't cast to the required type, we can't ever acquire
            // the result
            throw new UnresolvableInstanceException(this, e);
        }
        catch (InstanceNotFoundException e)
        {
            // when the instance is not found, it may be found later,
            // so we should be able to retry
            throw new InstanceUnavailableException(this, e);
        }
        catch (InstanceUnavailableException e)
        {
            // when the connector is unavailable, so is the attribute
            throw e;
        }
        catch (UnresolvableInstanceException e)
        {
            // when the connection is unresolvable, so is the attribute
            throw e;
        }
        catch (RuntimeException e)
        {
            throw new InstanceUnavailableException(this, e);
        }
        catch (Exception e)
        {
            throw new InstanceUnavailableException(this, e);
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public Class<T> getDeferredClass()
    {
        return m_attributeClass;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String toString()
    {
        return String.format("Deferred<MBeanAttribute>{on=%s, object=%s, attribute=%s, class=%s}",
                             m_deferredJMXConnector,
                             m_objectName,
                             m_attributeName,
                             m_attributeClass);
    }
}
