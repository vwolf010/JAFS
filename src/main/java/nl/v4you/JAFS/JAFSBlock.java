package nl.v4you.JAFS;

import java.io.IOException;
import java.io.RandomAccessFile;

import nl.v4you.JAFS.JAFS;
import nl.v4you.JAFS.JAFSException;

class JAFSBlock {
//	private static final int MAX_BLOCKS = 10000;
	
	private int blockSize = -1;
	private byte[] buf;
	public long bpos = -1;
	private RandomAccessFile raf;
	public int byteIdx = 0;

	private void init(JAFS vfs) {
		raf = vfs.getRaf();
		if (blockSize<0) {
			blockSize = vfs.getSuper().getBlockSize();
		}
		buf = new byte[blockSize];
	}
	
//	private VFSBlock(VFS vfs) {
//		init(vfs);
//	}
	
	JAFSBlock(JAFS vfs, long bpos) throws JAFSException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JAFSException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		init(vfs);
		this.bpos = bpos;
		byteIdx = 0;
	}
	
	JAFSBlock(JAFS vfs, long bpos, int blockSize) throws JAFSException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JAFSException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		this.blockSize = blockSize;
		init(vfs);
		this.bpos = bpos;
		byteIdx = 0;
	}

	void initZeros() {
		for (int n=0; n<blockSize; n++) {
			buf[n]=0;
		}
	}
	
	void initOnes() {
		for (int n=0; n<blockSize; n++) {
			buf[n]=(byte)0xff;
		}		
	}
	
	void setBlock(long bpos) throws JAFSException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JAFSException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		this.bpos = bpos;
		byteIdx = 0;
	}
	
	void seek(int b) throws JAFSException {
//		if (bpos>MAX_BLOCKS) {
//			throw new JAFSException("bpos>"+MAX_BLOCKS+" not allowed while debugging");
//		}
		byteIdx = b;
	}
		
	void readFromDisk() throws IOException {
		long start = blockSize + bpos*blockSize;
		raf.seek(start);
		raf.read(buf);
		byteIdx = 0;
	}
	
	void flush() throws IOException, JAFSException {
		long start = blockSize + bpos*blockSize;
		long end = start+blockSize;
		if (end>raf.length()) {
			throw new JAFSException("Trying to write beyond filesize");
		}
		raf.seek(start);
		raf.write(buf);
	}

	int getByte() throws JAFSException {
		if (byteIdx>=blockSize) {
			throw new JAFSException("Trying to read beyond buffer");
		}
		return buf[byteIdx++] & 0xff; 
	}

	int getBytes(byte a[], int start, int len) {
		int blockSpace = blockSize-byteIdx;
		int todo = a.length-start;
		if (todo>blockSpace) {
			todo=blockSpace;
		}
		if (todo>len) {
			todo=len;
		}
		for (int n=0; n<todo; n++) {
			a[start+n] = buf[byteIdx++];
		}
		return todo;
	}
	
	void setByte(int i) throws JAFSException {
		if (byteIdx>=blockSize) {
			throw new JAFSException("Trying to write beyond buffer");
		}
		buf[byteIdx++] = (byte)(i & 0xff);
	}
	
	int setBytes(byte a[], int start, int len) {
		int blockSpace = blockSize-byteIdx;
		int todo = a.length-start;
		if (todo>blockSpace) {
			todo=blockSpace;
		}
		if (todo>len) {
			todo=len;
		}
		for (int n=0; n<todo; n++) {
			buf[byteIdx++] = a[start+n];
		}
		return todo;
	}
		
	long getInt() throws JAFSException {
		int i = 0;
		i |= (long)(getByte() & 0xff)<<24;
		i |= (long)(getByte() & 0xff)<<16;
		i |= (long)(getByte() & 0xff)<< 8;
		i |= (long)(getByte() & 0xff);
		return i;
	}

	void setInt(long l) throws JAFSException {
		setByte((int)((l >> 24) & 0xff));
		setByte((int)((l >> 16) & 0xff));
		setByte((int)((l >>  8) & 0xff));
		setByte((int) (l & 0xff));
	}

	long getLong() throws JAFSException {
		long l = 0;
		l |= ((long)(getByte() & 0xffL))<<56;
		l |= ((long)(getByte() & 0xffL))<<48;
		l |= ((long)(getByte() & 0xffL))<<40;
		l |= ((long)(getByte() & 0xffL))<<32;
		l |= ((long)(getByte() & 0xffL))<<24;
		l |= ((long)(getByte() & 0xffL))<<16;
		l |= ((long)(getByte() & 0xffL))<<8;
		l |= ((long)(getByte() & 0xffL));
		return l;
	}
	
	void setLong(long l) throws JAFSException {
		setByte((int)((l >> 56) & 0xffL));
		setByte((int)((l >> 48) & 0xffL));
		setByte((int)((l >> 40) & 0xffL));
		setByte((int)((l >> 32) & 0xffL));
		setByte((int)((l >> 24) & 0xffL));
		setByte((int)((l >> 16) & 0xffL));
		setByte((int)((l >>  8)& 0xffL));
		setByte((int) (l & 0xffL));
	}

	byte[] getBytes(int i) throws JAFSException {
		byte[] b = new byte[i];
		for (int n=0; n<i; n++) {
			b[n] = (byte)(getByte() & 0xff);
		}
		return b;
	}
	
	void setBytes(byte[] b) throws JAFSException {
		for (int n=0; n<b.length; n++) {
			setByte(b[n] & 0xff);
		}
	}
	
	void setShort(short s) throws JAFSException {
		setByte(((s >>  8) & 0xff));
		setByte((s & 0xff));		
	}

	short getShort() throws JAFSException {
		short s = 0;
		s |= (short)(getByte() & 0xff)<<8;
		s |= (short)(getByte() & 0xff);
		return s;
	}
	
	void setLongVar(long l) throws JAFSException {
		while (l != (l & 0x7f)) {
			setByte((int)(0x80 | (l & 0x7f)));
			l >>= 7;
		}
		setByte((int)l);
	}
	
	long getLongVar() throws JAFSException {
		int shift = 0;
		long l = 0;
		int b = getByte();
		while ((b & 0x80)>0) {
			l |= (b & 0x7fL) << shift;
			shift += 7;
			b = getByte();
		}
		l |= b << shift;
		return l;
	}
}
