package com.myowndev.main;

import org.json.JSONArray;
import org.json.JSONObject;

import com.mashape.unirest.http.HttpResponse;
import com.mashape.unirest.http.JsonNode;
import com.mashape.unirest.http.Unirest;
import com.mashape.unirest.http.exceptions.UnirestException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;

public class Main {
    private String BASE_URL = "https://api.telegram.org/bot<Your Token there>/";
    // You should take your token from @BotFather. Read more on telegram bots API
    private String POLLING_URL = BASE_URL + "getUpdates";
    private String SENDMESSAGE_URL = BASE_URL + "sendMessage";

    private Connection connection = null;
    private Statement statmt;
    private ResultSet resSet;
    private int lines = 0;
    private int lineCount = 0;
    private int chars = 0;
    private int eff = 0;

    private  Main() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:res/db.s3db");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        try {
            run();
        } catch (UnirestException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public HttpResponse<JsonNode> sendMessage(Integer chatId, String text) throws UnirestException {
        return Unirest
                .post(SENDMESSAGE_URL)
                .field("chat_id", chatId)
                .field("text", text).asJson();
    }
    public HttpResponse<JsonNode> getUpdates(Integer offset) throws UnirestException {
        return Unirest
                .post(POLLING_URL)
                .field("offset", offset)
                .asJson();
    }
    private void run() throws Exception {
        int last_update_id = 0;
        HttpResponse<JsonNode> response;
        statmt = connection.createStatement();
        statmt.execute("CREATE TABLE if not exists 'users' ('id' INTEGER PRIMARY KEY AUTOINCREMENT," +
                "'first_name' TEXT, 'last_name' TEXT,'chat_id' INT, 'user_id' INT, 'lines' INT," +
                "'chars' INT, 'eff' INT);");
        boolean check = false; // Check for emptyness of dataset. Maybe SQL can provide this check? Dunno. In any case, eto kostyl
        resSet = statmt.executeQuery("SELECT * from users");
        if (resSet.next()){
            check = true;
        }
        while (true) {
            response = getUpdates(last_update_id++);
            if (response.getStatus() == 200) {
                JSONArray responses = response
                        .getBody()
                        .getObject()
                        .getJSONArray("result");
                if (responses.isNull(0)) {
                    continue;
                } else {
                    last_update_id = responses
                            .getJSONObject(responses.length() - 1)
                            .getInt("update_id") + 1;
                }
                for (int i = 0; i < responses.length(); i++) {
                    JSONObject message = responses
                            .getJSONObject(i)
                            .getJSONObject("message");
                    String first_name = "";
                    if (message.getJSONObject("from").has("first_name")) {
                        first_name = message
                                .getJSONObject("from")
                                .getString("first_name");
                        first_name = first_name.replace('\"', ' ').replace('\'', ' ').replace(';', ' ');
                    }
                    String last_name = "";
                    if (message.getJSONObject("from").has("last_name")) {
                        last_name = message
                                .getJSONObject("from")
                                .getString("last_name");
                        last_name = last_name.replace('\"'', ' ').replace('\'', ' ').replace(';', ' ');
                    }
                    int chat_id = message
                            .getJSONObject("chat")
                            .getInt("id");
                    int user_id = message
                            .getJSONObject("from")
                            .getInt("id");
                    if (message.has("text") && chat_id < 0) {
                        System.out.println(first_name + ' ' + last_name);
                        String text = message.getString("text");
                        if (text.contains("\n")) lineCount++;
                        resSet = statmt.executeQuery("SELECT first_name, last_name chat_id, user_id," +
                                "lines, chars, eff FROM users where chat_id = "
                                + chat_id + " and user_id = " + user_id + ";");
                        if (resSet.next() && check == true) {
                            lines = resSet.getInt("lines");
                            chars = resSet.getInt("chars");
                            eff = Math.round(chars/lines);
                            statmt.execute("UPDATE users set first_name = '" + first_name + "'," +
                                    "last_name = '" + last_name + "', lines = "
                                    + (lines = lines + 1 + lineCount) + ", chars = " + (chars + text.length()) + "," +
                                    "eff = " + eff + " where chat_id = " + chat_id + " and user_id = "
                                    + user_id + ";");
                        } else {
                            statmt.execute("INSERT into 'users' ('first_name', 'last_name', 'chat_id'," +
                                    " 'user_id', 'lines', 'chars', 'eff') values ('" + first_name + "', '"
                                    + last_name + "', '" + (chat_id) + "', '" + user_id + "', '" + 1
                                    + lineCount + "',  '" + text.length() + "', '" + ((1 + lineCount)/text.length()) + "');");
                            check = true;

                        }
                        System.out.print(chat_id + "|" + first_name + " " + last_name + ": " + text + " {"); // Monitoring messages from command line
                        System.out.print(lines + "/" + chars + "=" + eff + "}\n");
                        if (text.startsWith("/mystats")) {
                            sendMessage(chat_id, "Your statistics:\n" +
                                    "Lines|Chars|Eff\n" +
                                    lines + "|" + chars + "|" + eff);
                        }
                        if (text.startsWith("/showtop")) {
                            resSet = statmt.executeQuery("SELECT * FROM users where chat_id = " + chat_id + " order by eff desc;");
                            ArrayList<String> temp1 = new ArrayList<String>();
                            while (resSet.next()) {
                                temp1.add(resSet.getString("first_name") + " " + resSet.getString("last_name")
                                        + "|" + resSet.getInt("lines") + "|" + resSet.getInt("chars") + "|"
                                        + resSet.getInt("eff"));
                            }
                            String temp = "";
                            for (int h = 0; h < temp1.size(); h++) {
                                temp = temp + temp1.get(h) + "\n";
                            }
                            sendMessage(chat_id, "Efficiency top for this channel:\n" +
                                    "Name|Lines|Chars|Eff\n" + temp);

                        }
                        if (text.startsWith("/help")) {
                            sendMessage(chat_id, "/showtop - Shows top chat members on this channel by efficiency" +
                                    "/mystats - Shows only yours chat statistics on this channel");
                        }
                        lineCount = 0;
                    }
                }
            }
        }
    }
    public static void main(String args[]) {
        new Main();
    }
}



























































