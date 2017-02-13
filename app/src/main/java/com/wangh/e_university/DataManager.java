package com.wangh.e_university;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.select.Elements;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class DataManager {
    private final static int CLASS_TABLE = 0;
    private final static int SCORE = 1;
    private final static int EXAM = 2;
    private final static int PUBLIC_CLASSES = 3;
    private final static int CURRENT_NUMBER = 4;
    private final static int INFO = 5;
    private final static int CHOOSING_INFO = 6;
    private final static int CHOSE_CLASS = 7;


    private static boolean isGetNowScore = true;

    private SharedPreferences sharedPreferences;
    private DatabaseManager databaseManager;
    private Context context;
    private ChoosingClassesFragment targetFragment;
    private Date date;
    private int classCount;
    private ArrayList<String> classTitles;
    private ArrayList<Integer> classColors;
    private ArrayList<ClassForChoose> classForChooses;
    private boolean updated;
    private ClassesCurrentNumberUpdater currentNumberUpdater;
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CLASS_TABLE:
                    doUpdateClassTable((Document) msg.obj);
                    break;
                case SCORE:
                    doUpdateScore((Document) msg.obj);
                    break;
                case EXAM:
                    doUpdateExam((Document) msg.obj);
                    break;
                case PUBLIC_CLASSES:
                    doUpdatePublicClasses((Document) msg.obj);
                    break;
                case CURRENT_NUMBER:
                    doUpdateCurrentNumber((String) msg.obj);
                    break;
                case INFO:
                    doUpdateInfo((Document) msg.obj);
                    break;
            }
        }
    };

    public DataManager(Context context) {
        currentNumberUpdater = new ClassesCurrentNumberUpdater();
        this.context = context;
    }

    public static void setIsGetNowScore(boolean isGetNowScore) {
        DataManager.isGetNowScore = isGetNowScore;
    }

    public static boolean isGetNowScore() {
        return isGetNowScore;
    }

    public void getChoosedClass(final ChoseClassesUpdateListener updateListener) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpRequester httpRequester = new HttpRequester();
                httpRequester.setChoose(true);
                Document doc = httpRequester.get("http://xk.tjut.edu.cn/xsxk/index.xk",
                        "http://xk.tjut.edu.cn/xsxk/logout.xk",
                        false);
                doGetChoosedClass(doc, updateListener);
            }
        }).start();
    }

    private void doGetChoosedClass(Document doc, ChoseClassesUpdateListener updateListener) {
        List<ClassForChoose> result = new ArrayList<ClassForChoose>();
        int count = 0;
        for (Node node : doc.childNodes()) {
            count++;
            if (node.nodeName().equals("#comment")) {
                if (node.toString().equals("\n<!-- 加载当前已选教学班数据 -->")) {
                    List<Node> list = doc.childNodes();
                    for (int i = 0; ; i++) {
                        if (list.get(count + i).nodeName().equals("#comment")) {
                            break;
                        }
                        if (list.get(count + i).toString().split("'").length == 53) {
                            ClassForChoose aClass = new ClassForChoose();
                            aClass.parseClass(list.get(count + i).toString().split("'"));
                            result.add(aClass);
                        }
                    }
                    break;
                }
            }
        }
        updateListener.done(result);
    }

    public void getChoosingInfo(final ChoosingInfoUpdateListener updateListener) {
        Log.d("get","getChoosingInfo");
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpRequester httpRequester = new HttpRequester();
                httpRequester.setChoose(true);
                Document doc = httpRequester.get("http://xk.tjut.edu.cn/xsxk/index.xk",
                        "http://xk.tjut.edu.cn/xsxk/logout.xk",
                        false);
                doGetChoosingInfo(doc, updateListener);
            }
        }).start();
    }

    private void doGetChoosingInfo(Document doc, ChoosingInfoUpdateListener updateListener) {
        ChoosingInfo result = new ChoosingInfo();
        int count = 0;
        int credit = 0;
        for (Node i : doc.child(0).child(1).childNodes()) {
            count++;
            if (i.nodeName().equals("#comment")) {
                List<Node> list = i.parentNode().childNodes();
                switch (i.toString()) {
                    case "\n<!-- 加载学生基本信息 -->":
                        result.setGrade(list.get(count + 3).toString().split("'")[3]);
                        result.setClasses(list.get(count + 5).toString().split("'")[3]);
                        result.setDepartment(ClassInfoConst.CLASS_DEPARTMENT.get(list.get(count + 9).toString().split("'")[3]));
                        result.setMajor(ClassInfoConst.MAJOR.get(list.get(count + 11).toString().split("'")[3]));
                        result.setStdID(list.get(count + 17).toString().split("'")[3]);
                        result.setName(list.get(count + 21).toString().split("'")[3]);
                        result.setDegree(list.get(count + 29).toString().split("'")[3]);
                        break;
                    case "\n<!-- 加载选课设置信息 -->":
                        result.setMaximumCredit(Integer.parseInt(list.get(count + 31).toString().split("'")[3]));
                        break;
                    case "\n<!-- 加载学生个人方案基本信息 -->":
                        result.setLimitCredit(Integer.parseInt(list.get(count+1).toString().split("'")[17]));
                        break;
                    case "\n<!-- 加载可选课轮次基本信息 -->":
                        result.setStartTime(list.get(count+1).toString().split("'")[13]);
                        result.setEndTime(list.get(count+1).toString().split("'")[15]);
                        break;
                    case "\n<!-- 加载当前已选教学班数据 -->":
                        for (int j = 1; ; j+=2) {
                            if (list.get(count + j).nodeName().equals("#comment")) {
                                break;
                            }
                            if (list.get(count + j).toString().split("'").length == 53) {
                                credit += Double.parseDouble(list.get(count + j).toString().split("'")[21]);
                            }
                        }
                        result.setNowCredit(credit);
                        break;
                }
            }
        }
        Log.d("got ChoosingInfo", result.toString());
        updateListener.done(result);
    }

    public void updateInfo() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpRequester httpRequester = new HttpRequester();
                Message msg = new Message();
                msg.what = INFO;
                msg.obj = httpRequester.get("http://ssfw.tjut.edu.cn/ssfw/xjgl/jbxx.do");
                handler.sendMessage(msg);
            }
        }).start();
    }

    private void doUpdateInfo(Document doc) {
        sharedPreferences = context.getSharedPreferences("account", Context.MODE_APPEND);
        Element element = doc.getElementById("yxdm");
        SharedPreferences.Editor editor = sharedPreferences.edit();
//        Log.d("doUpdateInfo",CLASS_DEPARTMENT.get(element.val()));
        editor.putString("department", ClassInfoConst.CLASS_DEPARTMENT.get(element.val()));
        element = doc.getElementById("zydm");
//        Log.d("doUpdateInfo",MAJOR.get(element.val()));
        editor.putString("major", ClassInfoConst.MAJOR.get(element.val()));
        editor.apply();
    }

    public void updatePublicClasses(ChoosingClassesFragment fragment) {
        Log.d("get public classes", "start");
        updated = false;
        targetFragment = fragment;
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpRequester httpRequester = new HttpRequester();
                httpRequester.setChoose(true);
                Message msg = new Message();
                msg.what = PUBLIC_CLASSES;
                httpRequester.get("http://xk.tjut.edu.cn/xsxk/index.xk", "http://xk.tjut.edu.cn/xsxk/logout.xk", false);
                httpRequester.get("http://xk.tjut.edu.cn/xsxk/main.xk", "http://xk.tjut.edu.cn/xsxk/", false);
                httpRequester.get("http://xk.tjut.edu.cn/xsxk/loadData.xk?method=getXsLoginCnt", "http://xk.tjut.edu.cn/xsxk/main.xk", true);
                httpRequester.get("http://xk.tjut.edu.cn/xsxk/loadData.xk?method=getXksj", "http://xk.tjut.edu.cn/xsxk/main.xk", false);
                httpRequester.get("http://xk.tjut.edu.cn/xsxk/loadData.xk?method=loginCheck", "http://xk.tjut.edu.cn/xsxk/index.xk", false);
                httpRequester.get("http://xk.tjut.edu.cn/xsxk/xkjs.xk?pyfaid=01777&jxqdm=1&data-frameid=main&data-timer=2000&data-proxy=proxy.xk", "http://xk.tjut.edu.cn/xsxk/index.xk", false);
                msg.obj = httpRequester.get("http://xk.tjut.edu.cn/xsxk/qxgxk.xk", "http://xk.tjut.edu.cn/xsxk/xkjs.xk?pyfaid=01482&jxqdm=1&data-frameid=main&data-timer=2000&data-proxy=proxy.xk", true);
                handler.sendMessage(msg);
//                msg = new Message();
//                msg.what = PUBLIC_CLASSES;
//                msg.obj = httpRequester.getString("http://xk.tjut.edu.cn/xsxk/loadData.xk?method=gerJxbXkrs");
//                handler.sendMessage(msg);
            }
        }).start();
    }

    public ArrayList<ClassForChoose> getClassForChooses() {
        return classForChooses;
    }

    private void doUpdatePublicClasses(Document doc) {
        Element line;
        Elements classes = doc.getElementsByTag("script");
        String[] lines;
        String[] temp;
        ClassForChoose classForChoose;
        classForChooses = new ArrayList<ClassForChoose>();
        int classCount = 0;

        for (int i = 0; i < classes.size(); i++) {
            line = classes.get(i);
//            Log.d("class for choose",line.toString());
//            Log.d("class for choose",line.attr("src"));
            if (!line.hasAttr("src")) {
                lines = line.toString().split("'");
//                Log.d("class for choose",line.toString());
//                Log.d("line length",lines.length+"");
                if (lines.length == 51) {
                    Log.d("lines", line.toString());
                    Log.d("lines", lines[1]);
                    Log.d("lines", lines[3]);
                    Log.d("lines", lines[5]);
                    Log.d("lines", lines[29]);
                    Log.d("lines", lines[33]);
                    Log.d("lines", lines[45]);

                    classCount++;
                    classForChoose = new ClassForChoose();
                    classForChoose.parseClass(lines);
                    classForChooses.add(classForChoose);
                } else {
                    Log.d("lines", line.toString());
                    Log.d("lines", lines[7]);
                    Log.d("lines", lines[9]);
                    Log.d("lines", lines[11]);
                    classForChoose = classForChooses.get(classCount - 1);
                    classForChoose.addTime(lines);
                }
                Log.d("lines", classForChooses.get(classCount - 1).toString());
            }
        }
        for (int i = 0; i < classForChooses.size(); i++) {
            Log.d("got class for choose", classForChooses.get(i).toString());
        }
//        updated=true;
//        notifyAll();
        currentNumberUpdater.update();
    }

    public void updateCurrentNumber() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpRequester httpRequester = new HttpRequester();
                Message msg = new Message();
                httpRequester.setChoose(true);
                msg.what = CURRENT_NUMBER;
                msg.obj = httpRequester.getString("http://xk.tjut.edu.cn/xsxk/loadData.xk?method=gerJxbXkrs");
                handler.sendMessage(msg);
            }
        }).start();
    }

    private void doUpdateCurrentNumber(String date) {
        Log.d("doUpdateCurrentNumber", date);
        ClassForChoose classForChoose;
        String lines[] = date.split("\\{")[2].replace("}", "").replace("\\", "").replace("\"", "").split(",");
        for (int i = 0; i < classForChooses.size(); i++) {
            classForChoose = classForChooses.get(i);
            for (int j = 0; j < lines.length; j++) {
                //Log.d("number got",lines[j].split(":")[0]);
                if (classForChoose.getIdForChoose().equals(lines[j].split(":")[0])) {
                    classForChoose.setCurrentNumber(Integer.parseInt(lines[j].split(":")[1]));
                    Log.d("number got", classForChoose.getCurrentNumber() + "");
                }
            }
        }
        targetFragment.update();
    }

    public void updateExam() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpRequester httpRequester = new HttpRequester();
                Message msg = new Message();
                date = new Date();
                String postContent = "xnxqdm=";
                if (date.getMonth() <= 8 && date.getMonth() >= 2) {
                    postContent += (date.getYear() - 1) + "-" + date.getYear() + "-2";
                } else if (date.getMonth() > 8) {
                    postContent += date.getYear() + "-" + (date.getYear() + 1) + "-1";
                } else if (date.getMonth() < 2) {
                    postContent += (date.getYear() - 1) + "-" + date.getYear() + "-1";
                }
                msg.what = EXAM;
                msg.obj = httpRequester.post("http://ssfw.tjut.edu.cn/ssfw/xsks/kcxx.do", postContent, true);
                handler.sendMessage(msg);
            }
        }).start();
    }

    private void doUpdateExam(Document doc) {
        databaseManager = new DatabaseManager(context);
        databaseManager.deleteAllExam();
        Element table = doc.getElementsByTag("tbody").get(2);
        ExamItem aExamItem;

//        Log.d("exam",table.toString());
        table.child(0).remove();

        while (!table.children().isEmpty()) {
            aExamItem = new ExamItem();
            aExamItem.parseExam(table.child(0));
            table.child(0).remove();
            Log.d("got exam", aExamItem.toString());
            databaseManager.addExam(aExamItem);
        }
        databaseManager.closeDB();
    }

    public void updateScore() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpRequester httpRequester = new HttpRequester();
                Message msg = new Message();
                date = new Date();
                String postContent = "qXndm_ys=";
                if (isGetNowScore) {
                    if (date.getMonth() <= 8 && date.getMonth() >= 2) {
                        postContent += (date.getYear() - 1) + "-" + date.getYear();
                    } else if (date.getMonth() > 8) {
                        postContent += date.getYear() + "-" + (date.getYear() + 1);
                    } else if (date.getMonth() < 2) {
                        postContent += (date.getYear() - 1) + "-" + date.getYear();
                    }
                    postContent += "&qXqdm_ys=";
                    if (date.getMonth() <= 8 && date.getMonth() >= 2) {
                        postContent += 2;
                    } else {
                        postContent += 1;
                    }
                } else {
                    if (date.getMonth() <= 8 && date.getMonth() >= 2) {
                        postContent += (date.getYear() - 1) + "-" + date.getYear();
                    } else if (date.getMonth() > 8) {
                        postContent += (date.getYear() - 1) + "-" + date.getYear();
                    } else if (date.getMonth() < 2) {
                        postContent += (date.getYear() - 2) + "-" + (date.getYear() - 1);
                    }
                    postContent += "&qXqdm_ys=";
                    if (date.getMonth() <= 8 && date.getMonth() >= 2) {
                        postContent += 1;
                    } else {
                        postContent += 2;
                    }
                }
                postContent += "&";
                msg.what = 1;
                httpRequester.get("http://ssfw.tjut.edu.cn/ssfw/zhcx/cjxx.do");
                msg.obj = httpRequester.post("http://ssfw.tjut.edu.cn/ssfw/zhcx/cjxx.do", "opytpe=query&" +
                        "isFirst=1&" +
                        postContent +
                        "qKclbdm_ys=&" +
                        "qKch_ys=&" +
                        "qKcm_ys=&" +
                        "currentSelectTabId=01", true);
                handler.sendMessage(msg);
            }
        }).start();
    }

    private void doUpdateScore(Document doc) {
        databaseManager = new DatabaseManager(context);
        Element table = doc.getElementsByTag("tbody").get(7);
        if (table != null) {
            databaseManager.deleteAllScore();

            ScoreItem aScoreItem;

            table.child(0).remove();
//        Log.d("score",table.toString());
            if (table.child(0).child(0).text().equals("暂无记录")) {
                Log.d("doUpdateScore", "no score");
            } else while (!table.children().isEmpty()) {
                Log.d("got average credit", doc.getElementsByClass("ui_alert").first().child(0).text());
                databaseManager.updateAverageCredit(doc.getElementsByClass("ui_alert").first().child(0).text());
                aScoreItem = new ScoreItem();
                aScoreItem.parseScore(table.child(0));
                table.child(0).remove();
                Log.d("got Score", aScoreItem.toString());
                databaseManager.addScore(aScoreItem);
            }
        }


        databaseManager.closeDB();
    }

    public void updateClassTable() {
        Log.d("updateClassTable", "start");
        new Thread(new Runnable() {
            @Override
            public void run() {
                HttpRequester httpRequester = new HttpRequester();
                Message msg = new Message();
                date = new Date();
                String urlContent = "";
                if (date.getMonth() <= 8 && date.getMonth() >= 2) {
                    urlContent += (date.getYear() - 1) + "-" + date.getYear() + "-2";
                } else if (date.getMonth() > 8) {
                    urlContent += date.getYear() + "-" + (date.getYear() + 1) + "-1";
                } else if (date.getMonth() < 2) {
                    urlContent += (date.getYear() - 1) + "-" + date.getYear() + "-1";
                }

                msg.what = CLASS_TABLE;
                msg.obj = httpRequester.get("http://ssfw.tjut.edu.cn/ssfw/pkgl/kcbxx/4/" + urlContent + ".do");
//                    msg.obj = httpRequester.get("http://ssfw.tjut.edu.cn/ssfw/pkgl/kcbxx/4/2016-2017-1.do");
                handler.sendMessage(msg);
            }
        }).start();
    }

    private void doUpdateClassTable(Document doc) {
        databaseManager = new DatabaseManager(context);
        databaseManager.deleteAllClass();
        classTitles = new ArrayList<String>();
        classColors = new ArrayList<Integer>();
        Log.d("updateClassTable", "page got");
        Element table = doc.getElementsByTag("tbody").first();
        Boolean[][] haveClass = new Boolean[7][11];
        Element line;
        Element aClass;
        int classLanght;
        String tempText;
        int offset;
        classCount = 0;

        for (int i = 0; i < 7; i++) {
            for (int j = 0; j < 11; j++) {
                haveClass[i][j] = false;
            }
        }

        for (int i = 1; i <= 11; i++) {
            line = table.child(i);
            line.child(0).remove();
            line.child(0).remove();
            offset = 0;
            for (int j = 1; j <= 7; j++) {
                if (haveClass[j - 1][i - 1]) {
                    offset++;
                    continue;
                }

                aClass = line.child(j - 1 - offset);
                if (!aClass.toString().equals("<td colspan=\"1\" rowspan=\"1\">&nbsp;</td>")) {
                    classLanght = Integer.parseInt(aClass.attr("rowspan"));
                    for (int k = 0; k < classLanght; k++) {
                        haveClass[j - 1][i + k - 1] = true;
                    }
                    tempText = aClass.toString();
                    if (!aClass.getElementsByTag("hr").isEmpty()) {
                        String[] temps = tempText.split("<hr>");
                        for (int k = 0; k < temps.length; k++) {
                            if (k != 0) {
                                setClass("&nbsp;" + temps[k], i, j, classLanght);
                            } else {
                                setClass(temps[k], i, j, classLanght);
                            }
                        }
                    } else {
                        setClass(tempText, i, j, classLanght);
                    }
                }
            }
        }
        databaseManager.closeDB();
    }

    private void setClass(String theClass, int i, int date, int classLanght) {
        ClassItem aClassItem;
        String[] allInfo = new String[3];
        String[] titleAndTimeAndDate = new String[4];
        String[] titleAndId = new String[2];
        String[] week = new String[20];
        String[] whichWeek = new String[2];

        theClass = theClass.replace("\n", "");
        allInfo = theClass.split("<br>", 3);
        titleAndTimeAndDate = allInfo[0].split("&nbsp;", 4);
        titleAndId = titleAndTimeAndDate[1].split("\\[", 2);
        titleAndId[1] = titleAndId[1].replaceAll("\\] ", "");
        week = titleAndTimeAndDate[2].split(",");
        for (int k = 0; k < week.length; k++) {
            whichWeek = week[k].split("周", 2);

            aClassItem = new ClassItem();

            aClassItem.setClassTitle(titleAndId[0]);
            aClassItem.setTeacher(allInfo[1]);
            aClassItem.setClassLocation(allInfo[2].replace(" </td>", ""));
            aClassItem.setClassNumber(Integer.parseInt(titleAndId[1]));
            aClassItem.setTimeStart(i);
            aClassItem.setTimeEnd(i + classLanght - 1);
            aClassItem.setClassTime(ClassInfoConst.CLASS_START_TIME[i - 1] + "-" + ClassInfoConst.CLASS_END_TIME[i + classLanght - 2]);
            aClassItem.setWeekStart(Integer.parseInt(whichWeek[0].split("-", 2)[0]));
            aClassItem.setWeekEnd(Integer.parseInt(whichWeek[0].split("-", 2)[1]));
            aClassItem.setDate(date);
            Log.d("which week", "\"" + whichWeek[1].replace("\\s", "").replace("\\s\\s", "").replace(" ", "") + "\"");
            if (whichWeek[1].replaceAll("\\s", "").replace("\\s\\s", "").replace(" ", "").equals("(单)")) {
                aClassItem.setSingleWeek(1);
                aClassItem.setDoubleWeek(0);
            } else if (whichWeek[1].replaceAll("\\s", "").replace("\\s\\s", "").replace(" ", "").equals("(双)")) {
                aClassItem.setSingleWeek(0);
                aClassItem.setDoubleWeek(1);
            } else {
                aClassItem.setSingleWeek(1);
                aClassItem.setDoubleWeek(1);
            }

            boolean found = false;
            for (int j = 0; j < classTitles.size(); j++) {
                if (aClassItem.getClassTitle().equals(classTitles.get(j))) {
                    found = true;
                    aClassItem.setColorID(classColors.get(j));
                }
            }
            if (!found) {
                aClassItem.setColorID(classCount);
                classColors.add(classCount);
                classCount++;
                classTitles.add(aClassItem.getClassTitle());
            }
            Log.d("got class", aClassItem.toString());
            databaseManager.addClass(aClassItem);
        }
    }

    public interface UpdateListener {
        void update();
    }

    public interface ChoosingInfoUpdateListener {
        void done(ChoosingInfo choosingInfo);
    }

    public interface ChoseClassesUpdateListener {
        void done(List<ClassForChoose> result);
    }

    private class ClassesCurrentNumberUpdater implements UpdateListener {
        @Override
        public void update() {
            updateCurrentNumber();
        }
    }
}