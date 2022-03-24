import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.*;
import org.jsoup.select.Elements;


public class Main {

    private static String url = "https://www.mk.ru/news/";
    private static TaskController taskController;
    public static Set<String> setHref = new HashSet<String>();


    static Set<String> getLinks(Document doc) {
        Set<String> urls = new HashSet<String>();
        Elements elements = doc.select("li >a.news-listing__item-link");
        for(Element element : elements){
            if(urls.contains(element)){
                break;
            }
            urls.add(element.absUrl("href"));
        }
        return urls;
    }

    static void ParseNews(Document doc, String link){
        String title = doc.getElementsByClass("article__title").text();
        String data = doc.select("body > div.wraper > div.wraper__content > div > div.article-grid__content > main > div.article__meta > p > span:nth-child(1) > time").text();
        String text = doc.select("body > div.wraper > div.wraper__content > div > div.article-grid__content > main > div.article__body > p").text();
        String author = doc.select("body > div.wraper > div.wraper__content > div > div.article-grid__content > main > div.article__authors > div > ul > li > a").text();
        System.out.println("link: " + link + System.lineSeparator() +
                            "title: " + title + System.lineSeparator() +
                            "data: " + data  + System.lineSeparator() +
                            "text: " + text  + System.lineSeparator() +
                            "author: " + author  + System.lineSeparator()

        );

    }


    public static void main(String[] args) throws InterruptedException {

        ProducerConsumer PC = new ProducerConsumer();
        Thread tMain = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PC.produce();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        Thread tConsume1 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PC.consume();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        Thread tConsume2 = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    PC.consume();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

        tMain.start();
        tConsume1.start();
        tConsume2.start();

        tMain.join();
        tConsume1.join();
        tConsume2.join();


    }

    public static class ProducerConsumer {
        int BUFF_SIZE = 5;
        public static  PriorityQueue<String> QueueLink = new PriorityQueue<String>();

        public void produce() throws InterruptedException {
            taskController = new TaskController();

            Document doc = taskController.GetUrl(url);
            setHref = getLinks(doc);
            Iterator<String> iterator = setHref.iterator();

            System.out.println("Работа " + Thread.currentThread().getName());

            while(true){
                synchronized (this){
                        while(QueueLink.size() == BUFF_SIZE){
                            System.out.println("QueueLink: " +QueueLink);
                            wait();
                        }

                        if(iterator.hasNext()){
                            String etem = iterator.next(); // Вернет "первый" элемент
                            QueueLink.add(etem);
                            iterator.remove(); // Удалит "первый" элемент
                        }

                    notify();

                    //Thread.sleep(1000);
                }
            }


        }

        public void consume() throws InterruptedException {
            while (true){
                synchronized (this){
                    while(QueueLink.size() == 0){
                        wait();
                    }
                    System.out.println("Работа " + Thread.currentThread().getName());
                    String childlink = QueueLink.poll();
                    Document newDoc = taskController.GetUrl(childlink);
                    ParseNews(newDoc, childlink);
                    notify();

                   // Thread.sleep(1000);

                }
            }
        }
    }
}
