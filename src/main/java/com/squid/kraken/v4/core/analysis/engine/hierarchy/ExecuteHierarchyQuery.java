/*******************************************************************************
 * Copyright © Squid Solutions, 2016
 *
 * This file is part of Open Bouquet software.
 *  
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * There is a special FOSS exception to the terms and conditions of the 
 * licenses as they are applied to this program. See LICENSE.txt in
 * the directory of this program distribution.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Squid Solutions also offers commercial licenses with additional warranties,
 * professional functionalities or services. If you purchase a commercial
 * license, then it supersedes and replaces any other agreement between
 * you and Squid Solutions (above licenses and LICENSE.txt included).
 * See http://www.squidsolutions.com/EnterpriseBouquet/
 *******************************************************************************/
package com.squid.kraken.v4.core.analysis.engine.hierarchy;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squid.core.concurrent.CancellableCallable;
import com.squid.core.concurrent.ExecutionManager;
import com.squid.core.jdbc.engine.IExecutionItem;
import com.squid.core.jdbc.formatter.IJDBCDataFormatter;
import com.squid.kraken.v4.core.analysis.datamatrix.AxisValues;
import com.squid.kraken.v4.core.analysis.engine.index.IndexationException;
import com.squid.kraken.v4.core.analysis.engine.query.HierarchyQuery;
import com.squid.kraken.v4.core.analysis.engine.query.mapping.DimensionMapping;
import com.squid.kraken.v4.core.analysis.engine.query.mapping.SimpleMapping;
import com.squid.kraken.v4.core.analysis.universe.Axis;
import com.squid.kraken.v4.core.database.impl.DatasourceDefinition;
import com.squid.kraken.v4.core.database.impl.ExecuteQueryTask;
import com.squid.kraken.v4.core.sql.SelectUniversal;
import com.squid.kraken.v4.model.Attribute;

import com.squid.kraken.v4.model.DimensionPK;


/**
 * Handles the execution of the HierarchyQuery, and populates the associated
 * DimensionIndexes. It also support non-standard cancellation policy.
 * 
 * @author sergefantino
 *
 */
public class ExecuteHierarchyQuery implements CancellableCallable<ExecuteHierarchyQueryResult> {

	static final Logger logger = LoggerFactory.getLogger(ExecuteHierarchyQuery.class);

	private HierarchyQuery query;

	private ExecuteQueryTask executeQueryTask;

	public State state;

	
	
	public ExecuteHierarchyQuery(HierarchyQuery query) {
		this.query = query;
		this.state = State.NEW;
	}

	public enum State{
		NEW, ONGOING, DONE, CANCELLED, ERROR 
	};
	
	private volatile boolean abort = false;// make sure this callable will stop
											// working

	public void cancel() {
		abort = true;
		this.state = State.CANCELLED;
		if (executeQueryTask != null) {
			executeQueryTask.cancel();
		}
	}

	public ExecuteHierarchyQueryResult call() throws Exception {
		//
		
		ExecuteHierarchyQueryResult res = new ExecuteHierarchyQueryResult();
		
		this.state=State.ONGOING;
		ExecutionManager.INSTANCE.registerTask(this);
		//
		List<DimensionMapping> dx_map = query.getDimensionMapping();
		long metter_start = (new Date()).getTime();
		IExecutionItem item = null;
		//
		try {
			SelectUniversal select = query.getSelect();
			DatasourceDefinition ds = select.getDatasource();
			String SQL = select.render();
			executeQueryTask = ds.getDBManager().createExecuteQueryTask(SQL);
			executeQueryTask.setWorkerId("front");
			executeQueryTask.prepare();

			item = executeQueryTask.call();// calling the query in the same
											// thread
			ResultSet result = item.getResultSet();
			int bufferCommitSize = result.getFetchSize();
			//
			ResultSetMetaData metadata = result.getMetaData();
			IJDBCDataFormatter formatter = item.getDataFormatter();
			List<DimensionIndex> indexes = new ArrayList<>();
			// read resultset metadata
			for (DimensionMapping m : dx_map) {
				m.setMetadata(result, metadata);
				Axis axis = m.getDimensionIndex().getAxis();
				AxisValues axisData = new AxisValues(axis);
				// matrix.add(axisData);
				m.setAxisData(axisData);
				indexes.add(m.getDimensionIndex());
			}
			// prepare the hierarchy
			Map<DimensionIndex, List<Integer>> hierarchies_pos = new HashMap<>();
			Map<DimensionIndex, List<DimensionIndex>> hierarchies_type = new HashMap<>();
			for (int i = 0; i < dx_map.size(); i++) {
				DimensionIndex index = indexes.get(i);
				DimensionIndex root = index.getRoot();
				List<Integer> pos = hierarchies_pos.get(root);
				List<DimensionIndex> type = hierarchies_type.get(root);
				if (pos == null) {
					pos = new ArrayList<>();
					hierarchies_pos.put(root, pos);
					type = new ArrayList<>();
					hierarchies_type.put(root, type);
				}
				pos.add(i);// add the child
				type.add(index);
			}
			//
			long count = 0;
			int maxRecords = -1;
			DimensionMember[] dedup = new DimensionMember[dx_map.size()];
			ArrayList<DimensionMember[]> rowBuffer = new ArrayList<>(bufferCommitSize);
			@SuppressWarnings("unchecked")
			ArrayList<DimensionMember>[] indexBuffer = new ArrayList[dx_map.size()];

			// to detected proper end of indexation;

			boolean wait = true;
			while ((result.next()) && (count++ < maxRecords || maxRecords < 0)) {
				if (abort || executeQueryTask.isInterrupted() || Thread.interrupted()) {
					logger.info("cancelled reading SQLQuery#" + item.getID() + " method=executeQuery" + " duration= "
							+ " error=false status=cancelled queryid=" + item.getID() + " task="
							+ this.getClass().getName());
					throw new InterruptedException("cancelled while reading query results");
				}
				if (count % 100000 == 0) {
					long intermediate = new Date().getTime();
					long speed = Math.round((double) count / ((intermediate - metter_start) / 1000));// in
																										// row/s
					// logger.info("SQLQuery#" + item.getID() + " proceeded " +
					// count + "items, still running at "+speed+ "row/s");
					logger.info("reading SQLQuery#" + item.getID() + " proceeded " + count + "items, still running at "
							+ speed + "row/s" + " method=executeQuery" + " duration= " + " speed=" + speed
							+ " error=false status=reading queryid=" + item.getID() + " task="
							+ this.getClass().getName());

				}
				DimensionMember[] row_axis = new DimensionMember[dx_map.size()];
				int i = 0;
				for (DimensionMapping m : dx_map) {
					Object unbox = m.readData(formatter, result);
					// String label = formatter.formatJDBCObject(value,
					// m.getType());
					//
					if (unbox == null) {
						// ignore;
					} else if (dedup[i] == null || !dedup[i].getID().equals(unbox)) {
						DimensionMember member = new DimensionMember(-1, unbox,
								m.getDimensionIndex().getAttributes().size());
						// Object[] raw = new
						// Object[1+m.getDimensionIndex().getAttributes().size()];
						int k = 0;
						// raw[k++] = unbox;
						for (Attribute attr : m.getDimensionIndex().getAttributes()) {
							SimpleMapping s = m.getMapping(attr.getId().getAttributeId());
							Object aunbox = s.readData(formatter, result);
							// raw[k++] = aunbox;
							member.setAttribute(k, aunbox);
						}
						// DimensionMember member =
						// m.getDimensionIndex().index(raw);
						row_axis[i] = member;
						if (indexBuffer[i] == null)
							indexBuffer[i] = new ArrayList<>(bufferCommitSize);
						indexBuffer[i].add(member);
						dedup[i] = member;
					} else {
						// unbox == dedup
						row_axis[i] = dedup[i];
					}
					i++;// dumb
				}
				rowBuffer.add(row_axis);
				//
				// flush buffer ?
				if (rowBuffer.size() == bufferCommitSize) {

					// commit the index buffers
					this.flushDimensionBuffer(dx_map, indexBuffer, res.lastIndexedDimension, wait);
					// map the correlation
					this.flushCorrelationBuffer(dx_map, hierarchies_pos, hierarchies_type, rowBuffer, indexBuffer,
							res.lastIndexedCorrelation, wait);

					// only check ES state for the first batch
					if (wait) {
						wait = false;
					}
					// clear buffers
					rowBuffer.clear();
					indexBuffer = new ArrayList[dx_map.size()];
				}
				// end of while loop
			}

			// flush last buffer ?
			if (!rowBuffer.isEmpty()) {
				// check ES state for the last batch
				// commit the index buffers
				this.flushDimensionBuffer(dx_map, indexBuffer, res.lastIndexedDimension, true);
				// map the correlation
				this.flushCorrelationBuffer(dx_map, hierarchies_pos, hierarchies_type, rowBuffer, indexBuffer,
						res.lastIndexedCorrelation, true);
			}

			item.close();
 
			/*
			// check and set Indexes status
			this.waitForIndexationCompletion(res.lastIndexedDimension, res.lastIndexedCorrelation, 5);
			// check also empty dimensionIndexes
			for (DimensionIndex index : indexes) {
				if (!res.lastIndexedDimension.containsKey(index) && !res.lastIndexedCorrelation.containsKey(index)) {
					index.setDone();
				}
			} */

			
			
			long metter_finish = new Date().getTime();
			// logger.info("SQLQuery#" + item.getID() + " read "+count+" row(s)
			// in "+(metter_finish-metter_start)+" ms.");
			logger.info("complete SQLQuery#" + item.getID() + " reads " + count + " row(s) in "
					+ (metter_finish - metter_start) + " ms." + " method=executeQuery" + " duration="
					+ (metter_finish - metter_start) + " error=false status=complete queryid=" + item.getID() + "task="
					+ this.getClass().getName());

			//
			this.state = State.DONE;
			return res;
		} catch (Exception e) {
			this.state = State.ERROR;
			for (DimensionMapping m : dx_map) {
				m.getDimensionIndex().setPermanentError(e.getMessage());
			}
			if (!abort) {
				logger.info("failed SQLQuery#" + item.getID() + " method=executeQuery" + " duration="
						+ " error=true status=failed queryid=" + (item != null ? item.getID() : "?") + " "
						+ e.getLocalizedMessage() + "task=" + this.getClass().getName());
			}
			throw e;
		} finally {
			if (item != null)
				try {
					item.close();

				} catch (Exception e) {
					// swallow the exception
				}

			// unregister
			ExecutionManager.INSTANCE.unregisterTask(this);
		}
	}



	private HashMap<DimensionPK, String> flushDimensionBuffer(List<DimensionMapping> dx_map,
			ArrayList<DimensionMember>[] indexBuffer, HashMap<DimensionPK, String> lastIndexed, boolean wait)
					throws IndexationException {

		int j = 0;
		for (DimensionMapping m : dx_map) {
			if (indexBuffer[j] != null && m.isOption(DimensionMapping.COMPUTE_INDEX)) {// it
																						// may
																						// not
																						// have
																						// been
																						// initialized
				String id = m.getDimensionIndex().index(indexBuffer[j], wait);
				if (wait) {
					lastIndexed.put(m.getDimensionIndex().getDimension().getId(), id);
				}
			}
			j++;
		}
		return lastIndexed;
	}

	private HashMap<DimensionPK, String> flushCorrelationBuffer(List<DimensionMapping> dx_map,
			Map<DimensionIndex, List<Integer>> hierarchies_pos,
			Map<DimensionIndex, List<DimensionIndex>> hierarchies_type, ArrayList<DimensionMember[]> rowBuffer,
			ArrayList<DimensionMember>[] indexBuffer, HashMap<DimensionPK, String> lastIndexed, boolean wait)
					throws IndexationException {

		for (Entry<DimensionIndex, List<Integer>> entry : hierarchies_pos.entrySet()) {
			if (entry.getValue().size() > 1) {
				ArrayList<List<DimensionMember>> batch = new ArrayList<>(rowBuffer.size());
				for (DimensionMember[] row : rowBuffer) {
					List<DimensionMember> values = new ArrayList<>(entry.getValue().size());
					for (Integer pos : entry.getValue()) {
						values.add(row[pos]);
					}
					batch.add(values);
				}
				String id = entry.getKey().indexCorrelations(hierarchies_type.get(entry.getKey()), batch, wait);
				if (wait) {
					lastIndexed.put(entry.getKey().getDimension().getId(), id);
				}
			}
		}
		return lastIndexed;
	}

}
