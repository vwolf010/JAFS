package nl.v4you.jafs;

import java.io.IOException;
import java.util.LinkedList;

class JafsBlockCache {

	private Jafs vfs;

	private GenericCache<Long, JafsBlock> gcache;

    private LinkedList<JafsBlock> free = new LinkedList<>();
    private LinkedList<JafsBlock> busy = new LinkedList<>();

    public int cacheMaxSize = 100;

	JafsBlockCache(Jafs vfs, int size) throws JafsException {
	    this.vfs = vfs;
	    cacheMaxSize = size;
	    gcache = new GenericCache<>(size);
    }

	JafsBlock get(long bpos) throws JafsException, IOException {
		if (bpos<0) {
			// SuperBlock bpos = -1 and is not cached
			throw new JafsException("bpos should be 0 or greater, got: "+bpos);
		}

		if (bpos>=vfs.getSuper().getBlocksTotal()) {
			throw new JafsException("bpos ("+bpos+") >= blocks total ("+vfs.getSuper().getBlocksTotal()+")");
		}

//        JafsBlock blk = new JafsBlock(vfs, bpos);
//        blk.readFromDisk();

        JafsBlock blk = gcache.get(bpos);
        if (blk==null) {
            if (free.size()==0) {
                blk = new JafsBlock(vfs, bpos);
                busy.add(blk);
            }
            else {
                blk = free.removeFirst();
                blk.setBpos(bpos);
                busy.add(blk);
            }
            blk.readFromDisk();
            JafsBlock evicted = gcache.add(bpos, blk);
            if (evicted!=null) {
                busy.remove(evicted);
                free.add(evicted);
            }
        }

		return blk;
	}

	String stats() {
        return "   free    : " + free.size()+"\n   busy    : " + busy.size()+"\n" + gcache.stats();
    }
}
