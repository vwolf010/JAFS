package nl.v4you.jafs;

class JafsDirEntry {
	long startPos;
	long parentBpos;
	long bpos;
	int type;
	byte[] name;
	boolean isFile() {
		return (type & JafsInode.INODE_FILE) != 0;
	}
	boolean isDirectory() {
		return (type & JafsInode.INODE_DIR) != 0;
	}
}
