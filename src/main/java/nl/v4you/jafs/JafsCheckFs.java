package nl.v4you.jafs;

// walk directories starting with /
// for each file, check all blocks
// for each unnused block, check if all used blocks are really used

import nl.v4you.jafs.internal.JafsDirEntry;
import nl.v4you.jafs.internal.JafsInode;

import java.io.IOException;

public class JafsCheckFs {

    static class Report {
        int result;
        String message;
    }

    private final Jafs jafs;

    JafsCheckFs(Jafs jafs) {
        this.jafs = jafs;
    }

    private void checkEntry(JafsDirEntry de) throws JafsException, IOException {
        JafsInode inode = new JafsInode(jafs);
        inode.openInode(de.getBpos());
//        if ((inode.getType() & JafsInode.INODE_INLINED)==0) {
//            jafs.getINodeContext().checkDataAndPtrBlocks(inode);
//        }
    }

    private Report walkTree(JafsFile base) {
        Report report = new Report();
        report.result = 1;

        try {
            JafsFile[] lst = base.listFiles();
            for (JafsFile f : lst) {
                JafsDirEntry entry = f.getEntry(f.getName());
                if (f.isDirectory()) {
                    walkTree(f);
                } else {
                    checkEntry(entry);
                }
            }
        }
        catch (Exception e) {
            report.message = e.getMessage();
            return report;
        }
        report.result = 0;
        report.message = "ok";
        return report;
    }

    public Report walkTree(Jafs jafs) throws JafsException {
        return walkTree(jafs.getFile("/"));
    }

    public static void main(String[] args) throws Throwable {
        args = new String[1];
        args[0] = "c:/data/ggc/ggc_512_128_10MB_compressed_raw_new.jafs";
        Jafs jafs = new Jafs(args[0]);
        System.out.println(jafs.getINodeContext());
        long t1 = System.currentTimeMillis();
        JafsCheckFs checkFs = new JafsCheckFs(jafs);
        JafsCheckFs.Report report = checkFs.walkTree(jafs);
        System.out.println("walking the tree took "+((System.currentTimeMillis()-t1)/1000)+" seconds");
        System.out.println("result  : "+report.result);
        System.out.println("message : "+report.message);
        System.out.println("done");
    }
}
