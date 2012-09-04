package com.alvazan.orm.layer9z.spi.db.inmemory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.alvazan.orm.api.base.NoSqlEntityManager;
import com.alvazan.orm.api.z8spi.AbstractCursor;
import com.alvazan.orm.api.z8spi.BatchListener;
import com.alvazan.orm.api.z8spi.Key;
import com.alvazan.orm.api.z8spi.KeyValue;
import com.alvazan.orm.api.z8spi.MetaLookup;
import com.alvazan.orm.api.z8spi.NoSqlRawSession;
import com.alvazan.orm.api.z8spi.Row;
import com.alvazan.orm.api.z8spi.ScanInfo;
import com.alvazan.orm.api.z8spi.action.Action;
import com.alvazan.orm.api.z8spi.action.Column;
import com.alvazan.orm.api.z8spi.action.IndexColumn;
import com.alvazan.orm.api.z8spi.action.Persist;
import com.alvazan.orm.api.z8spi.action.PersistIndex;
import com.alvazan.orm.api.z8spi.action.Remove;
import com.alvazan.orm.api.z8spi.action.RemoveIndex;
import com.alvazan.orm.api.z8spi.meta.DboDatabaseMeta;
import com.alvazan.orm.api.z8spi.meta.DboTableMeta;
import com.alvazan.orm.api.z8spi.meta.StorageTypeEnum;
import com.alvazan.orm.util.ProxyTempCursor;

public class InMemorySession implements NoSqlRawSession {

	private static final Logger log = LoggerFactory.getLogger(InMemorySession.class);
	
	@Inject
	private NoSqlDatabase database;
	@Inject
	private DboDatabaseMeta dbMetaFromOrmOnly;
	
	@Override
	public AbstractCursor<KeyValue<Row>> find(String colFamily, Iterable<byte[]> rowKeys) {
		List<KeyValue<Row>> rows = new ArrayList<KeyValue<Row>>();
		for(byte[] key : rowKeys) {
			Row row = findRow(colFamily, key);
			Row newRow = null;
			if(row != null)
				newRow = row.deepCopy();
			KeyValue<Row> kv = new KeyValue<Row>();
			kv.setKey(key);
			kv.setValue(newRow);
			//This add null if there is no row to the list on purpose
			rows.add(kv);
		}
		
		AbstractCursor<KeyValue<Row>> proxy = new ProxyTempCursor<KeyValue<Row>>(rows);
		return proxy;
	}
	
	private Row findRow(String colFamily, byte[] key) {
		Table table = database.findTable(colFamily);
		if(table == null)
			return null;
		return table.getRow(key);
	}

	@Override
	public void sendChanges(List<Action> actions, MetaLookup ormSession) {
		sendChangesImpl(actions, ormSession);
	}
	
	public void sendChangesImpl(List<Action> actions, Object ormSession) {
		for(Action action : actions) {
			Table table = lookupColFamily(action, (NoSqlEntityManager) ormSession);
			if(action instanceof Persist) {
				persist((Persist)action, table);
			} else if(action instanceof Remove) {
				remove((Remove)action, table);
			} else if(action instanceof PersistIndex) {
				persistIndex((PersistIndex) action, table);
			} else if(action instanceof RemoveIndex) {
				removeIndex((RemoveIndex) action, table);
			}
		}
	}

	private void persistIndex(PersistIndex action, Table table) {
		byte[] rowKey = action.getRowKey();
		IndexColumn column = action.getColumn();
		IndexedRow row = (IndexedRow) table.findOrCreateRow(rowKey);
		row.addIndexedColumn(column.copy());
	}



	private void removeIndex(RemoveIndex action, Table table) {
		byte[] rowKey = action.getRowKey();
		IndexColumn column = action.getColumn();
		IndexedRow row = (IndexedRow) table.findOrCreateRow(rowKey);
		row.removeIndexedColumn(column.copy());		
	}
	
	private Table lookupColFamily(Action action, NoSqlEntityManager mgr) {
		String colFamily = action.getColFamily();
		Table table = database.findTable(colFamily);
		if(table != null)
			return table;
		
		log.info("CREATING column family="+colFamily+" in the in memory nosql store");
			
		DboTableMeta cf = dbMetaFromOrmOnly.getMeta(colFamily);
		if(cf == null) {
			//check the database now for the meta since it was not found in the ORM meta data.  This is for
			//those that are modifying meta data themselves
			//DboDatabaseMeta db = mgr.find(DboDatabaseMeta.class, DboDatabaseMeta.META_DB_ROWKEY);
			cf = mgr.find(DboTableMeta.class, colFamily);
			log.info("cf from db="+cf);
			//cf = db.getMeta(colFamily);
		}
		
		if(cf == null) {
			throw new IllegalStateException("Column family='"+colFamily+"' was not found AND we looked up meta data for this column" +
					" family to create it AND we could not find that data so we can't create it for you");
		}

		SortType sortType;
		StorageTypeEnum prefixType = cf.getColNamePrefixType();
		if(prefixType == null) {
			switch (cf.getNameStorageType()) {
			case BYTES:
				sortType = SortType.BYTES;
				break;
			case DECIMAL:
				sortType = SortType.DECIMAL;
				break;
			case INTEGER:
				sortType = SortType.INTEGER;
				break;
			case STRING:
				sortType = SortType.UTF8;
				break;
			default:
				throw new UnsupportedOperationException("type not supported="+cf.getNameStorageType());
			}
		} else {
			switch(prefixType) {
			case DECIMAL:
				sortType = SortType.DECIMAL_PREFIX;
				break;
			case INTEGER:
				sortType = SortType.INTEGER_PREFIX;
				break;
			case STRING:
				sortType = SortType.UTF8_PREFIX;
				break;
			default:
				throw new UnsupportedOperationException("type not supported="+prefixType);
			}
		}
		
		table = new Table(colFamily, sortType);
		database.putTable(colFamily, table);
		
		return table;
	}

	private void remove(Remove action, Table table) {
		if(action.getAction() == null)
			throw new IllegalArgumentException("action param is missing ActionEnum so we know to remove entire row or just columns in the row");
		switch(action.getAction()) {
		case REMOVE_ENTIRE_ROW:
			table.removeRow(action.getRowKey());
			break;
		case REMOVE_COLUMNS_FROM_ROW:
			removeColumns(action, table);
			break;
		default:
			throw new RuntimeException("bug, unknown remove action="+action.getAction());
		}
	}

	private void removeColumns(Remove action, Table table) {
		Row row = table.getRow(action.getRowKey());
		if(row == null)
			return;
		
		for(byte[] name : action.getColumns()) {
			row.remove(name);
		}
	}

	private void persist(Persist action, Table table) {
		Row row = table.findOrCreateRow(action.getRowKey());
		
		for(Column col : action.getColumns()) {
			row.put(col.getName(), col.copy());
		}
	}

	@Override
	public void clearDatabase() {
		database.clear();
	}

	@Override
	public void start(Map<String, Object> properties) {
		
	}

	public Iterable<Column> columnSliceImpl(String colFamily, byte[] rowKey,
			byte[] from, byte[] to, Integer batchSize, BatchListener l) {
		Table table = database.findTable(colFamily);
		if(table == null) {
			return new HashSet<Column>();
		}
		Row row = table.findOrCreateRow(rowKey);
		if(row == null)
			return new HashSet<Column>();
		
		return row.columnSlice(from, to);
	}
	
	public Collection<IndexColumn> scanIndexImpl(ScanInfo info, Key from, Key to, Integer batchSize, BatchListener l) {
		String colFamily = info.getIndexColFamily();
		byte[] rowKey = info.getRowKey();
		Table table = database.findTable(colFamily);
		if(table == null) {
			return new HashSet<IndexColumn>();
		}
		Row row = table.findOrCreateRow(rowKey);
		if(row == null)
			return new HashSet<IndexColumn>();
		
		return row.columnSlice(from, to);
	}

	@Override
	public void close() {
		
	}

	@Override
	public AbstractCursor<Column> columnSlice(String colFamily, byte[] rowKey,
			byte[] from, byte[] to, Integer batchSize, BatchListener l) {
		Iterable<Column> iter = columnSliceImpl(colFamily, rowKey, from, to, batchSize, l);
		return new ProxyTempCursor<Column>(iter);
	}

	@Override
	public AbstractCursor<IndexColumn> scanIndex(ScanInfo scan, Key from, Key to,
			Integer batchSize, BatchListener l) {
		Collection<IndexColumn> iter = scanIndexImpl(scan, from, to, batchSize, l);
		return new ProxyTempCursor<IndexColumn>(iter);
	}

}
