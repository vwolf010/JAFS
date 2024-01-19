package nl.v4you.jafs.internal;

import nl.v4you.jafs.Jafs;
import nl.v4you.jafs.JafsException;

import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;

public class JafsSuper {
	private static final int VERSION = 1;
	private static int POS_BLOCK_SIZE = 6;
	private static int POS_BLOCKS_USED = 10;
	private static int POS_BLOCKS_TOTAL = 14;
	private static int POS_IS_LOCKED = 18;
	private static int FALSE = 0;
	private static int TRUE = 1;
	private Jafs vfs;
	private JafsBlock rootBlock;
	private int blockSize;
	private long blocksTotal = 0;
	private long blocksUsed = 0;
	private long rootDirBPos = 1;
	byte[] buf = new byte[64];
	int isLocked = FALSE;

	private void writeIsLocked() throws JafsException, IOException {
		rootBlock.seekSet(POS_IS_LOCKED);
		Set<Long> blockList = new TreeSet<>();
		rootBlock.writeByte(blockList, isLocked);
		rootBlock.writeToDiskIfNeeded();
	}

	private void lock(File myFile, JafsUnusedMap unusedMap) throws JafsException, IOException {
		if (isLocked == TRUE) {
			setBlocksTotal(myFile);
			setBlocksUsed(unusedMap);
		}
		else {
			isLocked = TRUE;
			writeIsLocked();
		}
	}

	public void close() throws JafsException, IOException {
		isLocked = FALSE;
		writeIsLocked();
	}

	public JafsSuper(Jafs vfs, File myFile, JafsUnusedMap unusedMap) throws IOException, JafsException {
		this.vfs = vfs;
		rootBlock = new JafsBlock(vfs, -1, 32);
		read();
		rootBlock = new JafsBlock(vfs, -1, blockSize);
		lock(myFile, unusedMap);
	}

	public JafsSuper(Jafs vfs, int blockSize, File myFIle, JafsUnusedMap unusedMap) throws JafsException, IOException {
		this.vfs = vfs;
		this.blockSize = blockSize;
		rootBlock = new JafsBlock(vfs, -1, blockSize);
		Set<Long> blockList = new TreeSet<>();
		rootBlock.writeBytes(blockList, "JAFS".getBytes());
		rootBlock.writeByte(blockList, 0);
		rootBlock.writeByte(blockList, VERSION);
		setBlockSize(blockList, blockSize);
		lock(myFIle, unusedMap);
	}
	
//	public long getRootDirBpos() {
//		return rootDirBPos;
//	}
//
//	void setRootDirBpos(Set<Long> blockList, long bpos) {
//		this.rootDirBPos = bpos;
//		rootBlock.seekSet(14);
//		rootBlock.writeInt(blockList, bpos);
//	}

	public long getBlocksTotal() {
		return blocksTotal;
	}

	public long getBlocksUsed() {
		return blocksUsed;
	}

	public void incBlocksTotalAndUsed(Set<Long> blockList) {
		incBlocksTotal();
		incBlocksUsed();
	}
	
	public void incBlocksTotal() {
		blocksTotal++;
		rootBlock.seekSet(POS_BLOCKS_TOTAL);
		Set<Long> blockList = new TreeSet<>();
		rootBlock.writeInt(blockList, blocksTotal);
	}

	void writeBlocksUsed() {
		rootBlock.seekSet(POS_BLOCKS_USED);
		Set<Long> blockList = new TreeSet<>();
		rootBlock.writeInt(blockList, blocksUsed);
	}

	public void incBlocksUsed() {
		blocksUsed++;
		if (blocksUsed > blocksTotal) {
			throw new RuntimeException("blocksUsed ("+blocksUsed+") > blocksTotal ("+blocksTotal+")");
		}
		writeBlocksUsed();
	}

	void decBlocksUsed() {
		blocksUsed--;
		if (blocksUsed < 0) {
			throw new RuntimeException("blocksUsed < 0!!!");
		}
		writeBlocksUsed();
	}

	public int getBlockSize() {
		return blockSize;
	}

	void setBlockSize(Set<Long> blockList, int blockSize) {
		rootBlock.seekSet(POS_BLOCK_SIZE);
		rootBlock.writeInt(blockList, blockSize);
	}

//	public long getMaxFileSize() {
//		return maxFileSize;
//	}
	
//	public void setMaxFileSize(Set<Long> blockList, long maxFileSize) {
//		this.maxFileSize = maxFileSize;
//		rootBlock.seekSet(10);
//		rootBlock.writeInt(blockList, maxFileSize);
//	}

	public void read() throws IOException {
		rootBlock.seekSet(0);
		rootBlock.readFromDisk();
		rootBlock.readBytes(buf, 0, 19);
		blockSize = (int) Util.arrayToInt(buf, POS_BLOCK_SIZE);
		blocksUsed =  Util.arrayToInt(buf, POS_BLOCKS_USED);
		blocksTotal = Util.arrayToInt(buf, POS_BLOCKS_TOTAL);
		isLocked = buf[POS_IS_LOCKED];
	}

//	void writeToDisk() throws IOException, JafsException {
//		rootBlock.writeToDiskIfNeeded();
//	}
	
	void setRafSize() throws IOException {
		vfs.getRaf().setLength((1 + blocksTotal) * blockSize);
	}

	public void setBlocksTotal(File myFile) {
		blocksTotal = (int)(myFile.length() / blockSize) - 1 /* minus superblock */;
	}

	public void setBlocksUsed(JafsUnusedMap unusedMap) throws JafsException, IOException {
		int count = 0;
		long mapNumber = 0;
		while (true) {
			long mapBlockNumber = mapNumber * blockSize * 8;
			if (mapBlockNumber >= blocksTotal) {
				break;
			}
			count += unusedMap.countUsedBlocks((int)mapNumber);
			mapNumber++;
		}
		blocksUsed = count;
	}
}
