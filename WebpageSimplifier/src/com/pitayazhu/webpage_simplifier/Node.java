package com.pitayazhu.webpage_simplifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Represents an HTML Dom node without attributes for web page simplify.
 * The node is read and processed from BufferReader on construction and
 * recursively constructing all its children.
 * @author pitaya
 */

public class Node {

    public int nodeStartPos = 0, nodeEndPos = 0;
    public String tagName = "";
    public String finalLine = "";
    public ArrayList<Node> children = new ArrayList<>();
    public ArrayList<String> text = new ArrayList<>();
    public boolean isNode = true;

    // the threshold to ignore short lines
    private static final int MIN_LINE_LENGTH = 10;

    // the title of the page
    // static since one page only has one title element
    public static String title = "";

    // void tags are not enclosed
    private static final String[] voidTags = {"area", "base", "br", "col", "command", "embed", "hr", "img",
            "input", "keygen", "link", "meta", "param", "source", "track", "wbr"};

    // ignore these tags since their contents are not html
    private static final String[] ignoreTags = {"style", "script"};

    // these special entities are encoded and we should replace them with the original char
    private static Map<String, String> specialEntities;
    static {
        specialEntities = new HashMap<String, String>();
        specialEntities.put("&nbsp;", " ");
        specialEntities.put("&amp;", "&");
        specialEntities.put("&lt;", "<");
        specialEntities.put("&gt;", ">");
        specialEntities.put("&lsaquo;", "‹");
        specialEntities.put("&rsaquo;", "›");
        specialEntities.put("&lsquo;", "‘");
        specialEntities.put("&rsquo;", "’");
        specialEntities.put("&ldquo;", "“");
        specialEntities.put("&rdquo;", "”");
        specialEntities.put("&middot;", "·");
        specialEntities.put("&.*;", "");
    }

    /**
     * Constructor without starting position specified
     * @param line  the beginning line of the node
     * @param br    the BufferReader to read the following lines
     * @throws IOException
     */
    public Node(String line, BufferedReader br) throws IOException {
        this(line, 0, br);
    }

    /**
     * Constructs a node and recursively fetch its children
     * @param line          the beginning line of the node
     * @param startPosition the start position of this node
     * @param br            the BufferReader to read the following lines
     * @throws IOException
     */
    public Node(String line, int startPosition, BufferedReader br) throws IOException {

        String firstTagEndSymbol;
        String lineContent = "";

        int firstTagStartPos = line.indexOf('<', startPosition);

        // if '<' is not found in the given line, find it in the following lines
        // this should not occur in normal logic, however html could be so weired
        while (firstTagStartPos == -1) {
            if ((line = br.readLine()) == null) {
                isNode = false;
                return;
            }
            firstTagStartPos = line.indexOf('<', 0);
        }

        nodeStartPos = firstTagStartPos;

        int firstTagEndPos = line.indexOf('>', firstTagStartPos) + ">".length();

        // check if this tag is a enclosing tag
        if (line.charAt(firstTagStartPos + 1) == '/') {
            isNode = false;
            firstTagStartPos++;
        }

        // find the position of the tag name
        int tagNameStartPos = firstTagStartPos + 1;
        int tagNameEndPos;

        // special judge for comments since comments could come right after tag name without space
        if (tagNameStartPos + 2 < line.length() && line.substring(tagNameStartPos, tagNameStartPos + 3).equals("!--")) {
            firstTagEndSymbol = "-->";
            tagNameEndPos = tagNameStartPos + "!--".length();
        } else {
            firstTagEndSymbol = ">";
            // find first space
            tagNameEndPos = line.indexOf(' ', firstTagStartPos);
            if (tagNameEndPos == -1) {
                // if ">" is found on current line
                if (firstTagEndPos == 0) {
                    // the tag should be right after '<'
                    // since no spaces are found after '<', the remaining of this line is the tag name
                    tagNameEndPos = line.length();
                } else {
                    tagNameEndPos = firstTagEndPos - 1;
                }
            } else {
                // if '>' is found and appears before the space
                if (firstTagEndPos != 0 && firstTagEndPos - 1 < tagNameEndPos) {
                    tagNameEndPos = firstTagEndPos - 1;
                }
                // else the ending position is remained the position of space
            }
        }

        tagName = line.substring(tagNameStartPos, tagNameEndPos).toLowerCase();

        // read the remaining of the first tag if '>' is not found in the first line
        while (firstTagEndPos == 0 && (line = br.readLine()) != null) {
            firstTagEndPos = line.indexOf(firstTagEndSymbol) + firstTagEndSymbol.length();
        }

        if (line == null) {
            nodeEndPos = 0;
            return;
        }

        line = line.trim();
        // always keep the final line the same as current line in case of unusual returning
        finalLine = line;

        // exit if this is an enclosing tag
        if (!isNode) {
            nodeEndPos = firstTagEndPos;
            return;
        }

        // this node is enclosed by self-closing method
        if (line.charAt(firstTagEndPos - "/>".length()) == '/') {
            nodeEndPos = firstTagEndPos;
            return;
        }

        // check for doctype and comments and other special tags
        if (tagName.charAt(0) == '!') {
            nodeEndPos = firstTagEndPos;
            return;
        }

        if (Arrays.asList(voidTags).contains(tagName)) {
            nodeEndPos = firstTagEndPos;
            return;
        }

        int currentPosition = firstTagEndPos;

        // ignore the content of ignored tags
        if (Arrays.asList(ignoreTags).contains(tagName)) {
            String endTagName = "</" + tagName + ">";

            // find the enclosing tag in the following lines
            int endTagPosition = line.indexOf(endTagName, currentPosition);
            while (endTagPosition == -1) {
                if ((line = br.readLine()) == null) {
                    finalLine = "";
                    return;
                }
                line = line.trim();
                currentPosition = 0;
                endTagPosition = line.indexOf(endTagName, currentPosition);
            }
            finalLine = line;
            nodeEndPos = endTagPosition + endTagName.length();
        } else {
            Node newNode;
            do {

                // find the beginning position of the next tag
                int nextTagPosition = line.indexOf('<', currentPosition);
                while (nextTagPosition == -1) {
                    if (currentPosition < line.length()) {
                        lineContent += line.substring(currentPosition);
                    }
                    text.add(lineContent);
                    if ((line = br.readLine()) == null) {
                        finalLine = "";
                        return;
                    }
                    line = line.trim();
                    lineContent = "";
                    currentPosition = 0;
                    nextTagPosition = line.indexOf('<', currentPosition);
                }

                lineContent += line.substring(currentPosition, nextTagPosition);
                newNode = new Node(line, nextTagPosition, br);

                // update current states with the new child
                line = newNode.finalLine;
                finalLine = line;
                currentPosition = newNode.nodeEndPos;

                // special case for <br> to break a line
                if (newNode.tagName.equals("br")) {
                    text.add(lineContent);
                    lineContent = "";
                }

                // if this node is not an enclosing tag and is not empty
                if (newNode.isNode && newNode.text.size() > 0) {

                    // add new node to the children list
                    children.add(newNode);
                    lineContent += newNode.text.get(0);
                    if (newNode.text.size() > 1) {
                        text.add(lineContent);
                        if (newNode.text.size() > 1) {
                            text.addAll(newNode.text.subList(1, newNode.text.size() - 1));
                        }
                        lineContent = newNode.text.get(newNode.text.size() - 1);
                    }
                }

            // ends if the first tag is enclosed with the new child
            } while(newNode.isNode || !tagName.equals(newNode.tagName));
            text.add(lineContent);
            nodeEndPos = newNode.nodeEndPos;

            // special judge for title
            if (tagName.equals("title")) {
                title = "";
                for (String titleLine : text) {
                    title += titleLine;
                }
                title = cleanSpecialEntities(title);
            }
        }
    }

    /**
     * Returns the title of the entire document
     * @return  the title of the entire document
     */
    public String getTitle() {
        return title;
    }

    /**
     * Remove all empty lines of text in a node
     * @param node  the node to clean
     */
    public static void cleanEmptyLines(Node node) {
        for (int i = node.text.size() - 1; i >= 0; --i) {
            if (node.text.get(i).trim().equals("")) {
                node.text.remove(i);
            }
        }
        for (Node child : node.children) {
            cleanEmptyLines(child);
        }
    }

    /**
     * Replace special entities with their original characters
     * @param node  the node to clean
     */
    public static void replaceEntities(Node node) {
        for (int i = 0; i < node.text.size(); ++i) {
            node.text.set(i, cleanSpecialEntities(node.text.get(i)));
        }
        for (Node child : node.children) {
            replaceEntities(child);
        }
    }

    /**
     * Replace special entites with their original characters
     * @param line  the string to clean
     * @return      the original string
     */
    public static String cleanSpecialEntities(String line) {
        for (Map.Entry<String, String> entry : specialEntities.entrySet()) {
            line = line.replaceAll(entry.getKey(), entry.getValue());
        }
        return line;
    }

    /**
     * Sum up the length of all lines of text
     * @param node  the node to count
     * @return      the total length
     */
    public static int countContentLength(Node node) {
        int count = 0;
        for (String contentLine : node.text) {
            count += contentLine.length();
        }
        return count;
    }

    /**
     * Get the main content of current node
     * @return  the array of content lines
     */
    public ArrayList<String> getContent() {
        // assert if this node is not html
        if (tagName.equals("html")) {

            // find the body tag
            Node body = null;
            for (Node child : children) {
                if (child.tagName.equals("body")) {
                    body = child;
                }
            }
            if (body == null) {
                throw new IllegalStateException("Cannot find body tag.");
            }
            if (body.text.size() == 0) {
                throw new IllegalStateException("No content found.");
            }

            // find the first node with multiple valid nodes contained
            Node mainNode = body;
            while (mainNode.children.size() == 1) {
                mainNode = mainNode.children.get(0);
            }
            if (mainNode.children.size() == 0) {
                return mainNode.text;
            }

            // find the node with max length
            Node maxNode = mainNode.children.get(0);
            int maxSize = countContentLength(maxNode);
            for (Node child : mainNode.children) {
                int contentLength = countContentLength(child);
                if (contentLength > maxSize) {
                    maxSize = contentLength;
                    maxNode = child;
                }
            }

            // create the simplified lines array and filter short lines
            ArrayList<String> finalContent = maxNode.text;
            for (int i = finalContent.size() - 1; i >= 0; --i) {
                if (finalContent.get(i).length() <= MIN_LINE_LENGTH) {
                    finalContent.remove(i);
                }
            }
            return finalContent;
        } else {
            throw new IllegalStateException("Cannot find HTML tag.");
        }
    }

}
