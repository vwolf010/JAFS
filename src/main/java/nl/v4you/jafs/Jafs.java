package nl.v4you.jafs;

import nl.v4you.jafs.internal.*;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

// https://www.linuxjournal.com/article/2151

public class Jafs implements AutoCloseable {

	private static final int CACHE_BLOCK_MAX = 16 * 1024;
	private static final int CACHE_DIR_MAX   = 256 * 256;

	private JafsBlockCache blockCache;
	private JafsDirEntryCache dirCache;
	private JafsSuper superBlock;
	private File myFile;
	private RandomAccessFile raf;
	private JafsInodeContext ctx;
	private JafsDirEntry rootEntry = null;
	private JafsInodePool inodePool = null;
	private JafsDirPool dirPool = null;

	boolean isSupportedSize(int size, int sizeMin, int sizeMax) {
		boolean supported = false;
		int n;
		for (n = 1; n <= 16; n++) {
			int pow = 2 << n;
			if (size == pow && sizeMin <= size && size <= sizeMax) {
				supported = true;
				break;
			}
		}
		return supported;
	}

	public Jafs(String fname) throws IOException, JafsException {
		myFile = new File(fname);
		init(fname, 0);
	}

	public Jafs(String fname, int blockSize) throws JafsException, IOException {
		if (!isSupportedSize(blockSize, 64, 4096)) {
			throw new JafsException("block size " + blockSize + " not supported");
		}
		init(fname, blockSize);
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

	public RandomAccessFile getRaf() {
		return raf;
	}

	public JafsSuper getSuper() {
		return superBlock;
	}

	public JafsBlock getCacheBlock(long bpos) throws JafsException, IOException {
		return blockCache.get(bpos);
	}

	public JafsBlockCache getBlockCache() {
		return blockCache;
	}

	public void flushBlockCache() throws JafsException, IOException {
		blockCache.flushBlocks();
	}

	public JafsDirEntryCache getDirCache() {
		return dirCache;
	}

	public long appendNewBlockToArchive() {
		superBlock.incBlocksTotal();
		return (superBlock.getBlocksTotal());
	}

	public long getAvailableVpos() throws JafsException, IOException {
		long bpos = superBlock.getUnusedStackEnd();
		if (bpos == 0) {
			bpos = appendNewBlockToArchive();
		} else {
			superBlock.setUnavailable(bpos);
		}
		getSuper().incBlocksUsed();
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

	private void initInodeContext(int blockSize) {
		ctx = new JafsInodeContext(this, blockSize);
	}

	private void open(int blockSize) throws IOException, JafsException {
		if (!myFile.exists() && blockSize < 0) {
			throw new JafsException("[" + myFile.getName() + "] does not exist");
		}
		raf = new RandomAccessFile(myFile, "rw");
		blockCache = new JafsBlockCache(this, CACHE_BLOCK_MAX);
		dirCache = new JafsDirEntryCache(CACHE_DIR_MAX);
		inodePool = new JafsInodePool(this);
		dirPool = new JafsDirPool(this);
		boolean isNewFile = myFile.length() == 0;
		superBlock = new JafsSuper(this, blockSize);
		initInodeContext(superBlock.getBlockSize());
		if (isNewFile) {
			JafsDir.createRootDir(this);
			blockCache.flushBlocks();
		}
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

	public long getBlocksUsed() {
		return superBlock.getBlocksUsed();
	}

	public long getBlocksTotal() {
		return superBlock.getBlocksTotal();
	}

	public String stats() {
        return "blocksUsed         : " + superBlock.getBlocksUsed() + "\n" +
                "blocksTotal        : " + superBlock.getBlocksTotal() + "\n\n" +
                ctx.toString() + "\n" +
                "blockCache:\n" + blockCache.stats() +
                "inodePool:\n" + inodePool.stats() +
                "dirPool:\n" + dirPool.stats() +
                "dirCache:\n" + dirCache.stats();
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
			} else {
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