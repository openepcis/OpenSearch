/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.indices.replication.common;

import org.apache.lucene.index.IndexCommit;
import org.apache.lucene.index.IndexFileNames;
import org.apache.lucene.index.SegmentInfos;
import org.apache.lucene.util.Version;
import org.opensearch.common.collect.Map;
import org.opensearch.common.concurrent.GatedCloseable;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.index.shard.IndexShardTestCase;
import org.opensearch.index.shard.ShardId;
import org.opensearch.index.store.Store;
import org.opensearch.index.store.StoreFileMetadata;
import org.opensearch.indices.replication.checkpoint.ReplicationCheckpoint;

import java.io.IOException;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CopyStateTests extends IndexShardTestCase {

    private static final long EXPECTED_LONG_VALUE = 1L;
    private static final ShardId TEST_SHARD_ID = new ShardId("testIndex", "testUUID", 0);
    private static final StoreFileMetadata SEGMENTS_FILE = new StoreFileMetadata(IndexFileNames.SEGMENTS, 1L, "0", Version.LATEST);
    private static final StoreFileMetadata PENDING_DELETE_FILE = new StoreFileMetadata("pendingDelete.del", 1L, "1", Version.LATEST);

    private static final Store.MetadataSnapshot COMMIT_SNAPSHOT = new Store.MetadataSnapshot(
        Map.of(SEGMENTS_FILE.name(), SEGMENTS_FILE, PENDING_DELETE_FILE.name(), PENDING_DELETE_FILE),
        null,
        0
    );

    private static final Store.MetadataSnapshot SI_SNAPSHOT = new Store.MetadataSnapshot(
        Map.of(SEGMENTS_FILE.name(), SEGMENTS_FILE),
        null,
        0
    );

    public void testCopyStateCreation() throws IOException {
        final IndexShard mockIndexShard = createMockIndexShard();
        ReplicationCheckpoint testCheckpoint = new ReplicationCheckpoint(
            mockIndexShard.shardId(),
            mockIndexShard.getOperationPrimaryTerm(),
            0L,
            mockIndexShard.getProcessedLocalCheckpoint(),
            0L
        );
        CopyState copyState = new CopyState(testCheckpoint, mockIndexShard);
        ReplicationCheckpoint checkpoint = copyState.getCheckpoint();
        assertEquals(TEST_SHARD_ID, checkpoint.getShardId());
        // version was never set so this should be zero
        assertEquals(0, checkpoint.getSegmentInfosVersion());
        assertEquals(EXPECTED_LONG_VALUE, checkpoint.getPrimaryTerm());

        Set<StoreFileMetadata> pendingDeleteFiles = copyState.getPendingDeleteFiles();
        assertEquals(1, pendingDeleteFiles.size());
        assertTrue(pendingDeleteFiles.contains(PENDING_DELETE_FILE));
    }

    public static IndexShard createMockIndexShard() throws IOException {
        IndexShard mockShard = mock(IndexShard.class);
        when(mockShard.shardId()).thenReturn(TEST_SHARD_ID);
        when(mockShard.getOperationPrimaryTerm()).thenReturn(EXPECTED_LONG_VALUE);
        when(mockShard.getProcessedLocalCheckpoint()).thenReturn(EXPECTED_LONG_VALUE);

        Store mockStore = mock(Store.class);
        when(mockShard.store()).thenReturn(mockStore);

        SegmentInfos testSegmentInfos = new SegmentInfos(Version.LATEST.major);
        when(mockShard.getSegmentInfosSnapshot()).thenReturn(new GatedCloseable<>(testSegmentInfos, () -> {}));
        when(mockStore.getMetadata(testSegmentInfos)).thenReturn(SI_SNAPSHOT);

        IndexCommit mockIndexCommit = mock(IndexCommit.class);
        when(mockShard.acquireLastIndexCommit(false)).thenReturn(new GatedCloseable<>(mockIndexCommit, () -> {}));
        when(mockStore.getMetadata(mockIndexCommit)).thenReturn(COMMIT_SNAPSHOT);
        return mockShard;
    }
}
