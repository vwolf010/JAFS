package nl.v4you.jafs;

import java.util.Scanner;

public class Console {

    private static String getParam(String s) {
        String fname = s.split("[ ]+", 2)[1];
        if (fname.startsWith("\"") && fname.endsWith("\"")) {
            fname = fname.substring(1, fname.length()-1);
        }
        else if (fname.startsWith("'") && fname.endsWith("'")) {
            fname = fname.substring(1, fname.length()-1);
        }
        return fname;
    }

    private static void doesNotExist(JafsFile f) {
        System.out.println("file '"+f.getName()+"' does not exist");
    }

    public static void main(String[] args) throws Throwable {
        if (args.length!=1) {
            System.err.println("Usage: <jafs file name>");
            System.exit(1);
        }
        Jafs fs = new Jafs(args[0]);
        System.out.println("File               : " + args[0]);
        System.out.println(fs.stats());
        JafsFile f = fs.getFile("/");
        Scanner scanner = new Scanner(System.in, "UTF-8");
        System.out.print(f.getCanonicalPath()+"# ");
        while (scanner.hasNextLine()) {
            String cmd = scanner.nextLine().trim();
            if (cmd.equals("exit")) {
                fs.close();
                break;
            }
            else if (cmd.equals("ls")) {
                JafsFile entries[] = f.listFiles();
                for (JafsFile e : entries) {
                    String dirPostfix = "";
                    if (e.isDirectory()) {
                        dirPostfix = "/";
                    }
                    System.out.println(String.format("%10d %s%s", e.length(), e.getName(), dirPostfix));
                }
            }
            else if (cmd.startsWith("cd ")) {
                JafsFile g = fs.getFile(f, getParam(cmd));
                if (!g.exists()) {
                    doesNotExist(g);
                }
                else if (!g.isDirectory()) {
                    System.out.println("'"+g.getName()+"' should be a directory");
                }
                else {
                    f = g;
                }
            }
            else if (cmd.trim().startsWith("cat ")) {
                JafsFile g = fs.getFile(f, getParam(cmd));
                if (!g.exists()) {
                    doesNotExist(g);
                }
                else if (!g.isFile()) {
                    System.out.println("'"+g.getName()+"' should be a file");
                }
                else {
                    byte buf[] = new byte[4096];
                    JafsInputStream is = fs.getInputStream(g);
                    int n = is.read(buf);
                    while (n>0) {
                        System.out.write(buf, 0, n);
                        n = is.read(buf);
                    }
                    is.close();
                }
            }
            System.out.print(f.getCanonicalPath()+"# ");
        }
    }
}
