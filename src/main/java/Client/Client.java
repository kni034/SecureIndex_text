package Client;

import Server.Server;

import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class Client {
    private final String name;
    private final String password;
    private static String tmpFolder = "./src/main/resources/";
    private final BitSet[] Kpriv;
    private final int s;
    private final int r;
    private final String masterKey;
    private IvParameterSpec iv;
    private final SecretKeySpec secretKeySpec;
    private boolean recompute = false;
    private Server server;


    public Client(String name, String password,  int s, int r, File dir, Server server){
        this.server = server;
        tmpFolder = dir.getAbsolutePath() + "/";
        this.name = name;
        this.password = password;
        this.s = s;
        this.r = r;
        this.masterKey = CryptoHelper.sha512Hash(name + password);
        secretKeySpec = new SecretKeySpec(masterKey.substring(0,16).getBytes(), "AES");

        Path up = Paths.get(tmpFolder + "/clientStorage/" + name);
        try {
            Files.createDirectories(up);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        iv = new IvParameterSpec(masterKey.substring(16,32).getBytes(StandardCharsets.UTF_8));

        this.Kpriv = keygen(s,r,masterKey);
    }


    public String getName(){
        return name;
    }

    //user identifier
    public String getUid(){
        return CryptoHelper.sha512Hash(name);

    }



    public void search(String searchWord){
        int filesDownloaded = searchForWord(searchWord);
        System.out.println("Client: Downloaded " + filesDownloaded + " files from " + getName());
    }

    private int searchForWord(String searchWord){

        BigInteger[] trapdoor = trapdoor(searchWord);
        File[] files = server.searchAllFiles(getUid(), trapdoor);

        for(File f:files){
            File dec = decryptFile(f);




            //Error check does not work for extra words
            /*
            if(!checkError(searchWord, dec)){
                System.out.println(dec.getName() + " was downloaded from a hash collision, it will be deleted");
                dec.delete();
                continue;
            }

             */

            Path userPath = Paths.get(tmpFolder + "/clientStorage/" + getName());
            Path originalPath = Paths.get(dec.getPath());

            try {
                Files.createDirectories(userPath);

                Files.move(originalPath, userPath.resolve(originalPath.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
        return files.length;
        //System.out.println("Downloaded " + files.length + " files from " + getName());
    }

    public BitSet[] keygen(int s, int r, String masterKey){
        SecureRandom random = new SecureRandom(masterKey.getBytes());
        BitSet[] Kpriv = new BitSet[r];

        for(int i=0;i<r;i++){
            BitSet k = new BitSet(s);
            for(int j=0;j<s;j++){
                boolean bit = random.nextBoolean();
                k.set(j,bit);
            }
            Kpriv[i] = k;
        }
        return Kpriv;
    }

/*
    private boolean checkError(String w, File file){
        String[] words = getWords(file);

        for (String word : words) {
            word = formatSearchWord(word);
            if (word.equals(w)) {
                return true;
            }
        }

        return false;
    }

 */


    public BigInteger[] trapdoor(String w){
        BigInteger[] Tw = new BigInteger[Kpriv.length];

        String formattedWord = formatSearchWord(w);

        for (int i = 0; i < Kpriv.length; i++) {
            byte[] temp = CryptoHelper.calculateHMAC(formattedWord.getBytes(), Kpriv[i].toByteArray());
            byte[] xiByte = new byte[(s/8) - 0];
            System.arraycopy(temp, 0, xiByte, 0, xiByte.length);
            BigInteger xi = new BigInteger(xiByte);
            Tw[i] = xi;
        }

        return Tw;
    }

    public BigInteger[] codeWord(BigInteger[] tw, String Did){
        BigInteger[] cw = new BigInteger[tw.length];

        for (int i = 0; i < tw.length; i++) {
            byte[] temp = CryptoHelper.calculateHMAC(Did.getBytes(), tw[i].toByteArray());
            byte[] yiByte = new byte[(s/8) - 0];
            System.arraycopy(temp, 0, yiByte, 0, yiByte.length);
            BigInteger yi = new BigInteger(yiByte);
            cw[i] = yi;
        }

        return cw;
    }

    public File encryptFile(File file){
        String newFileName = file.getName();
        try {
            newFileName = CryptoHelper.encryptString(file.getName(), secretKeySpec, iv);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        File encrypted = new File(tmpFolder + newFileName);
        CryptoHelper.encryptFile(file, encrypted, iv, secretKeySpec);

        return encrypted;
    }

    public File decryptFile(File file) {

        String originalFileName = file.getName();
        try{
            originalFileName = CryptoHelper.decryptString(file.getName(), secretKeySpec, iv);
        }
        catch (Exception e){
            e.printStackTrace();
        }

        File clear = new File(tmpFolder + originalFileName);
        CryptoHelper.decryptFile(file, clear, iv, secretKeySpec);

        return clear;
    }

    public void upload(File file, String[] extraWords){

        File bloomFilter = buildIndex(file, extraWords, server.getUpperbound());

        File encrypted = encryptFile(file);


    }

    //used for testing basic version of scheme
    private String[] readWords(File file){
        ArrayList<String> allWords = new ArrayList<>(); //remove duplicates

        try {
            Scanner fileReader = new Scanner(file);
            fileReader.hasNextLine();

            while (fileReader.hasNextLine()) {
                String data = fileReader.nextLine();
                String[] words = data.split(" ");

                for (String word : words) {
                    allWords.add(word);
                }
            }

            fileReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String[] words = new String[allWords.size()];
        words = allWords.toArray(words);
        return words;
    }

    public String formatSearchWord(String word){
        word = word.replaceAll(" ", "");
        word = word.toLowerCase();
        return word;
    }

    public File buildIndex(File file, String[] extraWords, int u){
        ArrayList<String> allWords = new ArrayList<>();
        String[] words = readWords(file);
        System.out.println("Keywords for " + file.getName() +": " +Arrays.toString(words));

        if(words.length != 0){
            for(String s: words){
                allWords.add(s);
            }
        }

        if(extraWords.length != 0){
            for(String s: extraWords){
                allWords.add(s);
            }
        }

        return buildIndexWordsProvided(file, u, allWords.toArray(new String[0]));
    }

    public File buildIndexWordsProvided(File file, int u, String[] words){
        String newFileName = file.getName();
        try {
            newFileName = CryptoHelper.encryptString(file.getName(), secretKeySpec, iv);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        String Did = newFileName + ".bf";
        Set<BigInteger> bloomFilter = new HashSet<>();

        for(String word : words){
            BigInteger[] Tw = trapdoor(word);
            BigInteger[] Cw = codeWord(Tw, newFileName);
            bloomFilter.addAll(Arrays.asList(Cw));
        }

        //(upperbound u - unique words v) * number of hashes r
        //u = number of trapdoors(words), not entries in bloomfilter. (number of entries = u*r)
        while(bloomFilter.size() < u*r){
            byte[] bytes = new byte[s/8];
            SecureRandom random = new SecureRandom();
            try {
                random.nextBytes(bytes);
                byte[] temp = CryptoHelper.calculateHash(bytes);
                byte[] randomHash = new byte[(s/8) - 0];
                System.arraycopy(temp, 0, randomHash, 0, randomHash.length);
                bloomFilter.add(new BigInteger(randomHash));

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        File f = new File(tmpFolder + Did);
        ObjectOutputStream outputStream;
        try {
            outputStream = new ObjectOutputStream(new FileOutputStream(f));
            outputStream.writeObject(bloomFilter);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return f;
    }


    public boolean uploadTxt(File f){
        String[] words = readWords(f);
        File bloomfilter = buildIndexWordsProvided(f,server.getUpperbound(),words);
        File encrypted = encryptFile(f);


        server.createUser(getUid());
        server.upload(getUid(),encrypted,bloomfilter);
        return true;
    }

    public int searchTxt(String word){

        BigInteger[] trapdoor = trapdoor(word);
        File[] files = server.searchAllFiles(getUid(),trapdoor);

        for(File f:files){
            File dec = decryptFile(f);

            Path userPath = Paths.get(tmpFolder + "/clientStorage/" + getName());
            Path originalPath = Paths.get(dec.getPath());

            try {
                Files.createDirectories(userPath);

                Files.move(originalPath, userPath.resolve(originalPath.getFileName()),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            catch (Exception e){
                e.printStackTrace();
            }
        }
        System.out.println("Search complete, found " + files.length + " matches");
        return files.length;
    }


}
