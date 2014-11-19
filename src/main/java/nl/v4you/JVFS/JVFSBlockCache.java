package nl.v4you.JVFS;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import nl.v4you.JVFS.JVFS;
import nl.v4you.JVFS.JVFSException;

class CacheEntry {
	long bpos;
	JVFSBlock block;
	CacheEntry l;
	CacheEntry r;
}

class JVFSBlockCache {
	private static int CACHE_MAX_SIZE = 1000;

	private JVFS vfs;
	private Map<Long, CacheEntry> cache = new HashMap<Long, CacheEntry>();
	private CacheEntry mostLeft = null;
	private CacheEntry mostRight = null;
	
	JVFSBlockCache(JVFS vfs) {
		this.vfs = vfs;
		mostLeft = null;
		mostRight = null;
		cache.clear();
	}
	
	JVFSBlock get(long bpos, JVFSBlock block) throws JVFSException, IOException {
		CacheEntry ce = null;
		boolean cacheHit = false;
		
		if (bpos<0) {
			// SuperBlock bpos = -1 and is not cached
			throw new JVFSException("bpos should be 0 or greater");
		}
		
		if (bpos>=vfs.getSuper().getBlocksTotal()) {
			throw new JVFSException("bpos >= blocks total");
		}
		
		/*
		 * Check if this block is already in cache
		 */
		if (cache.containsKey(bpos)) {
			cacheHit = true;
			ce = cache.get(bpos);
			if (block!=null) {
				throw new JVFSException("Cache hit unexpected, block supplied to cache get method");
			}
		}
		
		if (cacheHit) {
			CacheEntry l;
			CacheEntry r;
			
			// Re-insert query at the end of the access list:
			
			// 1) Remove our entry from the access list
			l = ce.l;
			r = ce.r;			
			if (l!=null) l.r = r;
			if (r!=null) r.l = l;
			
			// 2) Add it to the end of the access list
			ce.l = mostRight;
			ce.r = null;
			mostRight = ce;
		}
		else {
			/*
			 * If not in cache, add it to the cache
			 */
			if (cache.size()>=CACHE_MAX_SIZE) {
				/*
				 * Cache too big? Evict (=delete) the oldest entry
				 */
				CacheEntry tmp = mostLeft;
				mostLeft = mostLeft.r;
				cache.remove(tmp.bpos);
			}
			
			/*
			 * Create new entry
			 */
			ce = new CacheEntry();
			ce.bpos = bpos;
			if (block!=null) {
				ce.block = block;
			}
			else {
				ce.block = new JVFSBlock(vfs, bpos);
				ce.block.readFromDisk();
			}
			
			// Update access list
			if (mostLeft==null) {
				mostLeft=ce;
			}
			ce.r = null;
			ce.l = mostRight;
			mostRight = ce;
			
			cache.put(bpos, ce);
		}
		if (ce.bpos!=bpos) {
			throw new JVFSException("Cached block bpos is not equal to requested bpos");
		}
		return ce.block;
	}
}
