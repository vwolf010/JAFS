package nl.v4you.jafs;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class Jafs {
	private JafsBlockCache cache;
	private JafsSuper superBlock;
	private String fname;
	private RandomAccessFile raf;		
	private JafsInodeContext ctx;
	private JafsUnusedMap um;
	
	/*
	 * Public
	 */
	public Jafs(String fname) throws IOException, JafsException {
		init(fname, -1, -1, -1);
	}

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
	
	public Jafs(String fname, int blockSize, int inodeSize, long maxFileSize) throws JafsException, IOException {
		if (!isSupportedSize(blockSize, 128, 8192)) {
			throw new JafsException("block size "+blockSize+" not supported");
		}
        if (!isSupportedSize(inodeSize, 32, 8192)) {
            throw new JafsException("inode size "+inodeSize+" not supported");
        }
		if (inodeSize>blockSize) {
			throw new JafsException("inode size "+inodeSize+" should be smaller or equal to block size "+blockSize);
		}
		if (maxFileSize<0) {
			maxFileSize = 0;
		}
		if (maxFileSize>JafsInodeContext.MAX_FILE_SIZE) {
			//TODO: maxFileSize should not apply to files that are directories
			maxFileSize = JafsInodeContext.MAX_FILE_SIZE;
		}
		init(fname, blockSize, inodeSize, maxFileSize);
		if (blockSize!=superBlock.getBlockSize()) {
			throw new JafsException("Supplied block size ["+blockSize+"] does not match header block size ["+superBlock.getBlockSize()+"]");
		}
		if (inodeSize!=superBlock.getInodeSize()) {
			throw new JafsException("Supplied inode size ["+inodeSize+"] does not match header inode size ["+superBlock.getInodeSize()+"]");
		}
		if (maxFileSize!=superBlock.getMaxFileSize()) {
			throw new JafsException("Supplied max file size ["+maxFileSize+"] does not match header max file size ["+superBlock.getMaxFileSize()+"]");
		}
	}
	
	public JafsFile getFile(String name) throws JafsException, IOException {
		return new JafsFile(this, name);
	}
	
	public JafsFile getFile(String parent, String name) throws JafsException, IOException {
		return new JafsFile(this, parent, name);
	}

	public JafsFile getFile(JafsFile parent, String name) throws JafsException, IOException {
		return new JafsFile(this, parent, name);
	}

	public JafsInputStream getInputStream(JafsFile f) throws JafsException, IOException {
		return new JafsInputStream(this, f);
	}
	
	public JafsOutputStream getOutputStream(JafsFile f) throws JafsException, IOException {
		return getOutputStream(f, false);
	}

	public JafsOutputStream getOutputStream(JafsFile f, boolean append) throws JafsException, IOException {
		if (!append && f.exists() && !f.delete()) {
			throw new JafsException("getOutputStream(): deleting ["+f.getAbsolutePath()+"] failed");
		}
		return new JafsOutputStream(this, f, append);
	}

	public void close() throws IOException {
		raf.close();
	}

	/*
	 * Default
	 */	
	JafsInodeContext getINodeContext() {
		return ctx;
	}
	
	JafsUnusedMap getUnusedMap() {
		return um;
	}
	
	long getRootBpos() {
		return superBlock.getRootDirBpos();
	}
	
	int getRootIpos() {
		return superBlock.getRootDirIpos();
	}
	
	RandomAccessFile getRaf() {
		return raf;
	}
		
	JafsSuper getSuper() {
		return superBlock;
	}

	JafsBlock setCacheBlock(long bpos, JafsBlock block) throws JafsException, IOException {
		return cache.get(bpos, block);
	}
	
	JafsBlock getCacheBlock(long bpos) throws JafsException, IOException {
		return cache.get(bpos, null);
	}

	long appendNewBlockToArchive() throws JafsException, IOException {
		long bpos = superBlock.getBlocksTotal();
		if (bpos==um.getUnusedMapBpos(bpos)) {
			um.createNewUnusedMap(bpos);
			bpos = superBlock.getBlocksTotal();
		}
		superBlock.incBlocksTotalAndFlush();
		raf.setLength((1+superBlock.getBlocksTotal())*superBlock.getBlockSize());
		return bpos;
	}

	/*
	 * Private
	 */
	private void open(int blockSize, int inodeSize, long maxFileSize) throws IOException, JafsException {
		File f = new File(fname);
		if (!f.exists() && blockSize<0) {
			throw new JafsException("["+fname+"] does not exist");
		}
		if (f.exists() && f.length()<64) {
			throw new JafsException("["+fname+"] does not contain a header");
		}
		raf = new RandomAccessFile(f, "rw");
		if (f.length()==0) {
			raf.setLength(blockSize);
			superBlock = new JafsSuper(this, blockSize);
			superBlock.setInodeSize(inodeSize);
			superBlock.setMaxFileSize(maxFileSize);
			superBlock.flush();
			cache = new JafsBlockCache(this);
			um = new JafsUnusedMap(this);
			ctx = new JafsInodeContext(this, blockSize, inodeSize, maxFileSize);
			JafsDir.createRootDir(this);
		}
		else {
			superBlock = new JafsSuper(this, 64);
			superBlock.read();
			superBlock = new JafsSuper(this, superBlock.getBlockSize()); // reconnect with correct blocksize
			superBlock.read();
			cache = new JafsBlockCache(this);
			um = new JafsUnusedMap(this);
			ctx = new JafsInodeContext(this, superBlock.getBlockSize(), superBlock.getInodeSize(), superBlock.getMaxFileSize());
		}
	}
	
	private void init(String fname, int blockSize, int inodeSize, long maxFileSize) throws JafsException, IOException {
		this.fname = fname;
		open(blockSize, inodeSize, maxFileSize);
	}
}
