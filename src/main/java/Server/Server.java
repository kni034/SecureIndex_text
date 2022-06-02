package Server;

import Client.CryptoHelper;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class Server {
    private static String path = "./src/main/resources/serverStorage/";
    private int upperbound;
    private int s;
    private int r;

    public Server(int s, int r, int upperbound, File dir){
        path = dir.getAbsolutePath() + "/serverStorage/";
        this.s = s;
        this.r = r;
        this.upperbound = upperbound;

        Path userPath = Paths.get(path);
        try {
            Files.createDirectories(userPath);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public int getUpperbound() {
        return upperbound;
    }

    public int getS(){
        return s;
    }

    public int getR(){
        return r;
    }

    public HashSet<BigInteger> readBloomFilter(File BloomFilter){

        ObjectInputStream inputStream = null;
        HashSet<BigInteger> bf = new HashSet<>();
        try {
            inputStream = new ObjectInputStream(new FileInputStream(BloomFilter));
            bf = (HashSet<BigInteger>)inputStream.readObject();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return bf;
    }

    public boolean userExists(String userID){
        Path userPath = Paths.get(path + userID);
        File dir = new File(String.valueOf(userPath));
        if (dir.exists()){
            return true;
        }
        return false;
    }

    public void createUser(String userID){
        Path userPath = Paths.get(path + userID);
        try {
            Files.createDirectories(userPath);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

    public void upload(String userID, File file, File bloomFilter){

        Path userPath = Paths.get(path + userID);
        Path originalPath = Paths.get(file.getPath());
        Path bfPath = Paths.get(bloomFilter.getPath());

        String newBfPath = bloomFilter.getName();
        String newImagePath = file.getName();

        try {
            Files.createDirectories(userPath);

            Files.move(originalPath, userPath.resolve(originalPath.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);

            Files.move(bfPath, userPath.resolve(bfPath.getFileName()),
                    StandardCopyOption.REPLACE_EXISTING);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        updateLookup(userID, newBfPath, newImagePath);
    }

    public BigInteger[] codeWord(BigInteger[] tw, String Did){
        BigInteger[] cw = new BigInteger[tw.length];

        for (int i = 0; i < tw.length; i++) {
            byte[] yiByte = CryptoHelper.calculateHMAC(Did.getBytes(), tw[i].toByteArray());
            BigInteger yi = new BigInteger(yiByte);
            cw[i] = yi;
        }

        return cw;
    }


    public File[] searchAllFiles(String userID, BigInteger[] trapdoor){
        String userPath = path + userID;
        File userDir = new File(userPath);
        userDir.mkdirs();

        ArrayList<File> returnFiles = new ArrayList<>();
        HashMap<String, String> lookup = readLookup(userID);

        for(String Did: lookup.keySet()){
            String bfPath = userPath + "/" + lookup.get(Did);
            BigInteger[] codeword = codeWord(trapdoor, Did);
            if(searchBF(new File(bfPath), codeword)){
                returnFiles.add(new File(userPath + "/" + Did));
            }
        }

        File[] returnArray = returnFiles.toArray(new File[0]);
        return returnArray;
    }

    private boolean searchBF(File bloomFilter, BigInteger[] codeword){
        HashSet<BigInteger> bf = readBloomFilter(bloomFilter);
        for(BigInteger xi : codeword){
            if(!bf.contains(xi)){
                return false;
            }
        }
        return true;
    }

    public void updateLookup(String userID, String bloomFilter, String imageFile){

        HashMap<String, String> lookup = readLookup(userID);
        lookup.put(imageFile, bloomFilter);
        writeLookup(userID, lookup);
    }

    public HashMap<String,String> readLookup(String userID) {

        File toFile = new File(path + userID + "/" + ".lookup");
        HashMap<String, String> lookup;
        try {

            File toRead = toFile;

            FileInputStream fis = new FileInputStream(toRead);
            ObjectInputStream ois = new ObjectInputStream(fis);

            HashMap<String,String> mapInFile=(HashMap<String,String>)ois.readObject();

            ois.close();
            fis.close();

            lookup = mapInFile;
            return lookup;

        } catch(Exception e) {
            System.out.println("Server: New user or missing lookup file, creating new");
        }
        return new HashMap<String, String>();
    }



    public void writeLookup(String userID, HashMap<String,String> lookup) {

        File lookupFile = new File(path + userID + "/" + ".lookup");

        try {
            FileOutputStream fos = new FileOutputStream(lookupFile);
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(lookup);
            oos.flush();
            oos.close();
            fos.close();

        } catch(Exception e) {
            e.printStackTrace();
        }
    }


    //removes file, its bloomfilter and the connection in the lookup
    public void deleteFile(String userID, File file){

        HashMap<String, String> lookup = readLookup(userID);

        try{
            String filePath = file.getPath();
            File bf = new File(lookup.get(filePath));
            file.delete();
            bf.delete();
            lookup.remove(filePath);
            writeLookup(userID, lookup);
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }

}
