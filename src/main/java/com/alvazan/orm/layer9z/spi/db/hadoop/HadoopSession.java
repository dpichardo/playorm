package com.alvazan.orm.layer9z.spi.db.hadoop;


import java.util.List;
import java.util.Map;

import com.alvazan.orm.api.z8spi.BatchListener;
import com.alvazan.orm.api.z8spi.Cache;
import com.alvazan.orm.api.z8spi.Key;
import com.alvazan.orm.api.z8spi.KeyValue;
import com.alvazan.orm.api.z8spi.MetaLookup;
import com.alvazan.orm.api.z8spi.NoSqlRawSession;
import com.alvazan.orm.api.z8spi.Row;
import com.alvazan.orm.api.z8spi.ScanInfo;
import com.alvazan.orm.api.z8spi.action.Action;
import com.alvazan.orm.api.z8spi.action.Column;
import com.alvazan.orm.api.z8spi.action.IndexColumn;
import com.alvazan.orm.api.z8spi.iter.AbstractCursor;
import com.alvazan.orm.api.z8spi.iter.DirectCursor;
import com.alvazan.orm.api.z8spi.meta.DboTableMeta;


import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.client.HTablePool;

public class HadoopSession implements NoSqlRawSession {

	/** The conf. */
	private HBaseConfiguration conf;

	/** The h table pool. */
	private HTablePool hTablePool;

	/** The pool size. */
	private int poolSize = 100;

	@Override
	public void sendChanges(List<Action> actions, MetaLookup ormSession) {
	}

	@Override
	public void clearDatabase() {
		throw new UnsupportedOperationException("Not supported by actual databases.  Only can be used with in-memory db.");
	}

	@Override
	public void start(Map<String, Object> properties) {
		properties.keySet();
		org.apache.hadoop.conf.Configuration hadoopConf = HBaseConfiguration.create();
        //hadoopConf.set("hbase.master", Bootstrap.HBASE_SEEDS);
		hadoopConf.set("hbase.zookeeper.quorum", "localhost");
		hTablePool = new HTablePool(conf, poolSize);

	}

	@Override
	public void close() {
	}

	@Override
	public void readMetaAndCreateTable(MetaLookup ormSession, String colFamily) {
		
	}

	@Override
	public AbstractCursor<Column> columnSlice(DboTableMeta colFamily, byte[] rowKey,
			byte[] from, byte[] to, Integer batchSize, BatchListener l,
			MetaLookup mgr) {
		return null;
	}

	@Override
	public AbstractCursor<IndexColumn> scanIndex(ScanInfo scan, Key from,
			Key to, Integer batchSize, BatchListener l, MetaLookup mgr) {
		return null;
	}

	@Override
	public AbstractCursor<IndexColumn> scanIndex(ScanInfo scanInfo,
			List<byte[]> values, BatchListener list, MetaLookup mgr) {
		return null;
	}

	@Override
	public AbstractCursor<KeyValue<Row>> find(DboTableMeta colFamily,
			DirectCursor<byte[]> rowKeys, Cache cache, int batchSize,
			BatchListener list, MetaLookup mgr) {
		return null;
	}

}
