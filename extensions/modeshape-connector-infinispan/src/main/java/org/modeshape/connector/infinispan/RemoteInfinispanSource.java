/*
 * ModeShape (http://www.modeshape.org)
 * See the COPYRIGHT.txt file distributed with this work for information
 * regarding copyright ownership.  Some portions may be licensed
 * to Red Hat, Inc. under one or more contributor license agreements.
 * See the AUTHORS.txt file in the distribution for a full listing of
 * individual contributors.
 *
 * ModeShape is free software. Unless otherwise indicated, all code in ModeShape
 * is licensed to you under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * ModeShape is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.modeshape.connector.infinispan;

import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.naming.BinaryRefAddr;
import javax.naming.Context;
import javax.naming.RefAddr;
import javax.naming.Reference;
import javax.naming.Referenceable;
import javax.naming.StringRefAddr;
import javax.naming.spi.ObjectFactory;
import net.jcip.annotations.ThreadSafe;
import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.manager.CacheContainer;
import org.infinispan.manager.DefaultCacheManager;
import org.modeshape.common.annotation.Category;
import org.modeshape.common.annotation.Description;
import org.modeshape.common.annotation.Label;
import org.modeshape.common.util.HashCode;
import org.modeshape.common.util.StringUtil;
import org.modeshape.graph.cache.CachePolicy;
import org.modeshape.graph.connector.RepositorySource;
import org.modeshape.graph.connector.base.BaseRepositorySource;

/**
 * A repository source that uses an Infinispan instance to manage the content. This source is capable of using an existing
 * {@link CacheContainer} or creating a new cache container. This process is controlled entirely by the JavaBean properties of the
 * InfinispanSource instance.
 * <p>
 * This source first attempts to find an existing cache manager found in {@link #getCacheContainerJndiName() JNDI} (or the
 * {@link DefaultCacheManager} if no such manager is available) and the {@link #getCacheConfigurationName() cache configuration
 * name} if supplied or the default configuration if not set.
 * </p>
 * <p>
 * Like other {@link RepositorySource} classes, instances of JBossCacheSource can be placed into JNDI and do support the creation
 * of {@link Referenceable JNDI referenceable} objects and resolution of references into JBossCacheSource.
 * </p>
 */
@ThreadSafe
public class RemoteInfinispanSource extends BaseInfinispanSource implements BaseRepositorySource, ObjectFactory {
    private static final long serialVersionUID = 1L;

    protected static final String INFINISPAN_SERVER_LIST = "remoteInfinispanServerList";

    @Description( i18n = InfinispanConnectorI18n.class, value = "remoteInfinispanServerListPropertyDescription" )
    @Label( i18n = InfinispanConnectorI18n.class, value = "remoteInfinispanServerListPropertyLabel" )
    @Category( i18n = InfinispanConnectorI18n.class, value = "remoteInfinispanServerListPropertyCategory" )
    private volatile String remoteInfinispanServerList;

    /**
     * Get the name in JNDI of a {@link cacheContainer} instance that should be used to create the cache for this source.
     * <p>
     * This source first attempts to find a cache instance using the {@link cacheContainer} found in
     * {@link #getCacheContainerJndiName() JNDI} (or the {@link DefaultCacheManager} if no such manager is available) and the
     * {@link #getCacheConfigurationName() cache configuration name} if supplied or the default configuration if not set.
     * </p>
     *
     * @return the JNDI name of the {@link cacheContainer} instance that should be used, or null if the {@link DefaultCacheManager}
     *         should be used if a cache is to be created
     * @see #setcacheContainerJndiName(String)
     * @see #getCacheConfigurationName()
     */
    public String getRemoteInfinispanServerList() {
        return remoteInfinispanServerList;
    }

    /**
     *
     * @param remoteInfinispanServerList the server list in appropriate server:port;server2:port2 format.
     */
    public synchronized void setRemoteInfinispanServerList( String remoteInfinispanServerList ) {
        if (this.remoteInfinispanServerList == remoteInfinispanServerList || this.remoteInfinispanServerList != null
            && this.remoteInfinispanServerList.equals(remoteInfinispanServerList)) return; // unchanged
        this.remoteInfinispanServerList = remoteInfinispanServerList;
    }

    @Override
    protected CacheContainer createCacheContainer() {
        Properties p = new Properties();
        if(this.getRemoteInfinispanServerList() == null || this.getRemoteInfinispanServerList().equals("")) {
            return new RemoteCacheManager();
        } else {
            return new RemoteCacheManager(this.getRemoteInfinispanServerList());
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized Reference getReference() {
        Reference ref = super.getReference();
        ref.add(new StringRefAddr(INFINISPAN_SERVER_LIST, getRemoteInfinispanServerList()));
        return ref;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object getObjectInstance( Object obj,
                                     javax.naming.Name name,
                                     Context nameCtx,
                                     Hashtable<?, ?> environment ) throws Exception {
        if (obj instanceof Reference) {
            Map<String, Object> values = new HashMap<String, Object>();
            Reference ref = (Reference)obj;
            Enumeration<?> en = ref.getAll();
            while (en.hasMoreElements()) {
                RefAddr subref = (RefAddr)en.nextElement();
                if (subref instanceof StringRefAddr) {
                    String key = subref.getType();
                    Object value = subref.getContent();
                    if (value != null) values.put(key, value.toString());
                } else if (subref instanceof BinaryRefAddr) {
                    String key = subref.getType();
                    Object value = subref.getContent();
                    if (value instanceof byte[]) {
                        // Deserialize ...
                        ByteArrayInputStream bais = new ByteArrayInputStream((byte[])value);
                        ObjectInputStream ois = new ObjectInputStream(bais);
                        value = ois.readObject();
                        values.put(key, value);
                    }
                }
            }
            String sourceName = (String)values.get(SOURCE_NAME);
            String rootNodeUuidString = (String)values.get(ROOT_NODE_UUID);
            String remoteServerList = (String)values.get(INFINISPAN_SERVER_LIST);
            Object defaultCachePolicy = values.get(DEFAULT_CACHE_POLICY);
            String retryLimit = (String)values.get(RETRY_LIMIT);
            String defaultWorkspace = (String)values.get(DEFAULT_WORKSPACE);
            String createWorkspaces = (String)values.get(ALLOW_CREATING_WORKSPACES);
            String updatesAllowed = (String)values.get(UPDATES_ALLOWED);

            String combinedWorkspaceNames = (String)values.get(PREDEFINED_WORKSPACE_NAMES);
            String[] workspaceNames = null;
            if (combinedWorkspaceNames != null) {
                List<String> paths = StringUtil.splitLines(combinedWorkspaceNames);
                workspaceNames = paths.toArray(new String[paths.size()]);
            }

            // Create the source instance ...
            RemoteInfinispanSource source = new RemoteInfinispanSource();
            if (sourceName != null) source.setName(sourceName);
            if (rootNodeUuidString != null) source.setRootNodeUuid(rootNodeUuidString);
            if (remoteServerList != null) source.setRemoteInfinispanServerList(remoteServerList);
            if (defaultCachePolicy instanceof CachePolicy) {
                source.setDefaultCachePolicy((CachePolicy)defaultCachePolicy);
            }
            if (retryLimit != null) source.setRetryLimit(Integer.parseInt(retryLimit));
            if (defaultWorkspace != null) source.setDefaultWorkspaceName(defaultWorkspace);
            if (createWorkspaces != null) source.setCreatingWorkspacesAllowed(Boolean.parseBoolean(createWorkspaces));
            if (workspaceNames != null && workspaceNames.length != 0) source.setPredefinedWorkspaceNames(workspaceNames);
            if (updatesAllowed != null) source.setUpdatesAllowed(Boolean.valueOf(updatesAllowed));
            return source;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals( Object obj ) {
        if (obj == this) return true;
        if (obj instanceof RemoteInfinispanSource) {
            RemoteInfinispanSource that = (RemoteInfinispanSource)obj;
            if (this.getName() == null) {
                if (that.getName() != null) return false;
            } else {
                if (!this.getName().equals(that.getName())) return false;
            }
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return HashCode.compute(getName());
    }
    
}
