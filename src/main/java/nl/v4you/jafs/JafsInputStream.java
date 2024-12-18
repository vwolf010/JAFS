package nl.v4you.jafs;

import java.io.IOException;
import java.io.InputStream;

public class JafsInputStream extends InputStream {
	Jafs vfs;
	String path;
	ZFile zfile;
	
	JafsInputStream(Jafs vfs, JafsFile f) throws JafsException, IOException {
		this.vfs = vfs;
		path = f.getPath();
		ZDirEntry entry = f.getEntry(path, true);
		if (entry.getVpos() != 0) {
			zfile = new ZFile(vfs);
			zfile.openInode(entry.getVpos());
		}
	}

	@Override
	public int read(byte[] b, int off, int len) throws IOException {
		int bread = 0;
		try {
			if (zfile == null) {
				JafsFile f = new JafsFile(vfs, path);
				ZDirEntry entry = f.getEntry(f.getPath(), true);
				if (entry.getVpos() != 0) {
					zfile = new ZFile(vfs);
					zfile.openInode(entry.getVpos());
				}
			}
			if (zfile !=null) {
				bread = zfile.readBytes(b, off, len);
			}
		} catch (JafsException e) {
			throw new IOException("VFSException wrapper: "+e.getMessage());
		}
		return bread;
	}
	
	@Override
	public int read() throws IOException {
		int b = -1;
		try {
			if (zfile ==null) {
				JafsFile f = new JafsFile(vfs, path);
				ZDirEntry entry = f.getEntry(f.getPath(), true);
				if (entry.getVpos() != 0) {
					zfile = new ZFile(vfs);
					zfile.openInode(entry.getVpos());
				}			
			}
			if (zfile != null) {
				return zfile.readByte();
			}
		} catch (JafsException e) {
			throw new IOException("VFSException wrapper: "+e.getMessage());
		}
		return b;
	}
}
