package com.pitayazhu.webpage_simplifier;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;

public class Main {

    public static void main(String[] args) {
        URL url;
        InputStream is = null;
        BufferedReader br;
        String line, title;

        try {
            url = new URL("http://cspo.zju.edu.cn/redir.php?catalog_id=21530&object_id=682155");
            String encoding = "GBK";
            is = url.openStream();
            br = new BufferedReader(new InputStreamReader(is, encoding) );

            while ((line = br.readLine()) != null) {
                line = line.trim();
                Node document = new Node(line, 0, br);

                // read each root node but only process the html node
                if (document.tagName.equals("html")) {

                    Node.replaceEntities(document);
                    Node.cleanEmptyLines(document);

                    title = document.getTitle();
                    ArrayList<String> mainContent = document.getContent();

                    try {
                        BufferedWriter out = new BufferedWriter(new FileWriter("file.txt"));

                        if (!title.equals("")) {
                            out.write("Page title: " + title + "\n");
                        }

                        out.write("Main content: " + "\n");
                        for (String contentLine : mainContent) {
                            out.write(contentLine + "\n");
                        }

                        out.close();
                    } catch (IOException e) {}

                    break;
                }
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException ioe) {
                // nothing to see here
            }
        }
    }
}
