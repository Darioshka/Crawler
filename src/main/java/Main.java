import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeoutException;

import org.jsoup.select.Elements;

 @JsonAutoDetect
class News {
     public String title;
     public String data;
     public String author;
     public String text;
     News(){}
}
public class Main {

    private static final String url = "https://www.mk.ru/news/";
    private static TaskController taskController;
    public static Set<String> setHref = new LinkedHashSet<String>();


    static void getLinks(Document doc) {
        Elements elements = doc.select("li >a.news-listing__item-link");
        for (Element element : elements) {
            SendLinks(element.absUrl("href").toString());
        }
    }

    static void ParseNews(Document doc, String link) throws IOException {
        News news = new News();
        news.title = doc.getElementsByClass("article__title").text();
        news.data = doc.select("body > div.wraper > div.wraper__content > div > div.article-grid__content > main > div.article__meta > p > span:nth-child(1) > time").text();
        news.text = doc.select("body > div.wraper > div.wraper__content > div > div.article-grid__content > main > div.article__body > p").text();
        news.author = doc.select("body > div.wraper > div.wraper__content > div > div.article-grid__content > main > div.article__authors > div > ul > li > a").text();
        StringWriter writer = new StringWriter();
        ObjectMapper mapper = new ObjectMapper();
        mapper.writeValue(writer, news);

        String result = writer.toString();
        System.out.println(result);

    }

    static void SendLinks(String link) {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("rabbitmq");
        factory.setPassword("rabbitmq");
        factory.setPort(5672);
        try (Connection connection = factory.newConnection(); Channel channel = connection.createChannel()) {
            channel.queueDeclare("LINKS", false, false, true, null);
            channel.basicPublish("", "LINKS", null, link.getBytes(StandardCharsets.UTF_8));
            channel.close();
            connection.close();
        } catch (Exception e) {
            return;
        }
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
                } catch (IOException | TimeoutException e) {
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
                } catch (IOException | TimeoutException e) {
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

        public void produce() throws InterruptedException {

            taskController = new TaskController();
            Document doc = taskController.GetUrl(url);
            getLinks(doc);
            System.out.println("Работа " + Thread.currentThread().getName());

        }
        public void consume() throws InterruptedException, IOException, TimeoutException {

            try {
                ConnectionFactory factory = new ConnectionFactory();
                factory.setHost("localhost");
                factory.setUsername("rabbitmq");
                factory.setPassword("rabbitmq");
                factory.setPort(5672);
                Connection connection = factory.newConnection();
                Channel channel = connection.createChannel();

                channel.queueDeclare("LINKS", false, false, true, null);
                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                    String message = new String(delivery.getBody(), "UTF-8");
                    try {
                        String childlink = message;
                        Document newDoc = taskController.GetUrl(childlink);
                        ParseNews(newDoc, childlink);
                    } catch (Exception e) {
                        System.out.println(" error downloading page " + e.toString());
                    } finally {
                        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
                    }

                };
                channel.basicConsume("LINKS", false, deliverCallback, consumerTag -> { });
            } catch (IOException e) {
                e.printStackTrace();
            } catch (TimeoutException e) {
                e.printStackTrace();
            }
        }
    }
}
