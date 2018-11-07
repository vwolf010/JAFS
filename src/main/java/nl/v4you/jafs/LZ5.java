package nl.v4you.jafs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;

// <token><more_literal_length_bytes><literals><repeat_bytes><more_repeat_length_bytes>

public class LZ5 {

    public static final int MATCH_LEN_MIN = 3;

    byte bb[] = new byte[100];

    public byte[] decompress(byte in[]) {

        int pIn=0;

        int flen=0;
        int shift=0;
        while ((in[pIn] & 0x80) != 0) {
            flen |= (in[pIn++] & 0x7f) << shift;
            shift += 7;
        }
        flen |= (in[pIn++] & 0x7f) << shift;

        byte out[] = new byte[flen];

        int bytesLeft=flen; // decompressed size
        int pOut=0;
        while (bytesLeft>0) {
            int token = in[pIn++] & 0xff;
            int copyLen = token>>>4;
            if (copyLen==0xf) {
                int cpyMore = in[pIn++] & 0xff;
                copyLen += cpyMore;
                while (cpyMore==0xff) {
                    cpyMore = in[pIn++] & 0xff;
                    copyLen += cpyMore;
                }
            }
            if (copyLen!=0) {
                bytesLeft-=copyLen;
                System.arraycopy(in, pIn, out, pOut, copyLen);
                pIn+=copyLen;
                pOut+=copyLen;
            }

            int offset = in[pIn++] & 0xff;
            if ((offset & 0x80)!=0) {
                offset &= 0x7f;
                offset |= (in[pIn++] & 0xff) << 7;
            }
            int matchLen = token & 0xf;
            if (matchLen==0xf) {
                int cpyMore = in[pIn++] & 0xff;
                matchLen += cpyMore;
                while (cpyMore==0xff) {
                    cpyMore = in[pIn++] & 0xff;
                    matchLen += cpyMore;
                }
            }
            matchLen += MATCH_LEN_MIN;
            if (matchLen>bytesLeft) {
                matchLen=bytesLeft;
            }
            bytesLeft -= matchLen;
            while (matchLen>0) {
                int cStart = pOut - offset;
                int len=matchLen;
                if ((pOut-cStart)<matchLen) {
                    len = pOut-cStart;
                }
                System.arraycopy(out, cStart, out, pOut, len);
                pOut += len;
                matchLen -= len;
            }
        }
        return out;
    }

    void compress(byte in[], byte out[]) {
        int pOut = 0;
        int len = in.length;
        if (len<0xf) {
            out[pOut++]=(byte)(len << 4);
        }
        else {
            out[pOut++]=(byte)(0xf << 4);
            len-=0xf;
            while (len>=0xff) {
                out[pOut++]=(byte)0xff;
                len-=0xff;
            }
            out[pOut++]=(byte)len;
        }
        System.arraycopy(in, 0, out, pOut, in.length);
    }

    int serializeInt(byte a[], int off, int i) {
        int len=0;
        while (i>0x7f) {
            a[off++]=(byte)(0x80 | (i & 0x7f));
            i >>>= 7;
            len++;
        }
        a[off]=(byte)i;
        len++;
        return len;
    }

//    int deserializeInt(byte a[], int off) {
//        int i=0;
//        while ((a[off] & 0x80) != 0) {
//            i |= a[off++] & 0x7f;
//            i <<= 7;
//        }
//        i |= a[off] & 0x7f;
//        return i;
//    }

    void compressLZ4(byte strAsBytes[], int litStart, int litLen, int offset, int repLen, ByteArrayOutputStream bos) {
        int token = litLen>=0xf ? 0xf0 : (litLen<<4);
        token |= repLen>=0xf ? 0xf : repLen;
        bos.write(token);
        if ((litLen-0xf)>=0) {
            int bytesLeft=litLen-0xf;
            while (bytesLeft>=0xff) {
                bos.write(0xff);
                bytesLeft-=0xff;
            }
            bos.write(bytesLeft);
        }
        bos.write(strAsBytes, litStart, litLen);
        if (offset>0x7f) {
            bos.write(0x80 | (offset & 0x7f));
            bos.write((offset >>> 7) & 0xff);
        }
        else {
            bos.write(offset & 0x7f);
        }
        int bytesLeft=repLen-0xf;
        if (bytesLeft>=0) {
            while (bytesLeft>=0xff) {
                bos.write(0xff);
                bytesLeft-=0xff;
            }
            bos.write(bytesLeft);
        }
    }

    void LZ78(String str) {
        char ts[] = str.toCharArray();

        String dict[] = new String[1000];
        int dictSize=256;
        for (int n=0; n<256; n++) {
            dict[n] = ""+(char)n;
        }

        String prev="";
        String comb;

        for (int i=0; i<ts.length; i++) {
            comb = prev+ts[i];
            int ptr = -1;
            if (comb.length()==1) {
                ptr=comb.charAt(0);
            }
            else for (int n=256; n<dictSize; n++) {
                if (dict[n].equals(comb)) {
                    ptr=n;
                    break;
                }
            }
            if (ptr!=-1) {
                prev=comb;
            }
            else {
                if (prev.length()==1) {
                    System.out.println((int)prev.charAt(0)+" ");
                }
                else for (int n=256; n<dictSize; n++) {
                    if (dict[n].equals(prev)) {
                        System.out.println(n+" ");
                        break;
                    }
                }
                dict[dictSize++]=comb;
                prev=""+ts[i];
            }
        }
        if (prev.length()==1) {
            System.out.println((int)prev.charAt(0)+" ");
        }
        else for (int n=256; n<dictSize; n++) {
            if (dict[n].equals(prev)) {
                System.out.println(n+" ");
                break;
            }
        }
        for (int n=256; n<dictSize; n++) {
            System.out.println(dict[n]);
        }
    }

    int byteArrayLastIndexOf(byte a[], int aStart, int aLen, int bStart, int bLen) {
        int p=0;
        int bEnd = bStart+bLen-1;
        for (int n=aStart+aLen-1; n>=aStart; n--) {
            if (a[n]==a[bEnd-p]) {
                p++;
                if (p==bLen) {
                    return n;
                }
            }
            else if (p!=0){
                p=0;
                n++;
            }
        }
        return -1;
    }

    public byte[] LZ77(byte strAsBytes[]) {

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        int len = serializeInt(bb, 0, strAsBytes.length);
        bos.write(bb, 0, len);

        int winMax = 32750;

        int ptr=0;

        int litLen=0;
        int prefixLen=0;

        int lastIndexOfPrefix=-1;
        while (ptr<strAsBytes.length) {
            int lastIndexOf;
            if (lastIndexOfPrefix!=-1) {
                if (strAsBytes[ptr]==strAsBytes[lastIndexOfPrefix+prefixLen]) {
                    lastIndexOf = lastIndexOfPrefix;
                }
                else {
                    int winStart = ptr-winMax;
                    if (winStart<0) {
                        winStart=0;
                    }
                    lastIndexOf = byteArrayLastIndexOf(
                            strAsBytes,
                            winStart,
                            (lastIndexOfPrefix + prefixLen + 1) - winStart,
                            ptr - prefixLen,
                            prefixLen + 1);
                }
            }
            else {
                int winStart = ptr-winMax;
                if (winStart<0) {
                    winStart=0;
                }
                lastIndexOf = byteArrayLastIndexOf(
                        strAsBytes,
                        winStart,
                        ptr-winStart,
                        ptr - prefixLen,
                        prefixLen + 1);
            }
            if (lastIndexOf!=-1) {
                prefixLen++;
                lastIndexOfPrefix = lastIndexOf;
            }
            else {
                if (prefixLen==0) {
                    litLen++;
                    prefixLen=0;
                }
                else if (prefixLen<MATCH_LEN_MIN) {
                    litLen+=prefixLen;
                    prefixLen=0;
                    ptr--; // retry byte we could not find earlier
                }
                else {
                    int back = ptr-prefixLen-lastIndexOfPrefix;
                    compressLZ4(strAsBytes, ptr-prefixLen-litLen, litLen, back, prefixLen-MATCH_LEN_MIN, bos);
                    litLen=0;
                    prefixLen=0;
                    ptr--; // retry byte we could not find earlier
                }
                lastIndexOfPrefix=-1;
            }
            ptr++;
        }
        if (prefixLen==0) {
            compressLZ4(strAsBytes, ptr-prefixLen-litLen, litLen, 0, 0, bos);
        }
        else {
            int back = ptr-prefixLen-lastIndexOfPrefix;
            int prefixLenNormalized=prefixLen-MATCH_LEN_MIN;
            if (prefixLenNormalized<0) {
                prefixLenNormalized=0;
            }
            compressLZ4(strAsBytes, ptr-prefixLen-litLen, litLen, back, prefixLenNormalized, bos);
        }
        return bos.toByteArray();
    }

    public static void main(String[] args) throws IOException {
        LZ5 lz4 = new LZ5();

        //File f = new File("c:/data/ggc/801042437.xml");
        File f = new File("c:/data/ggc/clustering.txt");
        FileInputStream fis = new FileInputStream(f);
        byte b[] = new byte[(int)f.length()];
        fis.read(b);
        fis.close();

        //byte original[] = "hallo hallo hallo hallo hallo hallo".getBytes();
        //byte original[] = "hallo a hallo b hallo c hallo d hallo e hallo".getBytes();
        //byte original[] = " hallo aaaaa hallo hallo".getBytes();
        //byte original[] = " aaaaa bbbbb bbbbb bbbbbc".getBytes();
        //byte original[] = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes();
        byte original[] = b;

        //lz4.compressLZ4("a".getBytes(), 1, 39);
        //lz4.compressLZ4("abcbla".getBytes(), 3, 6);

        byte compressed[] = lz4.LZ77(original);
//        FileOutputStream fos = new FileOutputStream(new File("c:/data/out.bin"));
//        fos.write(compressed);
//        fos.close();
        System.err.println(original.length+" : "+compressed.length);
        byte fDecompressed[] = lz4.decompress(compressed);
        //System.err.println(new String(fDecompressed, "UTF-8"));
        System.err.println(Arrays.equals(original, fDecompressed));

        //System.out.println(new String(Arrays.copyOf(back, len), Util.UTF8));
    }
}