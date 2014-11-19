package nl.v4you.JVFS;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

import nl.v4you.JVFS.JVFSBlockCache;
import nl.v4you.JVFS.JVFSFile;
import nl.v4you.JVFS.JVFSInodeContext;
import nl.v4you.JVFS.JVFSSuper;
import nl.v4you.JVFS.JVFSUnusedMap;

public class JVFS {
	private JVFSBlockCache cache;
	private JVFSSuper superBlock;
	private String fname;
	private RandomAccessFile raf;		
	private JVFSInodeContext ctx;
	private JVFSUnusedMap um;
	
	/*
	 * Public
	 */
	public JVFS (String fname) throws IOException, JVFSException {
		init(fname, -1, -1, -1);
	}
	
	public JVFS (String fname, int blockSize, int inodeSize, long maxFileSize) throws JVFSException, IOException {
		if ((blockSize/64)==0) {
			blockSize=64;
		}
		while ((blockSize & 63)>0) {
			blockSize++;			
		}
		if ((inodeSize/32)==0) {
			inodeSize=32;
		}
		while ((inodeSize & 31)>0) {
			inodeSize++;			
		}
		if (maxFileSize<0) {
			maxFileSize = 0;
		}
		if (maxFileSize>JVFSInodeContext.MAX_FILE_SIZE) {
			maxFileSize = JVFSInodeContext.MAX_FILE_SIZE;
		}
		init(fname, blockSize, inodeSize, maxFileSize);
		if (blockSize!=superBlock.getBlockSize()) {
			throw new JVFSException("Supplied block size ["+blockSize+"] does not match header block size ["+superBlock.getBlockSize()+"]");
		}
		if (inodeSize!=superBlock.getInodeSize()) {
			throw new JVFSException("Supplied inode size ["+blockSize+"] does not match header inode size ["+superBlock.getBlockSize()+"]");
		}
		if (maxFileSize!=superBlock.getMaxFileSize()) {
			throw new JVFSException("Supplied max file size ["+blockSize+"] does not match header max file size ["+superBlock.getBlockSize()+"]");
		}
	}
	
	public JVFSFile getFile(String name) throws JVFSException, IOException {
		return new JVFSFile(this, name);
	}
	
	public JVFSFileInputStream getFileInputStream(JVFSFile f) throws JVFSException, IOException {
		return new JVFSFileInputStream(this, f);
	}
	
	public JVFSFileOutputStream getFileOutputStream(JVFSFile f) throws JVFSException, IOException {
		return new JVFSFileOutputStream(this, f);
	}

	public void close() throws IOException {
		raf.close();
	}

	/*
	 * Default
	 */	
	JVFSInodeContext getINodeContext() {
		return ctx;
	}
	
	JVFSUnusedMap getUnusedMap() {
		return um;
	}
	
	long getRootBpos() {
		return superBlock.getRootDirBpos();
	}
	
	int getRootIdx() {
		return superBlock.getRootDirIdx();
	}
	
	RandomAccessFile getRaf() {
		return raf;
	}
		
	JVFSSuper getSuper() {
		return superBlock;
	}

	JVFSBlock setCacheBlock(long bpos, JVFSBlock block) throws JVFSException, IOException {
		return cache.get(bpos, block);
	}
	
	JVFSBlock getCacheBlock(long bpos) throws JVFSException, IOException {
		return cache.get(bpos, null);
	}

	long getNewUnusedBpos() throws JVFSException, IOException {
		long bpos = superBlock.getBlocksTotal();
		if (um.isUnusedMapBlock(bpos)) {
			um.create(bpos);
			bpos = superBlock.getBlocksTotal();
		}		
		superBlock.incBlocksTotalAndFlush();
		raf.setLength(superBlock.getBlockSize()*(superBlock.getBlocksTotal()+1));
		um.setUnusedBlock(bpos);
		return bpos;
	}

	/*
	 * Private
	 */
	private void open(int blockSize, int inodeSize, long maxFileSize) throws IOException, JVFSException {
		File f = new File(fname);
		if (!f.exists() && blockSize<0) {
			throw new JVFSException("["+fname+"] does not exist");
		}
		if (f.exists() && f.length()<64) {
			throw new JVFSException("["+fname+"] does not contain a header");
		}
		raf = new RandomAccessFile(f, "rw");
		if (f.length()==0) {
			raf.setLength(blockSize);
			superBlock = new JVFSSuper(this, blockSize);
			superBlock.setInodeSize(inodeSize);
			superBlock.setMaxFileSize(maxFileSize);
			superBlock.flush();
			cache = new JVFSBlockCache(this);
			um = new JVFSUnusedMap(this);
			ctx = new JVFSInodeContext(this, blockSize, inodeSize, maxFileSize);
			JVFSDir.createRootDir(this);
		}
		else {
			superBlock = new JVFSSuper(this, 64);
			superBlock.read();
			superBlock = new JVFSSuper(this, superBlock.getBlockSize()); // reconnect with correct blocksize
			superBlock.read();
			cache = new JVFSBlockCache(this);
			um = new JVFSUnusedMap(this);
			ctx = new JVFSInodeContext(this, superBlock.getBlockSize(), superBlock.getInodeSize(), superBlock.getMaxFileSize());
		}
	}
	
	private void init(String fname, int blockSize, int inodeSize, long maxFileSize) throws JVFSException, IOException {
		this.fname = fname;
		open(blockSize, inodeSize, maxFileSize);
	}
}
