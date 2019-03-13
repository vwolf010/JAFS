package nl.v4you.jafs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;

class JafsBlock {
	private static final int SUPERBLOCK_SIZE = 1;

	private int blockSize = -1;
	private byte[] buf;
	public long bpos = -1;
	private RandomAccessFile raf;
	public int byteIdx = 0;
	boolean needsFlush = false;

	int bytesLeft() {
		return blockSize-byteIdx;
	}

	JafsBlock(Jafs vfs, long bpos) {
		this(vfs, bpos, -1);
	}
	
	JafsBlock(Jafs vfs, long bpos, int blockSize) {
		raf = vfs.getRaf();
		if (blockSize<0) {
			blockSize = vfs.getSuper().getBlockSize();
		}
		this.blockSize = blockSize;
		buf = new byte[blockSize];
		this.bpos = bpos;
		byteIdx = 0;
	}

	void setBpos(long bpos) {
	    this.bpos = bpos;
        byteIdx = 0;
    }

	void initZeros() {
		Arrays.fill(buf, (byte)0);
        needsFlush=true;
	}
	
	void seekSet(int b) {
		byteIdx = b;
	}
		
	void readFromDisk() throws IOException {
		long start = (SUPERBLOCK_SIZE+bpos) * blockSize;
		raf.seek(start);
		raf.read(buf);
		byteIdx = 0;
		needsFlush=false;
	}
	
	private void writeToDisk() throws IOException, JafsException {
		long start = (SUPERBLOCK_SIZE+bpos) * blockSize;
		long end = start + blockSize;
		if (end>raf.length()) {
			throw new JafsException("Trying to write beyond filesize");
		}
		raf.seek(start);
		raf.write(buf);
		needsFlush=false;
	}

	void writeToDiskIfNeeded() throws IOException, JafsException {
	    if (needsFlush) {
	        writeToDisk();
	        needsFlush=false;
        }
    }

//	void dumpBlock(File f) {
//		try {
//			FileOutputStream fos = new FileOutputStream(f);
//			fos.write(buf);
//			fos.close();
//		}
//		catch (Exception e) {
//			System.err.println("Failed to dump block "+f.getAbsolutePath());
//		}
//	}
//
//	void dumpBlock() {
//		File f = new File("c:/data/temp/inode_block_"+bpos+".dmp");
//		dumpBlock(f);
//	}

	int readByte() {
		return buf[byteIdx++] & 0xff;
	}

	void writeByte(int b) {
		buf[byteIdx++] = (byte)(b & 0xff);
		needsFlush=true;
	}

	void readBytes(byte b[], int off, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(buf, byteIdx, b, off, len);
		byteIdx += len;
	}

	void writeBytes(byte b[], int off, int len) {
		if (len == 0) {
			return;
		}
		System.arraycopy(b, off, buf, byteIdx, len);
		byteIdx+=len;
		needsFlush=true;
	}
		
	long readInt() {
		long i = 0;
		i |= (buf[byteIdx++] & 0xffL)<<24;
		i |= (buf[byteIdx++] & 0xffL)<<16;
		i |= (buf[byteIdx++] & 0xffL)<< 8;
		i |= (buf[byteIdx++] & 0xffL);
		return i;
	}

	void writeInt(long l) {
		buf[byteIdx++] = (byte)((l >> 24) & 0xffL);
		buf[byteIdx++] = (byte)((l >> 16) & 0xffL);
		buf[byteIdx++] = (byte)((l >>  8) & 0xffL);
		buf[byteIdx++] = (byte)(l & 0xffL);
		needsFlush=true;
	}
}
