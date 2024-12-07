package nl.v4you.jafs.internal;

import nl.v4you.jafs.JafsException;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class JafsSuper {
	private static final int VERSION = 1;
	private static final int POS_BLOCK_SIZE = 6;
	private static final int POS_BLOCKS_USED = 10;
	private static final int POS_BLOCKS_TOTAL = 14;
	private static final int POS_IS_LOCKED = 18;
	private static final int FALSE = 0;
	private static final int TRUE = 1;
	private static final int HEADER_SIZE = 19;
	private final RandomAccessFile raf;
	private byte[] buf = new byte[64]; // not redundant

	private int blockSize = 0;
	private long blocksTotal = 0;
	private long blocksUsed = 0;
	int isLocked = FALSE;

	public void lock(File myFile, JafsUnusedMap unusedMap) throws JafsException, IOException {
		if (isLocked == TRUE) {
			setBlocksTotal(myFile);
			setBlocksUsed(unusedMap);
		} else {
			isLocked = TRUE;
			flush();
		}
	}

	public void close() throws IOException {
		isLocked = FALSE;
		flush();
	}

	public JafsSuper(RandomAccessFile raf, int blockSize) throws JafsException, IOException {
		this.raf = raf;
		if (raf.length() == 0) {
			if (blockSize <= 0) {
				throw new JafsException("Unable to create new jafs file with supplied blockSize " + blockSize);
			}
			this.blockSize = blockSize;
			buf = new byte[this.blockSize];
			flush();
		} else {
			read();
			if (this.blockSize > 0 && blockSize > 0 && this.blockSize != blockSize) {
				throw new JafsException("Supplied block size [" + blockSize + "] does not match header block size [" + this.blockSize + "]");
			}
			if (raf.length() < this.blockSize) {
				throw new JafsException("Malformed jafs file, file length (" + raf.length() + ") < block size (" + blockSize + ")");
			}
			buf = new byte[this.blockSize];
		}
	}

	public long getBlocksTotal() {
		return blocksTotal;
	}

	public long getBlocksUsed() {
		return blocksUsed;
	}

	public void incBlocksTotalAndUsed() {
		incBlocksTotal();
		incBlocksUsed();
	}

	public void incBlocksTotal() {
		blocksTotal++;
	}

	void setBlocksUsed() {
		Util.intToArray(buf, POS_BLOCKS_USED, blocksUsed);
	}

	public void incBlocksUsed() {
		blocksUsed++;
		if (blocksUsed > blocksTotal) {
			throw new RuntimeException("blocksUsed ("+blocksUsed+") > blocksTotal ("+blocksTotal+")");
		}
	}

	void decBlocksUsed() {
		blocksUsed--;
		if (blocksUsed < 0) {
			throw new RuntimeException("blocksUsed < 0!!!");
		}
		setBlocksUsed();
	}

	public int getBlockSize() {
		return blockSize;
	}

	public void read() throws IOException, JafsException {
		if (raf.length() < HEADER_SIZE) {
			throw new JafsException("File too small, only " + raf.length() + " bytes");
		}
		raf.seek(0);
		if (HEADER_SIZE != raf.read(buf, 0, HEADER_SIZE)) {
			throw new JafsException("Could not read header");
		}
		if (buf[0] != 'J' || buf[1] != 'A' || buf[2] != 'F' || buf[3] != 'S') {
			throw new JafsException("Magic is incorrect");
		}
		int version = ((buf[4] & 0xff) << 8) | (buf[5] & 0xff);
		if (version != VERSION) {
			throw new JafsException("Version is incorrect, should be " + VERSION + " but got " + version);
		}
		blockSize = (int) Util.arrayToInt(buf, POS_BLOCK_SIZE);
		blocksUsed =  Util.arrayToInt(buf, POS_BLOCKS_USED);
		blocksTotal = Util.arrayToInt(buf, POS_BLOCKS_TOTAL);
		isLocked = buf[POS_IS_LOCKED];
	}

	private void flush() throws IOException {
		if (raf.length() < 4096) raf.setLength(4096);
		buf[0] = 'J';
		buf[1] = 'A';
		buf[2] = 'F';
		buf[3] = 'S';
		buf[4] = 0;
		buf[5] = VERSION;
		Util.intToArray(buf, POS_BLOCK_SIZE, blockSize);
		Util.intToArray(buf, POS_BLOCKS_USED, blocksUsed);
		Util.intToArray(buf, POS_BLOCKS_TOTAL, blocksTotal);
		buf[POS_IS_LOCKED] = (byte)isLocked;
		raf.seek(0);
		raf.write(buf, 0, blockSize);
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
