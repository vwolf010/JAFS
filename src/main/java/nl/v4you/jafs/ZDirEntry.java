package nl.v4you.jafs;

class ZDirEntry {
	long prevStartPos;
	long startPos;
	long parentBpos;
	long vpos;
	int type;
	byte[] name;
	boolean isFile() {
		return (type & ZFile.INODE_FILE) != 0;
	}
	boolean isDirectory() {
		return (type & ZFile.INODE_DIR) != 0;
	}
	long getVpos() {
		return vpos;
	}
	long getParentBpos() {
		return parentBpos;
	}
	void setName(byte[] name) {
		this.name = name;
	}
}
