package nl.v4you.JVFS;

import java.io.IOException;

import nl.v4you.JVFS.JVFS;
import nl.v4you.JVFS.JVFSException;

class JVFSSuper {
	static final String magic = "JVFS";
	static final String version = "1";
	
	private JVFS vfs;
	private JVFSBlock rootBlock;
	private int blockSize = 256;
	private int inodeSize = 64;
	private long maxFileSize = 4L*1024L*1024L*1024L;
	private long blocksTotal = 0;
	private long blocksUsed = 0;
	private long rootDirBPos = 1;
	private int rootDirIdx = 0;
	
	JVFSSuper(JVFS vfs, int blockSize) throws JVFSException {
		this.vfs = vfs;
		this.blockSize = blockSize;
		rootBlock = new JVFSBlock(vfs, -1, blockSize);
	}
	
	long getRootDirBpos() {
		return rootDirBPos;
	}
	
	void setRootDirBpos(long bpos) {
		this.rootDirBPos = bpos;
	}

	int getRootDirIdx() {
		return rootDirIdx;
	}
	
	void setRootDirIdx(int idx) {
		this.rootDirIdx = idx;
	}
	
	long getBlocksTotal() {
		return blocksTotal;
	}

	void incBlocksTotal() {
		blocksTotal++;
	}
	
	void incBlocksTotalAndFlush() throws JVFSException, IOException {
		blocksTotal++;
		flush();
	}

	long getBlocksUsed() {
		return blocksUsed;
	}

	void incBlocksUsed() {
		blocksUsed++;
		if (blocksUsed>blocksTotal) {
			throw new RuntimeException("blocksUsed>blocksTotal!!!");
		}
	}

	void incBlocksUsedAndFlush() throws JVFSException, IOException {
		incBlocksUsed();
		flush();
	}
	
	void decBlocksUsed() {
		blocksUsed--;
		if (blocksUsed<0) {
			throw new RuntimeException("blocksUsed<0!!!");
		}		
	}
	
	void decBlocksUsedAndFlush() throws JVFSException, IOException {
		decBlocksUsed();
		flush();
	}
			
	long getBlocksUnused() {
		return blocksTotal-blocksUsed;
	}

	int getBlockSize() {
		return blockSize;
	}
	
	void setBlockSize(int blockSize) {
		this.blockSize = blockSize;
	}

	int getInodeSize() {
		return inodeSize;
	}
	
	void setInodeSize(int inodeSize) {
		this.inodeSize = inodeSize;
	}
	
	long getMaxFileSize() {
		return maxFileSize;
	}
	
	void setMaxFileSize(long maxFileSize) {
		this.maxFileSize = maxFileSize;
	}
		
	void read() throws JVFSException, IOException {
		rootBlock.seek(0);
		rootBlock.readFromDisk();
		byte arr[] = new byte[64];
		int i = 0;
		int b = rootBlock.getByte();
		while (b!=0) {
			arr[i++] = (byte)(b & 0xff);
			b = rootBlock.getByte();
		}
		byte dum[] = new byte[i];
		for (b=0; b<i; b++) {
			dum[b] = arr[b];
		}
		String str = new String(dum);
		String fields[] = str.split("[|]");
		if (fields.length!=9) {
			throw new JVFSException("Expected 8 fields");
		}
		blockSize = Integer.parseInt(fields[2]);
		inodeSize = Integer.parseInt(fields[3]);
		maxFileSize = Long.parseLong(fields[4]);
		rootDirBPos = Long.parseLong(fields[5]);
		rootDirIdx = Integer.parseInt(fields[6]);
		blocksTotal = Integer.parseInt(fields[7]);
		blocksUsed = Integer.parseInt(fields[8]);
	}
	
	void flush() throws JVFSException, IOException {
		rootBlock.initZeros();
		rootBlock.seek(0);
		rootBlock.setBytes((magic+"|").getBytes());
		rootBlock.setBytes((version+"|").getBytes());
		// TODO: should write the following integers as hexadecimal
		rootBlock.setBytes((String.format("%d|", blockSize)).getBytes());
		rootBlock.setBytes((String.format("%d|", inodeSize)).getBytes());
		rootBlock.setBytes((String.format("%d|", maxFileSize)).getBytes());
		rootBlock.setBytes((String.format("%d|", rootDirBPos)).getBytes());
		rootBlock.setBytes((String.format("%d|", rootDirIdx)).getBytes());
		rootBlock.setBytes((String.format("%d|", blocksTotal)).getBytes());
		rootBlock.setBytes((String.format("%d|", blocksUsed)).getBytes());
		rootBlock.flush();
	}
}
