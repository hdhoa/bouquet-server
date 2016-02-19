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
package com.squid.kraken.v4.caching.redis;

import java.io.IOException;
import java.util.HashSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.squid.kraken.v4.caching.redis.datastruct.RawMatrix;
import com.squid.kraken.v4.caching.redis.datastruct.RedisCacheReference;
import com.squid.kraken.v4.caching.redis.datastruct.RedisCacheValue;
import com.squid.kraken.v4.caching.redis.generationalkeysserver.RedisKey;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class RedisCacheProxy implements IRedisCacheProxy {
	
    static final Logger logger = LoggerFactory.getLogger(RedisCacheProxy.class);

   private static  RedisCacheProxy INSTANCE;
	
	private String REDIShost ="localhost" ;
	private int REDISport =6379 ;
	private JedisPool pool;
	
	public static IRedisCacheProxy getInstance(ServerID redisID){
		if (INSTANCE == null){
			INSTANCE = new RedisCacheProxy(redisID);
		}
		return INSTANCE;
		
	}
	public static IRedisCacheProxy getInstance(){
		if (INSTANCE == null){
			INSTANCE = new RedisCacheProxy();
		}
		return INSTANCE;
	}
	
	public RedisCacheProxy(){
		logger.info("connecting to REDIS on "+ this.REDIShost + " " + this.REDISport);
		JedisPoolConfig config = new JedisPoolConfig();
		config.setTestOnBorrow(true);
		this.pool = new JedisPool(config, this.REDIShost, this.REDISport);
	}

	public RedisCacheProxy(ServerID redisID){
		this.REDIShost = redisID.host;
		this.REDISport= redisID.port;
		logger.info("connecting to REDIS on "+ this.REDIShost + " " + this.REDISport);
		JedisPoolConfig config = new JedisPoolConfig();
		config.setTestOnBorrow(true);
		this.pool = new JedisPool(config, this.REDIShost, this.REDISport);
	}
	
/*	public void start(){
	}
	*/
	//PUT
	
	public boolean put(String k, String v){
		return this.put( k.getBytes(), v.getBytes());
	}

	public boolean put(String k, byte[] v){
		return this.put( k.getBytes(), v);
	}
	
	

	public boolean put(String k, RawMatrix v){
		try {
			return this.put(k.getBytes(), v.serialize());
		} catch (IOException e) {
			return false;
		}
	}
	
	public boolean put(byte[] k, byte[] v){
		
		try(Jedis jedis  = getResourceFromPool()) {
			String res = jedis.set(k, v);
			if (res == null)
				return false ;
			else
				return true;
		} 
	}
	
	//GET
	
	public RawMatrix getRawMatrix(String key){
		try (Jedis jedis  = getResourceFromPool()){	
			
			HashSet<String> pastKeys=  new HashSet<String>(); // do not get trapped in circular references
			String currKey = key;
			while(true){

				byte[] serialized = jedis.get(currKey.getBytes());
			
				RedisCacheValue  val = RedisCacheValue.deserialize(serialized);
				if (val instanceof RawMatrix){
					RawMatrix res= (RawMatrix) val;
					res.setRedisKey(currKey); 
					return res;
				}else{
					if(val instanceof  RedisCacheReference){	
						RedisCacheReference ref = (RedisCacheReference )val ;
						logger.info("Cache Reference : "+ currKey + "   " +  ref.getReferenceKey());
						pastKeys.add(currKey);
						currKey = ref.getReferenceKey();
						if (pastKeys.contains(currKey)){
							throw new RuntimeException();
						}
						
					}else{
						throw new ClassNotFoundException();
					}
				}
			}
			
		} catch (RuntimeException | ClassNotFoundException | IOException e) {
			logger.error("failed to getRawMatrix() on key="+key);
			throw new RuntimeException("Jedis: getRawMatrix() failed on key="+key, e);
		} 
	}
	
	public byte[] get(String key){	
		
		try(Jedis jedis  = getResourceFromPool()) {
			byte[] res =jedis.get(key.getBytes());
			return res;
		} catch (RuntimeException e) {
			logger.error("failed to get() on key="+key);
			throw new RuntimeException("Jedis: get() failed on key="+key, e);
		} 
	}
	
	// in cache
	
	public boolean inCache(RedisKey k){
		return this.inCache(k.toString());
	}	

	public boolean inCache(String key){
		
		try(Jedis jedis  = getResourceFromPool()) {
			boolean res =jedis.exists(key.getBytes());
			return res;
		} catch (RuntimeException e) {
			logger.error("failed to inCache() on key="+key);
			throw new RuntimeException("Jedis: inCache() failed on key="+key, e);
		}
	}

	
	//clear

	public String clear(){
		
		try(Jedis jedis  = getResourceFromPool()) {
			String res = jedis.flushAll();
			return res;
		} 
	}

	public void quit(){
		this.pool.close();
	} 
	
	//TTL
	
	public void setTTL(String key, int ttl){
	
		try(Jedis jedis  = getResourceFromPool()) {
			jedis.expire(key.getBytes(), ttl);
		} catch (RuntimeException e) {
			logger.error("failed to setTTL() on key="+key);
			throw new RuntimeException("Jedis: setTTL() failed on key="+key, e);
		} 
	}
	
	private Jedis getResourceFromPool() {
		try {
			return pool.getResource();
		} catch (RuntimeException e) {
			logger.error("Jedis: getResourceFromPool() failed: "+e.getLocalizedMessage());
			throw new RuntimeException("Jedis: getResourceFromPool() failed", e);
		}
	}

}
