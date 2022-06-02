import Client.Client;
import Server.Server;

import javax.swing.*;
import java.io.File;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) {

        loop();
    }
    public static void loop(){
        Scanner sc = new Scanner(System.in);
        System.out.println("Before we start, you have to choose a working directory.");
        System.out.println("This is where the server 'stores' files and downloads them to.");
        System.out.println("It is highly recommended that you choose an empty directory, or create a new one");
        System.out.println("You can open the directory while the program runs to see what is going on");
        System.out.println("Press 'Enter' to continue, this opens a window to choose directory");

        sc.nextLine();

        JFileChooser fc = new JFileChooser();
        fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fc.setMultiSelectionEnabled(false);
        fc.showOpenDialog(null);
        File dir = fc.getSelectedFile();
        if (dir == null){
            System.out.println("You need to choose a working directory, closing the program...");
            System.exit(1);
        }
        String path = dir.getAbsolutePath() + "/";


        fc = new JFileChooser(dir);
        Server server = new Server(512,3,100, dir);
        while(true){
            System.out.println("Username: ");
            String username = sc.nextLine();
            System.out.println("Password: ");
            String password = sc.nextLine();
            if (username.equals("") || password.equals("")){
                System.out.println("pass" +  password);
                System.out.println("username or password cannot be empty");
                continue;
            }
            Client client = new Client(username, password, 512,3,  dir, server);
            while(true){
                System.out.println("\n \n");
                System.out.println("You are logged in as " + client.getName());
                System.out.println("If you want to uplaod a file press 'u' (select multiple files by holding 'ctrl' while selecting, or 'ctrl' + 'a' to select all)");
                System.out.println("If you want to search for a word press 's'");
                System.out.println("If you want to log out press 'l', or press 'q' to quit");
                System.out.print("Command: ");
                String command = sc.nextLine();
                command = command.toLowerCase();
                if(command.equals("u")){
                    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
                    fc.setMultiSelectionEnabled(true);
                    fc.showOpenDialog(null);
                    File[] files = fc.getSelectedFiles();
                    if (files.length == 0){
                        System.out.println("No files chosen, try agian");
                        continue;
                    }
                    for (File file : files) {
                        client.uploadTxt(file);
                    }
                    System.out.println("Upload complete");
                }
                else if(command.equals("s")){
                    System.out.println("Keyword to search for: ");
                    String keyword = sc.nextLine();

                    client.searchTxt(keyword);

                }
                else if(command.equals("l")){
                    break;
                }
                else if(command.equals("q")){
                    System.exit(0);
                }
                else {
                    System.out.println("Wrong command, try again");
                }
            }
        }

    }

}