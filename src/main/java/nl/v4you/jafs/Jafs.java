package nl.v4you.jafs;

import nl.v4you.jafs.internal.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Set;
import java.util.TreeSet;

// https://www.linuxjournal.com/article/2151

public class Jafs implements AutoCloseable {

    private static int CACHE_BLOCK_MAX = 16 * 1024;
    private static int CACHE_DIR_MAX   = 256 * 256;

	private JafsBlockCache cache;
	private JafsDirEntryCache dirCache;
	private JafsSuper superBlock;
	private File myFile;
	private RandomAccessFile raf;		
	private JafsInodeContext ctx;
	private JafsUnusedMap um;
	private JafsDirEntry rootEntry = null;
	private JafsInodePool inodePool = null;
	private JafsDirPool dirPool = null;

	/*
	 * Public
	 */
	boolean isSupportedSize(int size, int sizeMin, int sizeMax) {
        boolean supported = false;
        int n;
        for (n=1; n<=16; n++) {
            int pow = 2 << n;
            if (size==pow && sizeMin<=size && size<=sizeMax) {
                supported = true;
                break;
            }
        }
        return supported;
    }

	public Jafs(String fname) throws IOException, JafsException {
		myFile = new File(fname);
		init(fname, -1);
	}

	public Jafs(String fname, int blockSize) throws JafsException, IOException {
		if (!isSupportedSize(blockSize, 64, 8192)) {
			throw new JafsException("block size " + blockSize + " not supported");
		}
		init(fname, blockSize);
		if (blockSize!=superBlock.getBlockSize()) {
			throw new JafsException("Supplied block size [" + blockSize + "] does not match header block size [" + superBlock.getBlockSize() + "]");
		}
	}
	
	public JafsFile getFile(String name) throws JafsException {
		return new JafsFile(this, name);
	}
	
	public JafsFile getFile(String parent, String name) throws JafsException {
		return new JafsFile(this, parent, name);
	}

	public JafsFile getFile(JafsFile parent, String name) throws JafsException {
		return new JafsFile(this, parent, name);
	}

	public JafsInputStream getInputStream(JafsFile f) throws JafsException, IOException {
		return new JafsInputStream(this, f);
	}
	
	public JafsOutputStream getOutputStream(JafsFile f) throws JafsException, IOException {
		return getOutputStream(f, false);
	}

	public JafsOutputStream getOutputStream(JafsFile f, boolean append) throws JafsException, IOException {
		if (f.exists()) {
			if (f.isDirectory()) {
				throw new JafsException(f.getCanonicalPath() + " should not be a directory");
			}
		}
		return new JafsOutputStream(this, f, append);
	}

	public void close() throws IOException {
		if (superBlock != null) {
			try {
				superBlock.close();
			}
			catch (Exception e) {}
		}
		raf.close();
	}

	/*
	 * Package private
	 */
	public JafsInodeContext getINodeContext() {
		return ctx;
	}
	
	public JafsUnusedMap getUnusedMap() {
		return um;
	}

	public RandomAccessFile getRaf() {
		return raf;
	}
		
	public JafsSuper getSuper() {
		return superBlock;
	}

	public JafsBlock getCacheBlock(long bpos) throws JafsException, IOException {
		return cache.get(bpos);
	}

	public JafsBlockCache getBlockCache() {
	    return cache;
    }

    public JafsDirEntryCache getDirCache() {
        return dirCache;
    }

    private long appendNewBlockToArchive(Set<Long> blockList) throws JafsException, IOException {
		superBlock.incBlocksTotal();
		long bpos = (superBlock.getBlocksTotal() - 1);
		long unusedMapBpos = um.getUnusedMapBpos(bpos);
		if (bpos == unusedMapBpos) {
			superBlock.incBlocksTotalAndUsed(blockList);
			um.initializeUnusedMap(blockList, unusedMapBpos);
			bpos++;
		}
		return bpos;
	}

	public long getAvailableBpos(Set<Long> blockList) throws JafsException, IOException {
		long bpos = getUnusedMap().getUnusedBpos(blockList);
		if (bpos == 0) bpos = appendNewBlockToArchive(blockList);
		getSuper().incBlocksUsed();
		um.setUnavailable(blockList, bpos);
		return bpos;
	}

    JafsDirEntry getRootEntry() {
        if (rootEntry != null) {
            return rootEntry;
        } else {
            rootEntry = new JafsDirEntry();
            rootEntry.setParentBpos(1);
            rootEntry.setBpos(1);
            rootEntry.setType(JafsInode.INODE_DIR);
            rootEntry.setName("/".getBytes());
            return rootEntry;
        }
    }

    /*
	 * Private
	 */
    private void initInodeContext(int blockSize) {
		um = new JafsUnusedMap(this);
		ctx = new JafsInodeContext(this, blockSize);
	}


    private void open(int blockSize) throws IOException, JafsException {
		if (!myFile.exists() && blockSize < 0) {
			throw new JafsException("[" + myFile.getName() + "] does not exist");
		}
		if (myFile.exists() && myFile.length() < 64) {
			throw new JafsException("[" + myFile.getName() + "] does not contain a header");
		}
		raf = new RandomAccessFile(myFile, "rw");
		cache = new JafsBlockCache(this, CACHE_BLOCK_MAX);
		dirCache = new JafsDirEntryCache(CACHE_DIR_MAX);
		inodePool = new JafsInodePool(this);
		dirPool = new JafsDirPool(this);
		if (myFile.length() == 0) {
			raf.setLength(blockSize);
			superBlock = new JafsSuper(this, blockSize, myFile, getUnusedMap());
			Set<Long> blockList = new TreeSet<>();
			initInodeContext(blockSize);
			JafsDir.createRootDir(blockList, this);
			cache.flushBlocks(blockList);
		}
		else {
			superBlock = new JafsSuper(this, myFile, getUnusedMap());
			initInodeContext(superBlock.getBlockSize());
		}
	}

	public void setBlocksTotal() {
		superBlock.setBlocksTotal(myFile);
	}

	public void setBlocksUsed() throws IOException, JafsException {
		superBlock.setBlocksUsed(getUnusedMap());
	}
	
	private void init(String fname, int blockSize) throws JafsException, IOException {
		myFile = new File(fname);
		open(blockSize);
	}

	public JafsInodePool getInodePool() {
        return inodePool;
    }

    public JafsDirPool getDirPool() {
    	return dirPool;
	}

	public String stats() {
        StringBuilder sb = new StringBuilder();
        sb.append("blocksUsed         : "+superBlock.getBlocksUsed()+"\n");
        sb.append("blocksTotal        : "+superBlock.getBlocksTotal()+"\n\n");
        sb.append(ctx.toString()+"\n");
        sb.append("blockCache:\n"+cache.stats());
        sb.append("inodePool:\n"+inodePool.stats());
        sb.append("dirPool:\n"+dirPool.stats());
        sb.append("dirCache:\n"+dirCache.stats());
        return sb.toString();
    }

    private void adviceBlockSizeScan(Fsize fsize, JafsFile f) throws JafsException, IOException {
        JafsFile[] l = f.listFiles();
        for (JafsFile g : l) {
            if (g.isFile()) {
                long size = g.length();
                for (int n=0; n<fsize.sizes.length; n++) {
                    long bs = fsize.sizes[n];
                    long mod = (size % bs);
                    if (mod == 0) {
                        fsize.onDisk[n] += (size / bs) * bs;
                    } else {
                        fsize.onDisk[n] += (size / bs) * bs + bs;
                    }
                    fsize.lost[n] += bs - mod;
                }
            }
            else {
                adviceBlockSizeScan(fsize, g);
            }
        }
    }

    public void adviceBlockSize() throws JafsException, IOException {
        Fsize fsize = new Fsize();
        JafsFile f = getFile("/");
        adviceBlockSizeScan(fsize, f);
        for (int n=0; n<fsize.sizes.length; n++) {
            System.err.println("blockSize " + fsize.sizes[n]+": " + fsize.onDisk[n] + " (on disk), " + fsize.lost[n] + " (lost), " + ((fsize.lost[n] * 100.f) / fsize.onDisk[n]) + "%");
        }
    }

    static class Fsize {
        long[] sizes = {64, 128, 256, 512, 1024, 2048, 4096};
        long[] onDisk = new long[sizes.length];
        long[] lost = new long[sizes.length];
    }
}
