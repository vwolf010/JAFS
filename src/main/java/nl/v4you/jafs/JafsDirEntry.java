package nl.v4you.jafs;

class JafsDirEntry {
	static final byte TYPE_FILE = 0x01;
	static final byte TYPE_DIR = 0x02;
	
	long startPos;
	long parentBpos;
	int parentIpos;
	
	long bpos;
	int ipos;
	byte type;
	byte name[];
	
	boolean isFile() {
		return (type & TYPE_FILE) > 0; 
	}
	
	boolean isDirectory() {
		return (type & TYPE_DIR) > 0; 
	}
}
