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

package org.modeshape.jcr;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.jcr.Node;
import javax.jcr.PathNotFoundException;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.observation.EventIterator;
import javax.jcr.observation.EventListener;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.modeshape.common.FixFor;
import org.modeshape.common.logging.Logger;
import org.modeshape.common.util.FileUtil;
import org.modeshape.jcr.api.JcrTools;
import org.modeshape.jcr.api.observation.Event;

/**
 * Unit test for various clustered repository scenarios.
 * 
 * @author Horia Chiorean (hchiorea@redhat.com)
 */
public class ClusteredRepositoryTest extends AbstractTransactionalTest {

    @BeforeClass
    public static void beforeClass() throws Exception {
        ClusteringHelper.bindJGroupsToLocalAddress();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        ClusteringHelper.removeJGroupsBindings();
    }

    @Test
    @FixFor( {"MODE-1618", "MODE-2830"} )
    public void shouldPropagateNodeChangesInCluster() throws Exception {
        JcrRepository repository1 = TestingUtil.startRepositoryWithConfig("config/clustered-repo-config.json");
        JcrSession session1 = repository1.login();

        JcrRepository repository2 = TestingUtil.startRepositoryWithConfig("config/clustered-repo-config.json");
        JcrSession session2 = repository2.login();

        try {
            int eventTypes = Event.NODE_ADDED | Event.PROPERTY_ADDED;
            ClusteringEventListener listener = new ClusteringEventListener(2);
            session2.getWorkspace().getObservationManager().addEventListener(listener, eventTypes, null, true, null, null, true);

            Node testNode = session1.getRootNode().addNode("testNode");
            String binary = "test string";
            testNode.setProperty("binaryProperty", session1.getValueFactory().createBinary(binary.getBytes()));
            session1.save();
            final String testNodePath = testNode.getPath();

            listener.waitForEvents();
            List<String> paths = listener.getPaths();
            assertEquals(3, paths.size());
            assertTrue(paths.contains("/testNode"));
            assertTrue(paths.contains("/testNode/binaryProperty"));
            assertTrue(paths.contains("/testNode/jcr:primaryType"));

            // check whether the node can be found in the second repository ...
            try {
                session2.refresh(false);
                session2.getNode(testNodePath);
            } catch (PathNotFoundException e) {
                fail("Should have found the '/testNode' created in other repository in this repository: ");
            }
        } finally {
            TestingUtil.killRepositories(repository1, repository2);
        }
    }

    @Test
    @FixFor( "MODE-1701" )
    public void shouldStartRepositoryWithJGroupsXMLConfigurationFile() throws Exception {
        JcrRepository repository = null;
        try {
            repository = TestingUtil.startRepositoryWithConfig("config/clustered-repo-config-jgroups-file.json");
            assertEquals(ModeShapeEngine.State.RUNNING, repository.getState());
        } finally {
            TestingUtil.killRepository(repository);
        }
    }

    @Test( expected = RepositoryException.class )
    @FixFor( "MODE-1701" )
    public void shouldNotStartRepositoryWithInvalidJGroupsConfiguration() throws Exception {
        TestingUtil.startRepositoryWithConfig("config/clustered-repo-config-invalid-jgroups-file.json");
    }

    /**
     * Each Infinispan configuration persists data in a separate location, and we use replication mode.
     * 
     * @throws Exception
     */
    @Test
    @FixFor( "MODE-1733" )
    public void shouldStartClusterWithReplicatedCachePersistedToSeparateAreasForEachProcess() throws Exception {
        FileUtil.delete("target/clustered");
        JcrRepository repository1 = null;
        JcrRepository repository2 = null;
        try {
            // Start the first process completely ...
            repository1 = TestingUtil.startRepositoryWithConfig("config/repo-config-clustered-persistent-1.json");
            Session session1 = repository1.login();
            assertThat(session1.getRootNode(), is(notNullValue()));
            assertThat(session1.getNode("/Cars"), is(notNullValue()));
            assertThat(session1.getNode("/Cars/Hybrid"), is(notNullValue()));
            assertThat(session1.getNode("/Cars/Hybrid/Toyota Prius"), is(notNullValue()));
            assertThat(session1.getWorkspace().getNodeTypeManager().getNodeType("car:Car"), is(notNullValue()));
            assertThat(session1.getWorkspace().getNodeTypeManager().getNodeType("air:Aircraft"), is(notNullValue()));

            // Start the second process completely ...
            repository2 = TestingUtil.startRepositoryWithConfig("config/repo-config-clustered-persistent-2.json");
            Session session2 = repository2.login();
            assertThat(session2.getRootNode(), is(notNullValue()));
            assertThat(session2.getNode("/Cars/Hybrid/Toyota Prius"), is(notNullValue()));
            assertThat(session2.getWorkspace().getNodeTypeManager().getNodeType("car:Car"), is(notNullValue()));
            assertThat(session2.getWorkspace().getNodeTypeManager().getNodeType("air:Aircraft"), is(notNullValue()));

            //in this setup, index changes are local so they *will not* be sent across to other nodes
            assertIndexChangesAreVisibleToOtherProcesses(false, session1, session2);

            session1.logout();
            session2.logout();
        } finally {
            Logger.getLogger(getClass())
                  .debug("Killing repositories in shouldStartClusterWithReplicatedCachePersistedToSeparateAreasForEachProcess");
            TestingUtil.killRepositories(repository1, repository2);
            FileUtil.delete("target/clustered");
        }

    }

    /**
     * Each Infinispan configuration persists data in a separate location, we use replication mode and the indexes are clustered
     * via JGroups.
     */
    @Test
    @FixFor( "MODE-1897" )
    public void shouldStartClusterWithReplicatedCachePersistedToSeparateAreasForEachProcessAndClusteringJGroupsIndexing() throws Exception {
        FileUtil.delete("target/clustered");
        JcrRepository repository1 = null;
        JcrRepository repository2 = null;
        try {
            // Start the master process completely ...
            repository1 = TestingUtil.startRepositoryWithConfig("config/repo-config-clustered-indexes-1.json");
            Session session1 = repository1.login();

            // Start the slave process completely ...
            repository2 = TestingUtil.startRepositoryWithConfig("config/repo-config-clustered-indexes-2.json");
            Session session2 = repository2.login();

            //in this setup, index changes clustered, so they *should be* be sent across to other nodes
            assertIndexChangesAreVisibleToOtherProcesses(true, session1, session2);

            session1.logout();
            session2.logout();
        } finally {
            Logger.getLogger(getClass())
                  .debug("Killing repositories in shouldStartClusterWithReplicatedCachePersistedToSeparateAreasForEachProcessAndClusteringJGroupsIndexing");
            TestingUtil.killRepositories(repository1, repository2);
            FileUtil.delete("target/clustered");
        }

    }

    /**
     * Each Infinispan configuration persists data to the SAME location, including indexes. This is NOT a valid option because the
     * indexes get corrupted, so we will ignore this
     * 
     * @throws Exception
     */
    @Ignore
    @Test
    @FixFor( "MODE-1733" )
    public void shouldStartClusterWithReplicatedCachePersistedToSameAreaForBothProcesses() throws Exception {
        FileUtil.delete("target/clustered");
        JcrRepository repository1 = null;
        JcrRepository repository2 = null;
        try {
            // Start the first process completely ...
            repository1 = TestingUtil.startRepositoryWithConfig("config/repo-config-clustered-persistent-1.json");
            Session session1 = repository1.login();
            assertThat(session1.getRootNode(), is(notNullValue()));

            // Start the second process completely ...
            repository2 = TestingUtil.startRepositoryWithConfig("config/repo-config-clustered-persistent-1.json");
            Session session2 = repository2.login();
            assertThat(session2.getRootNode(), is(notNullValue()));

            session1.logout();
            session2.logout();
        } finally {
            TestingUtil.killRepositories(repository1, repository2);
            FileUtil.delete("target/clustered");
        }
    }

    private void assertIndexChangesAreVisibleToOtherProcesses( boolean shouldBeVisible,
                                                               Session process1Session,
                                                               Session process2Session ) throws RepositoryException, InterruptedException {
        JcrTools jcrTools = new JcrTools();
        String query = "select * from [nt:unstructured] as n where n.[jcr:path]='/testNode'";

        // Add a jcr node in the 1st process and check it can be queried
        process1Session.getRootNode().addNode("testNode");
        process1Session.save();
        assertEquals(1, jcrTools.printQuery(process1Session, query).getNodes().getSize());

        //wait a bit for state transfer to complete
        Thread.sleep(100);

        //check that the custom jcr node created on the other process, was sent to this one
        assertNotNull(process2Session.getNode("/testNode"));
        int expectedSize = shouldBeVisible ? 1 : 0;
        assertEquals(expectedSize, jcrTools.printQuery(process2Session, query).getNodes().getSize());
    }

    protected class ClusteringEventListener implements EventListener {
        private final List<String> paths;
        private final CountDownLatch eventsLatch;

        protected ClusteringEventListener( int expectedEventsCount ) {
            this.paths = new ArrayList<String>();
            this.eventsLatch = new CountDownLatch(expectedEventsCount);
        }

        @Override
        public void onEvent( EventIterator events ) {
            while (events.hasNext()) {
                eventsLatch.countDown();
                Event event = (Event)events.nextEvent();
                try {
                    paths.add(event.getPath());
                } catch (RepositoryException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        void waitForEvents() throws InterruptedException {
            assertTrue(eventsLatch.await(2, TimeUnit.SECONDS));
        }

        public List<String> getPaths() {
            return paths;
        }
    }
}
