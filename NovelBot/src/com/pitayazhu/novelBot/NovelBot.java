package com.pitayazhu.novelBot;

import org.jsoup.Jsoup;
import org.jsoup.nodes.*;
import org.jsoup.select.Elements;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Scanner;

public class NovelBot {

    static final int TOTAL_RETRY_COUNT = 5;
    static final int CATEGORY_NUMS = 10;
    static final String BASE_URL = "http://www.tianyashuku.com/";
    static final String USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_11_1) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/45.0.2454.99 Safari/537.36";
    static final int TIMEOUT = 1000;
    static final String DEFAULT_DIR = "./books";

    enum MODE {SEARCH, DOWNLOAD};

    public static void main(String[] args) throws IOException {

        String book_name, book_url, category_url;

        Scanner in = new Scanner(System.in);

        File default_dir = new File(DEFAULT_DIR);
        if (!default_dir.exists()) {
            try {
                default_dir.mkdirs();
            } catch (SecurityException se) {
                System.out.println("Error: Cannot create directory. Message: " + se.getMessage());
                return;
            }
        }

        System.out.println("Select Mode: ");
        System.out.println("(1) Download Book by Name");
        System.out.println("(2) Download Book by Link");
        System.out.println("(3) Download All Books in a Category (May Result in IP Ban)");
        System.out.println("(0) Cancel Download and Exit");

        System.out.print("Please Select (0-3): ");
        int choice = in.nextInt();
        in.nextLine();
        switch (choice) {
            case 0:
                return;
            case 1:
                System.out.print("Please input the name of the book: ");
                book_name = in.nextLine();
                book_url = searchSite(book_name, TOTAL_RETRY_COUNT);
                if (book_url == null) {
                    System.out.println("Book not found.");
                } else {
                    System.out.println("Book link: " + book_url);
                    saveBook(book_url, DEFAULT_DIR, TOTAL_RETRY_COUNT);
                }
                break;
            case 2:
                System.out.print("Please input the URI of the book: ");
                String book_uri = in.nextLine();
                saveBook(BASE_URL + book_uri, DEFAULT_DIR, TOTAL_RETRY_COUNT);
                break;
            case 3:
                System.out.print("Please input the URI of the category homepage: ");
                String category_uri = in.nextLine();
                category_url = BASE_URL + category_uri;
                saveCategory(category_url, DEFAULT_DIR, TOTAL_RETRY_COUNT);
                break;
            default:
                System.out.println("Invalid input.");
        }
    }

    /**
     * Method to search for a certain book in the whole site by book name
     * @param target_book_name  the name of the target book
     * @param retry_count       the number of retries remaining
     * @return                  the link of the book
     * @throws IOException
     */
    private static String searchSite(String target_book_name,
                                     int retry_count) throws IOException {
        try {
            Document home_page = Jsoup.connect(BASE_URL).userAgent(USER_AGENT).timeout(TIMEOUT).get();

            Elements subpage_links = home_page.select(".book-list > div.box > div.box-title > h2 > a");
            // the last a few links in homepage are entrances to each categories
            List<Element> category_links = subpage_links.subList(subpage_links.size() - CATEGORY_NUMS,
                    subpage_links.size());

            // search each category for the book
            for (Element category_link : category_links) {
                String category_url = category_link.attr("abs:href");
                String result = searchCategory(category_url, target_book_name, TOTAL_RETRY_COUNT);
                System.out.println();
                if (result != null) {
                    System.out.println("Book found in category: " + category_link.text());
                    return result;
                }
            }
        } catch (SocketTimeoutException ste) {
            if (retry_count == 0) {
                System.out.println("Error: Homepage reading failed.");
            } else {
                System.out.println("Error: Homepage reading time out, " + retry_count + " more retry(s)...");
                return searchSite(target_book_name, retry_count - 1);
            }
        }
        return null;
    }

    /**
     * Search a category for a certain book by name
     * @param category_url      the link of the category
     * @param target_book_name  the name of the target book
     * @param retry_count       the number of retries remaining
     * @return                  the link of the book
     * @throws IOException
     */
    private static String searchCategory(String category_url,
                                         String target_book_name,
                                         int retry_count) throws IOException {
        try {
            Document category_page = Jsoup.connect(category_url).userAgent(USER_AGENT).timeout(TIMEOUT).get();

            String category_title = category_page.select(".catalog > h1").first().text();

            // get pages information
            Elements pages = category_page.select("select[name=select] option");
            Element current_page = pages.select("option[selected]").first();

            System.out.print("\rSearching category: " + category_title +
                    ", page " + (pages.indexOf(current_page) + 1) + " out of " + pages.size());

            // search current page
            Elements category_books = category_page.select(".mulu-list > ul > li");
            for (Element category_book : category_books) {
                Element book_link = category_book.select("a").first();
                String book_name = book_link.text();
                if (book_name.equals(target_book_name)) {
                    return book_link.attr("abs:href");
                }
            }

            // search in the next page if the page exist
            Element next_page = current_page.nextElementSibling();
            if (next_page != null) {
                String next_page_url = next_page.attr("abs:value");
                String result = searchCategory(next_page_url, target_book_name, TOTAL_RETRY_COUNT);
                if (result != null) {
                    return result;
                }
            }

        } catch (SocketTimeoutException ste) {
            if (retry_count == 0) {
                System.out.println("Error: Category reading failed.");
            } else {
                if (retry_count == TOTAL_RETRY_COUNT) {
                    System.out.println();
                }
                System.out.println("Error: Category reading time out, " + retry_count + " more retry(s)...");
                return searchCategory(category_url, target_book_name, retry_count - 1);
            }
        }

        return null;
    }

    /**
     * Save all books in a category to text files
     * @param category_url      the url of the category
     * @param save_directory    the root directory to save
     * @param retry_count       the numbers of retries remaining
     * @throws IOException
     */
    private static void saveCategory(String category_url, String save_directory, int retry_count) throws IOException {
        try {
            Document category_page = Jsoup.connect(category_url).userAgent(USER_AGENT).timeout(TIMEOUT).get();

            String category_title = category_page.select(".catalog > h1").first().text();

            // find page information
            Elements pages = category_page.select("select[name=select] option");
            Element current_page = pages.select("option[selected]").first();

            System.out.println("Saving category: " + category_title +
                    ", page " + (pages.indexOf(current_page) + 1) + " out of " + pages.size());
            System.out.println();

            // create save directory if not exit
            String category_directory = save_directory + "/" + category_title;
            File new_directory = new File(category_directory);
            if (!new_directory.exists()) {
                try {
                    new_directory.mkdirs();
                } catch (SecurityException se) {
                    System.out.println("Error: Cannot create directory. Message: " + se.getMessage());
                    return;
                }
            }

            // search for books in page
            Elements category_books = category_page.select(".mulu-list > ul > li");
            for (Element category_book : category_books) {
                Element book_link = category_book.select("a").first();
                String book_url = book_link.attr("abs:href");
                saveBook(book_url, category_directory, TOTAL_RETRY_COUNT);
                System.out.println();
            }

            // search for the next page if it exists
            Element next_page = current_page.nextElementSibling();
            if (next_page != null) {
                String next_page_url = next_page.attr("abs:value");
                System.out.println(next_page_url);
                saveCategory(next_page_url, save_directory, TOTAL_RETRY_COUNT);
            }

        } catch (SocketTimeoutException ste) {
            if (retry_count == 0) {
                System.out.println("Error: Category reading failed.");
            } else {
                System.out.println("Error: Category reading time out, " + retry_count + " more retry(s)...");
                saveCategory(category_url, save_directory, retry_count - 1);
            }
        }
    }

    /**
     * Save a book to a text file
     * @param book_url          the link to the url
     * @param save_directory    the directory to save the file
     * @param retry_count       the number of retries remaining
     * @throws IOException
     */
    private static void saveBook(String book_url, String save_directory, int retry_count) throws IOException {
        try {
            Document catalog_page = Jsoup.connect(book_url).userAgent(USER_AGENT).timeout(TIMEOUT).get();

            // get book name and description
            String book_title = catalog_page.select(".catalog > h1").first().text();
            Element book_description = catalog_page.select(".zuojia-summary-content > div").first();

            System.out.println("Saving book: " + book_title);

            String file_name = save_directory + '/' + book_title + ".txt";
            PrintStream writer = new PrintStream(file_name, "UTF-8");

            writer.println("书名：" + book_title);
            writer.println("内容简介：");
            Elements description_paragraphs = book_description.select("p");
            if (description_paragraphs.size() > 0) {
                for (Element paragraph : description_paragraphs) {
                    saveParagraph(paragraph, writer);
                }
            } else {
                saveParagraph(book_description, writer);
            }
            writer.println();

            // get volume list
            Elements volume_titles = catalog_page.select(".mulu-title");
            Elements volume_chapters = catalog_page.select(".mulu-list");

            for (int i = 0; i < volume_titles.size(); ++i) {
                String volume_title = volume_titles.eq(i).select("h2").first().text();
                writer.println("第" + convertNumbersToChinese(i + 1) + "卷：" + volume_title);

                // get chapter list
                Elements volume_links = volume_chapters.eq(i).select("ul > li > a");
                for (int j = 0; j < volume_links.size(); ++j) {
                    System.out.print("\rProcessing: Volume " + (i + 1) + " out of " + volume_titles.size() + ", " +
                            "Chapter " + (j + 1) + " out of " + volume_links.size());
                    saveChapter(volume_links.eq(j).attr("abs:href"), writer, TOTAL_RETRY_COUNT);
                }

                writer.println("== 第" + convertNumbersToChinese(i + 1) + "卷终 ==");
                writer.println();

                System.out.println();
            }
            System.out.println("Saved to: " + file_name);
            writer.close();
        } catch (SocketTimeoutException ste) {
            if (retry_count == 0) {
                System.out.println("Error: Catalog reading failed.");
            } else {
                System.out.println("Error: Catalog reading time out, " + retry_count + " more retry(s)...");
                saveBook(book_url, save_directory, retry_count - 1);
            }
        }
    }

    /**
     * Save a chapter to a given output stream
     * @param chapter_url   the link of the chapter
     * @param writer        the output stream to write to
     * @param retry_count   the number of retries remaining
     * @throws IOException
     */
    private static void saveChapter(String chapter_url, PrintStream writer, int retry_count) throws IOException {
        try {
            Document chapter_page = Jsoup.connect(chapter_url).userAgent(USER_AGENT).timeout(TIMEOUT).get();
            if (chapter_page.select(".content").size() == 0) {
                throw new SocketTimeoutException();
            }

            // get chapter title
            String chapter_title = chapter_page.select(".book-content > h1").first().text();
            writer.println(chapter_title);

            //save paragraphs
            Element main_content= chapter_page.select(".neirong").first();
            Elements paragraphs = main_content.select("p");
            if (paragraphs.size() == 0) {
                saveParagraph(main_content, writer);
            } else {
                for (Element paragraph : paragraphs) {
                    saveParagraph(paragraph, writer);
                }
            }
            writer.println();
        } catch (SocketTimeoutException ste) {
            if (retry_count == 0) {
                System.out.println("Error: Chapter reading failed.");
                writer.println("获取本章内容超时");
            } else {
                if (retry_count == TOTAL_RETRY_COUNT) {
                    System.out.println();
                }
                System.out.println("Error: Chapter reading time out, " + retry_count + " more retry(s)...");
                saveChapter(chapter_url, writer, retry_count - 1);
            }
        }
    }

    /**
     * Save a paragraph to a given output stream
     * @param paragraph the element of paragraph
     * @param writer    the output stream to write to
     */
    private static void saveParagraph(Element paragraph, PrintStream writer) {
        List<TextNode> text_nodes = paragraph.textNodes();
        for (TextNode node : text_nodes) {
            String line = "　　" + node.text().replace('　', ' ').trim();
            writer.println(line);
        }
    }

    /**
     * Convert decimal numbers to Chinese
     * @param x the number in decimal
     * @return  the number in Chinese
     */
    private static String convertNumbersToChinese(int x) {
        char chinese_numbers[] = {'零', '一', '二', '三', '四', '五', '六', '七', '八', '九'};
        String chinese_digits[] = {"", "十", "百", "千", "万"};
        String chinese = "";

        if (x == 0) {
            return String.valueOf(chinese_numbers[0]);
        }

        // if the number is too long to parse, return the number form
        if (String.valueOf(x).length() > 5) {
            return String.valueOf(x);
        }

        int digit = 0;
        while (x != 0) {
            int last_digit = x % 10;
            x = x / 10;
            digit += 1;
            // combine continuous zeros into one
            if (last_digit == 0) {
                while (last_digit == 0) {
                    last_digit = x % 10;
                    x = x / 10;
                    digit += 1;
                }
                // omit trailing zeros
                if (!chinese.isEmpty()) {
                    chinese = chinese_numbers[0] + chinese;
                }
            }
            chinese = chinese_numbers[last_digit] + chinese_digits[digit - 1] + chinese;
        }
        return chinese;
    }

}

