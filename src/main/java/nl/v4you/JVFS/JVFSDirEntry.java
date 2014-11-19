package nl.v4you.JVFS;

class JVFSDirEntry {
	static final byte TYPE_FILE = 0x01;
	static final byte TYPE_DIR = 0x02;
	
	long startPos;
	long parentBpos;
	int parentIdx;
	
	long bpos;
	int idx;
	byte type;
	String name;
	
	boolean isFile() {
		return (type & TYPE_FILE) > 0; 
	}
	
	boolean isDirectory() {
		return (type & TYPE_DIR) > 0; 
	}
}
