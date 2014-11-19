package nl.v4you.JVFS;

import java.io.IOException;
import java.io.InputStream;

import nl.v4you.JVFS.JVFS;
import nl.v4you.JVFS.JVFSException;

public class JVFSFileInputStream extends InputStream {
	JVFS vfs;
	String path;
	JVFSInode inode;
	
	JVFSFileInputStream(JVFS vfs, JVFSFile f) throws JVFSException, IOException {
		this.vfs = vfs;
		path = f.path;
		JVFSDirEntry entry = f.getEntry(path);
		if (entry.bpos>0) {
			inode = new JVFSInode(vfs, entry);
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int bread = 0;
		try {
			if (inode==null) {
				JVFSFile f = new JVFSFile(vfs, path);
				JVFSDirEntry entry = f.getEntry(f.path);
				if (entry.bpos>0) {
					inode = new JVFSInode(vfs, entry);
				}			
			}
			if (inode!=null) {
				bread = inode.readBytes(b, off, len);
			}
		} catch (JVFSException e) {
			throw new IOException("VFSException wrapper: "+e.getMessage());
		}
		return bread;
	}
	
	@Override
	public int read() throws IOException {
		int b = -1;
		try {
			if (inode==null) {
				JVFSFile f = new JVFSFile(vfs, path);
				JVFSDirEntry entry = f.getEntry(f.path);
				if (entry.bpos>0) {
					inode = new JVFSInode(vfs, entry);
				}			
			}
			if (inode!=null) {
				return inode.readByte();
			}
		} catch (JVFSException e) {
			throw new IOException("VFSException wrapper: "+e.getMessage());
		}
		return b;
	}
}
