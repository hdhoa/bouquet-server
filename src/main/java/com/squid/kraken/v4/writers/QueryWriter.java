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
package com.squid.kraken.v4.writers;

import com.squid.core.database.model.Database;
import com.squid.core.expression.scope.ScopeException;
import com.squid.kraken.v4.caching.redis.datastruct.RedisCacheValue;
import com.squid.kraken.v4.core.analysis.engine.processor.ComputingException;
import com.squid.kraken.v4.core.analysis.engine.query.mapping.QueryMapper;

/**
 * this is an abstract class that define the generic interface for write a Redis RawMatrix into "something".
 * It is used as an abstraction to handle both in-memory representation (DataMatrix) and exporting the data
 * @author hoa
 *
 */
public abstract class QueryWriter {

	protected RedisCacheValue val;
	protected QueryMapper mapper;
	protected Database db;
	protected String SQL;

	public QueryWriter() {
	}

	public abstract void write() throws ScopeException, ComputingException;

	public void setSource(RedisCacheValue val) {
		this.val = val;
	};

	public void setMapper(QueryMapper mapper) {
		this.mapper = mapper;
	}

	public void setDatabase(Database db) {
		this.db = db;
	}

	public void setSQL(String sql) {
		this.SQL = sql;
	}

}
